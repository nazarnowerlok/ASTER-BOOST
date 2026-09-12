package com.aster.boost

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        private const val IMPORT_REQUEST = 1001
        private const val VPN_REQUEST_BOOST = 1002
        private const val VPN_REQUEST_CONNECT = 1003
    }

    private lateinit var wg: WireGuardController
    private lateinit var store: ConfigStore

    private lateinit var gradeText: TextView
    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var vpnText: TextView
    private lateinit var avgText: TextView
    private lateinit var p95Text: TextView
    private lateinit var jitterText: TextView
    private lateinit var lossText: TextView
    private lateinit var mtuText: TextView
    private lateinit var boostButton: Button
    private lateinit var vpnButton: Button
    private lateinit var importButton: Button

    @Volatile
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wg = WireGuardController(this)
        store = ConfigStore(this)
        buildUi()
        refreshControls()
    }

    override fun onResume() {
        super.onResume()
        if (::vpnButton.isInitialized) refreshControls()
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

        root.addView(text("ASTER BOOST", 32f, Color.WHITE, Typeface.BOLD))
        root.addView(
            text("Real WireGuard control + route tuning.", 15f, Color.rgb(170, 177, 193), Typeface.NORMAL),
            marginParams(top = 4)
        )

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(22, 26, 35), 22f)
        }
        root.addView(card, marginParams(top = 22))

        gradeText = text("NETWORK", 21f, Color.WHITE, Typeface.BOLD)
        statusText = text("Status: READY", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        modeText = text("Mode: DIRECT", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        vpnText = text("VPN: OFF", 14f, Color.rgb(210, 214, 223), Typeface.BOLD)
        avgText = text("AVG: —", 16f, Color.WHITE, Typeface.BOLD)
        p95Text = text("P95: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        jitterText = text("Jitter: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        lossText = text("Loss: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        mtuText = text("MTU: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)

        card.addView(gradeText)
        card.addView(statusText, marginParams(top = 10))
        card.addView(modeText, marginParams(top = 5))
        card.addView(vpnText, marginParams(top = 5))
        card.addView(avgText, marginParams(top = 16))
        card.addView(p95Text, marginParams(top = 5))
        card.addView(jitterText, marginParams(top = 5))
        card.addView(lossText, marginParams(top = 5))
        card.addView(mtuText, marginParams(top = 5))

        boostButton = Button(this).apply {
            text = "SMART BOOST"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(89, 72, 246))
            setOnClickListener { startSmartBoost() }
        }
        root.addView(boostButton, marginParams(height = 62, top = 18))

        vpnButton = Button(this).apply {
            text = "SETUP VPN NODE"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(33, 126, 86))
            setOnClickListener { toggleVpn() }
        }
        root.addView(vpnButton, marginParams(height = 56, top = 12))

        importButton = Button(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.rgb(45, 51, 65))
            setOnClickListener { openConfigPicker() }
        }
        root.addView(importButton, marginParams(height = 54, top = 12))

        root.addView(
            text(
                "SMART BOOST compares DIRECT and the saved WireGuard node, tests safe MTU values, and leaves the better route active. CONNECT VPN forces the saved node on immediately. A real remote WireGuard node is required; ASTER BOOST does not fake VPN status or fake ping.",
                12.5f,
                Color.rgb(145, 152, 168),
                Typeface.NORMAL
            ),
            marginParams(top = 18)
        )

        setContentView(scroll)
    }

    private fun startSmartBoost() {
        if (busy) return
        val config = store.load()
        if (config != null) {
            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent != null) {
                startActivityForResult(permissionIntent, VPN_REQUEST_BOOST)
                return
            }
        }
        runBoost(config)
    }

    private fun toggleVpn() {
        if (busy) return
        val config = store.load()
        if (config == null) {
            statusText.text = "Status: Load ASTER NODE first"
            openConfigPicker()
            return
        }

        busy = true
        setBusyUi(true)
        Thread {
            val up = runCatching { wg.isUp() }.getOrDefault(false)
            busy = false
            runOnUiThread {
                setBusyUi(false)
                if (up) disconnectVpn() else requestOrConnectVpn()
            }
        }.start()
    }

    private fun requestOrConnectVpn() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_REQUEST_CONNECT)
        } else {
            connectSavedVpn()
        }
    }

    private fun connectSavedVpn() {
        if (busy) return
        val config = store.load() ?: run {
            statusText.text = "Status: No ASTER NODE loaded"
            return
        }

        busy = true
        setBusyUi(true)
        Thread {
            try {
                postStatus("Connecting VPN")
                wg.connect(config)
                Thread.sleep(900)
                if (!wg.isUp()) error("Tunnel did not come up")
                val result = NetBench.run(12)
                runOnUiThread {
                    showResult(result, "ASTER NODE", "VPN CONNECTED • ${result.grade}", ConfigTuner.extractMtu(config))
                    vpnText.text = "VPN: ON"
                }
            } catch (e: Exception) {
                runCatching { wg.disconnect() }
                runOnUiThread {
                    statusText.text = "Status: VPN error: ${e.message ?: "unknown"}"
                    vpnText.text = "VPN: OFF"
                }
            } finally {
                busy = false
                runOnUiThread {
                    setBusyUi(false)
                    refreshControls()
                }
            }
        }.start()
    }

    private fun disconnectVpn() {
        if (busy) return
        busy = true
        setBusyUi(true)
        Thread {
            try {
                wg.disconnect()
                Thread.sleep(250)
                runOnUiThread {
                    modeText.text = "Mode: DIRECT"
                    vpnText.text = "VPN: OFF"
                    statusText.text = "Status: VPN disconnected"
                    mtuText.text = "MTU: DIRECT"
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Status: Disconnect error: ${e.message ?: "unknown"}" }
            } finally {
                busy = false
                runOnUiThread {
                    setBusyUi(false)
                    refreshControls()
                }
            }
        }.start()
    }

    private fun runBoost(config: String?) {
        if (busy) return
        busy = true
        setBusyUi(true)

        Thread {
            try {
                postStatus("Testing DIRECT")
                runCatching { wg.disconnect() }
                Thread.sleep(400)
                val direct = NetBench.run(18)

                if (config == null) {
                    runOnUiThread {
                        showResult(direct, "DIRECT", "DIRECT active • load ASTER NODE for real VPN", null)
                        vpnText.text = "VPN: OFF"
                    }
                    return@Thread
                }

                var bestVpn: BenchResult? = null
                var bestMtu: Int? = null
                var bestConfig: String? = null

                for (mtu in ConfigTuner.candidateMtus(config)) {
                    postStatus("Testing ASTER NODE • MTU $mtu")
                    runCatching { wg.disconnect() }
                    Thread.sleep(300)

                    val tuned = ConfigTuner.withMtu(config, mtu)
                    val candidate = try {
                        wg.connect(tuned)
                        Thread.sleep(1200)
                        if (!wg.isUp()) throw IllegalStateException("Tunnel is down")
                        NetBench.run(15)
                    } catch (_: Exception) {
                        BenchResult(999.0, 999.0, 999.0, 100.0)
                    }

                    if (bestVpn == null || candidate.score < bestVpn!!.score) {
                        bestVpn = candidate
                        bestMtu = mtu
                        bestConfig = tuned
                    }
                }

                runCatching { wg.disconnect() }

                val vpn = bestVpn
                val useVpn = vpn != null && bestConfig != null && vpn.lossPct < 100.0 && vpn.score + 1.0 < direct.score

                if (useVpn) {
                    postStatus("Activating ASTER NODE • MTU $bestMtu")
                    wg.connect(bestConfig!!)
                    Thread.sleep(700)
                    if (!wg.isUp()) error("Winning VPN route failed to activate")
                    store.save(bestConfig!!)
                    runOnUiThread {
                        showResult(vpn!!, "ASTER NODE", "BOOSTED • VPN ON • ${vpn.grade}", bestMtu)
                        vpnText.text = "VPN: ON"
                    }
                } else {
                    runCatching { wg.disconnect() }
                    runOnUiThread {
                        showResult(
                            direct,
                            "DIRECT",
                            if (vpn == null || vpn.lossPct >= 100.0) "ASTER NODE unavailable • DIRECT active" else "DIRECT wins • VPN OFF",
                            null
                        )
                        vpnText.text = "VPN: OFF"
                    }
                }
            } catch (e: Exception) {
                runCatching { wg.disconnect() }
                runOnUiThread {
                    statusText.text = "Status: Error: ${e.message ?: "unknown"}"
                    vpnText.text = "VPN: OFF"
                }
            } finally {
                busy = false
                runOnUiThread {
                    setBusyUi(false)
                    refreshControls()
                }
            }
        }.start()
    }

    private fun showResult(result: BenchResult, mode: String, status: String, mtu: Int?) {
        gradeText.text = result.grade
        statusText.text = "Status: $status"
        modeText.text = "Mode: $mode"
        avgText.text = "AVG: ${formatMs(result.avgMs)}"
        p95Text.text = "P95: ${formatMs(result.p95Ms)}"
        jitterText.text = "Jitter: ${formatMs(result.jitterMs)}"
        lossText.text = "Loss: ${String.format(Locale.US, "%.0f%%", result.lossPct)}"
        mtuText.text = "MTU: ${mtu?.toString() ?: "DIRECT"}"
    }

    private fun postStatus(status: String) {
        runOnUiThread { statusText.text = "Status: $status" }
    }

    private fun setBusyUi(value: Boolean) {
        boostButton.isEnabled = !value
        vpnButton.isEnabled = !value
        importButton.isEnabled = !value
        boostButton.text = if (value) "WORKING…" else "SMART BOOST"
    }

    private fun refreshControls() {
        val hasConfig = store.load() != null
        importButton.text = if (hasConfig) "REPLACE ASTER NODE" else "LOAD ASTER NODE"

        Thread {
            val up = if (hasConfig) runCatching { wg.isUp() }.getOrDefault(false) else false
            runOnUiThread {
                vpnButton.text = when {
                    !hasConfig -> "SETUP VPN NODE"
                    up -> "DISCONNECT VPN"
                    else -> "CONNECT VPN"
                }
                vpnButton.backgroundTintList = ColorStateList.valueOf(
                    if (up) Color.rgb(178, 67, 67) else Color.rgb(33, 126, 86)
                )
                vpnText.text = if (up) "VPN: ON" else "VPN: OFF"
            }
        }.start()
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
                    require(config.contains("[Interface]", ignoreCase = true))
                    require(config.contains("[Peer]", ignoreCase = true))
                    require(config.contains("PrivateKey", ignoreCase = true))
                    require(config.contains("PublicKey", ignoreCase = true))
                    require(config.contains("Endpoint", ignoreCase = true))
                    store.save(config)
                    refreshControls()
                    statusText.text = "Status: ASTER node loaded • CONNECT VPN or SMART BOOST"
                } catch (_: Exception) {
                    statusText.text = "Status: Bad WireGuard config"
                }
            }

            VPN_REQUEST_BOOST -> {
                if (resultCode == RESULT_OK || VpnService.prepare(this) == null) {
                    statusText.text = "Status: VPN permission OK"
                    runBoost(store.load())
                } else {
                    statusText.text = "Status: VPN permission denied"
                }
            }

            VPN_REQUEST_CONNECT -> {
                if (resultCode == RESULT_OK || VpnService.prepare(this) == null) {
                    statusText.text = "Status: VPN permission OK"
                    connectSavedVpn()
                } else {
                    statusText.text = "Status: VPN permission denied"
                }
            }
        }
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
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (height > 0) dp(height) else height).apply {
            topMargin = dp(top)
        }

    private fun formatMs(value: Double): String = String.format(Locale.US, "%.1f ms", value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
