import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.cdimascio.dotenv.dotenv
import model.MatchedItem
import model.PriceResult
import model.Product
import model.StoreItem
import output.CsvExporter
import output.PriceTablePrinter
import service.AiMatcherService
import service.ExistingCheckService
import service.PicnicService
import service.ReweService
import java.io.File

fun main(args: Array<String>) {
    val dotenv = runCatching {
        dotenv { ignoreIfMissing = true }
    }.getOrNull()

    fun env(key: String) = System.getenv(key)
        ?: runCatching { dotenv?.get(key) }.getOrNull()
        ?: ""

    // Parse CLI arguments
    var productFile: String? = null
    var checkFile: String? = null
    var skipPicnic = false
    var skipRewe = false
    var simplifyNames = false
    var outputFile: String = "result.csv"
    var debug = false
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "-f", "--file"  -> productFile = args.getOrNull(++i)
            "-c", "--check" -> checkFile = args.getOrNull(++i)
            "--no-picnic"   -> skipPicnic = true
            "--no-rewe"     -> skipRewe = true
            "--simplify"    -> simplifyNames = true
            "-o", "--output" -> outputFile = args.getOrNull(++i) ?: outputFile
            "--debug"        -> debug = true
            "-h", "--help"  -> { printHelp(); return }
            else -> productFile = args[i]  // positional fallback
        }
        i++
    }

    // Resolve product list and optional existing-check baseline
    val existingCheckResult: PriceResult?
    val products: List<Product>

    when {
        checkFile != null -> {
            val parsed = ExistingCheckService().parse(File(checkFile))
                ?: run { System.err.println("Failed to parse check file: $checkFile"); return }
            products = parsed.first
            existingCheckResult = parsed.second
        }
        productFile != null -> {
            products = parseProductFile(File(productFile))
            existingCheckResult = null
        }
        else -> {
            products = readProductsFromStdin()
            existingCheckResult = null
        }
    }

    if (products.isEmpty()) {
        System.err.println("No products found. Exiting.")
        return
    }

    val aiMatcher = AiMatcherService(env("OPENAI_API_KEY"))

    val normCacheFile = File(".normalization-cache.json")
    val normCache = loadNormalizationCache(normCacheFile)
    val uncached = products.filter { it.name !in normCache }
    if (uncached.isNotEmpty()) {
        val cachedCount = products.size - uncached.size
        val suffix = if (cachedCount > 0) " ($cachedCount from cache)" else ""
        println("Normalizing ${uncached.size} product name(s) via AI$suffix...")
        val freshNormalized = aiMatcher.normalizeNames(uncached, simplifyNames)
        uncached.forEachIndexed { i, p -> normCache[p.name] = freshNormalized[i].name }
        saveNormalizationCache(normCacheFile, normCache)
    } else {
        println("All ${products.size} product name(s) from normalization cache.")
    }
    val normalizedProducts = products.map { p ->
        val normalized = normCache[p.name] ?: p.name
        if (normalized != p.name) p.copy(name = normalized) else p
    }

    println("Comparing prices for ${normalizedProducts.size} product(s)...")

    val allResults = mutableListOf<PriceResult>()
    existingCheckResult?.let { allResults.add(it) }

    // ── REWE ──────────────────────────────────────────────────────────────
    if (!skipRewe) {
        val reweService = ReweService(
            serviceType   = env("REWE_SERVICE_TYPE").ifBlank { "DELIVERY" },
            zipCode       = env("REWE_ZIP_CODE"),
            marketId      = env("REWE_MARKET_ID"),
            certFile      = env("REWE_CERT_FILE"),
            keyFile       = env("REWE_KEY_FILE"),
            sessionCookie = env("REWE_SESSION_COOKIE"),
            debug         = debug
        )
        reweService.checkSessionWarning()
        reweService.resolveMarketId()

        println("Searching REWE...")
        val reweCandidates: Map<Product, List<StoreItem>> = normalizedProducts.associateWith { reweService.search(it) }
        reweService.writeSearchLog(java.io.File("rewe-search.log"))
        val reweMatches = aiMatcher.matchAll(normalizedProducts, reweCandidates)
        val reweItems = normalizedProducts.mapNotNull { product ->
            val item = reweMatches[product] ?: return@mapNotNull null
            MatchedItem(product, item.name, item.price, item.url)
        }
        allResults.add(
            PriceResult(
                store = "REWE",
                totalPrice = if (reweItems.isEmpty()) null
                             else reweItems.sumOf { it.price * it.query.quantity },
                items = reweItems
            )
        )
    }

    // ── Picnic ─────────────────────────────────────────────────────────────
    if (!skipPicnic) {
        val picnicEmail    = env("PICNIC_EMAIL")
        val picnicPassword = env("PICNIC_PASSWORD")

        if (picnicEmail.isBlank() || picnicPassword.isBlank()) {
            System.err.println("Picnic credentials not set (PICNIC_EMAIL / PICNIC_PASSWORD) — skipping.")
        } else {
            val picnicService = PicnicService(picnicEmail, picnicPassword, env("PICNIC_COUNTRY").ifBlank { "de" }, debug)
            print("Logging into Picnic... ")
            if (picnicService.login()) {
                println("OK")
                println("Searching Picnic...")
                val picnicCandidates: Map<Product, List<StoreItem>> =
                    normalizedProducts.associateWith { picnicService.search(it) }
                picnicService.writeSearchLog(java.io.File("picnic-search.log"))
                val picnicMatches = aiMatcher.matchAll(normalizedProducts, picnicCandidates)
                val picnicItems = normalizedProducts.mapNotNull { product ->
                    val item = picnicMatches[product] ?: return@mapNotNull null
                    MatchedItem(product, item.name, item.price, item.url)
                }
                allResults.add(
                    PriceResult(
                        store = "Picnic",
                        totalPrice = if (picnicItems.isEmpty()) null
                                     else picnicItems.sumOf { it.price * it.query.quantity },
                        items = picnicItems
                    )
                )
            } else {
                println("FAILED")
                System.err.println("Picnic login failed — skipping Picnic results.")
            }
        }
    }

    aiMatcher.writeQueryLog(java.io.File("ai-queries.log"))
    PriceTablePrinter.print(allResults)
    CsvExporter.export(normalizedProducts, allResults, File(outputFile))
}

