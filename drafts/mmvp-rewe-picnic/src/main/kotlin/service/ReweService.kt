package service

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import model.Product
import model.StoreItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * REWE product search service.
 *
 * Uses the REWE mobile API (mobile-clients-api.rewe.de) which requires an mTLS
 * client certificate extracted from the REWE Android APK. Run setup-rewe-cert.py
 * to extract the cert, then set REWE_CERT_FILE and REWE_KEY_FILE in .env.
 *
 * Falls back to the legacy web API if no cert is available — that API returns
 * product names but NO prices (blocked by Cloudflare mTLS since March 2024).
 */
class ReweService(
    private val serviceType: String = "PICKUP",
    private val zipCode: String = "",
    private val marketId: String = "",
    certFile: String = "",
    keyFile: String = "",
    private val sessionCookie: String = "",
    private val debug: Boolean = false
) {
    companion object {
        private const val MOBILE_HOST = "mobile-clients-api.rewe.de"
        private const val LEGACY_URL  = "https://shop.rewe.de/api/products/"
        private const val MARKET_URL  = "https://mobile-clients-api.rewe.de/api/service-portfolio"
        private val USER_AGENTS = listOf(
            "Phone/Samsung_SM-G975U", "Phone/Google_Pixel_8_Pro",
            "Phone/Samsung_SM-S918B", "Phone/OnePlus_AC2003",
        )
    }

    private val useMobileApi: Boolean
    private val client: OkHttpClient
    private val rdfaId = UUID.randomUUID().toString()

    private var resolvedMarketId: String = marketId
    private val searchLog = mutableListOf<JsonObject>()

    init {
        val certPath = certFile.ifBlank { null }
        val keyPath  = keyFile.ifBlank { null }
        if (certPath != null && keyPath != null && File(certPath).exists() && File(keyPath).exists()) {
            useMobileApi = true
            client = buildMtlsClient(File(certPath), File(keyPath))
            if (debug) System.err.println("[REWE] Using mobile API with mTLS cert: $certPath")
        } else {
            useMobileApi = false
            client = buildLegacyClient()
            if (certPath != null || keyPath != null) {
                System.err.println("REWE: cert/key files not found ($certPath / $keyPath) — falling back to legacy API (no prices).")
            }
        }
    }

    // ── mTLS client ──────────────────────────────────────────────────────────

    private fun buildMtlsClient(certPem: File, keyPem: File): OkHttpClient {
        // Load certificate
        val cf = CertificateFactory.getInstance("X.509")
        val cert = certPem.inputStream().use { cf.generateCertificate(it) as X509Certificate }

        // Load PKCS8 private key (produced by setup-rewe-cert.py)
        val keyContent = keyPem.readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyContent)))

        // Build in-memory keystore
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setCertificateEntry("cert", cert)
        ks.setKeyEntry("key", privateKey, charArrayOf(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, charArrayOf())

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val trustManager = tmf.trustManagers.first() as X509TrustManager

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }

    // ── Legacy client (no mTLS, no prices) ───────────────────────────────────

    private fun buildLegacyClient() = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/122.0 Safari/537.36")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            if (sessionCookie.isNotBlank()) {
                builder.addHeader("Cookie", sessionCookie)
            }
            chain.proceed(builder.build())
        }
        .build()

    // ── Market resolution ─────────────────────────────────────────────────────

    fun checkSessionWarning() {
        if (!useMobileApi) {
            System.err.println(
                "REWE: no mTLS certificate configured — prices unavailable.\n" +
                "  Run: python3 setup-rewe-cert.py  (see instructions inside)\n" +
                "  Then add REWE_CERT_FILE=rewe.pem and REWE_KEY_FILE=rewe.key to .env"
            )
        }
    }

    fun resolveMarketId(): Boolean {
        if (resolvedMarketId.isNotBlank()) {
            println("REWE: using marketID=$resolvedMarketId")
            return true
        }
        if (zipCode.isBlank()) return false

        val url = if (useMobileApi) "$MARKET_URL/$zipCode" else
            "https://shop.rewe.de/api/marketselection/zipcodes/$zipCode/services/pickup"

        val request = Request.Builder().url(url).apply {
            if (useMobileApi) addMobileHeaders(null)
        }.build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    System.err.println("REWE market lookup failed (${response.code})")
                    return false
                }
                val body = response.body?.string() ?: return false
                val json = JsonParser.parseString(body)
                resolvedMarketId = if (useMobileApi) {
                    // mobile API: {"deliveryMarket":{"wwIdent":"..."}, "pickupMarkets":[...]}
                    json.asJsonObject.getAsJsonObject("deliveryMarket")?.get("wwIdent")?.asString
                        ?: json.asJsonObject.getAsJsonArray("pickupMarkets")
                            ?.firstOrNull()?.asJsonObject?.get("wwIdent")?.asString
                        ?: ""
                } else {
                    // legacy API
                    val arr = when {
                        json.isJsonArray -> json.asJsonArray
                        json.isJsonObject -> json.asJsonObject.getAsJsonArray("servicePortfolios")
                            ?: json.asJsonObject.getAsJsonArray("markets") ?: return false
                        else -> return false
                    }
                    arr.firstOrNull()?.asJsonObject?.let {
                        it.get("wwIdent")?.asString ?: it.get("marketID")?.asString ?: it.get("id")?.asString
                    } ?: ""
                }
                if (resolvedMarketId.isNotBlank()) {
                    println("REWE: resolved marketID=$resolvedMarketId for zip=$zipCode")
                    true
                } else false
            }
        } catch (e: Exception) {
            System.err.println("REWE market lookup error: ${e.message}")
            false
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(product: Product): List<StoreItem> {
        return if (useMobileApi) searchMobile(product) else searchLegacy(product)
    }

    private fun searchMobile(product: Product): List<StoreItem> {
        val ua = USER_AGENTS.random()
        val query = product.name.encodeUrl()
        val svcType = serviceType.uppercase().let { if (it == "PICKUP" || it == "DELIVERY") it else "PICKUP" }
        val params = buildString {
            append("query=$query")
            append("&page=1&objectsPerPage=10")
        }
        val url = "https://$MOBILE_HOST/api/products?$params"

        val request = Request.Builder().url(url).apply {
            addMobileHeaders(svcType, ua)
        }.build()

        return executeSearch(product, url, request) { body ->
            parseMobileProducts(body)
        }
    }

    private fun Request.Builder.addMobileHeaders(serviceType: String?, ua: String = USER_AGENTS[0]) {
        val instana = UUID.randomUUID().toString()
        val corr = UUID.randomUUID().toString()
        addHeader("user-agent", "REWE-Mobile-Client/5.7.3.47565 Android/14 $ua")
        addHeader("x-instana-android", instana)
        addHeader("Host", MOBILE_HOST)
        addHeader("Connection", "Keep-Alive")
        addHeader("rdfa", rdfaId)
        addHeader("rdtga", "payment-enable-google-pay,productlist-citrusad")
        addHeader("correlation-id", corr)
        if (serviceType != null && resolvedMarketId.isNotBlank()) {
            addHeader("rd-service-types",   serviceType)
            addHeader("x-rd-service-types", serviceType)
            addHeader("rd-customer-zip",    zipCode.ifBlank { "67065" })
            addHeader("x-rd-customer-zip",  zipCode.ifBlank { "67065" })
            addHeader("rd-market-id",       resolvedMarketId)
            addHeader("x-rd-market-id",     resolvedMarketId)
            addHeader("rd-postcode",        zipCode.ifBlank { "67065" })
            addHeader("rd-is-lsfk",         "false")
        }
    }

    private fun parseMobileProducts(body: String): List<StoreItem> {
        val items = mutableListOf<StoreItem>()
        val root = JsonParser.parseString(body).asJsonObject
        val products = root.getAsJsonObject("data")
            ?.getAsJsonObject("products")
            ?.getAsJsonArray("products")
            ?: return items

        for (el in products) {
            if (!el.isJsonObject) continue
            val p = el.asJsonObject
            val name = p.get("title")?.asString ?: continue
            val listing = p.getAsJsonObject("listing") ?: continue
            val priceCents = listing.get("currentRetailPrice")?.asLong ?: continue
            val price = priceCents / 100.0
            val unit = listing.get("grammage")?.asString ?: ""
            val id = p.get("productId")?.asString ?: p.get("articleId")?.asString
            val url = if (id != null) "https://www.rewe.de/p/$id" else null
            items.add(StoreItem(name, price, unit, url))
            if (items.size >= 5) break
        }
        return items
    }

    // ── Legacy web API (product names, no prices) ─────────────────────────────

    private fun searchLegacy(product: Product): List<StoreItem> {
        val query = product.name.encodeUrl()
        val params = buildString {
            append("query=$query")
            append("&serviceType=$serviceType")
            if (serviceType.uppercase() == "PICKUP" && resolvedMarketId.isNotBlank()) append("&marketID=$resolvedMarketId")
            if (zipCode.isNotBlank()) append("&zipCode=$zipCode")
        }
        val url = "$LEGACY_URL?$params"
        val request = Request.Builder().url(url).build()
        return executeSearch(product, url, request) { body -> parseLegacyProducts(body) }
    }

    private fun parseLegacyProducts(body: String): List<StoreItem> {
        val items = mutableListOf<StoreItem>()
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val products = root.getAsJsonObject("_embedded")?.getAsJsonArray("products")
                ?: root.getAsJsonObject("products")?.getAsJsonArray("products")
                ?: root.getAsJsonArray("products")
                ?: return items

            for (el in products) {
                if (!el.isJsonObject) continue
                val p = el.asJsonObject
                val name = p.get("productName")?.asString
                    ?: p.getAsJsonObject("productInfo")?.get("name")?.asString
                    ?: continue

                val articles = p.getAsJsonObject("_embedded")?.getAsJsonArray("articles")
                val articlePrice = articles?.firstOrNull()?.asJsonObject?.let { art ->
                    val listing = art.getAsJsonObject("_embedded")?.getAsJsonObject("listing")
                    findPrice(art, listing?.getAsJsonObject("pricing"), name)
                }
                val pricing = p.getAsJsonObject("pricing")
                val price = (articlePrice ?: findPrice(p, pricing, name) ?: continue) / 100.0

                val grammage = articles?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("_embedded")?.getAsJsonObject("listing")
                    ?.getAsJsonObject("pricing")?.get("grammage")?.asString
                val unit = grammage ?: pricing?.get("grammage")?.asString ?: p.get("grammage")?.asString ?: ""
                val id = p.get("id")?.asString ?: p.get("nan")?.asString
                items.add(StoreItem(name, price, unit, if (id != null) "https://www.rewe.de/p/$id" else null))
                if (items.size >= 5) break
            }
            items
        } catch (e: Exception) {
            System.err.println("REWE parse error: ${e.message}")
            items
        }
    }

    private fun findPrice(obj: JsonObject, pricing: JsonObject?, productName: String): Double? {
        fun numOrNull(e: JsonElement?): Double? {
            if (e == null || !e.isJsonPrimitive) return null
            val p = e.asJsonPrimitive
            return if (p.isNumber) p.asDouble else null
        }
        val fields = listOf("currentRetailPrice", "price", "retailPrice", "normalPrice", "listPrice", "amount")
        pricing?.let { pr -> fields.forEach { numOrNull(pr.get(it))?.let { return it } } }
        pricing?.entrySet()?.forEach { numOrNull(it.value)?.let { price ->
            if (debug) System.err.println("[REWE debug] price fallback field '${it.key}' = $price for '$productName'")
            return price
        }}
        fields.forEach { numOrNull(obj.get(it))?.let { return it } }
        if (debug) System.err.println("[REWE debug] no price found for '$productName'")
        return null
    }

    // ── Common search executor ────────────────────────────────────────────────

    private fun executeSearch(
        product: Product,
        url: String,
        request: Request,
        parse: (String) -> List<StoreItem>
    ): List<StoreItem> {
        val entry = JsonObject().apply {
            addProperty("query", product.name)
            addProperty("url", url)
            addProperty("api", if (useMobileApi) "mobile" else "legacy")
        }
        return try {
            client.newCall(request).execute().use { response ->
                entry.addProperty("status", response.code)
                val body = response.body?.string() ?: ""

                if (response.code == 403) {
                    val msg = if (useMobileApi) "403 — mTLS cert rejected?" else "403 — Cloudflare mTLS"
                    entry.addProperty("error", msg)
                    searchLog.add(entry)
                    System.err.println("REWE $msg for '${product.name}'")
                    println("  → ${product.name}: 0 candidates (HTTP 403)")
                    return emptyList()
                }
                if (!response.isSuccessful) {
                    entry.addProperty("error", body.take(300))
                    searchLog.add(entry)
                    System.err.println("REWE search failed (${response.code}) for '${product.name}'")
                    println("  → ${product.name}: 0 candidates (HTTP ${response.code})")
                    return emptyList()
                }

                val rawJson = runCatching { JsonParser.parseString(body) }.getOrNull()
                entry.add("raw_response", rawJson ?: com.google.gson.JsonPrimitive(body))

                if (debug && useMobileApi) {
                    System.err.println("[REWE mobile debug] $url")
                    System.err.println("[REWE mobile debug] ${body.take(1000)}")
                }

                val results = parse(body)
                entry.add("parsed_items", JsonArray().also { arr ->
                    results.forEach { item ->
                        arr.add(JsonObject().apply {
                            addProperty("name", item.name)
                            addProperty("price", item.price)
                            addProperty("unit", item.unit)
                        })
                    }
                })
                searchLog.add(entry)

                val suffix = if (!useMobileApi) " (no prices — cert needed)" else ""
                println("  → ${product.name}: ${results.size} candidate(s)${if (results.isEmpty()) " — no results" else ""}$suffix")
                results
            }
        } catch (e: Exception) {
            System.err.println("REWE search error for '${product.name}': ${e.message}")
            if (debug) e.printStackTrace()
            emptyList()
        }
    }

    fun writeSearchLog(file: File) {
        if (searchLog.isEmpty()) return
        val arr = com.google.gson.JsonArray()
        searchLog.forEach { arr.add(it) }
        file.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr))
        println("REWE search log saved to ${file.path}")
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
