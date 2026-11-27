@file:OptIn(ExperimentalMaterial3Api::class)

package com.mfz.podcastldr

import android.app.DatePickerDialog
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Composable
fun FormScreen(state: AppState) {
    val ctx = LocalContext.current
    val master = state.data?.master ?: emptyList()
    var afd by rememberSaveable { mutableStateOf("") }
    var tgl by rememberSaveable { mutableStateOf(today()) }
    var nama by rememberSaveable { mutableStateOf("") }
    var npk by rememberSaveable { mutableStateOf("") }
    var tk by rememberSaveable { mutableStateOf("Panen") }
    var kp by rememberSaveable { mutableStateOf("") }
    var kf by rememberSaveable { mutableStateOf("") }
    var saran by rememberSaveable { mutableStateOf("") }
    var losses by rememberSaveable { mutableStateOf("") }
    var kelas by rememberSaveable { mutableStateOf("") }
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var showSug by remember { mutableStateOf(false) }
    var afdMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val photoFile = remember { File(ctx.cacheDir, "foto_kamera.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) foto = Img.load(ctx, photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) foto = Img.load(ctx, u) }

    val q = nama.trim()
    val sug = remember(q, master, showSug) {
        if (!showSug || q.length < 2) emptyList()
        else master.filter { it.nama.contains(q, true) || it.npk.contains(q) }.take(8)
    }
    val pernah = remember(npk, state.data) {
        val n = npk.trim()
        if (n.isEmpty()) emptyList() else state.data?.responses?.filter { n in it.npks }?.map { fmtTgl(it.tgl) }.orEmpty()
    }

    fun validate(): String? = when {
        state.repo.pengisi.isBlank() -> "Isi Nama Pengisi di Pengaturan (ikon gerigi)"
        nama.isBlank() -> "Nama karyawan wajib diisi"
        npk.isBlank() -> "NPK wajib diisi"
        afd.isBlank() -> "Pilih afdeling"
        kp.isBlank() || kf.isBlank() || saran.isBlank() -> "Keluhan pekerjaan, fasilitas & saran wajib diisi (isi '-' jika tidak ada)"
        tk == "Panen" && losses.toDoubleOrNull() == null -> "Isi hasil losses CKP (angka, contoh 1.25)"
        tk == "Panen" && kelas.isBlank() -> "Pilih kelas pemanen"
        foto == null -> "Foto dokumentasi wajib"
        else -> null
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.pending > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.pending} data belum terkirim (tersimpan di HP)", Modifier.weight(1f))
                    TextButton(onClick = { state.sync() }) { Text("Kirim") }
                }
            }
        }
        Text("Form Sarasehan Karyawan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Pengisi: ${state.repo.pengisi.ifBlank { "(belum diisi)" }}", style = MaterialTheme.typography.labelMedium)

        OutlinedTextField(
            nama, { nama = it; showSug = true }, Modifier.fillMaxWidth(),
            label = { Text("Nama Karyawan *") },
            leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
            supportingText = { Text("Ketik min. 2 huruf nama atau NPK untuk mencari dari daftar TK") },
            singleLine = true
        )
        if (sug.isNotEmpty()) {
            Card {
                sug.forEach { k ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            nama = k.nama; npk = k.npk; afd = k.afd; showSug = false
                        }.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(k.nama, fontWeight = FontWeight.SemiBold)
                        Text("NPK ${k.npk} • ${afdLabel(k.afd)}", style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
        OutlinedTextField(
            npk, { v -> npk = v.filter { it.isDigit() || it == ',' || it == ' ' } }, Modifier.fillMaxWidth(),
            label = { Text("NPK Karyawan *") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (pernah.isNotEmpty()) {
            Text("Catatan: NPK ini sudah tercatat sarasehan (${pernah.joinToString()})", color = Color(0xFFE65100), style = MaterialTheme.typography.labelMedium)
        }

        Box {
            OutlinedButton(onClick = { afdMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (afd.isEmpty()) "Pilih Afdeling *" else afdLabel(afd), Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = afdMenu, onDismissRequest = { afdMenu = false }) {
                AFD_LIST.forEach { a -> DropdownMenuItem(text = { Text(afdLabel(a)) }, onClick = { afd = a; afdMenu = false }) }
            }
        }
        OutlinedButton(onClick = {
            val y = tgl.take(4).toIntOrNull() ?: 2026
            val m = (tgl.substring(5, 7).toIntOrNull() ?: 1) - 1
            val d = tgl.takeLast(2).toIntOrNull() ?: 1
            DatePickerDialog(ctx, { _, yy, mm, dd -> tgl = "%04d-%02d-%02d".format(yy, mm + 1, dd) }, y, m, d).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DateRange, null)
            Spacer(Modifier.width(8.dp))
            Text("Tanggal Sarasehan: ${fmtTgl(tgl)}", Modifier.weight(1f))
        }

        Text("Tenaga Kerja *", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Panen", "Rawat").forEach { t -> FilterChip(selected = tk == t, onClick = { tk = t }, label = { Text(t) }) }
        }

        OutlinedTextField(kp, { kp = it }, Modifier.fillMaxWidth(), label = { Text("Keluhan Pekerjaan *") }, minLines = 2)
        OutlinedTextField(kf, { kf = it }, Modifier.fillMaxWidth(), label = { Text("Keluhan Fasilitas & Benefit *") }, minLines = 2)
        OutlinedTextField(saran, { saran = it }, Modifier.fillMaxWidth(), label = { Text("Saran dari Karyawan *") }, minLines = 2)

        if (tk == "Panen") {
            OutlinedTextField(
                losses, { v -> losses = v.replace(',', '.').filter { it.isDigit() || it == '.' } }, Modifier.fillMaxWidth(),
                label = { Text("Hasil Losses CKP Ancak (kg/ha) *") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Text("Kelas Pemanen *", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A", "B", "C", "D").forEach { k -> FilterChip(selected = kelas == k, onClick = { kelas = k }, label = { Text("Kelas $k") }) }
            }
        }

        Text("Foto Dokumentasi * (otomatis diberi cap tanggal)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { camera.launch(photoUri) }) { Text("Kamera") }
            OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("Galeri") }
        }
        foto?.let {
            Image(it.asImageBitmap(), "Foto", Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            error = validate()
            if (error == null) {
                val o = JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("pengisi", state.repo.pengisi)
                    .put("afdeling", afdLabel(afd))
                    .put("tanggal", tgl)
                    .put("nama", nama.trim())
                    .put("npk", npk.trim())
                    .put("tk", tk)
                    .put("kp", kp.trim())
                    .put("kf", kf.trim())
                    .put("saran", saran.trim())
                    .put("losses", if (tk == "Panen") losses.toDouble() else 0.0)
                    .put("kelas", if (tk == "Panen") kelas else "")
                    .put("foto", Img.toBase64(foto!!))
                    .put("fotoName", "LDR_${npk.trim().replace(Regex("[^0-9]+"), "_")}_$tgl.jpg")
                state.repo.savePending(o)
                state.pending = state.repo.pendingCount()
                nama = ""; npk = ""; kp = ""; kf = ""; saran = ""; losses = ""; kelas = ""; foto = null
                state.message = "Data disimpan, mengirim…"
                state.sync()
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Send, null)
            Spacer(Modifier.width(8.dp))
            Text("Simpan & Kirim")
        }
        Spacer(Modifier.height(24.dp))
    }
}
