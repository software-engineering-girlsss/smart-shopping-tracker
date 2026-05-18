package com.shoppingplaner.service

import com.shoppingplaner.dto.BestPriceV2Dto
import com.shoppingplaner.dto.StorePriceV2Dto
import com.shoppingplaner.dto.UnifiedProductDto
import com.shoppingplaner.model.PicnicProduct
import com.shoppingplaner.model.Product
import com.shoppingplaner.model.ReweProduct
import com.shoppingplaner.model.StoreItem
import com.shoppingplaner.repository.PicnicProductRepository
import com.shoppingplaner.repository.ProductRepository
import com.shoppingplaner.repository.ReweProductRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProductCatalogService(
    private val productRepo: ProductRepository,
    private val reweProductRepo: ReweProductRepository,
    private val picnicProductRepo: PicnicProductRepository,
    private val categoryMatcher: CategoryMatcherService,
) {
    private val log = LoggerFactory.getLogger(ProductCatalogService::class.java)

    data class ParsedUnit(val amount: Double, val unit: String)

    fun parseUnit(rawUnit: String): ParsedUnit {
        val clean = rawUnit.lowercase().replace(",", ".").trim()
        val m = Regex("""(\d+(?:\.\d+)?)\s*(ml|l|g|kg|stk|st\.?|stück|stk\.|piece|pieces|x)?""")
            .find(clean)
        if (m != null) {
            val amount = m.groupValues[1].toDoubleOrNull() ?: 1.0
            return when (m.groupValues[2].trimEnd('.')) {
                "l"              -> ParsedUnit(amount, "l")
                "kg"             -> ParsedUnit(amount, "kg")
                "ml"             -> ParsedUnit(amount, "ml")
                "g"              -> ParsedUnit(amount, "g")
                "stück", "piece", "pieces", "x" -> ParsedUnit(amount, "stck")
                else             -> ParsedUnit(amount, "stck")
            }
        }
        return ParsedUnit(1.0, "stck")
    }

    fun normalizeForDedup(name: String): String =
        name.lowercase().trim().replace(Regex("\\s+"), " ")

    @Transactional
    fun upsertReweProduct(item: StoreItem, reweId: String, normalizedName: String): ReweProduct {
        val existing = reweProductRepo.findByReweId(reweId)
        if (existing != null) {
            val updated = existing.copy(
                lastPrice = item.price,
                available = true,
                lastUpdated = Instant.now()
            )
            return reweProductRepo.save(updated)
        }

        val parsed = parseUnit(item.unit)
        val catalog = findOrCreateProduct(item.name, normalizedName, item.imageUrl, parsed.amount, parsed.unit)

        val reweProduct = ReweProduct(
            catalog = catalog,
            reweId = reweId,
            reweUrl = item.url,
            lastPrice = item.price,
            available = true,
            lastUpdated = Instant.now()
        )
        log.debug("Persisting new REWE product: {} (rewe_id={})", item.name, reweId)
        return reweProductRepo.save(reweProduct)
    }

    @Transactional
    fun upsertPicnicProduct(item: StoreItem, picnicId: String, normalizedName: String): PicnicProduct {
        val existing = picnicProductRepo.findByPicnicId(picnicId)
        if (existing != null) {
            val updated = existing.copy(
                lastPrice = item.price,
                available = true,
                lastUpdated = Instant.now()
            )
            return picnicProductRepo.save(updated)
        }

        val parsed = parseUnit(item.unit)
        val catalog = findOrCreateProduct(item.name, normalizedName, item.imageUrl, parsed.amount, parsed.unit)

        val picnicProduct = PicnicProduct(
            catalog = catalog,
            picnicId = picnicId,
            picnicUrl = item.url,
            lastPrice = item.price,
            available = true,
            lastUpdated = Instant.now()
        )
        log.debug("Persisting new Picnic product: {} (picnic_id={})", item.name, picnicId)
        return picnicProductRepo.save(picnicProduct)
    }

    private fun findOrCreateProduct(
        displayName: String,
        normalizedName: String,
        imageUrl: String,
        quantityAmount: Double,
        quantityUnit: String
    ): Product {
        val existing = productRepo.findByNormalizedNameAndQuantityUnitAndQuantityAmount(
            normalizedName, quantityUnit, quantityAmount
        )
        if (existing != null) return existing

        val category = runCatching { categoryMatcher.match(displayName) }.getOrNull()

        val product = Product(
            id = UUID.randomUUID().toString(),
            name = displayName,
            normalizedName = normalizedName,
            imageUrl = imageUrl.ifBlank { null },
            quantityAmount = quantityAmount,
            quantityUnit = quantityUnit,
            category = category,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        return productRepo.save(product)
    }

    fun findByReweId(reweId: String): Product? =
        reweProductRepo.findByReweId(reweId)?.catalog

    fun findByPicnicId(picnicId: String): Product? =
        picnicProductRepo.findByPicnicId(picnicId)?.catalog

    fun buildUnified(product: Product): UnifiedProductDto? {
        val rewePrices = reweProductRepo.findByCatalogId(product.id)
            .filter { it.available && it.lastPrice != null }
            .map { StorePriceV2Dto(store = "rewe_online", price = it.lastPrice!!, unit = "${product.quantityAmount} ${product.quantityUnit}", available = true) }
        val picnicPrices = picnicProductRepo.findByCatalogId(product.id)
            .filter { it.available && it.lastPrice != null }
            .map { StorePriceV2Dto(store = "picnic", price = it.lastPrice!!, unit = "${product.quantityAmount} ${product.quantityUnit}", available = true) }

        val prices = rewePrices + picnicPrices
        if (prices.isEmpty()) return null

        val best = prices.minByOrNull { it.price }!!
        return UnifiedProductDto(
            id       = product.id,
            name     = product.name,
            imageUrl = product.imageUrl ?: "",
            category = product.category?.slug ?: "",
            prices   = prices,
            bestPrice = BestPriceV2Dto(store = best.store, price = best.price)
        )
    }

    fun findProductsByCategory(categoryId: Long, limit: Int = 50): List<UnifiedProductDto> =
        productRepo.findByCategoryId(categoryId)
            .take(limit)
            .mapNotNull { buildUnified(it) }

    fun findRecentFeaturedFromDb(perCategory: Int = 5): List<UnifiedProductDto> {
        val products = productRepo.findRecentWithCategory(PageRequest.of(0, 200))
        val seenCategories = mutableMapOf<Long, Int>()
        return products
            .filter { p ->
                val catId = p.category?.id ?: return@filter false
                val count = seenCategories.getOrDefault(catId, 0)
                if (count < perCategory) { seenCategories[catId] = count + 1; true } else false
            }
            .mapNotNull { buildUnified(it) }
    }
}
