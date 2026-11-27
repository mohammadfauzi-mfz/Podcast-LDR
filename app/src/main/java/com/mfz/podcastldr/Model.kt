package com.mfz.podcastldr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Config {
    /** Boleh diisi URL Web App di sini, atau lewat menu Pengaturan di aplikasi. */
    const val DEFAULT_URL = ""
    const val API_KEY = "ldr-2026"
}

val AFD_LIST = (1..14).map { "Afd $it" } + listOf("Afd KT1.1", "Afd KT1.2", "Afd KT1.3")
val afdComparator: Comparator<String> = compareBy { a -> AFD_LIST.indexOf(a).let { if (it < 0) 99 else it } }
/** Menyamakan penulisan afdeling, mis. "Afd KT1,1" -> "Afd KT1.1". */
fun normAfd(a: String) = a.trim().replace(',', '.').replace(Regex("\\s+"), " ")
fun afdLabel(a: String) = "Afdeling " + a.removePrefix("Afd ")

data class Karyawan(val npk: String, val nama: String, val afd: String, val estate: String)
data class EstateAfd(val estate: String, val afd: String)
data class Respon(
    val ts: String, val email: String, val afd: String, val tgl: String, val nama: String,
    val npk: String, val npks: List<String>, val tk: String, val kp: String, val kf: String,
    val saran: String, val foto: String, val lossRaw: String, val loss: Double?, val kelas: String
)
data class AppData(val updated: String, val master: List<Karyawan>, val estates: List<EstateAfd>, val responses: List<Respon>)

private fun JSONObject.s(k: String) = if (isNull(k)) "" else optString(k, "")
private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

fun parseData(txt: String): AppData {
    val t = txt.trim()
    if (t.startsWith("<")) throw Exception("Respon bukan JSON. Pastikan Web App di-deploy dengan akses 'Anyone' dan URL berakhiran /exec")
    val o = JSONObject(t)
    if (!o.optBoolean("ok")) throw Exception(o.optString("error", "Gagal"))
    val master = o.optJSONArray("master").objs().map { Karyawan(it.s("npk"), it.s("nama"), normAfd(it.s("afd")), it.s("estate")) }
    val estates = o.optJSONArray("estates").objs().map { EstateAfd(it.s("estate"), normAfd(it.s("afd"))) }
    val responses = o.optJSONArray("responses").objs().map { x ->
        val np = x.optJSONArray("npks")
        Respon(
            x.s("ts"), x.s("email"), normAfd(x.s("afd")), x.s("tgl"), x.s("nama"), x.s("npk"),
            if (np == null) emptyList() else (0 until np.length()).map { np.getString(it) },
            x.s("tk"), x.s("kp"), x.s("kf"), x.s("saran"), x.s("foto"), x.s("lossRaw"),
            if (x.isNull("loss")) null else x.optDouble("loss"), x.s("kelas")
        )
    }
    return AppData(o.s("updated"), master, estates, responses)
}

class Repo(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("ldr", Context.MODE_PRIVATE)
    private val cacheFile = File(ctx.filesDir, "cache.json")
    private val pendingDir = File(ctx.filesDir, "pending").apply { mkdirs() }

    var url: String
        get() = prefs.getString("url", Config.DEFAULT_URL) ?: ""
        set(v) = prefs.edit().putString("url", v.trim()).apply()
    var key: String
        get() = prefs.getString("key", Config.API_KEY) ?: Config.API_KEY
        set(v) = prefs.edit().putString("key", v.trim()).apply()
    var pengisi: String
        get() = prefs.getString("pengisi", "") ?: ""
        set(v) = prefs.edit().putString("pengisi", v.trim()).apply()

    fun loadCache(): AppData? = runCatching { parseData(cacheFile.readText()) }.getOrNull()

    fun fetch(): AppData {
        val txt = http("GET", "$url?key=" + URLEncoder.encode(key, "UTF-8"), null)
        val d = parseData(txt)
        cacheFile.writeText(txt)
        return d
    }

    fun savePending(o: JSONObject) = File(pendingDir, o.getString("id") + ".json").writeText(o.toString())
    fun pendingCount() = pendingDir.listFiles()?.size ?: 0

    /** Mengirim semua data tertunda; mengembalikan jumlah yang berhasil. */
    fun sendPending(): Int {
        var n = 0
        pendingDir.listFiles()?.sortedBy { it.lastModified() }?.forEach { f ->
            val o = JSONObject(f.readText()).put("key", key)
            val txt = http("POST", url, o.toString()).trim()
            if (txt.startsWith("<")) throw Exception("Respon bukan JSON, cek akses deploy 'Anyone'")
            val res = JSONObject(txt)
            if (!res.optBoolean("ok")) throw Exception(res.optString("error", "Gagal"))
            f.delete(); n++
        }
        return n
    }

    private fun http(method: String, u: String, body: String?): String {
        if (url.isBlank()) throw Exception("URL Web App belum diisi di Pengaturan")
        var target = u
        var m = method
        var b = body
        repeat(6) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000
            c.readTimeout = 90000
            c.requestMethod = m
            if (b != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(b!!.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code in 300..399) {
                target = c.getHeaderField("Location") ?: throw Exception("Redirect tanpa lokasi")
                m = "GET"; b = null
                c.disconnect()
                return@repeat
            }
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code !in 200..299) throw Exception("HTTP $code")
            return txt
        }
        throw Exception("Terlalu banyak redirect")
    }
}

