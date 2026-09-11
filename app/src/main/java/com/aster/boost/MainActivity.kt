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
        private const val VPN_REQUEST = 1002
    }

    private lateinit var wg: WireGuardController
    private lateinit var store: ConfigStore

    private lateinit var gradeText: TextView
    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var avgText: TextView
    private lateinit var p95Text: TextView
    private lateinit var jitterText: TextView
    private lateinit var lossText: TextView
    private lateinit var mtuText: TextView
    private lateinit var boostButton: Button
    private lateinit var importButton: Button

    @Volatile
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wg = WireGuardController(this)
        store = ConfigStore(this)
        buildUi()
        updateImportButton()
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
            text("Cleanest measured route wins.", 15f, Color.rgb(170, 177, 193), Typeface.NORMAL),
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
        avgText = text("AVG: —", 16f, Color.WHITE, Typeface.BOLD)
        p95Text = text("P95: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        jitterText = text("Jitter: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        lossText = text("Loss: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)
        mtuText = text("MTU: —", 14f, Color.rgb(210, 214, 223), Typeface.NORMAL)

        card.addView(gradeText)
        card.addView(statusText, marginParams(top = 10))
        card.addView(modeText, marginParams(top = 5))
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
                "DIRECT works without a VPN. With a private WireGuard node, SMART BOOST tests DIRECT and several safe MTU values, then keeps the route with the better stability score. Public probes are not PUBG server ping.",
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
                startActivityForResult(permissionIntent, VPN_REQUEST)
                return
            }
        }
        runBoost(config)
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
                        showResult(
                            direct,
                            mode = "DIRECT",
                            status = "DIRECT active • add ASTER NODE to compare VPN",
                            mtu = null
                        )
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
                    store.save(bestConfig!!)
                    Thread.sleep(600)
                    runOnUiThread {
                        showResult(
                            vpn!!,
                            mode = "ASTER NODE",
                            status = "BOOSTED • ${vpn.grade}",
                            mtu = bestMtu
                        )
                        updateImportButton()
                    }
                } else {
                    runOnUiThread {
                        showResult(
                            direct,
                            mode = "DIRECT",
                            status = if (vpn == null || vpn.lossPct >= 100.0) "ASTER NODE unavailable • DIRECT active" else "DIRECT wins",
                            mtu = null
                        )
                    }
                }
            } catch (e: Exception) {
                runCatching { wg.disconnect() }
                runOnUiThread {
                    statusText.text = "Status: Error: ${e.message ?: "unknown"}"
                }
            } finally {
                busy = false
                runOnUiThread { setBusyUi(false) }
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
        importButton.isEnabled = !value
        boostButton.text = if (value) "TUNING…" else "SMART BOOST"
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
                    updateImportButton()
                    statusText.text = "Status: ASTER node loaded • tap SMART BOOST"
                } catch (_: Exception) {
                    statusText.text = "Status: Bad WireGuard config"
                }
            }

            VPN_REQUEST -> {
                if (resultCode == RESULT_OK || VpnService.prepare(this) == null) {
                    statusText.text = "Status: VPN permission OK"
                    runBoost(store.load())
                } else {
                    statusText.text = "Status: VPN permission denied"
                }
            }
        }
    }

    private fun updateImportButton() {
        importButton.text = if (store.load() == null) "LOAD ASTER NODE" else "REPLACE ASTER NODE"
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
