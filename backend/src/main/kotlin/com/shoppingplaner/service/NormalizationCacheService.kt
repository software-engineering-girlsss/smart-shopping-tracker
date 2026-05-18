package com.shoppingplaner.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStringCommands
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class NormalizationCacheService(private val redis: StringRedisTemplate) {

    private val log = LoggerFactory.getLogger(NormalizationCacheService::class.java)
    private val ttl = Duration.ofDays(30)

    @PostConstruct
    fun init() {
        try {
            redis.execute { it.ping() }
            log.info("NormalizationCache: Redis connected")
        } catch (e: Exception) {
            log.warn("NormalizationCache: Redis unavailable at startup — running without cache ({})", e.message)
        }
    }

    fun getAll(names: List<String>): Map<String, String?> {
        if (names.isEmpty()) return emptyMap()
        return try {
            val prefixed = names.map { "norm:$it" }
            val values = redis.opsForValue().multiGet(prefixed) ?: return names.associateWith { null }
            names.zip(values).associate { (name, value) -> name to value }
        } catch (e: Exception) {
            log.warn("NormalizationCache: Redis multiGet failed — {}", e.message)
            names.associateWith { null }
        }
    }

    fun putAll(entries: Map<String, String>) {
        if (entries.isEmpty()) return
        try {
            redis.executePipelined { conn ->
                entries.forEach { (original, normalized) ->
                    conn.stringCommands().set(
                        "norm:$original".toByteArray(),
                        normalized.toByteArray(),
                        Expiration.from(ttl),
                        RedisStringCommands.SetOption.UPSERT
                    )
                }
                null
            }
            log.info("NormalizationCache: stored {} entries", entries.size)
        } catch (e: Exception) {
            log.warn("NormalizationCache: Redis pipeline write failed — {}", e.message)
        }
    }
}
