# Podcast LDR

Aplikasi Android untuk mengisi **Form Sarasehan Karyawan (Podcast LDR)** dan melihat **rekap** langsung dari HP.
Data tetap tersimpan di Google Spreadsheet yang sama dengan Google Form, jadi Form lama tetap bisa dipakai.

## Fitur
- **Isi Form**: cari karyawan dari daftar `DB_TKPanen` (nama, NPK, afdeling langsung terisi), tanggal, Panen/Rawat,
  keluhan, saran, losses & kelas (khusus Panen), foto dari kamera/galeri dengan cap tanggal otomatis.
  Data tersimpan dulu di HP jika tidak ada sinyal, lalu terkirim otomatis.
- **Rekap**: cakupan TK Panen yang sudah ikut per estate & afdeling (seperti sheet `Rekap`), daftar TK yang belum ikut
  (bisa dibagikan ke WhatsApp), sebaran kelas pemanen, rata-rata losses per afdeling, topik keluhan terbanyak. Filter per bulan.
- **Data**: daftar semua jawaban, cari nama/NPK/kata, filter afdeling, lihat detail, buka foto, bagikan.

## Pemasangan (sekali saja)
1. Buka spreadsheet hasil form → **Extensions → Apps Script**.
2. Hapus isi editor, tempel seluruh isi [`apps-script/Code.gs`](apps-script/Code.gs), simpan.
3. Pilih fungsi **setup** → **Run** → izinkan akses (Google akan minta izin Spreadsheet & Drive).
4. **Deploy → New deployment → Web app**
   - Execute as: **Me**
   - Who has access: **Anyone**
   - Klik Deploy, salin **Web app URL** (berakhiran `/exec`).
5. Unduh APK dari tab **Releases** (atau **Actions → artifact**), pasang di HP.
6. Buka aplikasi → Pengaturan (ikon gerigi): isi **Nama pengisi**, **URL Web App**, dan **Kunci API** (`ldr-2026`).

> Ganti `API_KEY` di `Code.gs` bila ingin kunci lain, lalu isi kunci yang sama di aplikasi.
> Jika `Code.gs` diubah, lakukan **Deploy → Manage deployments → Edit → Version: New version** agar URL tetap sama.

## Catatan
- Baris dari aplikasi masuk ke sheet `Form Responses 1` dengan urutan kolom yang sama seperti Form.
  Kolom *Email Address* diisi nama pengisi. NPK disimpan sebagai teks agar angka 0 di depan tidak hilang.
- Foto disimpan di folder Drive **Podcast LDR - Foto Aplikasi** (atau isi `FOLDER_ID` di `Code.gs`).
- Karyawan baru cukup ditambahkan di sheet `DB_TKPanen`, tekan muat ulang di aplikasi.
- APK dibangun otomatis oleh GitHub Actions setiap push ke `main`.
