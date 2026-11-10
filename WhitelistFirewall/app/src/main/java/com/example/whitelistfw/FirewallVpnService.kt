package com.example.whitelistfw

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

class FirewallVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.example.whitelistfw.STOP"
    }

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val allowed = Prefs.allowed(this)

        val builder = Builder()
            .setSession("Firewall")
            .addAddress("10.0.0.2", 32)
            .addAddress("fd00::2", 128)
            .addRoute("0.0.0.0", 0)   // كل IPv4 يدخل النفق ويُسقَط
            .addRoute("::", 0)        // كل IPv6 كذلك
            .setBlocking(true)

        // التطبيقات المسموحة (وتطبيقنا) تتجاوز النفق وتتصل بالإنترنت مباشرة
        (allowed + packageName).forEach {
            try { builder.addDisallowedApplication(it) } catch (_: Exception) {}
        }

        val newTun = builder.establish() ?: return
        val old = tun
        tun = newTun
        old?.close()

        // نقرأ الحزم ونتجاهلها: إسقاطها يعني قطع الإنترنت عن بقية التطبيقات
        Thread {
            try {
                val input = FileInputStream(newTun.fileDescriptor)
                val buf = ByteArray(32767)
                while (input.read(buf) >= 0) { /* drop */ }
            } catch (_: Exception) {}
        }.start()
    }

    private fun stopVpn() {
        tun?.close()
        tun = null
        stopSelf()
    }

    override fun onRevoke() {
        Prefs.setEnabled(this, false)
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        tun?.close()
        tun = null
        super.onDestroy()
    }
}
