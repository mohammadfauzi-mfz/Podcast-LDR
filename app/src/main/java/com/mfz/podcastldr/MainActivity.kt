@file:OptIn(ExperimentalMaterial3Api::class)

package com.mfz.podcastldr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppState(val repo: Repo, private val scope: CoroutineScope) {
    var data by mutableStateOf(repo.loadCache())
    var loading by mutableStateOf(false)
    var pending by mutableIntStateOf(repo.pendingCount())
    var message by mutableStateOf<String?>(null)
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateStatus by mutableStateOf("")
    var dlProgress by mutableStateOf<Float?>(null)
    var shareBusy by mutableStateOf<String?>(null)
    var tlSaving by mutableStateOf(false)

    fun saveTL(o: JSONObject, done: () -> Unit) {
        if (tlSaving) return
        scope.launch {
            tlSaving = true
            try {
                withContext(Dispatchers.IO) { repo.saveTL(o) }
                message = "Tindak lanjut tersimpan"
                done()
                load()
            } catch (e: Exception) {
                message = "Gagal menyimpan tindak lanjut: ${e.message}"
            }
            tlSaving = false
        }
    }

    /** Bagikan teks + foto (diunduh lewat Apps Script). withFoto=false untuk teks saja. */
    fun share(ctx: Context, text: String, list: List<Respon>, withFoto: Boolean = true) {
        if (shareBusy != null) return
        scope.launch {
            val uris = mutableListOf<Uri>()
            val targets = if (withFoto) list.filter { it.foto.isNotBlank() }.take(MAX_FOTO_SHARE) else emptyList()
            if (targets.isNotEmpty()) {
                shareBusy = "Menyiapkan foto…"
                withContext(Dispatchers.IO) { Sharer.clear(ctx) }
                targets.forEachIndexed { i, r ->
                    shareBusy = "Menyiapkan foto ${i + 1}/${targets.size}…"
                    withContext(Dispatchers.IO) { Sharer.prepare(ctx, repo, r, i) }?.let { uris.add(it) }
                }
                shareBusy = null
                if (uris.size < targets.size) message = "${targets.size - uris.size} foto gagal diambil (dibagikan tanpa foto tersebut)"
            }
            Sharer.send(ctx, text, uris)
        }
    }

    fun checkUpdate(silent: Boolean) {
        scope.launch {
            if (!silent) updateStatus = "Mengecek…"
            try {
                val u = withContext(Dispatchers.IO) { Updater.check() }
                if (u.code > BuildConfig.VERSION_CODE && u.apkUrl.isNotBlank()) {
                    update = u
                    updateStatus = "Versi baru tersedia: ${u.tag}"
                    if (silent) message = "Update ${u.tag} tersedia. Buka Pengaturan untuk memperbarui."
                } else {
                    update = null
                    updateStatus = "Aplikasi sudah versi terbaru"
                }
            } catch (e: Exception) {
                if (!silent) updateStatus = "Gagal cek update: ${e.message}"
            }
        }
    }

    fun installUpdate(ctx: Context) {
        val u = update ?: return
        if (dlProgress != null) return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            updateStatus = "Izinkan \"Instal aplikasi tak dikenal\" untuk Podcast LDR, lalu tekan Update lagi"
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
            return
        }
        scope.launch {
            dlProgress = 0f
            updateStatus = "Mengunduh ${u.tag}…"
            try {
                val f = File(ctx.cacheDir, "update.apk")
                withContext(Dispatchers.IO) { Updater.download(u.apkUrl, f) { p -> dlProgress = p } }
                updateStatus = "Unduhan selesai, membuka pemasang…"
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", f)
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                updateStatus = "Gagal mengunduh: ${e.message}"
            }
            dlProgress = null
        }
    }

    private suspend fun load() {
        if (repo.url.isBlank()) { message = "Isi URL Web App di Pengaturan"; return }
        loading = true
        try {
            data = withContext(Dispatchers.IO) { repo.fetch() }
        } catch (e: Exception) {
            message = "Gagal memuat data: ${e.message}"
        }
        loading = false
    }

    fun refresh() { scope.launch { load() } }

    fun sync() {
        scope.launch {
            if (repo.pendingCount() > 0 && repo.url.isNotBlank()) {
                loading = true
                try {
                    val n = withContext(Dispatchers.IO) { repo.sendPending() }
                    message = "$n data terkirim"
                } catch (e: Exception) {
                    message = "Belum terkirim, tersimpan di HP: ${e.message}"
                }
                pending = repo.pendingCount()
            }
            load()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Repo(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2E7D32), secondary = Color(0xFF558B2F),
                    primaryContainer = Color(0xFFC8E6C9), secondaryContainer = Color(0xFFDCEDC8)
                )
            ) {
                val scope = rememberCoroutineScope()
                val state = remember { AppState(repo, scope) }
                App(state)
            }
        }
    }
}

