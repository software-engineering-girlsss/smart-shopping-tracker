package com.shoppingplaner.api

import com.shoppingplaner.dto.*
import com.shoppingplaner.repository.PicnicProductRepository
import com.shoppingplaner.repository.ProductRepository
import com.shoppingplaner.repository.ReweProductRepository
import com.shoppingplaner.service.ProductRefreshService
import com.shoppingplaner.service.ProductSearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Products v2", description = "Product search and details — /api/v2/products")
@RestController
@RequestMapping("/api/v2/products")
class ProductsController(
    private val productSearchService: ProductSearchService,
    private val productRepo: ProductRepository,
    private val reweProductRepo: ReweProductRepository,
    private val picnicProductRepo: PicnicProductRepository,
    private val refreshService: ProductRefreshService
) {

    @Operation(
        summary = "Search products across stores",
        description = "Searches REWE and Picnic in parallel and returns unified product objects with per-store prices."
    )
    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) tags: String?,
        @RequestParam(required = false) stores: String?,
        @RequestParam(defaultValue = "1")  page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "relevance") sort: String
    ): ProductsPageResponseV2 {
        val result = productSearchService.search(q, page, limit)

        val tagFilter   = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val storeFilter = stores?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

        val unified = result.items.map { it.toUnified() }

        val filters = AvailableFiltersDto(
            tags   = TagsController.TAGS.map { TagFilterDto(it.id, it.name, 0) },
            stores = StoresController.STORES.map { StoreFilterDto(it.id, it.name, result.total) },
            priceRange = unified.flatMap { p -> p.prices.map { it.price } }
                .takeIf { it.isNotEmpty() }
                ?.let { prices -> PriceRangeDto(min = prices.min(), max = prices.max()) }
        )

        return ProductsPageResponseV2(items = unified, total = result.total, page = result.page, availableFilters = filters)
    }

    @Operation(summary = "Get full product details by ID (store:id format)")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ResponseEntity<UnifiedProductDto> {
        // Derive the query from the last part of the ID (e.g. "rewe:milk-1l" → "milk")
        val query = id.substringAfter(":").replace("-", " ")
        val result = productSearchService.search(query, 1, 1)
        val product = result.items.firstOrNull()?.toUnified()
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(product.copy(id = id))
    }

    @Operation(summary = "Featured products for the home screen")
    @GetMapping("/featured")
    fun featured(): List<UnifiedProductDto> = productSearchService.getFeatured().map { it.toUnified() }

    @Operation(summary = "Refresh store prices for a product in the catalog")
    @PostMapping("/{catalogId}/refresh")
    fun refresh(@PathVariable catalogId: String): ResponseEntity<Map<String, String>> {
        val product = productRepo.findById(catalogId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val reweProducts = reweProductRepo.findByCatalogId(catalogId)
        val picnicProducts = picnicProductRepo.findByCatalogId(catalogId)

        reweProducts.forEach { rp ->
            runCatching { refreshService.refreshReweProduct(rp.id!!) }
        }
        picnicProducts.forEach { pp ->
            runCatching { refreshService.refreshPicnicProduct(pp.id!!) }
        }

        return ResponseEntity.ok(mapOf("status" to "refreshed", "catalog_id" to catalogId))
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun com.shoppingplaner.dto.FrontendProductDto.toUnified() = UnifiedProductDto(
        id       = id,
        name     = name,
        brand    = brand,
        imageUrl = imageUrl,
        category = category,
        prices   = prices.map { sp ->
            StorePriceV2Dto(store = sp.store, price = sp.price, unit = sp.unit, available = sp.available)
        },
        bestPrice = BestPriceV2Dto(store = bestPrice.store, price = bestPrice.price),
        match     = score?.let { MatchScoreDto(score = it, level = matchLevel(it)) }
    )

    private fun matchLevel(score: Double) = when {
        score >= 0.95 -> "exact"
        score >= 0.80 -> "high"
        score >= 0.60 -> "partial"
        else          -> "low"
    }
}
