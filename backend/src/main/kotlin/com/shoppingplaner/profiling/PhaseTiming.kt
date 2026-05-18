package com.shoppingplaner.profiling

import org.slf4j.LoggerFactory

/**
 * Lightweight per-request phase timer.
 *
 * Usage:
 *   val t = PhaseTiming("compare")
 *   val norm = t.measure("openai_normalize") { aiMatcher.normalizeNames(uncached) }
 *   val rewe = t.measure("rewe_search")      { reweService.search(product) }
 *   t.report()
 */
class PhaseTiming(private val operation: String) {

    private val log = LoggerFactory.getLogger("TIMING.PHASE")
    private val phases = mutableListOf<PhaseResult>()

    data class PhaseResult(val name: String, val ms: Long)

    fun <T> measure(name: String, block: () -> T): T {
        val t0 = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - t0) / 1_000_000
        phases += PhaseResult(name, ms)
        log.debug("[{}] phase={} ms={}", operation, name, ms)
        return result
    }

    /** Logs total time and per-phase breakdown; warns if one phase dominates. */
    fun report() {
        val total = phases.sumOf { it.ms }
        val breakdown = phases.joinToString(" | ") { "${it.name}=${it.ms}ms" }
        val slowest = phases.maxByOrNull { it.ms }

        log.info("[{}] TOTAL={}ms | {}", operation, total, breakdown)

        if (slowest != null && slowest.ms > total * 0.6) {
            log.warn("[{}] BOTTLENECK: '{}' took {}ms ({}% of total)",
                operation, slowest.name, slowest.ms,
                (slowest.ms * 100 / total.coerceAtLeast(1)))
        }
    }

    fun phases(): List<PhaseResult> = phases.toList()
    fun totalMs(): Long = phases.sumOf { it.ms }
}
