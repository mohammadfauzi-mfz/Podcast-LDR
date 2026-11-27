/**
 * Podcast LDR – API untuk aplikasi Android
 * Pasang di spreadsheet hasil form: Extensions > Apps Script, tempel seluruh isi file ini.
 * Lalu jalankan fungsi setup() sekali (izinkan akses), kemudian Deploy > New deployment > Web app
 *   Execute as: Me   |   Who has access: Anyone
 */
const API_KEY = 'ldr-2026';                      // samakan dengan "Kunci API" di aplikasi
const SHEET_RESP = 'Form Responses 1';
const SHEET_DB = 'DB_TKPanen';
const SHEET_REKAP = 'Rekap';
const SHEET_TOPIK = 'Topik';
const DEFAULT_TOPIK = [
  ['Air bersih / air minum', 'air!, drum, gentong, kentong, ember, sumur, mck!'],
  ['Akses panen (titi, jalan, teras)', 'titi, tangga, jalan, ereng, akses, pasar pikul, jb!, teras, langsir, pelangsir'],
  ['Pruning / pelepah', 'prun, pronin, pelepah, pelelah, sengkleh'],
  ['Perumahan', 'rumah, perumahan, plafon, platfon, platpom, plywood, pliut, triplek, barak, dinding, pintu, seng!, wc!, kamar'],
  ['APD & alat kerja', 'helm, sepatu, boot, egrek, apd!, pipa, angkong, arco, korset, masker'],
  ['Gaji / upah / premi', 'gaji, upah, brondol, premi, slip, bjr!, basis, basic'],
  ['Ancak semak / piringan', 'semak, kentosan, piringan, gawangan, tebas, rgm!, cemis'],
  ['Pokok tinggi', 'pokok tinggi, terlalu tinggi, sudah tinggi, tinggi-tinggi, pipa luar, pipa panjang'],
  ['Grading / restan', 'grading, restan, kurang buah'],
  ['Transportasi', 'motor, kendaraan, antar jemput, transport, bus!'],
  ['Sarana olahraga', 'olahraga, lapangan, takraw, badminton'],
  ['Ternak / sapi', 'sapi, ternak'],
  ['Pekerjaan istri / keluarga', 'istri, anak saya']
];
const FOLDER_ID = '';                            // opsional: ID folder Drive untuk foto. Kosong = dibuat otomatis
const FOLDER_NAME = 'Podcast LDR - Foto Aplikasi';

function setup() {
  const ss = SpreadsheetApp.getActive();
  ensureTopik_(ss);
  Logger.log('Folder foto: ' + folder_().getUrl());
  Logger.log('Master TK: ' + readMaster_(ss).length + ' | Respon: ' + readResponses_(ss).length);
}

function doGet(e) {
  try {
    if (((e && e.parameter && e.parameter.key) || '') !== API_KEY) return json_({ ok: false, error: 'Kunci API salah' });
    if (e.parameter.action === 'foto') return foto_(e.parameter.id);
    const ss = SpreadsheetApp.getActive();
    return json_({
      ok: true,
      updated: Utilities.formatDate(new Date(), ss.getSpreadsheetTimeZone(), 'dd/MM/yyyy HH:mm'),
      master: readMaster_(ss),
      estates: readEstates_(ss),
      responses: readResponses_(ss),
      topics: readTopik_(ss)
    });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    const d = JSON.parse(e.postData.contents);
    if (d.key !== API_KEY) return json_({ ok: false, error: 'Kunci API salah' });
    lock.waitLock(30000);
    const props = PropertiesService.getScriptProperties();
    if (d.id && props.getProperty('sent_' + d.id)) return json_({ ok: true, dup: true });

    const ss = SpreadsheetApp.getActive();
    const tz = ss.getSpreadsheetTimeZone();
    const sh = ss.getSheetByName(SHEET_RESP);
    if (!sh) return json_({ ok: false, error: 'Sheet ' + SHEET_RESP + ' tidak ditemukan' });

    let foto = '';
    if (d.foto) {
      const blob = Utilities.newBlob(Utilities.base64Decode(d.foto), 'image/jpeg', d.fotoName || ('LDR_' + d.npk + '.jpg'));
      const file = folder_().createFile(blob);
      try { file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW); } catch (x) { /* kebijakan domain */ }
      foto = 'https://drive.google.com/open?id=' + file.getId();
    }
    const panen = d.tk === 'Panen';
    const row = [
      new Date(),
      d.pengisi || 'Aplikasi',
      '',
      d.afdeling || '',
      d.tanggal ? Utilities.parseDate(d.tanggal, tz, 'yyyy-MM-dd') : '',
      d.nama || '',
      "'" + String(d.npk || ''),                // apostrof = paksa teks, nol di depan tetap ada
      d.tk || '',
      d.kp || '',
      d.kf || '',
      d.saran || '',
      foto,
      panen ? Number(d.losses) || 0 : 0,
      panen && d.kelas ? 'Kelas ' + d.kelas : ''
    ];
    const r = sh.getLastRow() + 1;
    sh.getRange(r, 1, 1, row.length).setValues([row]);
    if (d.id) props.setProperty('sent_' + d.id, '1');
    return json_({ ok: true, foto: foto });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

/** Jalankan sekali untuk memperbaiki NPK yang kehilangan nol di depan (mis. 81833 -> 0081833). */
function fixNpk() {
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_RESP);
  const rg = sh.getRange(2, 7, sh.getLastRow() - 1, 1);
  const v = rg.getValues();
  let n = 0;
  const out = v.map(r => {
    const s = String(r[0]).trim();
    if (typeof r[0] === 'number' || (/^\d+$/.test(s) && s.length < 7)) { n++; return ["'" + pad_(r[0])]; }
    return [r[0]];
  });
  rg.setValues(out);
  Logger.log(n + ' NPK diperbaiki');
}

