package com.aster.boost

import java.net.HttpURLConnection
import java.net.URL

object NetworkIdentity {
    fun publicIp(): String? {
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com"
        )

        for (endpoint in endpoints) {
            val result = runCatching {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                    useCaches = false
                    setRequestProperty("User-Agent", "ASTER-BOOST/2.1")
                }
                try {
                    connection.inputStream.bufferedReader().use { it.readText().trim() }
                        .takeIf { it.isNotBlank() && it.length <= 64 }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()

            if (!result.isNullOrBlank()) return result
        }
        return null
    }
}
