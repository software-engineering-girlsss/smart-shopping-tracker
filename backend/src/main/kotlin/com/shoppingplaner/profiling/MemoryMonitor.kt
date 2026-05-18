package com.shoppingplaner.profiling

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Logs JVM heap usage every 2 minutes to the MEMORY logger.
 * Warns when usage exceeds 80% of the configured max heap — a leading indicator of OOM.
 *
 * Look for these lines in prod logs to diagnose memory growth over time.
 * All values are in MB to stay human-readable.
 */
@Component
class MemoryMonitor {

    private val log = LoggerFactory.getLogger("MEMORY")
    private val mb  = 1024L * 1024L

    @Scheduled(fixedRate = 120_000)
    fun report() {
        val rt    = Runtime.getRuntime()
        val used  = (rt.totalMemory() - rt.freeMemory()) / mb
        val total = rt.totalMemory() / mb
        val max   = rt.maxMemory() / mb
        val pct   = if (max > 0) used * 100 / max else 0L

        log.info("heap used={}MB committed={}MB max={}MB usage={}%", used, total, max, pct)

        if (pct >= 90) {
            log.error("CRITICAL MEMORY: heap usage {}% — OOM likely imminent (used={}MB max={}MB)", pct, used, max)
        } else if (pct >= 80) {
            log.warn("HIGH MEMORY: heap usage {}% — potential OOM risk (used={}MB max={}MB)", pct, used, max)
        }
    }
}
