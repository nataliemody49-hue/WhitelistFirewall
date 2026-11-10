package com.example.whitelistfw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.enabled(c) &&
            VpnService.prepare(c) == null
        ) {
            c.startService(Intent(c, FirewallVpnService::class.java))
        }
    }
}
