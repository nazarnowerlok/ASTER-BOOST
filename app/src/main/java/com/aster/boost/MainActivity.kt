package com.aster.boost

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val IMPORT_REQUEST = 2001
        private const val VPN_PERMISSION_REQUEST = 2002
        private const val FREE_CONFIG_URL = "https://protonvpn.com/support/wireguard-configurations"
    }

    private lateinit var wg: WireGuardController
    private lateinit var store: ConfigStore

    private lateinit var vpnStateText: TextView
    private lateinit var statusText: TextView
    private lateinit var endpointText: TextView
    private lateinit var directIpText: TextView
    private lateinit var vpnIpText: TextView
    private lateinit var trafficText: TextView
    private lateinit var handshakeText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var importButton: Button
    private lateinit var freeConfigButton: Button

    @Volatile
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wg = WireGuardController(this)
        store = ConfigStore(this)
        buildUi()
        refreshConfigUi()
        refreshTunnelState()
    }

    override fun onResume() {
        super.onResume()
        if (::vpnStateText.isInitialized && !busy) refreshTunnelState()
    }

    private fun buildUi() {
        window.statusBarColor = Color.rgb(8, 10, 15)
        window.navigationBarColor = Color.rgb(8, 10, 15)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(8, 10, 15))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        scroll.addView(root, LinearLayout.LayoutParams(-1, -1))

        root.addView(text("ASTER BOOST 2.0", 31f, Color.WHITE, Typeface.BOLD))
        root.addView(
            text("VPN first. Verify it for real. Optimize later.", 14.5f, Color.rgb(169, 177, 194), Typeface.NORMAL),
            marginParams(top = 4)
        )

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(22, 26, 35), 22f)
        }
        root.addView(card, marginParams(top = 22))

        vpnStateText = text("VPN: OFF", 22f, Color.WHITE, Typeface.BOLD)
        statusText = text("Status: READY", 14f, Color.rgb(215, 219, 228), Typeface.NORMAL)
        endpointText = text("Endpoint: no config", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        directIpText = text("Direct IP: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        vpnIpText = text("VPN IP: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        trafficText = text("Traffic: RX —  /  TX —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        handshakeText = text("Handshake: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)

        card.addView(vpnStateText)
        card.addView(statusText, marginParams(top = 10))
        card.addView(endpointText, marginParams(top = 12))
        card.addView(directIpText, marginParams(top = 6))
        card.addView(vpnIpText, marginParams(top = 6))
        card.addView(trafficText, marginParams(top = 6))
        card.addView(handshakeText, marginParams(top = 6))

        connectButton = Button(this).apply {
            text = "CONNECT & VERIFY"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(46, 142, 91))
            setOnClickListener { requestConnect() }
        }
        root.addView(connectButton, marginParams(height = 62, top = 18))

        disconnectButton = Button(this).apply {
            text = "DISCONNECT VPN"
            textSize = 15.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(166, 65, 65))
            setOnClickListener { disconnectVpn() }
        }
        root.addView(disconnectButton, marginParams(height = 54, top = 10))

        importButton = Button(this).apply {
            text = "IMPORT WIREGUARD .CONF"
            textSize = 15f
            setTextColor(Color.WHITE)
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(50, 57, 72))
            setOnClickListener { openConfigPicker() }
        }
        root.addView(importButton, marginParams(height = 54, top = 10))

        freeConfigButton = Button(this).apply {
            text = "GET FREE WIREGUARD CONFIG"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(60, 65, 83))
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FREE_CONFIG_URL))) }
                    .onFailure { statusText.text = "Status: Could not open browser" }
            }
        }
        root.addView(freeConfigButton, marginParams(height = 52, top = 10))

        root.addView(
            text(
                "This build deliberately removes SMART BOOST until the tunnel itself is proven. CONNECT & VERIFY starts the official WireGuard userspace backend, creates real traffic, checks WireGuard RX/TX + handshake data, and compares your public IP before/after. It never shows VPN: VERIFIED just because Android displayed a VPN icon.",
                12.5f,
                Color.rgb(145, 152, 168),
                Typeface.NORMAL
            ),
            marginParams(top = 18)
        )

        setContentView(scroll)
    }

    private fun requestConnect() {
        if (busy) return

        val config = store.load()
        if (config == null) {
            statusText.text = "Status: Import a working WireGuard .conf first"
            openConfigPicker()
            return
        }

        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST)
        } else {
            connectAndVerify()
        }
    }

    private fun connectAndVerify() {
        if (busy) return
        val config = store.load() ?: return

        busy = true
        setBusy(true)

        Thread {
            try {
                postStatus("Checking direct connection…")
                runCatching { if (wg.isUp()) wg.disconnect() }
                Thread.sleep(350)
                val directIp = NetworkIdentity.publicIp()

                postStatus("Starting WireGuard…")
                wg.validate(config)
                wg.connect(config)
                Thread.sleep(1400)

                postStatus("Generating VPN traffic…")
                val vpnIp = NetworkIdentity.publicIp()
                Thread.sleep(900)
                val health = wg.health()

                if (!health.up) error("WireGuard backend reports tunnel DOWN")

                val ipChanged = directIp != null && vpnIp != null && !directIp.equals(vpnIp, ignoreCase = true)
                val verified = ipChanged || (health.hasRecentHandshake && health.hasTraffic)
                val endpoint = extractEndpoint(config) ?: "unknown"

                runOnUiThread {
                    endpointText.text = "Endpoint: $endpoint"
                    directIpText.text = "Direct IP: ${directIp ?: "check failed"}"
                    vpnIpText.text = "VPN IP: ${vpnIp ?: "check failed"}"
                    trafficText.text = "Traffic: RX ${formatBytes(health.rxBytes)}  /  TX ${formatBytes(health.txBytes)}"
                    handshakeText.text = when (val age = health.handshakeAgeSeconds) {
                        null -> "Handshake: NONE"
                        else -> "Handshake: ${age}s ago"
                    }

                    when {
                        verified && ipChanged -> {
                            vpnStateText.text = "VPN: VERIFIED ✓"
                            vpnStateText.setTextColor(Color.rgb(119, 230, 159))
                            statusText.text = "Status: REAL VPN ACTIVE • public IP changed"
                        }
                        verified -> {
                            vpnStateText.text = "VPN: ACTIVE ✓"
                            vpnStateText.setTextColor(Color.rgb(119, 230, 159))
                            statusText.text = "Status: WireGuard handshake + traffic verified"
                        }
                        else -> {
                            vpnStateText.text = "VPN: NO HANDSHAKE"
                            vpnStateText.setTextColor(Color.rgb(255, 183, 96))
                            statusText.text = "Status: Tunnel interface is UP, but server did not verify"
                        }
                    }
                    connectButton.text = "RECONNECT & VERIFY"
                    disconnectButton.isEnabled = true
                }
            } catch (e: Exception) {
                runCatching { wg.disconnect() }
                runOnUiThread {
                    vpnStateText.text = "VPN: ERROR"
                    vpnStateText.setTextColor(Color.rgb(255, 126, 126))
                    statusText.text = "Status: ${friendlyError(e)}"
                    handshakeText.text = "Handshake: —"
                    trafficText.text = "Traffic: RX —  /  TX —"
                }
            } finally {
                busy = false
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun disconnectVpn() {
        if (busy) return
        busy = true
        setBusy(true)

        Thread {
            try {
                runCatching { wg.disconnect() }
                Thread.sleep(250)
                runOnUiThread {
                    vpnStateText.text = "VPN: OFF"
                    vpnStateText.setTextColor(Color.WHITE)
                    statusText.text = "Status: VPN disconnected"
                    vpnIpText.text = "VPN IP: —"
                    trafficText.text = "Traffic: RX —  /  TX —"
                    handshakeText.text = "Handshake: —"
                    connectButton.text = "CONNECT & VERIFY"
                    disconnectButton.isEnabled = false
                }
            } finally {
                busy = false
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun refreshTunnelState() {
        if (busy) return
        Thread {
            val health = runCatching { wg.health() }.getOrNull()
            runOnUiThread {
                if (health?.up == true) {
                    vpnStateText.text = if (health.hasRecentHandshake && health.hasTraffic) "VPN: ACTIVE ✓" else "VPN: UP"
                    vpnStateText.setTextColor(
                        if (health.hasRecentHandshake && health.hasTraffic) Color.rgb(119, 230, 159) else Color.rgb(255, 183, 96)
                    )
                    trafficText.text = "Traffic: RX ${formatBytes(health.rxBytes)}  /  TX ${formatBytes(health.txBytes)}"
                    handshakeText.text = health.handshakeAgeSeconds?.let { "Handshake: ${it}s ago" } ?: "Handshake: NONE"
                    disconnectButton.isEnabled = true
                    connectButton.text = "RECONNECT & VERIFY"
                } else {
                    vpnStateText.text = "VPN: OFF"
                    vpnStateText.setTextColor(Color.WHITE)
                    disconnectButton.isEnabled = false
                }
            }
        }.start()
    }

    private fun refreshConfigUi() {
        val config = store.load()
        if (config == null) {
            endpointText.text = "Endpoint: no config"
            importButton.text = "IMPORT WIREGUARD .CONF"
        } else {
            endpointText.text = "Endpoint: ${extractEndpoint(config) ?: "loaded"}"
            importButton.text = "REPLACE WIREGUARD .CONF"
        }
    }

    private fun openConfigPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            IMPORT_REQUEST -> {
                if (resultCode != RESULT_OK || data?.data == null) return
                try {
                    val uri = data.data!!
                    val config = contentResolver.openInputStream(uri)!!.use { input ->
                        BufferedReader(InputStreamReader(input)).readText()
                    }

                    wg.validate(config)
                    require(extractEndpoint(config) != null) { "Config has no Endpoint" }
                    store.save(config)
                    refreshConfigUi()
                    statusText.text = "Status: Config parsed OK • tap CONNECT & VERIFY"
                } catch (e: Exception) {
                    statusText.text = "Status: Bad .conf: ${friendlyError(e)}"
                }
            }

            VPN_PERMISSION_REQUEST -> {
                if (resultCode == RESULT_OK || VpnService.prepare(this) == null) {
                    statusText.text = "Status: VPN permission granted"
                    connectAndVerify()
                } else {
                    statusText.text = "Status: VPN permission denied"
                }
            }
        }
    }

    private fun extractEndpoint(config: String): String? =
        Regex("(?im)^\\s*Endpoint\\s*=\\s*(.+?)\\s*$")
            .find(config)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        if (raw.isBlank()) return error.javaClass.simpleName
        return raw.take(140)
    }

    private fun postStatus(value: String) {
        runOnUiThread { statusText.text = "Status: $value" }
    }

    private fun setBusy(value: Boolean) {
        connectButton.isEnabled = !value
        importButton.isEnabled = !value
        freeConfigButton.isEnabled = !value
        if (value) {
            connectButton.text = "VERIFYING…"
            disconnectButton.isEnabled = false
        } else {
            refreshConfigUi()
            refreshTunnelState()
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun text(value: String, size: Float, color: Int, style: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setTypeface(Typeface.DEFAULT, style)
        gravity = Gravity.START
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun marginParams(height: Int = -2, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (height > 0) dp(height) else height
        ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
