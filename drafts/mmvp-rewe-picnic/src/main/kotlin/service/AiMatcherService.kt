package service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import model.Product
import model.StoreItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class AiMatcherService(private val openAiKey: String) {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val endpoint = "https://api.openai.com/v1/chat/completions"
    private val queryLog = mutableListOf<JsonObject>()

    fun writeQueryLog(file: File) {
        if (queryLog.isEmpty()) return
        val arr = JsonArray()
        queryLog.forEach { arr.add(it) }
        file.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr))
        println("AI query log saved to ${file.path}")
    }

    /**
     * Expands abbreviated receipt product names to full, searchable product names.
     * Example: "Kn.FS Kürbiscreme" → "Knorr Fix Suppe Kürbiscreme"
     */
    fun normalizeNames(products: List<Product>, simplify: Boolean = false): List<Product> {
        if (products.isEmpty()) return products
        if (openAiKey.isBlank()) {
            System.err.println("OPENAI_API_KEY not set — skipping name normalization")
            return products
        }

        val prompt = buildString {
            if (simplify) {
                appendLine("You are a German grocery product search term simplifier.")
                appendLine("The following are product names (possibly from a receipt or shopping list).")
                appendLine("Simplify each name to the most generic searchable term — strip brand names, store names, fat percentages, and marketing words.")
                appendLine("Rules:")
                appendLine("- Output ONLY a JSON array of strings, one simplified name per input, in the same order.")
                appendLine("- Keep only the core product type in German (1-2 words max).")
                appendLine("- Remove brand names, store names, percentages, adjectives like 'bio', 'frisch', 'extra'.")
                appendLine("- Example input: [\"Milch 3.5% Kaufland\", \"Elinas gri.Joghurt\", \"Kn.FS Kürbiscreme\"]")
                appendLine("- Example output: [\"Milch\", \"Joghurt\", \"Kürbiscremesuppe\"]")
            } else {
                appendLine("You are a German grocery product name expander.")
                appendLine("The following are abbreviated product names from a German supermarket receipt.")
                appendLine("Expand each abbreviated name to a full, searchable German product name.")
                appendLine("Rules:")
                appendLine("- Output ONLY a JSON array of strings, one expanded name per input, in the same order.")
                appendLine("- Keep brand names if recognizable (e.g. 'Elinas', 'Knorr', 'Lotus').")
                appendLine("- Expand German abbreviations (e.g. 'gri.' → 'griechischer', 'FS' → 'Fix Suppe', 'Kn.' → 'Knorr').")
                appendLine("- If the name is already clear, return it unchanged.")
                appendLine("- Example input: [\"Kn.FS Kürbiscreme\", \"Elinas gri.Joghurt\"]")
                appendLine("- Example output: [\"Knorr Fix Suppe Kürbiscreme\", \"Elinas griechischer Joghurt\"]")
            }
            appendLine()
            val names = products.map { "\"${it.name}\"" }.joinToString(", ")
            appendLine("Input: [$names]")
        }

        val responseText = callOpenAi(prompt, logLabel = "normalize") ?: run {
            System.err.println("OpenAI normalization failed — using original names")
            return products
        }

        return try {
            val jsonStr = Regex("""\[[\s\S]*?]""").find(responseText)?.value ?: "[]"
            val names = JsonParser.parseString(jsonStr).asJsonArray.map { it.asString }
            products.mapIndexed { i, product ->
                val expanded = names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: product.name
                if (expanded != product.name)
                    println("  Normalized: \"${product.name}\" → \"$expanded\"")
                product.copy(name = expanded)
            }
        } catch (e: Exception) {
            System.err.println("OpenAI normalization parse error: ${e.message} — using original names")
            products
        }
    }

    /**
     * For a list of products and their store candidates, returns the best-matching
     * StoreItem (or null) for each product — all resolved in a single API call.
     */
    fun matchAll(
        products: List<Product>,
        candidates: Map<Product, List<StoreItem>>
    ): Map<Product, StoreItem?> {
        if (products.isEmpty()) return emptyMap()
        if (openAiKey.isBlank()) {
            System.err.println("OPENAI_API_KEY not set — using first candidate as fallback")
            return products.associateWith { candidates[it]?.firstOrNull() }
        }

        val prompt = buildPrompt(products, candidates)
        val responseText = callOpenAi(prompt, logLabel = "match") ?: run {
            System.err.println("OpenAI call failed — using first candidate as fallback")
            return products.associateWith { candidates[it]?.firstOrNull() }
        }

        return parseResponse(products, candidates, responseText)
    }

    private fun buildPrompt(
        products: List<Product>,
        candidates: Map<Product, List<StoreItem>>
    ): String = buildString {
        appendLine("You are a grocery product matcher. For each user product, pick the cheapest candidate that is a good match.")
        appendLine("Rules:")
        appendLine("- A candidate matches if it is the same product type and broadly comparable (e.g. same fat content for milk, same weight class).")
        appendLine("- Among all matching candidates, pick the one with the lowest price.")
        appendLine("- If no candidate matches at all, respond with index -1.")
        appendLine("- Respond ONLY with a JSON array of integers, one per product, representing the 0-based index of the chosen candidate (or -1).")
        appendLine("- Example response for 3 products: [0, 2, -1]")
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

    private fun callOpenAi(prompt: String, logLabel: String = "query"): String? {
        val requestBody = JsonObject().apply {
            addProperty("model", "gpt-4o-mini")
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
            .addHeader("Authorization", "Bearer $openAiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string() ?: ""
                val entry = JsonObject().apply {
                    addProperty("label", logLabel)
                    addProperty("prompt", prompt)
                    addProperty("status", response.code)
                }
                if (!response.isSuccessful) {
                    entry.addProperty("error", bodyText)
                    queryLog.add(entry)
                    System.err.println("OpenAI API error (${response.code}): $bodyText")
                    return null
                }
                val responseText = JsonParser.parseString(bodyText).asJsonObject
                    .getAsJsonArray("choices")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
                entry.addProperty("response", responseText)
                queryLog.add(entry)
                responseText
            }
        } catch (e: Exception) {
            System.err.println("OpenAI call error: ${e.message}")
            null
        }
    }

    private fun parseResponse(
        products: List<Product>,
        candidates: Map<Product, List<StoreItem>>,
        responseText: String
    ): Map<Product, StoreItem?> {
        return try {
            val jsonStr = Regex("""\[[\s\S]*?]""").find(responseText)?.value ?: "[]"
            val indices = JsonParser.parseString(jsonStr).asJsonArray.map { it.asInt }

            products.mapIndexed { i, product ->
                val idx = indices.getOrNull(i) ?: -1
                val list = candidates[product] ?: emptyList()
                product to if (idx in list.indices) list[idx] else list.firstOrNull()
            }.toMap()
        } catch (e: Exception) {
            System.err.println("OpenAI response parse error: ${e.message} — using first candidates")
            products.associateWith { candidates[it]?.firstOrNull() }
        }
    }
}
