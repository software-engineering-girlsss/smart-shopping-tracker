package com.shoppingplaner.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shoppingplaner.model.MatchCacheEntry
import com.shoppingplaner.model.StoreItem
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStringCommands
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

@Service
class MatchCacheService(private val redis: StringRedisTemplate) {

    private val log    = LoggerFactory.getLogger(MatchCacheService::class.java)
    private val mapper = jacksonObjectMapper()
    private val ttl    = Duration.ofDays(30)

    @PostConstruct
    fun init() {
        try {
            redis.execute { it.ping() }
            log.info("MatchCache: Redis connected")
        } catch (e: Exception) {
            log.warn("MatchCache: Redis unavailable at startup — running without cache ({})", e.message)
        }
    }

    fun cacheKey(store: String, productName: String, candidates: List<StoreItem>): String {
        val raw = "$store|$productName|${candidates.map { it.name }.sorted().joinToString("|")}"
        return sha256(raw)
    }

    fun getAll(keys: List<String>): Map<String, MatchCacheEntry?> {
        if (keys.isEmpty()) return emptyMap()
        return try {
            val prefixed = keys.map { "match:$it" }
            val values = redis.opsForValue().multiGet(prefixed) ?: return keys.associateWith { null }
            keys.zip(values).associate { (key, value) ->
                key to value?.let { runCatching { mapper.readValue<MatchCacheEntry>(it) }.getOrNull() }
            }
        } catch (e: Exception) {
            log.warn("MatchCache: Redis multiGet failed — {}", e.message)
            keys.associateWith { null }
        }
    }

    fun putAll(entries: Map<String, MatchCacheEntry>) {
        if (entries.isEmpty()) return
        try {
            redis.executePipelined { conn ->
                entries.forEach { (key, entry) ->
                    conn.stringCommands().set(
                        "match:$key".toByteArray(),
                        mapper.writeValueAsBytes(entry),
                        Expiration.from(ttl),
                        RedisStringCommands.SetOption.UPSERT
                    )
                }
                null
            }
            log.info("MatchCache: stored {} entries (ttl={}d)", entries.size, ttl.toDays())
        } catch (e: Exception) {
            log.warn("MatchCache: Redis pipeline write failed — {}", e.message)
        }
    }

    fun toStoreItem(entry: MatchCacheEntry?): StoreItem? =
        if (entry != null && entry.hasMatch) StoreItem(entry.matchedName, entry.matchedPrice, entry.matchedUnit, entry.matchedUrl, entry.matchedImageUrl)
        else null

    fun toEntry(item: StoreItem?): MatchCacheEntry =
        if (item != null) MatchCacheEntry(true, item.name, item.price, item.unit, item.url, item.imageUrl)
        else              MatchCacheEntry(false)

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
