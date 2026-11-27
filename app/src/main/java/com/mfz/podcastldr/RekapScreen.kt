@file:OptIn(ExperimentalMaterial3Api::class)

package com.mfz.podcastldr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun estateGroups(d: AppData): List<Pair<String, List<String>>> {
    val out = LinkedHashMap<String, MutableList<String>>()
    d.estates.forEach { out.getOrPut(it.estate.ifBlank { "Lainnya" }) { mutableListOf() }.add(it.afd) }
    val listed = out.values.flatten().toSet()
    d.master.map { it.afd }.distinct().filter { it.isNotBlank() && it !in listed }.sortedWith(afdComparator).forEach { a ->
        val est = d.master.first { it.afd == a }.estate.ifBlank { "Lainnya" }
        out.getOrPut(est) { mutableListOf() }.add(a)
    }
    return out.map { it.key to it.value.toList() }
}

@Composable
fun RekapScreen(state: AppState) {
    val d = state.data
    if (d == null) { EmptyInfo(); return }
    var bulan by rememberSaveable { mutableStateOf("") }
    var afdDetail by remember { mutableStateOf<String?>(null) }
    var topikDetail by remember { mutableStateOf<Pair<String, List<Respon>>?>(null) }

    var estF by rememberSaveable { mutableStateOf("") }
    var afdF by rememberSaveable { mutableStateOf("") }
    var estMenu by remember { mutableStateOf(false) }
    var afdMenu by remember { mutableStateOf(false) }

    val groups = remember(d) { estateGroups(d) }
    val scopeAfds: Set<String>? = remember(groups, estF, afdF) {
        when {
            afdF.isNotEmpty() -> setOf(afdF)
            estF.isNotEmpty() -> groups.firstOrNull { it.first == estF }?.second?.toSet() ?: emptySet()
            else -> null
        }
    }
    val master = remember(d, scopeAfds) { if (scopeAfds == null) d.master else d.master.filter { it.afd in scopeAfds } }
    val shownGroups = remember(groups, estF, afdF) {
        groups.filter { estF.isEmpty() || it.first == estF }
            .map { (e, a) -> e to a.filter { afdF.isEmpty() || it == afdF } }
            .filter { it.second.isNotEmpty() }
    }
    val months = remember(d) { d.responses.map { it.tgl.take(7) }.filter { it.length == 7 && it[4] == '-' }.distinct().sortedDescending() }
    val resp = remember(d, bulan, scopeAfds) {
        d.responses.filter { (bulan.isEmpty() || it.tgl.startsWith(bulan)) && (scopeAfds == null || it.afd in scopeAfds) }
    }
    val done = remember(resp) { HashMap<String, Respon>().also { m -> resp.forEach { r -> r.npks.forEach { m[it] = r } } } }
    val scopeLabel = when {
        afdF.isNotEmpty() -> afdLabel(afdF)
        estF.isNotEmpty() -> "Estate $estF"
        else -> "Semua Estate"
    }
    val panen = remember(resp) { resp.filter { it.tk.equals("Panen", true) } }
    val topik = remember(resp, d) {
        d.topics.ifEmpty { TOPIK }.map { (n, rx) -> n to resp.filter { it.matches(rx) } }.filter { it.second.isNotEmpty() }.sortedByDescending { it.second.size }
    }
    val totalTk = master.size
    val totalDone = master.count { done.containsKey(it.npk) }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text("Bulan pelaksanaan sarasehan:", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.labelMedium)
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = bulan.isEmpty(), onClick = { bulan = "" }, label = { Text("Semua") }) }
                items(months) { m -> FilterChip(selected = bulan == m, onClick = { bulan = m }, label = { Text(monthLabel(m)) }) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { estMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (estF.isEmpty()) "Estate: Semua" else "Estate: $estF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = estMenu, onDismissRequest = { estMenu = false }) {
                        DropdownMenuItem(text = { Text("Semua Estate") }, onClick = { estF = ""; afdF = ""; estMenu = false })
                        groups.forEach { (e, _) -> DropdownMenuItem(text = { Text("Estate $e") }, onClick = { estF = e; afdF = ""; estMenu = false }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { afdMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (afdF.isEmpty()) "Afd: Semua" else "Afd: ${afdF.removePrefix("Afd ")}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = afdMenu, onDismissRequest = { afdMenu = false }) {
                        DropdownMenuItem(text = { Text("Semua Afdeling") }, onClick = { afdF = ""; afdMenu = false })
                        groups.filter { estF.isEmpty() || it.first == estF }.forEach { (e, afds) ->
                            afds.forEach { a ->
                                DropdownMenuItem(text = { Text(afdLabel(a)) }, onClick = { afdF = a; estF = e; afdMenu = false })
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Cakupan Podcast LDR – $scopeLabel") {
                val p = if (totalTk > 0) totalDone.toFloat() / totalTk else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pct(totalDone, totalTk), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = pctColor(p))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("$totalDone dari $totalTk TK Panen", fontWeight = FontWeight.SemiBold)
                        Text("sudah ikut sarasehan", style = MaterialTheme.typography.labelMedium)
                    }
                }
                LinearProgressIndicator(
                    progress = { p }, modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().height(10.dp),
                    color = pctColor(p), trackColor = pctColor(p).copy(alpha = 0.15f)
                )
                Text("Total sesi: ${resp.size}  •  Panen: ${panen.size}  •  Rawat: ${resp.size - panen.size}")
                Text("Ketuk afdeling untuk melihat TK yang belum/sudah ikut.", style = MaterialTheme.typography.labelSmall)
            }
        }
        items(shownGroups) { (est, afds) ->
            val tkE = master.filter { it.afd in afds }
            val dnE = tkE.count { done.containsKey(it.npk) }
            SectionCard("Estate $est   ${dnE}/${tkE.size} (${pct(dnE, tkE.size)})") {
                afds.forEach { a ->
                    val t = master.filter { it.afd == a }
                    val n = t.count { done.containsKey(it.npk) }
                    val p = if (t.isNotEmpty()) n.toFloat() / t.size else 0f
                    BarRow(afdLabel(a), n, t.size, "$n/${t.size}  (${pct(n, t.size)})", pctColor(p)) { afdDetail = a }
                }
            }
        }
        item {
            SectionCard("Tindak Lanjut Keluhan") {
                val perlu = resp.filter { it.status != "Tidak perlu" }
                val sel = perlu.count { it.status == "Selesai" }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    STATUS_LIST.forEach { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${resp.count { it.status == s }}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = statusColor(s))
                            Text(s, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                BarRow("Selesai (semua)", sel, perlu.size, "$sel/${perlu.size}  (${pct(sel, perlu.size)})", statusColor("Selesai"))
                perlu.groupBy { it.afd }.toList().sortedWith(compareBy(afdComparator) { it.first }).forEach { (a, l) ->
                    val n = l.count { it.status == "Selesai" }
                    BarRow(afdLabel(a), n, l.size, "$n/${l.size}", statusColor("Selesai"))
                }
                Text("Ubah status di tab Data → ketuk data → Tindak Lanjut.", style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            SectionCard("Kelas Pemanen") {
                val ks = listOf("A", "B", "C", "D")
                val tot = panen.count { it.kelas in ks }
                ks.forEach { k ->
                    val n = panen.count { it.kelas == k }
                    BarRow("Kelas $k", n, tot, "$n  (${pct(n, tot)})", kelasColor(k))
                }
            }
        }
        item {
            SectionCard("Rata-rata Losses CKP Ancak (kg/ha)") {
                val valid = panen.filter { (it.loss ?: 0.0) > 0.0 }
                if (valid.isEmpty()) Text("Belum ada data losses")
                else {
                    Text("Rata-rata semua: ${fmt2(valid.map { it.loss!! }.average())} kg/ha (n=${valid.size})", fontWeight = FontWeight.SemiBold)
                    val rows = valid.groupBy { it.afd }.map { (a, l) -> Triple(a, l.map { it.loss!! }.average(), l.size) }
                        .sortedWith(compareBy(afdComparator) { it.first })
                    val mx = rows.maxOf { it.second }
                    rows.forEach { (a, avg, n) ->
                        BarRow(afdLabel(a), (avg * 100).toInt(), (mx * 100).toInt(), "${fmt2(avg)}  (n=$n)", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        item {
            SectionCard("Topik Keluhan & Saran Terbanyak") {
                if (topik.isEmpty()) Text("Belum ada data")
                topik.forEach { (n, l) -> BarRow(n, l.size, resp.size, "${l.size} sesi", Color(0xFF6D4C41)) { topikDetail = n to l } }
                Text("Dikelompokkan otomatis dari kata kunci. Ketuk untuk melihat isinya.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    afdDetail?.let { a -> AfdDialog(a, d.master.filter { it.afd == a }, done, if (bulan.isEmpty()) "" else monthLabel(bulan)) { afdDetail = null } }
    topikDetail?.let { (n, l) ->
        ListDialog("$n (${l.size})", { topikDetail = null }) {
            LazyColumn {
                items(l) { r ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("${r.nama} • ${afdLabel(r.afd)} • ${fmtTgl(r.tgl)}", fontWeight = FontWeight.SemiBold)
                        Text("Pekerjaan: ${r.kp}", style = MaterialTheme.typography.bodySmall)
                        Text("Fasilitas: ${r.kf}", style = MaterialTheme.typography.bodySmall)
                        Text("Saran: ${r.saran}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun AfdDialog(afd: String, tk: List<Karyawan>, done: Map<String, Respon>, periode: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val belum = remember(tk, done) { tk.filter { !done.containsKey(it.npk) }.sortedBy { it.nama } }
    val sudah = remember(tk, done) { tk.filter { done.containsKey(it.npk) }.sortedBy { it.nama } }
    val list = if (tab == 0) belum else sudah
    ListDialog(
        afdLabel(afd) + if (periode.isNotEmpty()) " • $periode" else "", onDismiss,
        actions = {
            TextButton(onClick = {
                val judul = if (tab == 0) "BELUM" else "SUDAH"
                val txt = "Podcast LDR ${afdLabel(afd)}${if (periode.isNotEmpty()) " ($periode)" else ""}\nTK Panen $judul sarasehan: ${list.size} dari ${tk.size}\n\n" +
                    list.mapIndexed { i, k -> "${i + 1}. ${k.nama} (${k.npk})" }.joinToString("\n")
                shareText(ctx, txt)
            }) { Text("Bagikan") }
        }
    ) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Belum (${belum.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sudah (${sudah.size})") })
        }
        LazyColumn {
            items(list) { k ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(k.nama, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("NPK ${k.npk}" + (done[k.npk]?.let { "  •  ${fmtTgl(it.tgl)}" } ?: ""), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
            }
        }
    }
}
