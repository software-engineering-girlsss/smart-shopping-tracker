package output

import model.MatchedItem
import model.PriceResult
import model.Product
import java.io.File

object CsvExporter {

    fun export(allProducts: List<Product>, results: List<PriceResult>, file: File) {
        val sb = StringBuilder()

        // Header
        val storeCols = results.flatMap { listOf(it.store + " Price", it.store + " Matched Name") }
        sb.appendLine((listOf("Product", "Qty", "Unit") + storeCols).joinToString(","))

        // Track per-store totals (only found items)
        val totals = results.associate { it.store to 0.0 }.toMutableMap()
        var anyNotFound = false

        // One row per product
        for (product in allProducts) {
            val cells = mutableListOf(
                csvEscape(product.name),
                product.quantity.toDisplayString(),
                product.unit
            )

            for (result in results) {
                val item: MatchedItem? = result.items.find { it.query.name == product.name }
                if (item != null) {
                    val lineTotal = item.price * product.quantity
                    totals[result.store] = (totals[result.store] ?: 0.0) + lineTotal
                    cells += "€${"%.2f".format(lineTotal)}"
                    cells += csvEscape(item.matchedName)
                } else {
                    cells += "NOT FOUND"
                    cells += ""
                    anyNotFound = true
                }
            }

            sb.appendLine(cells.joinToString(","))
        }

        // Totals row
        val totalCells = mutableListOf("TOTAL (found items only)", "", "")
        for (result in results) {
            val t = totals[result.store]
            totalCells += if (t != null && t > 0.0) "€${"%.2f".format(t)}" else "N/A"
            totalCells += ""
        }
        sb.appendLine()
        sb.appendLine(totalCells.joinToString(","))

        // Cheapest store note
        val cheapest = totals.filter { it.value > 0.0 }.minByOrNull { it.value }
        if (cheapest != null) {
            sb.appendLine()
            sb.appendLine("Cheapest,${cheapest.key},€${"%.2f".format(cheapest.value)}")
        }

        if (anyNotFound) {
            sb.appendLine()
            sb.appendLine("Note,NOT FOUND items are excluded from store totals")
        }

        file.writeText(sb.toString())
        println("Results saved to ${file.path}")
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun Double.toDisplayString() =
        if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
}
