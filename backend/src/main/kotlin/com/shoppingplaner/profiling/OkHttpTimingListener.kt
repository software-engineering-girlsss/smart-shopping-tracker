package com.shoppingplaner.profiling

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * OkHttp EventListener that records call duration as a Micrometer timer
 * (exposed at /actuator/prometheus) and logs slow calls.
 *
 * Use [factory] when building OkHttpClient:
 *   OkHttpClient.Builder().eventListenerFactory(OkHttpTimingListener.factory(registry, "rewe"))
 *
 * Each call gets a fresh listener instance (OkHttp's EventListener.Factory contract).
 */
class OkHttpTimingListener(
    private val registry: MeterRegistry,
    private val clientName: String
) : EventListener() {

    private var callStartNs        = 0L
    private var connectStartNs     = 0L
    private var responseBodyStartNs = 0L

    override fun callStart(call: Call) {
        callStartNs = System.nanoTime()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartNs = System.nanoTime()
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        if (connectStartNs == 0L) return
        val ms = (System.nanoTime() - connectStartNs) / 1_000_000
        if (ms > 200) log.warn("[okhttp:{}] slow TCP connect to {} = {}ms", clientName, call.request().url.host, ms)
    }

    override fun responseBodyStart(call: Call) {
        responseBodyStartNs = System.nanoTime()
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        if (responseBodyStartNs == 0L) return
        val ms = (System.nanoTime() - responseBodyStartNs) / 1_000_000
        Timer.builder("okhttp.response.body")
            .tag("client", clientName)
            .tag("host", call.request().url.host)
            .register(registry)
            .record(ms, TimeUnit.MILLISECONDS)
    }

    override fun callEnd(call: Call) = recordCall(call, "success")

    override fun callFailed(call: Call, ioe: IOException) {
        log.warn("[okhttp:{}] call failed — {}: {}", clientName, call.request().url.host, ioe.message)
        recordCall(call, "failure")
    }

    private fun recordCall(call: Call, outcome: String) {
        if (callStartNs == 0L) return
        val ms = (System.nanoTime() - callStartNs) / 1_000_000
        Timer.builder("okhttp.call")
            .tag("client", clientName)
            .tag("host", call.request().url.host)
            .tag("outcome", outcome)
            .register(registry)
            .record(ms, TimeUnit.MILLISECONDS)
        log.info("[okhttp:{}] {} {} → {}ms ({})",
            clientName, call.request().method, call.request().url.encodedPath, ms, outcome)
    }

    companion object {
        private val log = LoggerFactory.getLogger("TIMING.HTTP")

        fun factory(registry: MeterRegistry, clientName: String) =
            Factory { OkHttpTimingListener(registry, clientName) }
    }
}
