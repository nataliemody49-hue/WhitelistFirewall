package com.example.whitelistfw

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap?)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    // خدمات جوجل بدون أيقونة لكنها لازمة لوصول إشعارات الدفع
    val extras = setOf("com.google.android.gms", "com.google.android.gsf")
    return pm.getInstalledApplications(0)
        .filter {
            it.packageName != ctx.packageName &&
                (pm.getLaunchIntentForPackage(it.packageName) != null || it.packageName in extras)
        }
        .map {
            AppItem(
                it.packageName,
                it.loadLabel(pm).toString(),
                runCatching { pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SetupScreen(onDone: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("إنشاء كلمة السر", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("اختر كلمة سر (4 خانات على الأقل) واحفظها جيداً، فلا يمكن استرجاعها.")
        Spacer(Modifier.height(16.dp))
        PinField(p1, "كلمة السر") { p1 = it }
        Spacer(Modifier.height(8.dp))
        PinField(p2, "تأكيد كلمة السر") { p2 = it }
        if (err.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                when {
                    p1.length < 4 -> err = "كلمة السر قصيرة جداً"
                    p1 != p2 -> err = "كلمتا السر غير متطابقتين"
                    else -> onDone(p1)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("حفظ") }
    }
}

@Composable
fun UnlockScreen(ctx: Context, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    fun tryUnlock() {
        val left = PinManager.lockRemainingMs(ctx)
        if (left > 0) {
            msg = "مقفل مؤقتاً. حاول بعد ${left / 1000 + 1} ثانية"
            return
        }
        if (PinManager.verify(ctx, pin)) {
            onUnlock()
        } else {
            pin = ""
            val l2 = PinManager.lockRemainingMs(ctx)
            msg = if (l2 > 0) "محاولات خاطئة كثيرة. القفل لمدة ${l2 / 1000} ثانية"
            else "كلمة السر خاطئة"
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("التطبيق مقفل", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        PinField(pin, "كلمة السر") { pin = it }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { tryUnlock() }, modifier = Modifier.fillMaxWidth()) { Text("فتح") }
    }
}

@Composable
fun MainScreen(
    ctx: Context,
    vpnOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onAllowedChanged: () -> Unit
) {
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var allowed by remember { mutableStateOf(Prefs.allowed(ctx)) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
    }

    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (vpnOn) "الحماية تعمل" else "الحماية متوقفة",
                    style = MaterialTheme.typography.titleLarge
                )
                Text("التطبيقات المسموح لها بالإنترنت: ${allowed.size}")
            }
            Switch(checked = vpnOn, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = app.pkg in allowed,
                        onCheckedChange = { on ->
                            allowed = if (on) allowed + app.pkg else allowed - app.pkg
                            Prefs.setAllowed(ctx, allowed)
                            onAllowedChanged()
                        }
                    )
                }
            }
        }
    }
}
