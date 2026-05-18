package com.shoppingplaner.profiling

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Measures total backend processing time for every HTTP request.
 * Log format is grep-friendly: [TIMING] METHOD /path STATUS | total=Xms
 *
 * Enable via application.properties:
 *   logging.level.TIMING=INFO
 */
@Component
@Order(1)
class TimingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("TIMING")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val t0     = System.nanoTime()
        val rt     = Runtime.getRuntime()
        val memBefore = (rt.totalMemory() - rt.freeMemory()) / MB
        try {
            filterChain.doFilter(request, response)
        } finally {
            val totalMs   = (System.nanoTime() - t0) / 1_000_000
            val memAfter  = (rt.totalMemory() - rt.freeMemory()) / MB
            val memDelta  = memAfter - memBefore
            val method    = request.method
            val uri       = request.requestURI
            val status    = response.status

            log.info("[TIMING] {} {} {} | total={}ms | mem={}MB Δ{}MB",
                method, uri, status, totalMs, memAfter, memDelta)

            if (totalMs > 5_000) {
                log.warn("[TIMING][SLOW] {} {} took {}ms — investigate external calls (mem used={}MB)",
                    method, uri, totalMs, memAfter)
            }
        }
    }

    companion object {
        private const val MB = 1024L * 1024L
    }
}
