# EHS Daily TBT Tracker (Android)

Offline-first Kotlin + Jetpack Compose app for logging daily Toolbox Talks (TBT) against the Google Sheet
**"Contractor Daily Tbt details"** (`1nmAYAH25c2-4TVLvPJaOJLjbFRYbuzK8tteh1xPD-jw`, tab gid `1314221799`).

## Setup (about 10 minutes)

### 1. Deploy the backend (Google Apps Script, Option A)
1. Open the sheet, then go to **Extensions → Apps Script**. You can also create a standalone script at script.google.com.
2. Paste `apps-script/Code.gs`. Turn on *Show "appsscript.json"* in Project Settings and paste `apps-script/appsscript.json`.
3. Go to **Project Settings → Script properties** and add:
   - `API_TOKEN`: a long random string (for example, `openssl rand -hex 24`).
   - `PHOTO_FOLDER_ID` (optional): the Drive folder for uploads. If you leave it out, the script creates one called "EHS TBT Photos".
   - `PUBLIC_PHOTOS` (optional): set it to `true` to share uploaded photos as *anyone with the link can view*.
4. Run `setupCheck` once from the editor to grant the Sheets, Drive and UrlFetch scopes.
5. Go to **Deploy → New deployment → Web app**. Set *Execute as*: **Me** and *Who has access*: **Anyone**. Copy the `/exec` URL.

The first time a record is uploaded, the script adds an 8th column, **Client Ref**, to the sheet. The script uses it to
skip duplicate rows when WorkManager retries an upload. Your existing Google Form keeps working, and its rows leave this column blank.

### 2. Configure the app
Create `local.properties` in the project root. It is git-ignored.
```properties
sdk.dir=/path/to/Android/sdk
EHS_WEB_APP_URL=https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec
EHS_API_TOKEN=<same value as API_TOKEN>
```

### 3. Build & test
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest            # JUnit 5 + MockK + Turbine (JVM)
./gradlew connectedDebugAndroidTest    # Room, WorkManager, Compose UI (device/emulator)
node apps-script/test/backend.test.js  # Apps Script logic against mocked Sheets/Drive
```
Toolchain: Android Studio Ladybug or newer, JDK 17, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01.
All versions are listed in `gradle/libs.versions.toml`, so you can update them in one place.

## Getting the APK without Android Studio (GitHub Actions)
1. Create a **private** GitHub repository and upload this project folder to it.
2. In the repository, go to **Settings → Secrets and variables → Actions** and add two secrets: `EHS_WEB_APP_URL` and `EHS_API_TOKEN`.
3. Go to the **Actions** tab, open **Build APK**, and click **Run workflow**. The build takes about 8–10 minutes.
4. Open the finished run and download **EHS-TBT-Tracker-apk** under *Artifacts*. The download is a zip; unzip it to get `app-debug.apk`.
5. Install it on the phone. Android will ask you to allow installing apps from this source.

## Architecture

```
UI (Compose, M3)            Site dashboard (KPIs · 14-day trend · not reported · coverage · log) → New TBT form (+CameraX)
   │  StateFlow<UiState>
ViewModels (Hilt)           DashboardViewModel · TbtFormViewModel
   │
Domain (pure Kotlin)        use cases: ComputeDashboard · ValidateTbtDraft · SubmitTbt
                            parsing:   ManpowerParser · SheetDateParser · DriveLinkResolver · ContractorNormalizer
                            contracts: TbtRepository · SyncScheduler · ConnectivityObserver
   │
Data                        TbtRepositoryImpl ── Room (single source of truth)
                                   │         └─ Retrofit → Apps Script Web App → Sheet + Drive
                            TbtSyncWorker (WorkManager, CONNECTED, exponential backoff 30s)
                            PhotoProcessor (EXIF rotate → 1600px → watermark → JPEG q80)
                            DrivePhotoFetcher (Coil 3: disk cache → public lh3 URL → Apps Script thumb proxy)
```

**How records flow**
- *Submit:* the photo and the record are saved to Room with state `PENDING`, then `SyncScheduler.scheduleSync()` runs. The UI updates right away and shows an "Offline - Sync Pending" chip.
- *Sync:* the worker runs as soon as the phone has a network connection. It posts each pending record with its client UUID and the photo as base64. The script saves the photo to Drive, appends the row and returns the row number and link. The record is then marked `SYNCED`.
  - Network errors and 5xx responses are retried with backoff.
  - 4xx responses mark the record `FAILED`, and the user can retry it from the Log tab.
- *Refresh (pull-to-refresh or app start):* the app downloads the whole sheet and updates the local copy. `PENDING` records are never overwritten. A pending record whose Client Ref already appears in the sheet (the upload succeeded but the response was lost) is marked `SYNCED`. Rows deleted from the sheet are removed locally.
- *Private Form photos:* Google Form uploads are private to the form owner, so the public `lh3.googleusercontent.com/d/ID` link returns 403 for them. The Coil fetcher then falls back to the script's `thumb` endpoint, which reads the photo with the owner's access, and caches the thumbnail on disk so it also shows offline.

## Data cleaning, checked against the live sheet
The parsers were run on all 94 rows in the sheet (25 Aug – 24 Sep 2026). A copy of those rows is saved as a test fixture in `app/src/test/resources/sheet_list_response.json`.

| Issue in the sheet | Example | Result |
|---|---|---|
| Mixed date locale | `2026-09-12` and `9/14/2026` | 94/94 dates and timestamps parse |
| Text in headcount | `10 Labour`, `09nos`, `09 nos`, `06` | 94/94 → integers > 0 |
| Contractor spellings | `Choudhary construction ` / `Choudhary Construction`, `Alu-wind` / `Alu-wind infratech`, `…enterprises tbt work team` | 18 spellings → 15 contractors |
| Drive links | `open?id=…` (IDs with `_` and `-`) | 94/94 file IDs extracted |

Snapshot for **24 Sep 2026**: 7 TBTs, 71 workers covered, 7 of 15 contractors reporting. The largest contractor overall is
Credible Construction Company, with 386 worker-briefings across 12 sessions.

Other things noticed in the data:
- Two rows have a **TBT date later than the submission date**:
  - Asif Intirior & Decoratior, submitted 14 Sep 16:07 with date 15 Sep.
  - Asif Intirior & Decoratior, submitted 21 Sep 17:33 with date 22 Sep.

  The app's form blocks future dates.
- The 7th column is headed **"Photo"** and is empty in every row. The app writes *Status / Photo Notes* into it.
- **"Location of TBT" is blank** in the first 8 rows (before 5 Sep). The Log tab shows them as "Location not recorded".

## Option B (Sheets API v4)
Only `SheetsWebAppApi` and `TbtRepositoryImpl` would change. The Room layer, the worker and the UI would stay the same.
Option A is recommended for this setup because:
- Photos need to go to Drive. The Apps Script does that and appends the row in a single call.
- Using the Sheets API directly would mean shipping a service-account key inside the APK, which is a security risk. The alternative is OAuth sign-in for every supervisor.

## Project layout
```
app/src/main/java/com/ehs/tbttracker/
  domain/{model,parsing,usecase,repository}
  data/{local,remote,repository,sync,network,photo}
  di/AppModules.kt
  ui/{theme,components,dashboard,form,camera,feed,util}/, TbtApp.kt
app/src/test/…          unit tests (JUnit 5)
app/src/androidTest/…   Room DAO, WorkManager queue, Compose form tests
apps-script/            Code.gs, appsscript.json, test/backend.test.js
```
