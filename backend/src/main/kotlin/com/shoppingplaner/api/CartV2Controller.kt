package com.shoppingplaner.api

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.shoppingplaner.dto.*
import com.shoppingplaner.model.Cart
import com.shoppingplaner.model.CartItem
import com.shoppingplaner.repository.CartItemRepository
import com.shoppingplaner.repository.CartRepository
import com.shoppingplaner.security.PRINCIPAL_ATTR
import com.shoppingplaner.security.AuthPrincipal
import com.shoppingplaner.service.BasketItem
import com.shoppingplaner.service.CartService
import com.shoppingplaner.service.DeliveryPriceCalculationService
import com.shoppingplaner.service.PriceComparisonService
import com.shoppingplaner.service.ProductSearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Tag(name = "Cart v2", description = "Single-user shopping cart management — /api/v2/cart")
@RestController
@RequestMapping("/api/v2/cart")
class CartV2Controller(
    private val cartRepo: CartRepository,
    private val cartItemRepo: CartItemRepository,
    private val cartService: CartService,
    private val comparisonService: PriceComparisonService,
    private val objectMapper: ObjectMapper,
    private val productSearchService: ProductSearchService,
    private val deliveryServices: List<DeliveryPriceCalculationService>
) {

    private val selectionsType = object : TypeReference<Map<String, StoreSelectionDto>>() {}

    private fun parseSelections(json: String?): Map<String, StoreSelectionDto> =
        if (json.isNullOrBlank()) emptyMap()
        else try { objectMapper.readValue(json, selectionsType) } catch (_: Exception) { emptyMap() }

    // ── Cart CRUD ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get the current user's cart")
    @GetMapping
    @Transactional
    fun getCart(request: HttpServletRequest): ResponseEntity<CartV2Dto> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val cart = getOrCreateCart(userId)
        return ResponseEntity.ok(cart.toDto())
    }

    @Operation(summary = "Clear all items from the cart")
    @DeleteMapping
    @Transactional
    fun clearCart(request: HttpServletRequest): ResponseEntity<Void> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val cart = getOrCreateCart(userId)
        cart.items.clear()
        cartRepo.save(cart)
        return ResponseEntity.noContent().build()
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Add an item to the cart",
        description = "Provide either a free-text `query` or a specific `product_id` (e.g. \"rewe:12345\"), " +
                "plus optional quantity and filters."
    )
    @PostMapping("/items")
    @Transactional
    fun addItem(@RequestBody req: AddCartItemRequest, request: HttpServletRequest): ResponseEntity<CartItemV2Dto> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val query = req.query ?: req.productId
            ?: return ResponseEntity.badRequest().build()

        val cart = getOrCreateCart(userId)
        val item = CartItem(
            name              = query,
            quantity          = req.quantity.toDouble(),
            unit              = req.filters?.volume ?: "stk",
            productId         = req.productId,
            tagsFilter        = req.filters?.tags?.joinToString(","),
            catalogProductId  = req.catalogProductId,
            cart              = cart
        )
        val savedItem = cartItemRepo.save(item)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedItem.toDto(req.filters))
    }

    @Operation(summary = "Update quantity or filters for a cart item")
    @PatchMapping("/items/{itemId}")
    @Transactional
    fun updateItem(
        @PathVariable itemId: Long,
        @RequestBody req: UpdateCartItemV2Request,
        request: HttpServletRequest
    ): ResponseEntity<CartItemV2Dto> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val cart = getOrCreateCart(userId)
        val idx = cart.items.indexOfFirst { it.id == itemId }
        if (idx < 0) return ResponseEntity.notFound().build()

        val existing = cart.items[idx]
        val newFilters = req.filters
        val updatedSelections = req.storeSelection?.let { sel ->
            val current = parseSelections(existing.storeSelectionsJson).toMutableMap()
            current[sel.store.lowercase()] = sel
            objectMapper.writeValueAsString(current)
        } ?: existing.storeSelectionsJson
        val updated = existing.copy(
            quantity             = (req.quantity ?: existing.quantity.toInt()).toDouble(),
            unit                 = newFilters?.volume ?: existing.unit,
            tagsFilter           = newFilters?.tags?.joinToString(",") ?: existing.tagsFilter,
            storeSelectionsJson  = updatedSelections
        )
        cartItemRepo.save(updated)
        return ResponseEntity.ok(updated.toDto(req.filters))
    }

    @Operation(summary = "Remove an item from the cart")
    @DeleteMapping("/items/{itemId}")
    @Transactional
    fun removeItem(@PathVariable itemId: Long, request: HttpServletRequest): ResponseEntity<Void> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val cart = getOrCreateCart(userId)
        return if (cart.items.removeIf { it.id == itemId }) {
            cartRepo.save(cart)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    // ── Comparison ────────────────────────────────────────────────────────────

    @Operation(
        summary = "Compare cart prices across stores",
        description = "Runs AI-assisted product matching against REWE and Picnic and returns a " +
                "per-store cost breakdown with match scores."
    )
    @GetMapping("/comparison")
    fun getComparison(request: HttpServletRequest): ResponseEntity<CartComparisonResponseV2> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val picnicToken = (request.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess)?.picnicToken

        // Load cart data in a short transaction — connection is released before external API calls.
        val snapshot = cartService.loadOrCreateCart(userId)

        if (snapshot.items.isEmpty())
            return ResponseEntity.ok(
                CartComparisonResponseV2(
                    cartId  = snapshot.cartId.toString(),
                    stores  = emptyList(),
                    summary = ComparisonSummaryDto(null, null, null)
                )
            )

        // Merge duplicate cart items (same query name) so the comparison sees unique products
        // with summed quantities instead of multiple separate entries.
        val mergedItems = snapshot.items
            .groupBy { it.name }
            .map { (name, entries) -> Triple(name, entries.sumOf { it.quantity }, entries.first().unit) }

        // No DB connection held from here — external API calls take 5–15 s.
        val compareReq = CompareRequest(
            products = mergedItems.map { (name, qty, unit) -> ProductDto(name = name, quantity = qty, unit = unit) }
        )
        val result = comparisonService.compare(compareReq, picnicToken)

        val itemsByName = snapshot.items.associateBy { it.name }

        val deliveryByStore = deliveryServices.associateBy { it.store }

        val storeResults = result.stores.map { storeResult ->
            val storeId = storeResult.store.lowercase()

            data class ItemEntry(val dto: ComparisonItemV2Dto, val price: Double, val qty: Double, val productId: String?)

            val entries = storeResult.items.mapIndexed { i, matched ->
                val (mergedName, mergedQty, _) = mergedItems.getOrNull(i) ?: Triple("", 1.0, "stk")
                val snap      = itemsByName[mergedName]
                val selection = snap?.let { parseSelections(it.storeSelectionsJson)[storeId] }

                if (selection != null && (selection.price ?: 0.0) > 0) {
                    val selPrice = selection.price!!
                    ItemEntry(
                        dto = ComparisonItemV2Dto(
                            cartItemId        = snap.id.toString(),
                            product           = UnifiedProductDto(
                                id        = "$storeId:sel:${snap.id}",
                                name      = selection.name,
                                imageUrl  = selection.imageUrl ?: "",
                                prices    = listOf(StorePriceV2Dto(store = storeId, price = selPrice, unit = "stk")),
                                bestPrice = BestPriceV2Dto(store = storeId, price = selPrice)
                            ),
                            match             = MatchScoreDto(score = 1.0, level = "exact"),
                            status            = "available",
                            alternativesCount = 0
                        ),
                        price     = selPrice,
                        qty       = mergedQty,
                        productId = selection.productId
                    )
                } else {
                    val isAvail = matched.price > 0
                    ItemEntry(
                        dto = ComparisonItemV2Dto(
                            cartItemId        = snap?.id?.toString() ?: i.toString(),
                            product           = if (isAvail) buildUnifiedProduct(matched, storeResult.store) else null,
                            match             = if (isAvail) MatchScoreDto(score = 0.85, level = "high") else null,
                            status            = if (isAvail) "available" else "missing",
                            alternativesCount = 0
                        ),
                        price     = matched.price,
                        qty       = mergedQty,
                        productId = matched.url?.trimEnd('/')?.substringAfterLast('/')
                    )
                }
            }

            val total     = entries.sumOf { it.price * it.qty }
            val available = entries.count { it.dto.status == "available" }
            val missing   = entries.count { it.dto.status == "missing" }

            val basketItems = entries
                .filter { it.dto.status == "available" && it.price > 0 }
                .map { BasketItem(productId = it.productId, name = it.dto.product?.name ?: "", price = it.price, quantity = it.qty.toInt().coerceAtLeast(1)) }

            val deliveryCost = deliveryByStore[storeId]?.calculate(basketItems)
            val delivery = deliveryCost?.let { dc ->
                DeliveryCostDto(
                    fee           = dc.fee,
                    isFree        = dc.isFree,
                    minimumOrder  = dc.minimumOrderAmount,
                    freeThreshold = dc.freeDeliveryThreshold,
                    amountToFree  = dc.amountToFreeDelivery,
                    hint          = dc.hint
                )
            }

            StoreComparisonResultV2Dto(
                store               = storeId,
                storeName           = storeResult.store,
                total               = total,
                totalWithDelivery   = total + (deliveryCost?.fee ?: 0.0),
                availableItems      = available,
                missingItems        = missing,
                items               = entries.map { it.dto },
                missing             = entries.mapIndexedNotNull { i, entry ->
                    if (entry.dto.status == "missing") {
                        val mergedName = mergedItems.getOrNull(i)?.first
                        val snap       = mergedName?.let { itemsByName[it] }
                        MissingItemV2Dto(
                            cartItemId        = snap?.id?.toString() ?: i.toString(),
                            query             = mergedName ?: entry.dto.cartItemId,
                            alternativesCount = 0
                        )
                    } else null
                },
                delivery = delivery
            )
        }

        val validTotals = storeResults.filter { it.availableItems > 0 }
        val cheapest    = validTotals.minByOrNull { it.totalWithDelivery }
        val mostExp     = validTotals.maxByOrNull { it.totalWithDelivery }

        return ResponseEntity.ok(
            CartComparisonResponseV2(
                cartId  = snapshot.cartId.toString(),
                stores  = storeResults,
                summary = ComparisonSummaryDto(
                    cheapestStore      = cheapest?.store,
                    mostExpensiveStore = mostExp?.store,
                    maxSavings         = if (cheapest != null && mostExp != null && cheapest != mostExp)
                        "%.2f".format(mostExp.totalWithDelivery - cheapest.totalWithDelivery).toDouble() else null
                )
            )
        )
    }

    @Operation(summary = "Get alternative products for a cart item at a specific store")
    @GetMapping("/comparison/{storeId}/items/{cartItemId}/alternatives")
    fun getAlternatives(
        @PathVariable storeId: String,
        @PathVariable cartItemId: String,
        request: HttpServletRequest
    ): ResponseEntity<AlternativesResponseDto> {
        resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val itemId = cartItemId.toLongOrNull()
            ?: return ResponseEntity.badRequest().build()
        val item = cartItemRepo.findById(itemId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // rewe_online is the store key used internally by ProductSearchService
        val storeKey = if (storeId.lowercase() == "rewe") "rewe_online" else storeId.lowercase()

        val searchResult = productSearchService.search(item.name, 1, 10)

        val alternatives = searchResult.items
            .filter { p -> p.prices.any { it.store == storeKey } }
            .map { p ->
                val storePrice = p.prices.first { it.store == storeKey }
                AlternativeItemDto(
                    product = UnifiedProductDto(
                        id        = p.id,
                        name      = p.name,
                        imageUrl  = p.imageUrl,
                        prices    = listOf(StorePriceV2Dto(store = storeId.lowercase(), price = storePrice.price, unit = storePrice.unit)),
                        bestPrice = BestPriceV2Dto(store = storeId.lowercase(), price = storePrice.price)
                    ),
                    match = MatchScoreDto(score = 0.8, level = "high")
                )
            }

        return ResponseEntity.ok(AlternativesResponseDto(alternatives = alternatives))
    }

    // ── Order & Share ─────────────────────────────────────────────────────────

    @Operation(summary = "Initiate an order at the given store")
    @PostMapping("/order/{storeId}")
    fun orderAtStore(@PathVariable storeId: String, request: HttpServletRequest): ResponseEntity<OrderResponseDto> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val cart   = getOrCreateCart(userId)

        return when (storeId.lowercase()) {
            "rewe" -> ResponseEntity.ok(
                OrderResponseDto(type = "deeplink", deeplink = "rewe://cart?items=${cart.items.joinToString(",") { it.name }}")
            )
            "picnic" -> ResponseEntity.ok(
                OrderResponseDto(type = "api_order", orderId = "ord_${System.currentTimeMillis()}", redirectUrl = "https://picnic.app")
            )
            else -> ResponseEntity.badRequest().build()
        }
    }

    @Operation(summary = "Generate a shareable link or text for the cart")
    @PostMapping("/share")
    fun shareCart(@RequestBody req: ShareRequest, request: HttpServletRequest): ResponseEntity<ShareResponseDto> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val cart   = getOrCreateCart(userId)
        val text   = "Meine Einkaufsliste:\n" + cart.items.joinToString("\n") { "- ${it.name} (${it.quantity.toInt()}x)" }
        val link   = "https://app.example.com/shared/${cart.id}"
        return ResponseEntity.ok(
            when (req.format) {
                "text" -> ShareResponseDto(text = text)
                "pdf"  -> ShareResponseDto(pdfUrl = "$link.pdf")
                else   -> ShareResponseDto(link = link)
            }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveUserId(request: HttpServletRequest): String? =
        (request.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess)?.userId

    private fun getOrCreateCart(userId: String): Cart {
        return cartRepo.findFirstByUserIdOrderByIdAsc(userId).orElseGet {
            try {
                cartRepo.save(Cart(name = "My Cart", userId = userId))
            } catch (_: DataIntegrityViolationException) {
                // Concurrent request just created the cart — read it back.
                cartRepo.findFirstByUserIdOrderByIdAsc(userId).orElseThrow()
            }
        }
    }

    private fun Cart.toDto() = CartV2Dto(
        id        = id.toString(),
        userId    = userId ?: "",
        items     = items.map { it.toDto() },
        updatedAt = Instant.now().toString()
    )

    private fun CartItem.toDto(filters: CartItemFiltersDto? = null) = CartItemV2Dto(
        id              = id.toString(),
        query           = name,
        quantity        = quantity.toInt(),
        filters         = filters ?: CartItemFiltersDto(
            tags   = tagsFilter?.split(",")?.filter { it.isNotBlank() },
            volume = unit.takeIf { it != "stk" }
        ),
        storeSelections = parseSelections(storeSelectionsJson).takeIf { it.isNotEmpty() }
    )

    private fun buildUnifiedProduct(matched: MatchedItemDto, store: String): UnifiedProductDto {
        val storeId = store.lowercase()
        return UnifiedProductDto(
            id        = "$storeId:${matched.matchedName.replace(" ", "-").lowercase()}",
            name      = matched.matchedName,
            imageUrl  = matched.imageUrl,
            prices    = listOf(StorePriceV2Dto(store = storeId, price = matched.price, unit = "stk")),
            bestPrice = BestPriceV2Dto(store = storeId, price = matched.price)
        )
    }
}
