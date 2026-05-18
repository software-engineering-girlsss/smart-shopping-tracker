package com.shoppingplaner.security

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit

@Component
@Order(2)
class AuthRateLimitFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AuthRateLimitFilter::class.java)

    // Caffeine evicts entries after 2 min of inactivity and caps at 50k IPs,
    // so the map never grows unboundedly even under IP-spoofing attacks.
    private val windows = Caffeine.newBuilder()
        .expireAfterAccess(2, TimeUnit.MINUTES)
        .maximumSize(50_000)
        .build<String, ArrayDeque<Long>>()

    override fun shouldNotFilterErrorDispatch() = true
    override fun shouldNotFilterAsyncDispatch() = true

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!request.requestURI.startsWith("/api/v2/auth/")) {
            chain.doFilter(request, response)
            return
        }
        val ip = request.getHeader("CF-Connecting-IP") ?: request.remoteAddr
        val now = System.currentTimeMillis()
        val timestamps = windows.get(ip) { ArrayDeque() }
        synchronized(timestamps) {
            timestamps.removeIf { it < now - WINDOW_MS }
            if (timestamps.size >= MAX_REQUESTS) {
                log.warn("Rate limit exceeded for IP={} on {}", ip, request.requestURI)
                response.status = 429
                response.contentType = "application/json"
                response.writer.write("""{"error":"Too many requests. Please try again later."}""")
                return
            }
            timestamps.addLast(now)
        }
        chain.doFilter(request, response)
    }

    companion object {
        private const val MAX_REQUESTS = 10
        private const val WINDOW_MS = 60_000L
    }
}
