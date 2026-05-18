package com.shoppingplaner.service

import com.shoppingplaner.dto.CompareRequest
import com.shoppingplaner.dto.CompareResponse
import com.shoppingplaner.dto.MatchedItemDto
import com.shoppingplaner.dto.ProductDto
import com.shoppingplaner.dto.StoreCompareResult
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.model.StoreItem
import com.shoppingplaner.profiling.PhaseTiming
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Core orchestrator: takes a list of products, searches both stores in parallel,
 * runs AI matching, and returns a comparison with the cheapest store identified.
 */
@Service
class PriceComparisonService(
    private val reweService: ReweService,
    private val picnicService: PicnicService,
    private val aiMatcher: AiMatcherService,
    private val normCache: NormalizationCacheService
) {
    private val log      = LoggerFactory.getLogger(PriceComparisonService::class.java)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * Runs a full price comparison.
     *
     * @param picnicToken When supplied (non-null), the user's own Picnic JWT is used
     *   for Picnic searches instead of the shared owner session.  Pass null to fall
     *   back to the owner's session (original behavior).
     */
    fun compare(request: CompareRequest, picnicToken: String? = null): CompareResponse {
        val t = PhaseTiming("compare[${request.products.size}p]")
        val rawProducts = request.products.map { SearchQuery(it.name, it.quantity, it.unit) }

        // Single Redis round trip to check all cached normalizations at once
        val cachedNorms = normCache.getAll(rawProducts.map { it.name })
        val uncached = rawProducts.filter { cachedNorms[it.name] == null }

        val newNorms: Map<String, String> = if (uncached.isNotEmpty()) {
            val expanded = t.measure("openai_normalize") { aiMatcher.normalizeNames(uncached) }
            val entries = uncached.zip(expanded).associate { (orig, exp) -> orig.name to exp.name }
            normCache.putAll(entries)
            entries
        } else emptyMap()

        val allNorms = cachedNorms + newNorms
        val normalized = rawProducts.map { sq -> sq.copy(name = allNorms[sq.name] ?: sq.name) }

        log.info("Comparing {} product(s) [userToken={}]",
            normalized.size, if (picnicToken != null) "yes" else "no")

        // Fan out every individual product search as its own virtual thread.
        // All 2×N searches start immediately; wall-clock time ≈ slowest single search,
        // not N × slowest (the old sequential-within-future behaviour).
        val rewePerProduct   = normalized.map { p -> p to executor.submit(Callable { reweService.search(p) }) }
        val picnicPerProduct = normalized.map { p ->
            p to executor.submit(Callable {
                if (picnicToken != null) picnicService.searchWithToken(p, picnicToken)
                else                    picnicService.search(p)
            })
        }

        val reweResults   = t.measure("rewe_search")   { rewePerProduct.map   { (p, f) -> p to f.get() } }
        val picnicResults = t.measure("picnic_search") { picnicPerProduct.map { (p, f) -> p to f.get() } }

        // Build candidate maps: Product → List<StoreItem>
        val reweCandidates   = reweResults.toMap()
        val picnicCandidates = picnicResults.toMap()

        // AI matching for both stores in parallel — each is one OpenAI call (~1-3 s).
        // Running them sequentially would double this cost for no reason.
        val reweMatchFuture   = executor.submit(Callable { aiMatcher.matchAll(normalized, reweCandidates,   "REWE") })
        val picnicMatchFuture = executor.submit(Callable { aiMatcher.matchAll(normalized, picnicCandidates, "Picnic") })

        val reweMatched   = t.measure("ai_match_rewe")   { reweMatchFuture.get() }
        val picnicMatched = t.measure("ai_match_picnic") { picnicMatchFuture.get() }

        val reweResult   = buildStoreResult("REWE",   normalized, reweMatched)
        val picnicResult = buildStoreResult("Picnic", normalized, picnicMatched)

        val stores = listOf(reweResult, picnicResult)
        val cheapest = stores.filter { it.totalPrice != null }.minByOrNull { it.totalPrice!! }
        val mostExp  = stores.filter { it.totalPrice != null }.maxByOrNull { it.totalPrice!! }
        val savings  = if (cheapest != null && mostExp != null && cheapest != mostExp)
            mostExp.totalPrice!! - cheapest.totalPrice!! else null

        t.report()

        return CompareResponse(
            products     = request.products,
            stores       = stores,
            cheapestStore = cheapest?.store,
            savings      = savings?.let { "%.2f".format(it).toDouble() }
        )
    }

    private fun buildStoreResult(
        storeName: String,
        products: List<SearchQuery>,
        matched: Map<SearchQuery, StoreItem?>
    ): StoreCompareResult {
        val items = products.map { product ->
            val item = matched[product]
            MatchedItemDto(
                queryName   = product.name,
                matchedName = item?.name ?: "No match",
                price       = item?.price ?: 0.0,
                url         = item?.url,
                imageUrl    = item?.imageUrl ?: ""
            )
        }
        val total = items.takeIf { it.any { i -> i.price > 0 } }
            ?.sumOf { it.price * (products.find { p -> p.name == it.queryName }?.quantity ?: 1.0) }

        return StoreCompareResult(store = storeName, totalPrice = total, items = items)
    }
}
