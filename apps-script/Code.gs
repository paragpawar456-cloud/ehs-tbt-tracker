/**
 * EHS Daily TBT Tracker — Google Apps Script Web App backend.  (v3: auto-detects the data tab, reads Master Contractors tab)
 *
 * Sheet: "Contractor Daily Tbt details"
 *   A Timestamp | B Date | C Name contractor | D Nos of Manpower | E Location of TBT
 *   F Tool box photo | G Photo (used as Status / Photo Notes) | H Client Ref (added by this script)
 *
 * Endpoints (all JSON, all require ?token= / body.token == Script Property API_TOKEN):
 *   GET  ?action=list[&since=ISO]        -> { ok, serverTime, rows:[...], masters:[...] }
 *        masters = names from an optional tab whose name contains "Master" (first column, below its header)
 *   GET  ?action=thumb&id=FILE_ID[&size=800] -> { ok, mime, data(base64) }  (private Drive photos)
 *   GET  ?action=health                  -> { ok, sheet, rows }
 *   POST { action:"create", clientRef, date, contractor, manpower, location, notes,
 *          photoBase64, photoMime, photoName }  -> { ok, row, photoUrl, timestamp, duplicate }
 *
 * Deploy: Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone.
 * Script properties (Project Settings > Script properties):
 *   API_TOKEN        (optional) overrides DEFAULT_API_TOKEN below
 *   PHOTO_FOLDER_ID  (optional) Drive folder for uploads; auto-created "EHS TBT Photos" if absent
 *   PUBLIC_PHOTOS    (optional) "true" => uploaded photos shared "anyone with link can view"
 */

// Pre-generated shared secret. The app is built with the same value. A Script Property API_TOKEN overrides it.
var DEFAULT_API_TOKEN = 'ehs-55d7b73c83db2873a682e4556334a006c398095e';

var SPREADSHEET_ID = '1nmAYAH25c2-4TVLvPJaOJLjbFRYbuzK8tteh1xPD-jw';
var CLIENT_REF_HEADER = 'Client Ref';
var COL = { TIMESTAMP: 1, DATE: 2, CONTRACTOR: 3, MANPOWER: 4, LOCATION: 5, PHOTO: 6, NOTES: 7, CLIENT_REF: 8 };
var MAX_PHOTO_BYTES = 8 * 1024 * 1024;

// ---------------------------------------------------------------------------- entry points

function doGet(e) {
  return handle_(function () {
    var p = (e && e.parameter) || {};
    authorize_(p.token);
    switch (p.action || 'list') {
      case 'list':   return listRows_(p.since);
      case 'thumb':  return thumbnail_(p.id, Number(p.size) || 800);
      case 'health': return { sheet: getSheet_().getName(), rows: Math.max(0, getSheet_().getLastRow() - 1) };
      default: throw apiError_(400, 'Unknown action: ' + p.action);
    }
  });
}

function doPost(e) {
  return handle_(function () {
    var body;
    try {
      body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    } catch (err) {
      throw apiError_(400, 'Body is not valid JSON');
    }
    authorize_(body.token);
    switch (body.action || 'create') {
      case 'create': return createRow_(body);
      default: throw apiError_(400, 'Unknown action: ' + body.action);
    }
  });
}

// ---------------------------------------------------------------------------- actions

function listRows_(since) {
  var sheet = getSheet_();
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return { serverTime: new Date().toISOString(), rows: [], masters: readMasters_() };
  var width = Math.max(sheet.getLastColumn(), COL.NOTES);
  // Display values keep what humans typed ("10 Labour", "9/14/2026"); the app sanitises them.
  var values = sheet.getRange(2, 1, lastRow - 1, width).getDisplayValues();
  var sinceMs = since ? Date.parse(since) : NaN;
  var rows = [];
  for (var i = 0; i < values.length; i++) {
    var v = values[i];
    if (!v[COL.TIMESTAMP - 1] && !v[COL.CONTRACTOR - 1]) continue; // skip blank rows
    if (!isNaN(sinceMs)) {
      var ts = Date.parse(v[COL.TIMESTAMP - 1]);
      if (!isNaN(ts) && ts < sinceMs) continue;
    }
    rows.push({
      row: i + 2,
      timestamp: v[COL.TIMESTAMP - 1],
      date: v[COL.DATE - 1],
      contractor: v[COL.CONTRACTOR - 1],
      manpower: v[COL.MANPOWER - 1],
      location: v[COL.LOCATION - 1],
      photo: v[COL.PHOTO - 1],
      notes: v[COL.NOTES - 1],
      clientRef: width >= COL.CLIENT_REF ? v[COL.CLIENT_REF - 1] : ''
    });
  }
  return { serverTime: new Date().toISOString(), rows: rows, masters: readMasters_() };
}

