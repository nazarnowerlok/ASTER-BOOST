package com.aster.boost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
        get() = avgMs + (p95Ms - avgMs).coerceAtLeast(0.0) * 0.85 + jitterMs * 2.3 + lossPct * 11.0

    val grade: String
        get() = when {
            lossPct > 2.0 -> "LOSS"
            jitterMs > 18.0 -> "UNSTABLE"
            avgMs <= 25 && jitterMs <= 5 && lossPct == 0.0 -> "ELITE"
            avgMs <= 45 && jitterMs <= 9 && lossPct == 0.0 -> "GREAT"
            avgMs <= 70 && lossPct <= 1.0 -> "GOOD"
            else -> "FAIR"
        }
}

object NetBench {
    private val endpoints = listOf(
        "1.1.1.1" to 443,
        "8.8.8.8" to 443,
        "9.9.9.9" to 443
    )

    suspend fun run(attempts: Int = 15): BenchResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Double>()
        var failures = 0

        repeat(attempts) { i ->
            val (host, port) = endpoints[i % endpoints.size]
            val start = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), 1200)
                }
                samples += (System.nanoTime() - start) / 1_000_000.0
            } catch (_: Exception) {
                failures++
            }
            delay(75)
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

        BenchResult(avg, p95, jitter, loss)
    }
}
