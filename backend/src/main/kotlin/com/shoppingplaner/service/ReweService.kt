package com.shoppingplaner.service

import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID
import com.github.benmanes.caffeine.cache.Caffeine
import com.shoppingplaner.profiling.OkHttpTimingListener
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * REWE product search service.
 *
 * Uses the REWE mobile API (mobile-clients-api.rewe.de) which requires an mTLS
 * client certificate extracted from the REWE Android APK.
 *
 * Returns an empty list when no cert is configured.
 */
@Service
class ReweService(
    private val props: AppProperties,
    private val meterRegistry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(ReweService::class.java)

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
    private var resolvedMarketId: String = props.rewe.marketId

    private val searchCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, List<StoreItem>>()

    private val timingFactory = OkHttpTimingListener.factory(meterRegistry, "rewe")

    init {
        val certPath = props.rewe.certFile.ifBlank { null }
        val keyPath  = props.rewe.keyFile.ifBlank { null }

        val mtlsClient = if (certPath != null && keyPath != null && File(certPath).isFile && File(keyPath).isFile) {
            try {
                buildMtlsClient(File(certPath), File(keyPath)).also {
                    log.info("REWE: using mobile API with mTLS cert: {}", certPath)
                }
            } catch (e: Exception) {
                log.warn("REWE: failed to load mTLS cert ({}) — REWE search disabled", e.message)
                null
            }
        } else {
            if (certPath != null || keyPath != null)
                log.warn("REWE: cert/key path is not a valid file — REWE search disabled. certPath={}", certPath)
            else
                log.info("REWE: no cert configured — REWE search disabled")
            null
        }

        useMobileApi = mtlsClient != null
        client = mtlsClient ?: buildFallbackClient()
    }

    // ── mTLS client ───────────────────────────────────────────────────────────

    private fun buildFallbackClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .eventListenerFactory(timingFactory)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 Chrome/122.0")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "de-DE,de;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    private fun buildMtlsClient(certPem: File, keyPem: File): OkHttpClient { // also sets timeouts
        val cf   = CertificateFactory.getInstance("X.509")
        val cert = certPem.inputStream().use { cf.generateCertificate(it) as X509Certificate }

        val keyContent = keyPem.readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyContent)))

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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .eventListenerFactory(timingFactory)
            .build()
    }

    // ── Market resolution ─────────────────────────────────────────────────────

    fun resolveMarketId(): Boolean {
        if (resolvedMarketId.isNotBlank()) {
            log.debug("REWE: using configured marketId={}", resolvedMarketId)
            return true
        }
        log.info("REWE: resolving marketId for zip='{}', serviceType='{}'", props.rewe.zipCode, props.rewe.serviceType)
        val zip = props.rewe.zipCode.ifBlank { log.warn("REWE: zipCode is blank, cannot resolve marketId"); return false }

        val url = if (useMobileApi) "$MARKET_URL/$zip"
                  else "https://shop.rewe.de/api/marketselection/zipcodes/$zip/services/pickup"

        val request = Request.Builder().url(url).apply {
            if (useMobileApi) addMobileHeaders(null)
        }.build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) { log.warn("REWE market lookup failed ({})", response.code); return false }
                val body = response.body?.string() ?: return false
                log.debug("REWE market response (first 300): {}", body.take(300))
                val json = JsonParser.parseString(body)
                resolvedMarketId = if (useMobileApi) {
                    // Response can be wrapped in {"data":{"servicePortfolio":{...}}} or flat
                    val root = json.asJsonObject
                    val sp = root.getAsJsonObject("data")?.getAsJsonObject("servicePortfolio") ?: root
                    sp.getAsJsonObject("deliveryMarket")?.get("wwIdent")?.asString
                        ?: sp.getAsJsonArray("pickupMarkets")
                            ?.firstOrNull()?.asJsonObject?.get("wwIdent")?.asString ?: ""
                } else {
                    val arr = when {
                        json.isJsonArray  -> json.asJsonArray
                        json.isJsonObject -> json.asJsonObject.getAsJsonArray("servicePortfolios")
                            ?: json.asJsonObject.getAsJsonArray("markets") ?: return false
                        else -> return false
                    }
                    arr.firstOrNull()?.asJsonObject?.let {
                        it.get("wwIdent")?.asString ?: it.get("marketID")?.asString ?: it.get("id")?.asString
                    } ?: ""
                }
                if (resolvedMarketId.isNotBlank()) {
                    log.info("REWE: resolved marketId={} for zip={}", resolvedMarketId, zip)
                    true
                } else {
                    log.warn("REWE: could not parse marketId from response for zip={}. Body: {}", zip, body.take(300))
                    false
                }
            }
        } catch (e: Exception) {
            log.error("REWE market lookup error: {}", e.message)
            false
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(product: SearchQuery): List<StoreItem> {
        if (!useMobileApi) {
            log.warn("REWE: no cert configured, skipping search for '{}'", product.name)
            return emptyList()
        }
        if (resolvedMarketId.isBlank()) resolveMarketId()
        return searchMobile(product)
    }

    private fun searchMobile(product: SearchQuery): List<StoreItem> {
        searchCache.getIfPresent(product.name)?.let { return it }

        val svcType = props.rewe.serviceType.uppercase().let { if (it == "PICKUP" || it == "DELIVERY") it else "PICKUP" }
        val url = "https://$MOBILE_HOST/api/products?query=${product.name.encodeUrl()}&page=1&objectsPerPage=10"

        val request = Request.Builder().url(url).apply { addMobileHeaders(svcType) }.build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 403) { log.warn("REWE 403 for '{}' — mTLS cert rejected?", product.name); return emptyList() }
                if (!response.isSuccessful) { log.warn("REWE search failed ({}) for '{}'", response.code, product.name); return emptyList() }
                val body = response.body?.string() ?: return emptyList()
                parseMobileProducts(body).also { results ->
                    log.debug("REWE '{}': {} result(s)", product.name, results.size)
                    searchCache.put(product.name, results)
                }
            }
        } catch (e: Exception) {
            log.error("REWE search error for '{}': {}", product.name, e.message)
            emptyList()
        }
    }

    private fun Request.Builder.addMobileHeaders(serviceType: String?, ua: String = USER_AGENTS[0]) {
        addHeader("user-agent", "REWE-Mobile-Client/5.7.3.47565 Android/14 $ua")
        addHeader("x-instana-android", UUID.randomUUID().toString())
        addHeader("Host", MOBILE_HOST)
        addHeader("rdfa", rdfaId)
        addHeader("rdtga", "payment-enable-google-pay,productlist-citrusad")
        addHeader("correlation-id", UUID.randomUUID().toString())
        if (serviceType != null && resolvedMarketId.isNotBlank()) {
            val zip = props.rewe.zipCode.ifBlank { "67065" }
            addHeader("rd-service-types",   serviceType)
            addHeader("x-rd-service-types", serviceType)
            addHeader("rd-customer-zip",    zip)
            addHeader("x-rd-customer-zip",  zip)
            addHeader("rd-market-id",       resolvedMarketId)
            addHeader("x-rd-market-id",     resolvedMarketId)
            addHeader("rd-postcode",        zip)
            addHeader("rd-is-lsfk",         "false")
        }
    }

    private fun parseMobileProducts(body: String): List<StoreItem> {
        val items = mutableListOf<StoreItem>()
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val products = root.getAsJsonObject("data")
                ?.getAsJsonObject("products")
                ?.getAsJsonArray("products") ?: return items

            for (el in products) {
                if (!el.isJsonObject) continue
                val p       = el.asJsonObject
                val name    = p.get("title")?.asString ?: continue
                val listing = p.getAsJsonObject("listing") ?: continue
                val price   = (listing.get("currentRetailPrice")?.asLong ?: continue) / 100.0
                val unit    = listing.get("grammage")?.asString ?: ""
                val id      = p.get("productId")?.asString ?: p.get("articleId")?.asString
                val imageUrl = p.get("imageURL")?.asString ?: ""
                items.add(StoreItem(name, price, unit, if (id != null) "https://www.rewe.de/p/$id" else null, imageUrl))
                if (items.size >= 5) break
            }
            items
        } catch (e: Exception) {
            log.error("REWE parse error: {}", e.message)
            items
        }
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    // ── Basket / delivery fees ────────────────────────────────────────────────

    data class ReweBasketFees(
        val serviceFee: Double,
        val minimumOrderAmount: Double?,
        val remainingForNextTier: Double?
    )

    /**
     * POSTs a basket to the REWE mobile API and returns the fee breakdown.
     * Returns null if mTLS cert is not configured or the call fails.
     *
     * @param positions list of (articleId, quantity) from REWE product URLs
     */
    fun fetchBasketFees(positions: List<Pair<String, Int>>): ReweBasketFees? {
        if (!useMobileApi) return null
        if (resolvedMarketId.isBlank()) resolveMarketId()

        val positionsJson = positions.joinToString(",") { (id, qty) ->
            """{"articleId":"$id","amount":$qty}"""
        }
        val body = """{"positions":[$positionsJson]}"""

        val svcType = props.rewe.serviceType.uppercase().let { if (it == "PICKUP" || it == "DELIVERY") it else "DELIVERY" }
        val request = Request.Builder()
            .url("https://$MOBILE_HOST/api/baskets")
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .apply { addMobileHeaders(svcType) }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.debug("REWE basket API returned {}: {}", response.code, response.body?.string()?.take(200))
                    return null
                }
                val json = JsonParser.parseString(response.body?.string() ?: return null).asJsonObject
                parseBasketFees(json)
            }
        } catch (e: Exception) {
            log.debug("REWE basket fees fetch failed: {}", e.message)
            null
        }
    }

    private fun parseBasketFees(json: JsonObject): ReweBasketFees? {
        return try {
            val feeCents = json.getAsJsonObject("fees")
                ?.getAsJsonObject("serviceFee")
                ?.get("fee")?.asLong ?: 0L

            val minOrderCents = json.getAsJsonObject("serviceConfiguration")
                ?.get("minimumOrderAmount")?.asLong

            val remainingCents = json.getAsJsonObject("staggerings")
                ?.getAsJsonObject("nextStaggering")
                ?.get("remainingArticlePrice")?.asLong

            ReweBasketFees(
                serviceFee            = feeCents / 100.0,
                minimumOrderAmount    = minOrderCents?.let { it / 100.0 },
                remainingForNextTier  = remainingCents?.let { it / 100.0 }
            )
        } catch (e: Exception) {
            log.debug("REWE basket fees parse error: {}", e.message)
            null
        }
    }
}
