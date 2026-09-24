const fs = require('fs'); const vm = require('vm');
const grid = [["Timestamp","Date","Name contractor","Nos of Manpower ","Location of TBT","Tool box photo","Photo"],
  ["9/24/2026 9:34:45","9/24/2026","Choudhary construction ","09","Tower D1 Laval 09 ","https://drive.google.com/open?id=13ROyvWlJfOrBj3ZakM0kvdNRSY0aH37X",""]];
const disp = v => v instanceof Date ? `${v.getMonth()+1}/${v.getDate()}/${v.getFullYear()}` : (v == null ? "" : String(v));
const sheet = {
  getSheetId: () => 1314221799, getName: () => "Form Responses 1",
  getLastRow: () => grid.length, getLastColumn: () => Math.max(...grid.map(r => r.length)),
  getRange: (r, c, nr = 1, nc = 1) => ({
    getDisplayValues: () => grid.slice(r-1, r-1+nr).map(row => Array.from({length: nc}, (_, i) => disp(row[c-1+i]))),
    getDisplayValue: () => disp((grid[r-1]||[])[c-1]),
    setValue: v => { grid[r-1][c-1] = v; },
    createTextFinder: txt => ({ matchEntireCell: () => ({ findNext: () => {
      for (let i = r-1; i < r-1+nr; i++) if (disp(grid[i][c-1]) === txt) return { getRow: () => i+1 }; return null; } }) }),
  }),
  appendRow: row => grid.push(row.map(x => x)),
};
const props = { API_TOKEN: "s3cret", PHOTO_FOLDER_ID: "folder" };
let created = [];
const ctx = {
  console, Date, JSON, Math, Number, String, Error, isNaN,
  SpreadsheetApp: { openById: () => ({ getSheets: () => [sheet] }), flush: () => {} },
  PropertiesService: { getScriptProperties: () => ({ getProperty: k => props[k], setProperty: (k, v) => props[k] = v }) },
  LockService: { getScriptLock: () => ({ waitLock: () => {}, releaseLock: () => {} }) },
  Utilities: { base64Decode: s => Buffer.from(s, 'base64'), newBlob: (b, m, n) => ({ b, m, n }), base64Encode: b => Buffer.from(b).toString('base64') },
  DriveApp: { getFolderById: () => ({ createFile: blob => { created.push(blob); return { getId: () => "NEWFILEID12345", setSharing: () => {} }; } }),
              Access: {}, Permission: {} },
  ContentService: { createTextOutput: s => ({ setMimeType: () => ({ body: s }) }), MimeType: { JSON: 'json' } },
  ScriptApp: {}, UrlFetchApp: {}, Logger: { log: () => {} },
};
vm.createContext(ctx); vm.runInContext(fs.readFileSync(require('path').join(__dirname, '..', 'Code.gs'), 'utf8'), ctx);
const get = p => JSON.parse(ctx.doGet({ parameter: p }).body);
const post = b => JSON.parse(ctx.doPost({ postData: { contents: JSON.stringify(b) } }).body);
const assert = (c, m) => { if (!c) { console.log("FAIL", m); process.exitCode = 1; } else console.log("ok  ", m); };

assert(get({ token: "bad" }).code === 401, "rejects bad token");
const l = get({ token: "s3cret" });
assert(l.ok && l.rows.length === 1 && l.rows[0].manpower === "09" && l.rows[0].row === 2, "lists display values with row numbers");
const body = { action: "create", token: "s3cret", clientRef: "uuid-1", date: "2026-09-24", contractor: "Stellar", manpower: 4,
  location: "B1. 8 Floor", notes: "PPE ok", photoBase64: Buffer.from("jpegbytes").toString('base64'), photoMime: "image/jpeg", photoName: "a b.jpg" };
const c1 = post(body);
assert(c1.ok && c1.row === 3 && !c1.duplicate && c1.photoUrl === "https://drive.google.com/open?id=NEWFILEID12345", "creates row + uploads photo");
assert(grid[0][7] === "Client Ref" && grid[2][7] === "uuid-1" && grid[2][6] === "PPE ok", "writes notes + client ref column");
assert(created[0].n === "a_b.jpg", "sanitises photo filename");
const c2 = post(body);
assert(c2.ok && c2.duplicate && c2.row === 3 && grid.length === 3 && created.length === 1, "retry with same clientRef is idempotent");
assert(post({ ...body, clientRef: "u2", manpower: 0 }).code === 422, "validates manpower");
assert(post({ ...body, clientRef: "u3", date: "9/24/2026" }).code === 422, "requires ISO date");
const l2 = get({ token: "s3cret" });
assert(l2.rows.length === 2 && l2.rows[1].clientRef === "uuid-1", "new row visible to list with clientRef");
assert(JSON.parse(ctx.doPost({ postData: { contents: "{not json" } }).body).code === 400, "bad JSON -> 400");
