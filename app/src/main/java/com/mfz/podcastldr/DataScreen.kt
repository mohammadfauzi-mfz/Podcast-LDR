package com.mfz.podcastldr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
    var q by rememberSaveable { mutableStateOf("") }
    var afd by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Respon?>(null) }
    val list = remember(d, q, afd) {
        d.responses.filter {
            (afd.isEmpty() || it.afd == afd) &&
                (q.isBlank() || "${it.nama} ${it.npk} ${it.kp} ${it.kf} ${it.saran}".contains(q.trim(), true))
        }.sortedByDescending { it.ts }
    }
    Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                q, { q = it }, Modifier.weight(1f),
                placeholder = { Text("Cari nama / NPK / kata") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { menu = true }) { Text(if (afd.isEmpty()) "Semua" else afd.removePrefix("Afd ")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Semua Afdeling") }, onClick = { afd = ""; menu = false })
                    AFD_LIST.forEach { a -> DropdownMenuItem(text = { Text(afdLabel(a)) }, onClick = { afd = a; menu = false }) }
                }
            }
        }
        Text("${list.size} data", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.labelMedium)
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
    detail?.let { DetailDialog(it) { detail = null } }
}

@Composable
fun DetailDialog(r: Respon, onDismiss: () -> Unit) {
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
        TextButton(onClick = {
            shareText(ctx, "*Sarasehan Podcast LDR*\nNama: ${r.nama}\n" + rows.joinToString("\n") { "${it.first}: ${it.second}" } +
                if (r.foto.isNotBlank()) "\nFoto: ${r.foto}" else "")
        }) { Text("Bagikan") }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.forEach { (k, v) ->
                Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(v, Modifier.padding(bottom = 8.dp))
            }
        }
    }
}
