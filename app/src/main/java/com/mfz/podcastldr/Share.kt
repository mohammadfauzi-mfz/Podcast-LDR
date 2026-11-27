package com.mfz.podcastldr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

const val MAX_FOTO_SHARE = 30

fun respText(r: Respon): String {
    val sb = StringBuilder()
    sb.append("*${r.nama}* (NPK ${r.npk})\n")
    sb.append("${afdLabel(r.afd)} • Sarasehan ${fmtTgl(r.tgl)} • ${r.tk}")
    if (r.tk.equals("Panen", true)) {
        if (r.kelas.isNotBlank()) sb.append(" • Kelas ${r.kelas}")
        r.loss?.let { sb.append(" • Losses ${fmt2(it)} kg/ha") }
    }
    sb.append("\nKeluhan pekerjaan: ${r.kp}\nKeluhan fasilitas & benefit: ${r.kf}\nSaran: ${r.saran}")
    return sb.toString()
}

fun singleText(r: Respon) = "*Sarasehan Podcast LDR*\n" + respText(r) + (if (r.foto.isNotBlank()) "\nFoto: ${r.foto}" else "")

fun multiText(list: List<Respon>, judul: String): String =
    "*Rekap Sarasehan Podcast LDR*\n$judul\nJumlah: ${list.size} data\n\n" +
        list.mapIndexed { i, r -> "${i + 1}. " + respText(r) }.joinToString("\n\n")

object Sharer {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

    fun clear(ctx: Context) { dir(ctx).listFiles()?.forEach { it.delete() } }

    /** Unduh foto satu jawaban, perkecil (maks 1280 px), simpan, kembalikan Uri untuk dibagikan. */
    fun prepare(ctx: Context, repo: Repo, r: Respon, idx: Int): Uri? {
        return try {
            val id = driveId(r.foto) ?: return null
            val bytes = repo.foto(id)
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= 1280) s *= 2
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
            val sc = minOf(1f, 1280f / maxOf(raw.width, raw.height))
            val bmp = if (sc < 1f) Bitmap.createScaledBitmap(raw, (raw.width * sc).toInt(), (raw.height * sc).toInt(), true) else raw
            val name = "LDR_" + (idx + 1) + "_" + r.nama.replace(Regex("[^A-Za-z0-9]+"), "_").take(30) + ".jpg"
            val f = File(dir(ctx), name)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", f)
        } catch (e: Exception) {
            null
        }
    }

    fun send(ctx: Context, text: String, uris: List<Uri>) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Podcast LDR", text))
        val i = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
            uris.size == 1 -> Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uris[0])
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        i.putExtra(Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri("foto", uris[0])
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            i.clipData = clip
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Bagikan"))
        Toast.makeText(ctx, "Teks juga disalin. Tempel jika keterangan tidak muncul.", Toast.LENGTH_LONG).show()
    }
}