/* ---------- topik & foto ---------- */

function ensureTopik_(ss) {
  let sh = ss.getSheetByName(SHEET_TOPIK);
  if (!sh) {
    sh = ss.insertSheet(SHEET_TOPIK);
    sh.getRange(1, 1, 1, 2).setValues([['Topik', 'Kata kunci (pisahkan dengan koma; akhiri ! untuk kata utuh)']]).setFontWeight('bold');
    sh.getRange(2, 1, DEFAULT_TOPIK.length, 2).setValues(DEFAULT_TOPIK);
    sh.setColumnWidth(1, 240); sh.setColumnWidth(2, 640);
  }
  return sh;
}

function readTopik_(ss) {
  const sh = ss.getSheetByName(SHEET_TOPIK);
  if (!sh || sh.getLastRow() < 2) return [];
  return sh.getRange(2, 1, sh.getLastRow() - 1, 2).getValues()
    .filter(r => String(r[0]).trim() && String(r[1]).trim())
    .map(r => ({
      name: String(r[0]).trim(),
      kws: String(r[1]).split(/[,;\n]+/).map(s => s.trim().toLowerCase()).filter(s => s)
    }));
}

/** Mengambil foto Drive (dari Form maupun aplikasi) sebagai base64, diperkecil bila bisa. */
function foto_(id) {
  if (!/^[\w-]{10,}$/.test(id || '')) return json_({ ok: false, error: 'ID foto tidak valid' });
  let blob = null;
  try {
    const r = UrlFetchApp.fetch('https://drive.google.com/thumbnail?id=' + id + '&sz=w1600', {
      headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() }, muteHttpExceptions: true
    });
    const h = r.getHeaders();
    const ct = String(h['Content-Type'] || h['content-type'] || '');
    if (r.getResponseCode() === 200 && ct.indexOf('image') === 0) blob = r.getBlob();
  } catch (x) { /* pakai file asli */ }
  if (!blob) blob = DriveApp.getFileById(id).getBlob();
  return json_({ ok: true, mime: blob.getContentType(), data: Utilities.base64Encode(blob.getBytes()) });
}

/* ---------- pembaca data ---------- */

function readMaster_(ss) {
  const sh = ss.getSheetByName(SHEET_DB);
  if (!sh || sh.getLastRow() < 2) return [];
  const v = sh.getRange(2, 1, sh.getLastRow() - 1, 6).getValues();
  return v.filter(r => r[1] !== '' && r[1] !== null).map(r => ({
    npk: pad_(r[1]), nama: String(r[2]).trim(), afd: normAfd_(r[3]), estate: String(r[4]).trim()
  }));
}

function readEstates_(ss) {
  const sh = ss.getSheetByName(SHEET_REKAP);
  if (!sh || sh.getLastRow() < 4) return [];
  const v = sh.getRange(4, 2, sh.getLastRow() - 3, 2).getValues();
  const out = []; let est = '';
  for (const r of v) {
    if (String(r[0]).trim()) est = String(r[0]).trim();
    const afd = normAfd_(r[1]);
    if (!afd) break;
    out.push({ estate: est, afd: afd });
  }
  return out;
}

function readResponses_(ss) {
  const sh = ss.getSheetByName(SHEET_RESP);
  if (!sh || sh.getLastRow() < 2) return [];
  const tz = ss.getSpreadsheetTimeZone();
  const v = sh.getRange(2, 1, sh.getLastRow() - 1, 14).getValues();
  return v.filter(r => r[0] !== '' && r[5] !== '').map(r => ({
    ts: fmt_(r[0], tz, 'yyyy-MM-dd HH:mm'),
    email: String(r[1]),
    afd: normAfd_(r[3]),
    tgl: fmt_(r[4], tz, 'yyyy-MM-dd'),
    nama: String(r[5]).trim(),
    npk: String(r[6]).trim(),
    npks: String(r[6]).split(/[,;\/&]+/).map(pad_).filter(x => x),
    tk: String(r[7]).trim(),
    kp: String(r[8]).trim(),
    kf: String(r[9]).trim(),
    saran: String(r[10]).trim(),
    foto: String(r[11]).trim(),
    lossRaw: String(r[12]),
    loss: parseLoss_(r[12]),
    kelas: String(r[13]).replace(/kelas/i, '').trim().toUpperCase()
  }));
}

/* ---------- utilitas ---------- */

function pad_(x) {
  let s = (typeof x === 'number') ? String(Math.round(x)) : String(x || '').trim();
  if (/^\d+$/.test(s) && s.length < 7) s = s.padStart(7, '0');
  return s;
}
function normAfd_(x) {
  const s = String(x || '').trim().replace(/^afdeling\s*/i, '').replace(/^afd\.?\s*/i, '').replace(/,/g, '.').trim();
  return s ? 'Afd ' + s : '';
}
function parseLoss_(x) {
  if (typeof x === 'number') return x;
  const m = String(x || '').replace(/,/g, '.').match(/\d+(\.\d+)?/);
  return m ? parseFloat(m[0]) : null;
}
function fmt_(d, tz, p) {
  return (d instanceof Date) ? Utilities.formatDate(d, tz, p) : String(d || '');
}
function folder_() {
  if (FOLDER_ID) return DriveApp.getFolderById(FOLDER_ID);
  const it = DriveApp.getFoldersByName(FOLDER_NAME);
  return it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
}
function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
