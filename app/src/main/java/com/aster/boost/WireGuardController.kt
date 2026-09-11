package com.aster.boost

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class WireGuardController(context: Context) {
    private val backend = GoBackend(context.applicationContext)

    private val tunnel = object : Tunnel {
        override fun getName(): String = "ASTER_BOOST"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    fun connect(configText: String) {
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun isUp(): Boolean = backend.getState(tunnel) == Tunnel.State.UP
}
