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
        private const val VERIFY_WINDOW_MS = 6500L
        private const val ACTION_CONNECT = 1
        private const val ACTION_OPTIMIZE = 2
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

    private lateinit var routeGradeText: TextView
    private lateinit var avgText: TextView
    private lateinit var p95Text: TextView
    private lateinit var jitterText: TextView
    private lateinit var lossText: TextView
    private lateinit var mtuText: TextView
    private lateinit var directBenchText: TextView

    private lateinit var connectButton: Button
    private lateinit var optimizeButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var importButton: Button
    private lateinit var freeConfigButton: Button

    @Volatile
    private var busy = false
    private var pendingVpnAction = ACTION_CONNECT

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

        root.addView(text("ASTER BOOST 2.2", 31f, Color.WHITE, Typeface.BOLD))
        root.addView(
            text("Verified WireGuard + automatic game-route tuning.", 14.5f, Color.rgb(169, 177, 194), Typeface.NORMAL),
            marginParams(top = 4)
        )

        val vpnCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(22, 26, 35), 22f)
        }
        root.addView(vpnCard, marginParams(top = 22))

        vpnStateText = text("VPN: OFF", 22f, Color.WHITE, Typeface.BOLD)
        statusText = text("Status: READY", 14f, Color.rgb(215, 219, 228), Typeface.NORMAL)
        endpointText = text("Endpoint: no config", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        directIpText = text("Direct IP: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        vpnIpText = text("VPN IP: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        trafficText = text("Traffic: RX —  /  TX —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        handshakeText = text("Handshake: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)

        vpnCard.addView(vpnStateText)
        vpnCard.addView(statusText, marginParams(top = 10))
        vpnCard.addView(endpointText, marginParams(top = 12))
        vpnCard.addView(directIpText, marginParams(top = 6))
        vpnCard.addView(vpnIpText, marginParams(top = 6))
        vpnCard.addView(trafficText, marginParams(top = 6))
        vpnCard.addView(handshakeText, marginParams(top = 6))

        val routeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(18, 23, 32), 22f)
        }
        root.addView(routeCard, marginParams(top = 14))

        routeGradeText = text("GAME ROUTE: NOT TUNED", 19f, Color.WHITE, Typeface.BOLD)
        avgText = text("AVG: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        p95Text = text("P95: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        jitterText = text("Jitter: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        lossText = text("Probe loss: —", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        mtuText = text("MTU: ${ConfigTuner.extractMtu(store.load().orEmpty()) ?: "—"}", 13.5f, Color.rgb(190, 197, 211), Typeface.NORMAL)
        directBenchText = text("DIRECT baseline: —", 12.5f, Color.rgb(145, 152, 168), Typeface.NORMAL)

        routeCard.addView(routeGradeText)
        routeCard.addView(avgText, marginParams(top = 10))
        routeCard.addView(p95Text, marginParams(top = 5))
        routeCard.addView(jitterText, marginParams(top = 5))
        routeCard.addView(lossText, marginParams(top = 5))
        routeCard.addView(mtuText, marginParams(top = 5))
        routeCard.addView(directBenchText, marginParams(top = 10))

        connectButton = Button(this).apply {
            text = "CONNECT & VERIFY"
            textSize = 16.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(46, 142, 91))
            setOnClickListener { requestConnect() }
        }
        root.addView(connectButton, marginParams(height = 60, top = 18))

        optimizeButton = Button(this).apply {
            text = "GAME OPTIMIZE • AUTO MTU"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(89, 70, 235))
            setOnClickListener { requestOptimize() }
        }
        root.addView(optimizeButton, marginParams(height = 60, top = 10))

        disconnectButton = Button(this).apply {
            text = "DISCONNECT VPN"
            textSize = 15.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            isEnabled = false
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
                withDirectInternet {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FREE_CONFIG_URL))) }
                        .onFailure { statusText.text = "Status: Could not open browser" }
                }
            }
        }
        root.addView(freeConfigButton, marginParams(height = 52, top = 10))

        root.addView(
            text(
                "GAME OPTIMIZE tests several safe MTU values through the verified VPN, scores average latency, P95, jitter and failed probes, then saves and keeps the most stable route. Public probes are not PUBG server ping, so this improves route quality but cannot change the game's hitboxes or server-side hit registration.",
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
        if (store.load() == null) {
            statusText.text = "Status: Import a working WireGuard .conf first"
            openConfigPicker()
            return
        }

        pendingVpnAction = ACTION_CONNECT
        requestVpnPermissionOrRun()
    }

    private fun requestOptimize() {
        if (busy) return
        if (store.load() == null) {
            statusText.text = "Status: Import a working WireGuard .conf first"
            openConfigPicker()
            return
        }

        pendingVpnAction = ACTION_OPTIMIZE
        requestVpnPermissionOrRun()
    }

    private fun requestVpnPermissionOrRun() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST)
        } else {
            runPendingVpnAction()
        }
    }

    private fun runPendingVpnAction() {
        if (pendingVpnAction == ACTION_OPTIMIZE) optimizeGameRoute() else connectAndVerify()
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
                Thread.sleep(300)
                val directIp = NetworkIdentity.publicIp()

                postStatus("Starting WireGuard…")
                wg.validate(config)
                wg.connect(config)
                Thread.sleep(500)

                postStatus("Probing VPN route…")
                var vpnIp = NetworkIdentity.publicIp()
                var health = wg.health()
                val deadline = System.currentTimeMillis() + VERIFY_WINDOW_MS

                while (
                    health.up &&
                    !(health.hasRecentHandshake && health.rxBytes > 0L) &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(500)
                    health = wg.health()
                }

                if (!health.up) error("WireGuard backend reports tunnel DOWN")

                val endpoint = extractEndpoint(config) ?: "unknown"
                val verified = health.hasRecentHandshake && health.rxBytes > 0L

                if (!verified) {
                    val failedHealth = health
                    runCatching { wg.disconnect() }
                    Thread.sleep(250)

                    runOnUiThread {
                        endpointText.text = "Endpoint: $endpoint"
                        directIpText.text = "Direct IP: ${directIp ?: "check failed"}"
                        vpnIpText.text = "VPN IP: check failed"
                        trafficText.text = "Traffic: RX ${formatBytes(failedHealth.rxBytes)}  /  TX ${formatBytes(failedHealth.txBytes)}"
                        handshakeText.text = failedHealth.handshakeAgeSeconds?.let { "Handshake: ${it}s ago" } ?: "Handshake: NONE"
                        vpnStateText.text = "VPN: FAILED • AUTO OFF"
                        vpnStateText.setTextColor(Color.rgb(255, 183, 96))
                        statusText.text = "Status: No server reply • direct internet restored automatically"
                        connectButton.text = "TRY AGAIN"
                        disconnectButton.isEnabled = false
                    }
                } else {
                    if (vpnIp == null) vpnIp = NetworkIdentity.publicIp()
                    val ipChanged = directIp != null && vpnIp != null && !directIp.equals(vpnIp, ignoreCase = true)

                    runOnUiThread {
                        endpointText.text = "Endpoint: $endpoint"
                        directIpText.text = "Direct IP: ${directIp ?: "check failed"}"
                        vpnIpText.text = "VPN IP: ${vpnIp ?: "check failed"}"
                        trafficText.text = "Traffic: RX ${formatBytes(health.rxBytes)}  /  TX ${formatBytes(health.txBytes)}"
                        handshakeText.text = health.handshakeAgeSeconds?.let { "Handshake: ${it}s ago" } ?: "Handshake: NONE"

                        if (ipChanged) {
                            vpnStateText.text = "VPN: VERIFIED ✓"
                            statusText.text = "Status: REAL VPN ACTIVE • public IP changed"
                        } else {
                            vpnStateText.text = "VPN: ACTIVE ✓"
                            statusText.text = "Status: WireGuard handshake + return traffic verified"
                        }
                        vpnStateText.setTextColor(Color.rgb(119, 230, 159))
                        connectButton.text = "RECONNECT & VERIFY"
                        disconnectButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runCatching { wg.disconnect() }
                runOnUiThread {
                    vpnStateText.text = "VPN: ERROR • AUTO OFF"
                    vpnStateText.setTextColor(Color.rgb(255, 126, 126))
                    statusText.text = "Status: ${friendlyError(e)} • direct internet restored"
                    vpnIpText.text = "VPN IP: —"
                    handshakeText.text = "Handshake: —"
                    trafficText.text = "Traffic: RX —  /  TX —"
                    disconnectButton.isEnabled = false
                }
            } finally {
                busy = false
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun optimizeGameRoute() {
        if (busy) return
        val baseConfig = store.load() ?: return

        busy = true
        setBusy(true)

        Thread {
            try {
                postStatus("GAME OPTIMIZE • measuring DIRECT baseline…")
                runCatching { if (wg.isUp()) wg.disconnect() }
                Thread.sleep(300)

                val directIp = NetworkIdentity.publicIp()
                val directBench = NetBench.run(attempts = 14)
                val candidates = ConfigTuner.candidateMtus(baseConfig)

                var bestBench: BenchResult? = null
                var bestConfig: String? = null
                var bestMtu: Int? = null

                for ((index, mtu) in candidates.withIndex()) {
                    postStatus("GAME OPTIMIZE • MTU $mtu • ${index + 1}/${candidates.size}")
                    runCatching { if (wg.isUp()) wg.disconnect() }
                    Thread.sleep(180)

                    val tuned = ConfigTuner.withMtu(baseConfig, mtu)
                    wg.validate(tuned)
                    wg.connect(tuned)
                    Thread.sleep(450)

                    // Warm up the route so first-handshake setup is not counted as gameplay latency.
                    NetBench.run(attempts = 3)
                    val warmHealth = wg.health()
                    if (!warmHealth.up || !warmHealth.hasRecentHandshake || warmHealth.rxBytes <= 0L) {
                        runCatching { wg.disconnect() }
                        Thread.sleep(150)
                        continue
                    }

                    val result = NetBench.run(attempts = 18)
                    val health = wg.health()
                    if (health.up && health.hasRecentHandshake && health.rxBytes > 0L) {
                        val currentBest = bestBench
                        if (currentBest == null || result.score < currentBest.score) {
                            bestBench = result
                            bestConfig = tuned
                            bestMtu = mtu
                        }
                    }

                    runCatching { wg.disconnect() }
                    Thread.sleep(150)
                }

                val selectedBench = bestBench ?: error("No MTU candidate kept a verified VPN route")
                val selectedConfig = bestConfig ?: error("No stable VPN config selected")
                val selectedMtu = bestMtu ?: error("No MTU selected")

                postStatus("GAME OPTIMIZE • applying MTU $selectedMtu…")
                store.save(selectedConfig)
                wg.connect(selectedConfig)
                Thread.sleep(450)
                NetBench.run(attempts = 3)
                val finalHealth = wg.health()

                if (!finalHealth.up || !finalHealth.hasRecentHandshake || finalHealth.rxBytes <= 0L) {
                    store.save(baseConfig)
                    runCatching { wg.disconnect() }
                    error("Selected route failed final verification")
                }

                val vpnIp = NetworkIdentity.publicIp()
                val endpoint = extractEndpoint(selectedConfig) ?: "unknown"

                runOnUiThread {
                    endpointText.text = "Endpoint: $endpoint"
                    directIpText.text = "Direct IP: ${directIp ?: "check failed"}"
                    vpnIpText.text = "VPN IP: ${vpnIp ?: "active"}"
                    trafficText.text = "Traffic: RX ${formatBytes(finalHealth.rxBytes)}  /  TX ${formatBytes(finalHealth.txBytes)}"
                    handshakeText.text = finalHealth.handshakeAgeSeconds?.let { "Handshake: ${it}s ago" } ?: "Handshake: NONE"

                    vpnStateText.text = "VPN: GAME TUNED ✓"
                    vpnStateText.setTextColor(Color.rgb(119, 230, 159))
                    statusText.text = "Status: Best verified VPN route selected • MTU $selectedMtu"

                    routeGradeText.text = "GAME ROUTE: ${selectedBench.grade}"
                    routeGradeText.setTextColor(
                        when (selectedBench.grade) {
                            "ELITE", "GREAT" -> Color.rgb(119, 230, 159)
                            "GOOD" -> Color.rgb(236, 210, 113)
                            else -> Color.rgb(255, 183, 96)
                        }
                    )
                    avgText.text = String.format(Locale.US, "AVG: %.1f ms", selectedBench.avgMs)
                    p95Text.text = String.format(Locale.US, "P95: %.1f ms", selectedBench.p95Ms)
                    jitterText.text = String.format(Locale.US, "Jitter: %.1f ms", selectedBench.jitterMs)
                    lossText.text = String.format(Locale.US, "Probe loss: %.0f%%", selectedBench.lossPct)
                    mtuText.text = "MTU: $selectedMtu"
                    directBenchText.text = String.format(
                        Locale.US,
                        "DIRECT baseline: AVG %.1f • P95 %.1f • jitter %.1f ms",
                        directBench.avgMs,
                        directBench.p95Ms,
                        directBench.jitterMs
                    )
                    connectButton.text = "RECONNECT & VERIFY"
                    optimizeButton.text = "RE-TUNE GAME ROUTE"
                    disconnectButton.isEnabled = true
                }
            } catch (e: Exception) {
                runCatching { wg.disconnect() }
                runOnUiThread {
                    vpnStateText.text = "VPN: OFF"
                    vpnStateText.setTextColor(Color.WHITE)
                    statusText.text = "Status: GAME OPTIMIZE failed: ${friendlyError(e)} • direct internet restored"
                    disconnectButton.isEnabled = false
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
            var health = runCatching { wg.health() }.getOrNull()
            val clearlyDead = health?.let {
                it.up && !it.hasRecentHandshake && it.rxBytes == 0L && it.txBytes > 0L
            } == true

            if (clearlyDead) {
                runCatching { wg.disconnect() }
                Thread.sleep(150)
                health = runCatching { wg.health() }.getOrNull()
            }

            runOnUiThread {
                if (clearlyDead) {
                    vpnStateText.text = "VPN: OFF"
                    vpnStateText.setTextColor(Color.WHITE)
                    statusText.text = "Status: Dead tunnel auto-disconnected • direct internet restored"
                    disconnectButton.isEnabled = false
                    connectButton.text = "TRY AGAIN"
                } else if (health?.up == true) {
                    val active = health.hasRecentHandshake && health.rxBytes > 0L
                    vpnStateText.text = if (active) "VPN: ACTIVE ✓" else "VPN: UP"
                    vpnStateText.setTextColor(
                        if (active) Color.rgb(119, 230, 159) else Color.rgb(255, 183, 96)
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
            mtuText.text = "MTU: —"
        } else {
            endpointText.text = "Endpoint: ${extractEndpoint(config) ?: "loaded"}"
            importButton.text = "REPLACE WIREGUARD .CONF"
            mtuText.text = "MTU: ${ConfigTuner.extractMtu(config) ?: "auto"}"
        }
    }

    private fun openConfigPicker() {
        if (busy) return
        withDirectInternet {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, IMPORT_REQUEST)
        }
    }

    private fun withDirectInternet(action: () -> Unit) {
        if (busy) return
        busy = true
        setBusy(true)
        postStatus("Restoring direct internet…")

        Thread {
            runCatching { if (wg.isUp()) wg.disconnect() }
            Thread.sleep(200)
            runOnUiThread {
                busy = false
                setBusy(false)
                action()
            }
        }.start()
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
                    routeGradeText.text = "GAME ROUTE: NOT TUNED"
                    avgText.text = "AVG: —"
                    p95Text.text = "P95: —"
                    jitterText.text = "Jitter: —"
                    lossText.text = "Probe loss: —"
                    directBenchText.text = "DIRECT baseline: —"
                    optimizeButton.text = "GAME OPTIMIZE • AUTO MTU"
                    statusText.text = "Status: Config parsed OK • connect or run GAME OPTIMIZE"
                } catch (e: Exception) {
                    statusText.text = "Status: Bad .conf: ${friendlyError(e)}"
                }
            }

            VPN_PERMISSION_REQUEST -> {
                if (resultCode == RESULT_OK || VpnService.prepare(this) == null) {
                    statusText.text = "Status: VPN permission granted"
                    runPendingVpnAction()
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
        optimizeButton.isEnabled = !value
        importButton.isEnabled = !value
        freeConfigButton.isEnabled = !value
        if (value) {
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
