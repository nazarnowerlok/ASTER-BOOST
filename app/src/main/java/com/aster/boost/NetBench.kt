package com.aster.boost

import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.ceil

data class BenchResult(
    val avgMs: Double,
    val p95Ms: Double,
    val jitterMs: Double,
    val lossPct: Double
) {
    val score: Double
        get() = avgMs + (p95Ms - avgMs).coerceAtLeast(0.0) * 0.95 + jitterMs * 2.6 + lossPct * 14.0

    val grade: String
        get() = when {
            lossPct > 2.0 -> "LOSS"
            jitterMs > 18.0 -> "UNSTABLE"
            avgMs <= 25 && p95Ms <= 35 && jitterMs <= 5 && lossPct == 0.0 -> "ELITE"
            avgMs <= 45 && p95Ms <= 60 && jitterMs <= 9 && lossPct == 0.0 -> "GREAT"
            avgMs <= 70 && lossPct <= 1.0 -> "GOOD"
            else -> "FAIR"
        }
}

object NetBench {
    // Diverse public connectivity targets. These are NOT PUBG servers;
    // they are only used to compare route stability between MTU candidates.
    private val endpoints = listOf(
        "8.8.8.8" to 443,
        "8.8.4.4" to 443,
        "9.9.9.9" to 853,
        "149.112.112.112" to 853,
        "1.1.1.1" to 443
    )

    fun run(attempts: Int = 15): BenchResult {
        val samples = mutableListOf<Double>()
        var failures = 0

        repeat(attempts) { i ->
            val (host, port) = endpoints[i % endpoints.size]
            val start = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), 900)
                }
                samples += (System.nanoTime() - start) / 1_000_000.0
            } catch (_: Exception) {
                failures++
            }
            Thread.sleep(55)
        }

        val sorted = samples.sorted()
        val avg = if (sorted.isEmpty()) 999.0 else sorted.average()
        val p95 = if (sorted.isEmpty()) 999.0 else {
            sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)]
        }
        val jitter = if (samples.size < 2) 999.0 else {
            samples.zipWithNext().map { (a, b) -> abs(b - a) }.average()
        }
        val loss = failures * 100.0 / attempts

        return BenchResult(avg, p95, jitter, loss)
    }
}
