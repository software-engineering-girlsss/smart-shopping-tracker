package com.shoppingplaner.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shoppingplaner.config.AppProperties
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.model.StoreItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.shoppingplaner.profiling.OkHttpTimingListener
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.TimeUnit

/**
 * AI-powered product name matching using OpenAI.
 *
 * Falls back to picking the first candidate when no API key is configured.
 */
@Service
class AiMatcherService(
    private val props: AppProperties,
    private val meterRegistry: MeterRegistry,
    private val matchCache: MatchCacheService,
) {

    private val log = LoggerFactory.getLogger(AiMatcherService::class.java)

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .eventListenerFactory(OkHttpTimingListener.factory(meterRegistry, "openai"))
        .build()

    private val endpoint = "https://api.openai.com/v1/chat/completions"

    /**
     * Expands abbreviated product names using OpenAI.
     * Falls back to original names when no API key is set.
     */
    fun normalizeNames(products: List<SearchQuery>): List<SearchQuery> {
        if (products.isEmpty()) return products
        if (props.openai.apiKey.isBlank()) {
            log.debug("AiMatcher: no API key — skipping normalization")
            return products
        }

        val prompt = buildString {
            appendLine("You are a German grocery product name expander.")
            appendLine("Expand each abbreviated product name to a full, searchable German product name.")
            appendLine("Rules:")
            appendLine("- Output ONLY a JSON array of strings, one per input, in the same order.")
            appendLine("- Keep brand names if recognizable.")
            appendLine("- Expand German abbreviations (e.g. 'gri.' → 'griechischer', 'FS' → 'Fix Suppe').")
            appendLine("- If the name is already clear, return it unchanged.")
            appendLine()
            val names = products.map { "\"${it.name}\"" }.joinToString(", ")
            appendLine("Input: [$names]")
        }

        val responseText = callOpenAi(prompt) ?: run {
            log.warn("AiMatcher: normalization failed — using original names")
            return products
        }

        return try {
            val jsonStr = Regex("""\[[\s\S]*?]""").find(responseText)?.value ?: "[]"
            val names   = JsonParser.parseString(jsonStr).asJsonArray.map { it.asString }
            products.mapIndexed { i, searchQuery ->
                val expanded = names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: searchQuery.name
                if (expanded != searchQuery.name) log.debug("Normalized: '{}' → '{}'", searchQuery.name, expanded)
                searchQuery.copy(name = expanded)
            }
        } catch (e: Exception) {
            log.warn("AiMatcher: normalization parse error ({}) — using original names", e.message)
            products
        }
    }

    /**
     * For each product picks the best-matching candidate from the given store results.
     *
     * Cache hit path: zero OpenAI calls.
     * Partial hit path: one OpenAI call covering only the uncached products.
     * Full miss path: one OpenAI call for all products (same as before).
     *
     * [store] is used as part of the cache key (e.g. "REWE", "Picnic").
     */
    fun matchAll(
        products: List<SearchQuery>,
        candidates: Map<SearchQuery, List<StoreItem>>,
        store: String,
    ): Map<SearchQuery, StoreItem?> {
        if (products.isEmpty()) return emptyMap()

        // Split into cache hits and misses — single Redis round trip for all products
        val keys    = products.associateWith { matchCache.cacheKey(store, it.name, candidates[it] ?: emptyList()) }
        val entries = matchCache.getAll(products.map { keys[it]!! })
        val hits    = mutableMapOf<SearchQuery, StoreItem?>()
        val misses  = mutableListOf<SearchQuery>()

        for (p in products) {
            val entry = entries[keys[p]!!]
            if (entry != null) hits[p] = matchCache.toStoreItem(entry)
            else               misses.add(p)
        }

        if (misses.isEmpty()) {
            log.debug("AiMatcher [{}]: full cache hit ({} products)", store, products.size)
            return hits
        }
        log.debug("AiMatcher [{}]: {}/{} cache misses", store, misses.size, products.size)

        // Resolve only the misses via OpenAI (or fallback)
        val missedCandidates = misses.associateWith { candidates[it] ?: emptyList() }
        val fresh: Map<SearchQuery, StoreItem?> = when {
            props.openai.apiKey.isBlank() -> {
                log.debug("AiMatcher: no API key — using first candidate")
                misses.associateWith { missedCandidates[it]?.firstOrNull() }
            }
            else -> {
                val text = callOpenAi(buildMatchPrompt(misses, missedCandidates)) ?: run {
                    log.warn("AiMatcher: match call failed — falling back to first candidates")
                    return hits + misses.associateWith { missedCandidates[it]?.firstOrNull() }
                }
                parseMatchResponse(misses, missedCandidates, text)
            }
        }

        matchCache.putAll(fresh.entries.associate { (p, item) -> keys[p]!! to matchCache.toEntry(item) })

        return hits + fresh
    }

    private fun buildMatchPrompt(
        products: List<SearchQuery>,
        candidates: Map<SearchQuery, List<StoreItem>>
    ): String = buildString {
        appendLine("You are a grocery product matcher. For each user product, pick the cheapest candidate that is a good match.")
        appendLine("Rules:")
        appendLine("- A candidate matches if it is the same product type and broadly comparable.")
        appendLine("- Among matching candidates, pick the one with the lowest price.")
        appendLine("- If no candidate matches, respond with index -1.")
        appendLine("- Respond ONLY with a JSON array of integers (0-based index or -1), one per product.")
        appendLine("- Example for 3 products: [0, 2, -1]")
        appendLine()
        products.forEachIndexed { i, product ->
            appendLine("Product ${i + 1}: ${product.name} (${product.quantity} ${product.unit})")
            val list = candidates[product] ?: emptyList()
            if (list.isEmpty()) {
                appendLine("  Candidates: (none)")
            } else {
                list.forEachIndexed { j, item ->
                    appendLine("  [$j] ${item.name} — €${"%.2f".format(item.price)} ${item.unit}")
                }
            }
            appendLine()
        }
    }

    private fun parseMatchResponse(
        products: List<SearchQuery>,
        candidates: Map<SearchQuery, List<StoreItem>>,
        responseText: String
    ): Map<SearchQuery, StoreItem?> = try {
        val jsonStr = Regex("""\[[\s\S]*?]""").find(responseText)?.value ?: "[]"
        val indices = JsonParser.parseString(jsonStr).asJsonArray.map { it.asInt }
        products.mapIndexed { i, product ->
            val idx  = indices.getOrNull(i) ?: -1
            val list = candidates[product] ?: emptyList()
            product to if (idx in list.indices) list[idx] else list.firstOrNull()
        }.toMap()
    } catch (e: Exception) {
        log.warn("AiMatcher: response parse error ({}) — using first candidates", e.message)
        products.associateWith { candidates[it]?.firstOrNull() }
    }

    private fun callOpenAi(prompt: String): String? {
        val requestBody = JsonObject().apply {
            addProperty("model", props.openai.model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
            addProperty("temperature", 0)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${props.openai.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    log.error("OpenAI API error ({}): {}", response.code, bodyText.take(300))
                    return null
                }
                JsonParser.parseString(bodyText).asJsonObject
                    .getAsJsonArray("choices")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
            }
        } catch (e: Exception) {
            log.error("OpenAI call error: {}", e.message)
            null
        }
    }
}
