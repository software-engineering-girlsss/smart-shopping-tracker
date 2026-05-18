package com.shoppingplaner.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.shoppingplaner.dto.BestPriceDto
import com.shoppingplaner.dto.FrontendProductDto
import com.shoppingplaner.dto.FrontendSearchResponse
import com.shoppingplaner.dto.StorePriceDto
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.model.StoreItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Adapter that runs raw store searches and composes frontend-compatible Product objects
 * with a `prices` array across stores (mirrors the TypeScript Product type).
 */
@Service
class ProductSearchService(
    private val reweService: ReweService,
    private val picnicService: PicnicService,
    private val translationService: TranslationService,
    private val catalogService: ProductCatalogService,
) {
    private val log = LoggerFactory.getLogger(ProductSearchService::class.java)

    // Platform threads avoid virtual-thread pinning caused by OkHttp's internal synchronized
    // blocks (ConnectionPool etc). Pinned virtual threads exhaust the carrier-thread pool on
    // resource-constrained hosts (Render free tier: 1-2 CPUs), blocking ALL request handling.
    private val executor = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() * 4).coerceAtLeast(8),
        Thread.ofPlatform().name("search-worker-", 0).factory()
    )

    // Prevents concurrent bursts of getFeatured() from each spawning 32 threads and
    // spiking heap. One warm result set shared for 2 minutes is enough for a home screen.
    private val featuredCache = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .build<String, List<FrontendProductDto>>()

    private val STORE_TIMEOUT_S = 22L
    private val MIN_FEATURED = 10

    fun search(query: String, page: Int = 1, limit: Int = 20): FrontendSearchResponse {
        val germanQuery = translationService.toGerman(query)
        val product = SearchQuery(germanQuery, 1.0, "stk")

        val reweFuture   = executor.submit(Callable { reweService.search(product) })
        val picnicFuture = executor.submit(Callable { picnicService.search(product) })

        val reweItems = try {
            reweFuture.get(STORE_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            log.warn("REWE search timed out for '{}'", germanQuery)
            reweFuture.cancel(true)
            emptyList()
        }
        val picnicItems = try {
            picnicFuture.get(STORE_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            log.warn("Picnic search timed out for '{}'", germanQuery)
            picnicFuture.cancel(true)
            emptyList()
        }

        val products = mergeByNameAndUnit(reweItems, picnicItems, germanQuery, limit)

        executor.submit {
            try {
                persistResults(reweItems, picnicItems, germanQuery)
            } catch (e: Exception) {
                log.debug("Background persistence failed (non-critical): {}", e.message)
            }
        }

        val offset = (page - 1) * limit
        val paged  = products.drop(offset).take(limit)

        return FrontendSearchResponse(items = paged, total = products.size, page = page)
    }

    fun getFeatured(): List<FrontendProductDto> {
        return featuredCache.get("featured") { buildFeatured() }!!
    }

    private fun buildFeatured(): List<FrontendProductDto> {
        // Prefer DB products (populated by daily job) — they carry category info.
        val dbProducts = runCatching { catalogService.findRecentFeaturedFromDb(perCategory = 5) }.getOrElse { emptyList() }
        if (dbProducts.size >= MIN_FEATURED) {
            return dbProducts.map { it.toFrontend() }
        }

        // Fall back to live searches for the initial state before the daily job runs.
        val defaultQueries = listOf("Milch", "Butter", "Eier", "Brot", "Joghurt", "Käse", "Apfel", "Tomate")
        val futures = defaultQueries.map { q -> executor.submit(Callable { search(q).items.take(3) }) }
        val liveItems = futures.flatMap { f ->
            try {
                f.get(STORE_TIMEOUT_S + 5, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                f.cancel(true)
                emptyList()
            }
        }

        if (dbProducts.isEmpty()) return liveItems
        val seen = dbProducts.map { it.id }.toSet()
        return dbProducts.map { it.toFrontend() } + liveItems.filter { it.id !in seen }
    }

    private fun com.shoppingplaner.dto.UnifiedProductDto.toFrontend() = FrontendProductDto(
        id             = id,
        name           = name,
        normalizedName = name,
        brand          = brand,
        imageUrl       = imageUrl,
        category       = category,
        prices         = prices.map { com.shoppingplaner.dto.StorePriceDto(it.store, it.price, it.unit, it.available) },
        bestPrice      = com.shoppingplaner.dto.BestPriceDto(bestPrice.store, bestPrice.price)
    )

    private fun persistResults(reweItems: List<StoreItem>, picnicItems: List<StoreItem>, normalizedQuery: String) {
        reweItems.forEach { item ->
            val reweId = extractReweId(item.url) ?: return@forEach
            runCatching {
                catalogService.upsertReweProduct(item, reweId, normalizedQuery)
            }.onFailure { log.debug("Failed to persist REWE product {}: {}", reweId, it.message) }
        }
        picnicItems.forEach { item ->
            val picnicId = extractPicnicId(item.url) ?: return@forEach
            runCatching {
                catalogService.upsertPicnicProduct(item, picnicId, normalizedQuery)
            }.onFailure { log.debug("Failed to persist Picnic product {}: {}", picnicId, it.message) }
        }
    }

    private fun extractReweId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Regex("""/p/(\d+)""").find(url)?.groupValues?.get(1)
            ?: Regex("""\b(\d{6,})\b""").find(url)?.groupValues?.get(1)
    }

    private fun extractPicnicId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trimEnd('/').substringAfterLast('/')
            .takeIf { it.isNotBlank() && it.length >= 4 }
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    private fun mergeByNameAndUnit(
        reweItems: List<StoreItem>,
        picnicItems: List<StoreItem>,
        query: String,
        limit: Int
    ): List<FrontendProductDto> {
        val usedPicnicIndices = mutableSetOf<Int>()
        val products = mutableListOf<FrontendProductDto>()

        for (rewe in reweItems) {
            // Find the best Picnic item: same quantity/unit AND highest name similarity
            val bestPicnic = picnicItems
                .mapIndexed { idx, it -> idx to it }
                .filter { (idx, _) -> idx !in usedPicnicIndices }
                .filter { (_, picnic) -> quantitiesMatch(rewe.unit, picnic.unit) }
                .maxByOrNull { (_, picnic) -> nameSimilarity(rewe.name, picnic.name) }
                ?.takeIf { (_, picnic) -> nameSimilarity(rewe.name, picnic.name) >= 0.5 }

            if (bestPicnic != null) usedPicnicIndices.add(bestPicnic.first)

            val prices = buildList {
                add(StorePriceDto("rewe_online", rewe.price, rewe.unit, true))
                bestPicnic?.let { (_, picnic) ->
                    add(StorePriceDto("picnic", picnic.price, picnic.unit, true))
                }
            }
            val best = prices.minByOrNull { it.price }!!

            products.add(FrontendProductDto(
                id             = UUID.randomUUID().toString(),
                name           = rewe.name,
                normalizedName = query,
                imageUrl       = rewe.imageUrl.ifBlank { "" },
                prices         = prices,
                bestPrice      = BestPriceDto(best.store, best.price)
            ))
        }

        // Unmatched Picnic items are shown as standalone entries with their own image
        picnicItems.forEachIndexed { idx, picnic ->
            if (idx !in usedPicnicIndices) {
                val price = StorePriceDto("picnic", picnic.price, picnic.unit, true)
                products.add(FrontendProductDto(
                    id             = UUID.randomUUID().toString(),
                    name           = picnic.name,
                    normalizedName = query,
                    imageUrl       = picnic.imageUrl,
                    prices         = listOf(price),
                    bestPrice      = BestPriceDto("picnic", picnic.price)
                ))
            }
        }

        return products.take(limit)
    }

    // ── Unit parsing & comparison ─────────────────────────────────────────────

    private data class ParsedUnit(val amount: Double, val base: String)

    private fun parseUnit(unit: String): ParsedUnit? {
        val clean = unit.lowercase().replace(",", ".").trim()
        val m = Regex("""(\d+(?:\.\d+)?)\s*(ml|l|g|kg|stk|st\.?|stück)?""").find(clean)
            ?: return null
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].trimEnd('.')) {
            "l"  -> ParsedUnit(amount * 1000.0, "ml")
            "kg" -> ParsedUnit(amount * 1000.0, "g")
            "ml" -> ParsedUnit(amount, "ml")
            "g"  -> ParsedUnit(amount, "g")
            else -> ParsedUnit(amount, "stk")
        }
    }

    // Two products match on quantity if they have the same base unit and amount within 15%
    private fun quantitiesMatch(unitA: String, unitB: String): Boolean {
        val a = parseUnit(unitA) ?: return false
        val b = parseUnit(unitB) ?: return false
        if (a.base != b.base) return false
        val ratio = if (b.amount > 0) a.amount / b.amount else return false
        return ratio in 0.85..1.15
    }

    // Jaccard similarity over word tokens (ignores special chars and case)
    private fun nameSimilarity(a: String, b: String): Double {
        fun tokens(s: String) = s.lowercase()
            .replace(Regex("[^a-z0-9äöüß]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .toSet()
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size.toDouble()
    }
}
