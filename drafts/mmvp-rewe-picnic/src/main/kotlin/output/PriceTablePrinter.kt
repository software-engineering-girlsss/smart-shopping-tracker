package output

import model.PriceResult

object PriceTablePrinter {

    fun print(results: List<PriceResult>) {
        if (results.isEmpty()) {
            println("No results to display.")
            return
        }

        val cheapest = results
            .filter { it.totalPrice != null }
            .minByOrNull { it.totalPrice!! }

        println()
        println("=".repeat(70))
        println("  PRICE COMPARISON RESULTS")
        println("=".repeat(70))

        // Per-store summary
        for (result in results) {
            val isCheapest = result == cheapest
            val flag = if (isCheapest) " ★ CHEAPEST" else ""
            val priceStr = result.totalPrice?.let { "€${"%.2f".format(it)}" } ?: "N/A"
            println("  %-35s %10s%s".format(result.store, priceStr, flag))
        }

        println("-".repeat(70))

        // Per-item breakdown
        println()
        println("  ITEM BREAKDOWN")
        println()

        val allProducts = results
            .flatMap { it.items.map { item -> item.query } }
            .distinctBy { it.name }

        for (product in allProducts) {
            println("  ${product.name} (${product.quantity} ${product.unit})")
            for (result in results) {
                val item = result.items.find { it.query.name == product.name }
                val priceStr = item?.let { "€${"%.2f".format(it.price)} × ${product.quantity} = €${"%.2f".format(it.price * product.quantity)}" }
                    ?: "N/A"
                val matchStr = item?.matchedName?.let { " → $it" } ?: ""
                println("    %-25s %s%s".format(result.store + ":", priceStr, matchStr))
            }
            println()
        }

        println("=".repeat(70))
        if (cheapest != null) {
            println("  Cheapest option: ${cheapest.store}  (€${"%.2f".format(cheapest.totalPrice!!)})")
        }
        println("=".repeat(70))
        println()
    }
}
