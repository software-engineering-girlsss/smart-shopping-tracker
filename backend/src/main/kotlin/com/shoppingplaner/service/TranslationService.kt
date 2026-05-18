package com.shoppingplaner.service

import com.shoppingplaner.repository.ProductTranslationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TranslationService(
    private val translationRepo: ProductTranslationRepository
) {
    private val log = LoggerFactory.getLogger(TranslationService::class.java)

    private val fallback = mapOf(
        "milk" to "Milch", "butter" to "Butter", "eggs" to "Eier", "egg" to "Eier",
        "bread" to "Brot", "yogurt" to "Joghurt", "yoghurt" to "Joghurt",
        "cheese" to "Käse", "juice" to "Saft", "water" to "Wasser",
        "coffee" to "Kaffee", "tea" to "Tee", "flour" to "Mehl",
        "sugar" to "Zucker", "salt" to "Salz", "oil" to "Öl",
        "pasta" to "Nudeln", "rice" to "Reis", "chicken" to "Hähnchen",
        "beef" to "Rindfleisch", "fish" to "Fisch", "cream" to "Sahne",
        "chocolate" to "Schokolade", "ice cream" to "Eis", "beer" to "Bier",
        "wine" to "Wein", "potato" to "Kartoffeln", "potatoes" to "Kartoffeln",
        "tomato" to "Tomaten", "tomatoes" to "Tomaten", "apple" to "Äpfel",
        "apples" to "Äpfel", "banana" to "Bananen", "bananas" to "Bananen"
    )

    /**
     * If the query appears to be in English (not German), return the German translation.
     * If no translation found, returns the original query unchanged.
     */
    fun toGerman(query: String): String {
        val trimmed = query.trim().lowercase()

        if (appearsGerman(trimmed)) return query

        val dbResult = try {
            translationRepo.findByTermEnIgnoreCase(trimmed)?.termDe
        } catch (e: Exception) {
            log.debug("Translation DB lookup failed: {}", e.message)
            null
        }
        if (dbResult != null) {
            log.debug("Translated '{}' → '{}'", query, dbResult)
            return dbResult
        }

        val fallbackResult = fallback[trimmed]
        if (fallbackResult != null) {
            log.debug("Translated (fallback) '{}' → '{}'", query, fallbackResult)
            return fallbackResult
        }

        return query
    }

    private fun appearsGerman(query: String): Boolean {
        if (query.any { it in "äöüß" }) return true
        val germanTerms = setOf(
            "milch", "butter", "brot", "joghurt", "käse", "eier", "wasser",
            "kaffee", "tee", "mehl", "zucker", "salz", "öl", "nudeln", "reis",
            "hähnchen", "rindfleisch", "fisch", "sahne", "schokolade", "bier",
            "wein", "kartoffeln", "tomaten", "äpfel", "bananen", "fleisch",
            "wurst", "quark", "frischkäse", "vollmilch", "magermilch",
            "haferflocken", "müsli", "marmelade", "honig"
        )
        return query.split(Regex("\\s+")).any { it in germanTerms }
    }
}
