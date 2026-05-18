package service

import model.MatchedItem
import model.PriceResult
import model.Product
import java.io.File

/**
 * Parses an existing receipt/check file.
 *
 * Supported format — plain text or CSV, one line per product:
 *   product_name, quantity, unit, unit_price
 *   e.g.:
 *   Milch 3.5%, 2, liter, 1.29
 *   Butter, 250, g, 1.79
 *
 * The resulting PriceResult uses the parsed unit prices directly as the baseline.
 */
class ExistingCheckService {
    fun parse(file: File): Pair<List<Product>, PriceResult>? {
        if (!file.exists()) {
            System.err.println("Check file not found: ${file.path}")
            return null
        }

        val products = mutableListOf<Product>()
        val matched = mutableListOf<MatchedItem>()

        file.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val parts = line.split(",").map { it.trim() }
                when {
                    parts.size >= 4 -> {
                        val name = parts[0]
                        val qty = parts[1].toDoubleOrNull() ?: 1.0
                        val unit = parts[2]
                        val unitPrice = parts[3].toDoubleOrNull() ?: run {
                            System.err.println("Skipping line with unparsable price: $line")
                            return@forEach
                        }
                        val product = Product(name, qty, unit)
                        products.add(product)
                        matched.add(MatchedItem(product, name, unitPrice, null))
                    }
                    parts.size == 3 -> {
                        // name, quantity, unit — no price; add as product only
                        val name = parts[0]
                        val qty = parts[1].toDoubleOrNull() ?: 1.0
                        val unit = parts[2]
                        products.add(Product(name, qty, unit))
                    }
                    else -> System.err.println("Skipping unrecognised line: $line")
                }
            }

        if (products.isEmpty()) return null

        val total = matched.sumOf { it.price * it.query.quantity }
        val result = PriceResult(
            store = "Existing check (${file.name})",
            totalPrice = total,
            items = matched
        )
        return products to result
    }
}
