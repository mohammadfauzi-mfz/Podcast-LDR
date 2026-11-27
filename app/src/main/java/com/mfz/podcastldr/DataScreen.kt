package com.mfz.podcastldr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DataScreen(state: AppState) {
    val d = state.data
    if (d == null) { EmptyInfo(); return }
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    var afd by rememberSaveable { mutableStateOf("") }
    var bulan by rememberSaveable { mutableStateOf("") }
    var afdMenu by remember { mutableStateOf(false) }
    var blnMenu by remember { mutableStateOf(false) }
    var askShare by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Respon?>(null) }
    val months = remember(d) { d.responses.map { it.tgl.take(7) }.filter { it.length == 7 && it[4] == '-' }.distinct().sortedDescending() }
    val list = remember(d, q, afd, bulan) {
        d.responses.filter {
            (afd.isEmpty() || it.afd == afd) &&
                (bulan.isEmpty() || it.tgl.startsWith(bulan)) &&
                (q.isBlank() || "${it.nama} ${it.npk} ${it.kp} ${it.kf} ${it.saran}".contains(q.trim(), true))
        }.sortedByDescending { it.ts }
    }
    Column {
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            placeholder = { Text("Cari nama / NPK / kata") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { afdMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (afd.isEmpty()) "Afd: Semua" else "Afd: ${afd.removePrefix("Afd ")}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = afdMenu, onDismissRequest = { afdMenu = false }) {
                    DropdownMenuItem(text = { Text("Semua Afdeling") }, onClick = { afd = ""; afdMenu = false })
                    AFD_LIST.forEach { a -> DropdownMenuItem(text = { Text(afdLabel(a)) }, onClick = { afd = a; afdMenu = false }) }
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { blnMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (bulan.isEmpty()) "Bulan: Semua" else monthLabel(bulan), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = blnMenu, onDismissRequest = { blnMenu = false }) {
                    DropdownMenuItem(text = { Text("Semua Bulan") }, onClick = { bulan = ""; blnMenu = false })
                    months.forEach { m -> DropdownMenuItem(text = { Text(monthLabel(m)) }, onClick = { bulan = m; blnMenu = false }) }
                }
            }
            FilledTonalIconButton(onClick = { askShare = true }, enabled = list.isNotEmpty()) { Icon(Icons.Default.Share, "Bagikan semua") }
        }
        Text("${list.size} data • ketuk ikon bagikan untuk mengirim semua hasil filter", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.labelMedium)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { r ->
                Card(Modifier.fillMaxWidth().clickable { detail = r }) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(r.nama, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (r.kelas.isNotBlank()) Text("Kelas ${r.kelas}", color = kelasColor(r.kelas), fontWeight = FontWeight.Bold)
                        }
                        Text("${afdLabel(r.afd)} • NPK ${r.npk} • ${r.tk}", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Sarasehan ${fmtTgl(r.tgl)}" + (r.loss?.takeIf { r.tk.equals("Panen", true) }?.let { " • Losses ${fmt2(it)} kg/ha" } ?: ""),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(r.kp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    detail?.let { DetailDialog(it, state) { detail = null } }
    if (askShare) {
        val nFoto = minOf(list.count { it.foto.isNotBlank() }, MAX_FOTO_SHARE)
        val judul = listOf(if (afd.isEmpty()) "Semua Afdeling" else afdLabel(afd), if (bulan.isEmpty()) "Semua bulan" else monthLabel(bulan)).joinToString(" • ") +
            (if (q.isNotBlank()) " • kata \"${q.trim()}\"" else "")
        AlertDialog(
            onDismissRequest = { askShare = false },
            title = { Text("Bagikan ${list.size} data") },
            text = {
                Text("$judul\n\nFoto yang ikut: $nFoto" + (if (list.count { it.foto.isNotBlank() } > MAX_FOTO_SHARE) " (maks. $MAX_FOTO_SHARE)" else "") +
                    "\nTeks rekap juga disalin otomatis, tempel di WhatsApp bila keterangan tidak ikut.")
            },
            confirmButton = {
                TextButton(onClick = { askShare = false; state.share(ctx, multiText(list, judul), list, true) }) { Text("Dengan foto") }
            },
            dismissButton = {
                TextButton(onClick = { askShare = false; state.share(ctx, multiText(list, judul), list, false) }) { Text("Teks saja") }
            }
        )
    }
}

@Composable
fun DetailDialog(r: Respon, state: AppState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val rows = listOf(
        "Afdeling" to afdLabel(r.afd),
        "Tanggal sarasehan" to fmtTgl(r.tgl),
        "NPK" to r.npk,
        "Tenaga kerja" to r.tk,
        "Keluhan pekerjaan" to r.kp,
        "Keluhan fasilitas & benefit" to r.kf,
        "Saran" to r.saran,
        "Losses CKP (kg/ha)" to r.lossRaw,
        "Kelas pemanen" to r.kelas,
        "Diisi oleh" to r.email,
        "Waktu input" to r.ts
    ).filter { it.second.isNotBlank() }
    ListDialog(r.nama, onDismiss, actions = {
        if (r.foto.isNotBlank()) TextButton(onClick = { openUrl(ctx, r.foto) }) { Text("Foto") }
        TextButton(onClick = { state.share(ctx, singleText(r), listOf(r), true) }) { Text("Bagikan") }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.forEach { (k, v) ->
                Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(v, Modifier.padding(bottom = 8.dp))
            }
        }
    }
}
