package com.example.whitelistfw

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {

    private var unlocked by mutableStateOf(false)
    private var vpnOn by mutableStateOf(false)

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r -> if (r.resultCode == RESULT_OK) setProtection(true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // منع لقطات الشاشة ومعاينة التطبيقات الأخيرة
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        vpnOn = Prefs.enabled(this)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        var hasPin by remember { mutableStateOf(PinManager.hasPin(this@MainActivity)) }
                        when {
                            !hasPin -> SetupScreen { pin ->
                                PinManager.setPin(this@MainActivity, pin)
                                hasPin = true
                                unlocked = true
                            }
                            !unlocked -> UnlockScreen(this@MainActivity) { unlocked = true }
                            else -> MainScreen(
                                this@MainActivity,
                                vpnOn,
                                { requestProtection(it) },
                                { refreshService() }
                            )
                        }
                    }
                }
            }
        }
    }

    // يُقفل التطبيق كلما خرجت منه
    override fun onStop() {
        super.onStop()
        unlocked = false
    }

    private fun requestProtection(on: Boolean) {
        if (!on) { setProtection(false); return }
        val p = VpnService.prepare(this)
        if (p != null) vpnPermission.launch(p) else setProtection(true)
    }

    private fun setProtection(on: Boolean) {
        Prefs.setEnabled(this, on)
        vpnOn = on
        val i = Intent(this, FirewallVpnService::class.java)
        if (!on) i.action = FirewallVpnService.ACTION_STOP
        startService(i)
    }

    private fun refreshService() {
        if (vpnOn) startService(Intent(this, FirewallVpnService::class.java))
    }
}
