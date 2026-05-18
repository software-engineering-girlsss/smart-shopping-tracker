package com.shoppingplaner.api

import com.github.benmanes.caffeine.cache.Caffeine
import com.shoppingplaner.profiling.OkHttpTimingListener
import com.shoppingplaner.service.PicnicService
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/v2/images")
class ImageProxyController(
    private val picnicService: PicnicService,
    private val meterRegistry: MeterRegistry,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .eventListenerFactory(OkHttpTimingListener.factory(meterRegistry, "image-proxy"))
        .build()

    private val allowedHosts = setOf(
        "storefront-prod.de.picnicinternational.com",
        "storefront-prod.nl.picnicinternational.com",
        "img.rewe-static.de",
        "images.rewe.de"
    )

    private data class CachedImage(val bytes: ByteArray, val contentType: String)

    private val imageCache = Caffeine.newBuilder()
        .maximumWeight(20 * 1024 * 1024L) // 20 MB cap
        .weigher { _: String, v: CachedImage -> v.bytes.size }
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, CachedImage>()

    @GetMapping("/proxy")
    fun proxy(@RequestParam url: String): ResponseEntity<ByteArray> {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return ResponseEntity.badRequest().build()

        if (uri.host == null || uri.host !in allowedHosts) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        imageCache.getIfPresent(url)?.let { cached ->
            val mediaType = runCatching { MediaType.parseMediaType(cached.contentType) }.getOrDefault(MediaType.IMAGE_PNG)
            return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(cached.bytes)
        }

        val reqBuilder = Request.Builder().url(url)

        // Picnic CDN requires x-picnic-auth — inject the service token
        if (uri.host.contains("picnicinternational.com")) {
            picnicService.currentAuthToken()?.let { token ->
                reqBuilder
                    .header("x-picnic-auth", token)
                    .header("x-picnic-agent", "30100;1.228.1-15480;")
                    .header("x-picnic-did", "3C417201548B2E3B")
                    .header("User-Agent", "okhttp/4.9.0")
            }
        }

        val resp = runCatching { client.newCall(reqBuilder.build()).execute() }.getOrNull()
            ?: return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()

        val body = resp.body ?: run { resp.close(); return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build() }
        val contentType = resp.header("Content-Type") ?: "image/png"
        val mediaType = runCatching { MediaType.parseMediaType(contentType) }.getOrDefault(MediaType.IMAGE_PNG)

        val bytes = runCatching { body.bytes() }.getOrElse { resp.close(); return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build() }
        resp.close()

        if (resp.code in 200..299) {
            imageCache.put(url, CachedImage(bytes, contentType))
        }

        return ResponseEntity.status(resp.code)
            .contentType(mediaType)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(bytes)
    }
}