/** Contractor names from a "Master Contractors" tab (any tab whose name contains "master"). */
function readMasters_() {
  try {
    var sheets = SpreadsheetApp.openById(SPREADSHEET_ID).getSheets();
    for (var i = 0; i < sheets.length; i++) {
      var sh = sheets[i];
      if (sh.getType && sh.getType() !== SpreadsheetApp.SheetType.GRID) continue;
      if (!/master/i.test(sh.getName()) || sh.getLastRow() < 2) continue;
      var width = Math.max(1, Math.min(sh.getLastColumn(), 10));
      var header = sh.getRange(1, 1, 1, width).getDisplayValues()[0];
      var col = 0;
      for (var c = 0; c < header.length; c++) {
        if (/contractor|agency|name/i.test(header[c])) { col = c; break; }
      }
      var values = sh.getRange(2, col + 1, sh.getLastRow() - 1, 1).getDisplayValues();
      var seen = {}, out = [];
      values.forEach(function (r) {
        var n = String(r[0] || '').trim();
        if (n && !seen[n.toLowerCase()]) { seen[n.toLowerCase()] = true; out.push(n); }
      });
      return out;
    }
  } catch (e) { console.error(e); }
  return [];
}

function createRow_(b) {
  var clientRef = str_(b.clientRef);
  var contractor = str_(b.contractor);
  var location = str_(b.location);
  var manpower = parseInt(b.manpower, 10);
  if (!clientRef) throw apiError_(422, 'clientRef is required');
  if (!contractor) throw apiError_(422, 'contractor is required');
  if (!location) throw apiError_(422, 'location is required');
  if (!(manpower > 0)) throw apiError_(422, 'manpower must be an integer > 0');
  var date = parseIsoDate_(b.date);
  if (!date) throw apiError_(422, 'date must be yyyy-MM-dd');

  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sheet = getSheet_();
    ensureClientRefColumn_(sheet);

    // Idempotency: WorkManager may retry after a timeout that actually succeeded server-side.
    var existing = findByClientRef_(sheet, clientRef);
    if (existing) {
      return {
        row: existing,
        duplicate: true,
        photoUrl: sheet.getRange(existing, COL.PHOTO).getDisplayValue(),
        timestamp: sheet.getRange(existing, COL.TIMESTAMP).getDisplayValue()
      };
    }

    var photoUrl = '';
    if (b.photoBase64) photoUrl = savePhoto_(b.photoBase64, b.photoMime, b.photoName || (clientRef + '.jpg'));

    var now = new Date();
    var row = [];
    row[COL.TIMESTAMP - 1] = now;
    row[COL.DATE - 1] = date;
    row[COL.CONTRACTOR - 1] = contractor;
    row[COL.MANPOWER - 1] = manpower;
    row[COL.LOCATION - 1] = location;
    row[COL.PHOTO - 1] = photoUrl;
    row[COL.NOTES - 1] = str_(b.notes);
    row[COL.CLIENT_REF - 1] = clientRef;
    sheet.appendRow(row);
    var rowNum = sheet.getLastRow();
    SpreadsheetApp.flush();
    return {
      row: rowNum,
      duplicate: false,
      photoUrl: photoUrl,
      timestamp: sheet.getRange(rowNum, COL.TIMESTAMP).getDisplayValue()
    };
  } finally {
    lock.releaseLock();
  }
}

/** Returns a resized thumbnail of a (possibly private) Drive photo as base64, using the owner's auth. */
function thumbnail_(fileId, size) {
  if (!fileId || !/^[\w-]{10,}$/.test(fileId)) throw apiError_(400, 'Invalid id');
  size = Math.min(Math.max(size, 100), 1600);
  var token = ScriptApp.getOAuthToken();
  try {
    var meta = UrlFetchApp.fetch(
      'https://www.googleapis.com/drive/v3/files/' + encodeURIComponent(fileId) + '?fields=thumbnailLink,mimeType',
      { headers: { Authorization: 'Bearer ' + token }, muteHttpExceptions: true });
    if (meta.getResponseCode() === 200) {
      var link = JSON.parse(meta.getContentText()).thumbnailLink;
      if (link) {
        var img = UrlFetchApp.fetch(link.replace(/=s\d+$/, '') + '=s' + size,
          { headers: { Authorization: 'Bearer ' + token }, muteHttpExceptions: true });
        if (img.getResponseCode() === 200) {
          var blob = img.getBlob();
          return { mime: blob.getContentType() || 'image/jpeg', data: Utilities.base64Encode(blob.getBytes()) };
        }
      }
    }
  } catch (ignored) { /* fall through to DriveApp thumbnail */ }
  var thumb = DriveApp.getFileById(fileId).getThumbnail();
  if (!thumb) throw apiError_(404, 'No thumbnail available');
  return { mime: thumb.getContentType() || 'image/png', data: Utilities.base64Encode(thumb.getBytes()) };
}

// ---------------------------------------------------------------------------- helpers

/**
 * Returns the tab that holds the TBT rows. The link's gid can point at a chart tab, so the data tab
 * is found by its header row (Timestamp + Name contractor) and remembered in script properties.
 */
