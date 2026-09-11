package com.aster.boost

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AsterBoostApp() }
    }
}

@Composable
private fun AsterBoostApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val wg = remember { WireGuardController(activity) }
    val store = remember { ConfigStore(activity) }

    var status by remember { mutableStateOf("READY") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BenchResult?>(null) }
    var hasConfig by remember { mutableStateOf(store.load() != null) }
    var mode by remember { mutableStateOf("DIRECT") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        status = if (r.resultCode == Activity.RESULT_OK || VpnService.prepare(activity) == null) {
            "VPN permission OK • tap SMART BOOST again"
        } else {
            "VPN permission denied"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = activity.contentResolver.openInputStream(uri)!!.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }
                require(text.contains("[Interface]", ignoreCase = true))
                require(text.contains("[Peer]", ignoreCase = true))
                store.save(text)
                hasConfig = true
                status = "ASTER node loaded"
            } catch (_: Exception) {
                status = "Bad WireGuard config"
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                Text("ASTER BOOST", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text("Cleanest measured route wins.")

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(result?.grade ?: "NETWORK", fontWeight = FontWeight.ExtraBold)
                        Text("Status: $status")
                        Text("Mode: $mode")
                        Text("AVG: ${result?.avgMs?.let { "%.1f ms".format(it) } ?: "—"}")
                        Text("P95: ${result?.p95Ms?.let { "%.1f ms".format(it) } ?: "—"}")
                        Text("Jitter: ${result?.jitterMs?.let { "%.1f ms".format(it) } ?: "—"}")
                        Text("Loss: ${result?.lossPct?.let { "%.0f%%".format(it) } ?: "—"}")
                    }
                }

                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    onClick = {
                        val cfg = store.load()
                        val permissionIntent: Intent? = if (cfg != null) VpnService.prepare(activity) else null

                        if (permissionIntent != null) {
                            permissionLauncher.launch(permissionIntent)
                            return@Button
                        }

                        busy = true
                        scope.launch {
                            try {
                                status = "Testing DIRECT"
                                runCatching { wg.disconnect() }
                                delay(300)
                                val direct = NetBench.run()

                                if (cfg == null) {
                                    result = direct
                                    mode = "DIRECT"
                                    status = "DIRECT active • load a private WireGuard node for VPN comparison"
                                } else {
                                    status = "Testing ASTER NODE"
                                    wg.connect(cfg)
                                    delay(1000)
                                    val vpn = NetBench.run()

                                    if (vpn.score < direct.score) {
                                        result = vpn
                                        mode = "ASTER NODE"
                                        status = "BOOSTED • ${vpn.grade}"
                                    } else {
                                        wg.disconnect()
                                        result = direct
                                        mode = "DIRECT"
                                        status = "DIRECT wins"
                                    }
                                }
                            } catch (e: Exception) {
                                runCatching { wg.disconnect() }
                                status = "Error: ${e.message ?: "unknown"}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(if (busy) "TUNING…" else "SMART BOOST", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
                ) {
                    Text(if (hasConfig) "REPLACE ASTER NODE" else "LOAD ASTER NODE")
                }

                Text(
                    if (hasConfig) {
                        "Node saved. Normal use: SMART BOOST → play."
                    } else {
                        "DIRECT mode works now. VPN mode needs one private WireGuard server config."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
