package com.shoppingplaner.service

import com.shoppingplaner.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CatalogueRefreshJob(
    private val productSearchService: ProductSearchService,
    private val productRepo: ProductRepository,
    private val categoryMatcher: CategoryMatcherService,
) {
    private val log = LoggerFactory.getLogger(CatalogueRefreshJob::class.java)

    private val CATEGORY_QUERIES = listOf(
        "Milch", "Butter", "Joghurt", "Käse", "Sahne",
        "Hähnchen", "Lachs", "Rinderhack", "Wurst",
        "Apfel", "Tomate", "Banane", "Paprika", "Kartoffel",
        "Brot", "Brötchen", "Toast",
        "Wasser", "Orangensaft", "Kaffee",
        "Schokolade", "Chips", "Kekse",
        "Tiefkühlpizza", "Fischstäbchen",
        "Nudeln", "Reis", "Mehl", "Olivenöl", "Müsli",
        "Spülmittel", "Toilettenpapier",
    )

    @Scheduled(cron = "0 0 3 * * *")
    fun refreshCatalogue() {
        log.info("Starting daily catalogue refresh ({} queries)", CATEGORY_QUERIES.size)
        var populated = 0
        for (query in CATEGORY_QUERIES) {
            runCatching {
                val result = productSearchService.search(query, limit = 20)
                populated += result.items.size
            }.onFailure { log.warn("Catalogue query '{}' failed: {}", query, it.message) }
        }
        backfillCategories()
        log.info("Daily catalogue refresh done: {} products touched", populated)
    }

    private fun backfillCategories() {
        val uncategorized = productRepo.findByCategoryIsNull()
        var updated = 0
        for (product in uncategorized) {
            val cat = categoryMatcher.match(product.name)
                ?: categoryMatcher.match(product.normalizedName)
            if (cat != null) {
                productRepo.save(product.copy(category = cat))
                updated++
            }
        }
        if (updated > 0) log.info("Backfilled categories for {} products", updated)
    }
}