@Composable
fun App(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var showSettings by remember { mutableStateOf(state.repo.url.isBlank() || state.repo.pengisi.isBlank()) }
    val snack = remember { SnackbarHostState() }
    val appCtx = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        if (state.repo.pendingCount() > 0) SyncWorker.enqueue(appCtx)
        state.sync(); state.checkUpdate(silent = true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.pending = state.repo.pendingCount() }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); state.message = null }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Podcast LDR", fontWeight = FontWeight.Bold)
                        state.data?.let { Text("Data per ${it.updated}", style = MaterialTheme.typography.labelSmall) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, actionIconContentColor = Color.White
                ),
                actions = {
                    if (state.loading) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else IconButton(onClick = { state.sync() }) { Icon(Icons.Default.Refresh, "Muat ulang") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Pengaturan") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf("Isi Form" to Icons.Default.Edit, "Rekap" to Icons.Default.Home, "Data" to Icons.Default.List)
                    .forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> FormScreen(state)
                1 -> RekapScreen(state)
                else -> DataScreen(state)
            }
        }
    }
    if (showSettings) SettingsDialog(state) { showSettings = false }
    state.shareBusy?.let { msg ->
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Membagikan") }, text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(msg)
            }
        })
    }
}

@Composable
fun SettingsDialog(state: AppState, onDismiss: () -> Unit) {
    var nama by remember { mutableStateOf(state.repo.pengisi) }
    var url by remember { mutableStateOf(state.repo.url) }
    var key by remember { mutableStateOf(state.repo.key) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nama, { nama = it }, Modifier.fillMaxWidth(), label = { Text("Nama pengisi (mandor/asisten)") }, singleLine = true)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL Web App (berakhiran /exec)") })
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Kunci API") }, singleLine = true)
                Text("URL & kunci didapat dari admin (Apps Script spreadsheet).", style = MaterialTheme.typography.labelSmall)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Versi terpasang: ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                if (state.updateStatus.isNotBlank()) Text(state.updateStatus, style = MaterialTheme.typography.bodySmall)
                state.update?.notes?.takeIf { it.isNotBlank() }?.let { Text("Catatan: $it", style = MaterialTheme.typography.labelSmall) }
                state.dlProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                val ctx = LocalContext.current
                if (state.update != null) {
                    Button(onClick = { state.installUpdate(ctx) }, enabled = state.dlProgress == null, modifier = Modifier.fillMaxWidth()) {
                        Text("Update ke ${state.update?.tag}")
                    }
                } else {
                    OutlinedButton(onClick = { state.checkUpdate(silent = false) }, modifier = Modifier.fillMaxWidth()) { Text("Cek Update") }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Dibuat oleh Mohammad Fauzi", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { openUrl(ctx, "https://wa.me/6285252390543") }) { Text("Hubungi via WhatsApp") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.repo.pengisi = nama; state.repo.url = url; state.repo.key = key
                onDismiss(); state.sync()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/* ---------- komponen bersama ---------- */

fun pctColor(p: Float) = when {
    p >= 0.8f -> Color(0xFF2E7D32)
    p >= 0.5f -> Color(0xFFF9A825)
    else -> Color(0xFFC62828)
}

fun statusColor(s: String) = when (s) {
    "Selesai" -> Color(0xFF2E7D32)
    "Diproses" -> Color(0xFF1565C0)
    "Tidak perlu" -> Color(0xFF757575)
    else -> Color(0xFFC62828)
}

fun kelasColor(k: String) = when (k) {
    "A" -> Color(0xFF2E7D32)
    "B" -> Color(0xFF7CB342)
    "C" -> Color(0xFFF9A825)
    else -> Color(0xFFC62828)
}

@Composable
fun EmptyInfo() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Belum ada data.\nIsi URL Web App di Pengaturan (ikon gerigi), lalu tekan muat ulang.")
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun BarRow(label: String, value: Int, total: Int, right: String, color: Color, onClick: (() -> Unit)? = null) {
    val p = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 5.dp)
    ) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(right, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.padding(top = 3.dp).fillMaxWidth().height(8.dp),
            color = color, trackColor = color.copy(alpha = 0.15f)
        )
    }
}

@Composable
fun ListDialog(title: String, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f, fill = false)) { content() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    actions()
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
            }
        }
    }
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "Bagikan"))
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Tidak bisa membuka tautan", Toast.LENGTH_SHORT).show()
    }
}