function getSheet_() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheets = ss.getSheets();
  var cached = Number(prop_('DATA_SHEET_ID'));
  for (var i = 0; i < sheets.length; i++) {
    if (cached && sheets[i].getSheetId() === cached && isDataSheet_(sheets[i])) return sheets[i];
  }
  var candidates = [];
  for (var j = 0; j < sheets.length; j++) {
    if (isDataSheet_(sheets[j])) candidates.push(sheets[j]);
  }
  if (!candidates.length) throw apiError_(500, 'No tab with a "Timestamp" and "Name contractor" header row was found');
  // Prefer the Google Form responses tab, then the one with the most rows.
  candidates.sort(function (a, b) {
    var fa = a.getFormUrl ? (a.getFormUrl() ? 1 : 0) : 0, fb = b.getFormUrl ? (b.getFormUrl() ? 1 : 0) : 0;
    return (fb - fa) || (b.getLastRow() - a.getLastRow());
  });
  PropertiesService.getScriptProperties().setProperty('DATA_SHEET_ID', String(candidates[0].getSheetId()));
  return candidates[0];
}

function isDataSheet_(sheet) {
  if (sheet.getType && sheet.getType() !== SpreadsheetApp.SheetType.GRID) return false;
  if (sheet.getLastRow() < 1 || sheet.getLastColumn() < 3) return false;
  var header = sheet.getRange(1, 1, 1, Math.min(sheet.getLastColumn(), 10)).getDisplayValues()[0]
    .map(function (h) { return String(h).trim().toLowerCase(); });
  return header[0] === 'timestamp' && header.some(function (h) { return h.indexOf('contractor') >= 0; });
}

function ensureClientRefColumn_(sheet) {
  var header = sheet.getRange(1, COL.CLIENT_REF).getDisplayValue();
  if (header !== CLIENT_REF_HEADER) sheet.getRange(1, COL.CLIENT_REF).setValue(CLIENT_REF_HEADER);
}

function findByClientRef_(sheet, clientRef) {
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return 0;
  var hit = sheet.getRange(2, COL.CLIENT_REF, lastRow - 1, 1)
    .createTextFinder(clientRef).matchEntireCell(true).findNext();
  return hit ? hit.getRow() : 0;
}

function savePhoto_(base64, mime, name) {
  var bytes = Utilities.base64Decode(base64);
  if (bytes.length > MAX_PHOTO_BYTES) throw apiError_(413, 'Photo too large');
  mime = /^image\/(jpeg|png|webp)$/.test(mime || '') ? mime : 'image/jpeg';
  var blob = Utilities.newBlob(bytes, mime, String(name).replace(/[^\w.\-]/g, '_'));
  var file = getPhotoFolder_().createFile(blob);
  if (prop_('PUBLIC_PHOTOS') === 'true') {
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  }
  // Same link style Google Forms writes, so the app parses both identically.
  return 'https://drive.google.com/open?id=' + file.getId();
}

function getPhotoFolder_() {
  var id = prop_('PHOTO_FOLDER_ID');
  if (id) return DriveApp.getFolderById(id);
  var folder = DriveApp.createFolder('EHS TBT Photos');
  PropertiesService.getScriptProperties().setProperty('PHOTO_FOLDER_ID', folder.getId());
  return folder;
}

function parseIsoDate_(s) {
  var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str_(s));
  if (!m) return null;
  var d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return isNaN(d.getTime()) ? null : d;
}

function authorize_(token) {
  var expected = prop_('API_TOKEN') || DEFAULT_API_TOKEN;
  if (!expected) throw apiError_(500, 'API token is not configured');
  if (!token || !safeEquals_(String(token), expected)) throw apiError_(401, 'Unauthorized');
}

function safeEquals_(a, b) {
  if (a.length !== b.length) return false;
  var diff = 0;
  for (var i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function prop_(k) { return PropertiesService.getScriptProperties().getProperty(k); }
function str_(v) { return v == null ? '' : String(v).trim(); }

function apiError_(code, message) {
  var err = new Error(message);
  err.apiCode = code;
  return err;
}

/** Apps Script cannot set HTTP status codes, so failures are {ok:false, code, error} with HTTP 200. */
function handle_(fn) {
  var out;
  try {
    var result = fn() || {};
    result.ok = true;
    out = result;
  } catch (err) {
    out = { ok: false, code: err.apiCode || 500, error: String(err.message || err) };
    if (!err.apiCode) console.error(err.stack || err);
  }
  return ContentService.createTextOutput(JSON.stringify(out)).setMimeType(ContentService.MimeType.JSON);
}

/** Run once from the editor to authorise scopes and verify access. */
function setupCheck() {
  var sheet = getSheet_();
  Logger.log('Sheet: %s, data rows: %s', sheet.getName(), sheet.getLastRow() - 1);
  Logger.log('API_TOKEN set: %s', !!prop_('API_TOKEN'));
  Logger.log('Photo folder: %s', getPhotoFolder_().getName());
}