// ──────────────────────────────────────────────────────────────────────────────

private fun parseProductFile(file: File): List<Product> {
    if (!file.exists()) {
        System.err.println("Product file not found: ${file.path}")
        return emptyList()
    }
    return parseProductLines(file.readLines())
}

private fun readProductsFromStdin(): List<Product> {
    println("Enter products (name, quantity, unit) one per line. Empty line to finish:")
    val lines = mutableListOf<String>()
    while (true) {
        val line = readLine() ?: break
        if (line.isBlank()) break
        lines.add(line)
    }
    return parseProductLines(lines)
}

private fun parseProductLines(lines: List<String>): List<Product> {
    return lines
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(",").map { it.trim() }
            when {
                parts.size >= 3 -> Product(parts[0], parts[1].toDoubleOrNull() ?: 1.0, parts[2])
                parts.size == 2 -> Product(parts[0], parts[1].toDoubleOrNull() ?: 1.0, "stk")
                parts.size == 1 -> Product(parts[0], 1.0, "stk")
                else -> null
            }
        }
}

private fun loadNormalizationCache(file: File): MutableMap<String, String> {
    if (!file.exists()) return mutableMapOf()
    return try {
        val obj = JsonParser.parseString(file.readText()).asJsonObject
        obj.keySet().associateWithTo(mutableMapOf()) { obj.get(it).asString }
    } catch (e: Exception) {
        mutableMapOf()
    }
}

private fun saveNormalizationCache(file: File, cache: Map<String, String>) {
    val obj = JsonObject()
    cache.forEach { (k, v) -> obj.addProperty(k, v) }
    file.writeText(obj.toString())
}

private fun printHelp() {
    println("""
        Usage: mmvp-rewe-picnic [options] [product-file]

        Options:
          -f, --file <file>    Product list CSV (name, quantity, unit)
          -c, --check <file>   Existing receipt/check CSV (name, qty, unit, unit_price)
          --no-picnic          Skip Picnic search
          --no-rewe            Skip REWE search
          --simplify           Strip brands/percentages, search by generic term (e.g. "Milch 3.5% Kaufland" → "Milch")
          -o, --output <file>  Output CSV path (default: result.csv)
          -h, --help           Show this help

        Product file format (CSV, one per line):
          Milch 3.5%, 2, liter
          Butter, 250, g
          Eier, 10, stk

        Environment variables (or .env file):
          PICNIC_EMAIL, PICNIC_PASSWORD
          OPENAI_API_KEY         (for name normalization + AI matching)
          REWE_SERVICE_TYPE      (DELIVERY or PICKUP, default: DELIVERY)
          REWE_ZIP_CODE
          REWE_MARKET_ID         (only needed for PICKUP mode)
          REWE_SESSION_COOKIE    (session cookie — required for REWE prices)
                                 Open www.rewe.de, select your store, then DevTools → Application
                                 → Cookies → www.rewe.de. Try rstp=<value> or rwSession=<value>.
    """.trimIndent())
}
