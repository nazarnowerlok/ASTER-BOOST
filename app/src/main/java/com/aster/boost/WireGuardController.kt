package com.aster.boost

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

data class TunnelHealth(
    val up: Boolean,
    val rxBytes: Long,
    val txBytes: Long,
    val latestHandshakeEpochMillis: Long,
    val backendVersion: String
) {
    val handshakeAgeSeconds: Long?
        get() = if (latestHandshakeEpochMillis <= 0L) null else
            ((System.currentTimeMillis() - latestHandshakeEpochMillis) / 1000L).coerceAtLeast(0L)

    val hasTraffic: Boolean
        get() = rxBytes > 0L || txBytes > 0L

    val hasRecentHandshake: Boolean
        get() = handshakeAgeSeconds?.let { it <= 180L } ?: false
}

class WireGuardController(context: Context) {
    private val backend = GoBackend(context.applicationContext)

    private val tunnel = object : Tunnel {
        override fun getName(): String = "ASTER_BOOST"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    fun validate(configText: String): Config =
        Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))

    fun connect(configText: String): Tunnel.State {
        val config = validate(configText)
        return backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun disconnect(): Tunnel.State =
        backend.setState(tunnel, Tunnel.State.DOWN, null)

    fun isUp(): Boolean = backend.getState(tunnel) == Tunnel.State.UP

    fun health(): TunnelHealth {
        val state = backend.getState(tunnel)
        val stats = backend.getStatistics(tunnel)
        var latestHandshake = 0L

        for (peer in stats.peers()) {
            val peerStats = stats.peer(peer) ?: continue
            latestHandshake = maxOf(latestHandshake, peerStats.latestHandshakeEpochMillis())
        }

        return TunnelHealth(
            up = state == Tunnel.State.UP,
            rxBytes = stats.totalRx(),
            txBytes = stats.totalTx(),
            latestHandshakeEpochMillis = latestHandshake,
            backendVersion = backend.version
        )
    }
}