object Img {
    /** Memuat foto, memperkecil (maks 1280 px), memutar sesuai EXIF dan memberi cap tanggal. */
    fun load(ctx: Context, uri: Uri): Bitmap? {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
            val raw = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            var rot = 0
            var exifDate: String? = null
            cr.openInputStream(uri)?.use {
                val ex = ExifInterface(it)
                rot = when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                exifDate = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: ex.getAttribute(ExifInterface.TAG_DATETIME)
            }
            val mx = Matrix()
            if (rot != 0) mx.postRotate(rot.toFloat())
            val sc = minOf(1f, 1280f / maxOf(raw.width, raw.height))
            mx.postScale(sc, sc)
            val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, mx, true).copy(Bitmap.Config.ARGB_8888, true)
            val out = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
            val stamp = exifDate?.let { d ->
                runCatching { out.format(SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(d)!!) }.getOrNull()
            } ?: out.format(Date())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                textSize = bmp.width / 20f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
            }
            Canvas(bmp).drawText(stamp, bmp.width - 20f, bmp.height - 24f, p)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    fun toBase64(b: Bitmap): String {
        val bo = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 75, bo)
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }
}

fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
fun fmtTgl(t: String): String = if (t.length >= 10 && t[4] == '-') "${t.substring(8, 10)}/${t.substring(5, 7)}/${t.substring(0, 4)}" else t
fun fmt2(d: Double): String = String.format(Locale.US, "%.2f", d)
fun pct(n: Int, t: Int): String = if (t == 0) "0%" else "${Math.round(n * 100f / t)}%"
fun monthLabel(m: String): String = runCatching {
    listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")[m.substring(5, 7).toInt() - 1] + " " + m.take(4)
}.getOrDefault(m)

private fun kw(vararg k: String) = k.map { w ->
    if (w.endsWith("!")) Regex("\\b" + Regex.escape(w.dropLast(1)) + "\\b") else Regex("\\b" + Regex.escape(w))
}

/** Pengelompokan topik keluhan/saran berdasarkan kata kunci ("!" = kata utuh). */
val TOPIK: List<Pair<String, List<Regex>>> = listOf(
    "Air bersih / air minum" to kw("air!", "drum", "gentong", "kentong", "ember", "sumur", "mck!"),
    "Akses panen (titi, jalan, teras)" to kw("titi", "tangga", "jalan", "ereng", "akses", "pasar pikul", "jb!", "teras", "langsir", "pelangsir"),
    "Pruning / pelepah" to kw("prun", "pelepah", "pelelah", "sengkleh"),
    "Perumahan" to kw("rumah", "perumahan", "plafon", "platfon", "platpom", "plywood", "pliut", "triplek", "barak", "dinding", "pintu", "seng!", "wc!", "kamar"),
    "APD & alat kerja" to kw("helm", "sepatu", "boot", "egrek", "apd!", "pipa", "angkong", "arco", "korset", "masker"),
    "Gaji / upah / premi" to kw("gaji", "upah", "brondol", "premi", "slip", "bjr!", "basis", "basic"),
    "Ancak semak / piringan" to kw("semak", "kentosan", "piringan", "gawangan", "tebas", "rgm!", "cemis"),
    "Pokok tinggi" to kw("tinggi"),
    "Sarana olahraga" to kw("olahraga", "lapangan", "takraw", "badminton"),
    "Ternak / sapi" to kw("sapi", "ternak"),
    "Pekerjaan istri / keluarga" to kw("istri", "anak saya")
)

fun Respon.matches(rx: List<Regex>): Boolean {
    val t = "$kp $kf $saran".lowercase()
    return rx.any { it.containsMatchIn(t) }
}

data class UpdateInfo(val code: Int, val tag: String, val apkUrl: String, val notes: String)

object Updater {
    private const val API = "https://api.github.com/repos/mohammadfauzi-mfz/podcast-ldr/releases/latest"

    fun check(): UpdateInfo {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        if (code == 404) throw Exception("Rilis tidak ditemukan (repo harus public)")
        if (code !in 200..299) throw Exception("HTTP $code")
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = o.optString("tag_name")
        val assets = o.optJSONArray("assets")
        var apk = ""
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
        }
        return UpdateInfo(tag.substringAfterLast('.').toIntOrNull() ?: 0, tag, apk, o.optString("body"))
    }

    fun download(url: String, dest: File, progress: (Float) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20000; c.readTimeout = 60000
        var conn = c
        var hops = 0
        while (conn.responseCode in 300..399 && hops++ < 5) {
            val loc = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = (URL(loc).openConnection() as HttpURLConnection).apply { connectTimeout = 20000; readTimeout = 60000 }
        }
        if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { inp ->
            dest.outputStream().use { out ->
                val buf = ByteArray(32 * 1024)
                var done = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
    }
}
