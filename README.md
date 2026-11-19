# MapLedger

Native Android GPS mileage & tax tracker for a Bee Maps / Hivemapper driver.
Kotlin · Jetpack Compose (Material 3, dark) · MVVM + repositories · Room · FusedLocationProvider foreground service · WorkManager · offline-first.

Home base: Manteno, Illinois · Primary vehicle: 2025 Hyundai Elantra Hybrid Limited.

> MapLedger is a record-keeping tool, not tax advice. Have a tax professional review anything you file.

## Status: Phase 1

Phase 1 goal — record a complete home → mapping → home drive, save the GPS route and mileage locally, and show the finished trip in history — is implemented, together with the tax log, expenses, earnings, Bee Maps reconciliation, imports, exports and encrypted backup listed below.

**What has been verified:** 50 automated tests pass (JVM + Robolectric), including a full home-to-home drive persisted to a real SQLite/Room database, crash recovery, duplicate imports, and a UI walk-through that launches the real activity and opens every screen on a phone-sized and a Galaxy Z Fold-sized display.

**What has not been verified:** the app has **not yet been run on a physical phone or emulator** (the build environment has no hardware virtualisation). Real GPS behaviour, the foreground notification, the lock screen, Samsung battery management, Google Maps tiles and the PDF export need a first on-device test. Please report anything that misbehaves.

## Install on a Samsung Galaxy

1. Copy `dist/MapLedger-0.1.0-phase1.apk` to the phone (or download the `mapledger-apk` artifact from the latest GitHub Actions run).
2. Open it from **My Files**; allow **Install unknown apps** for My Files when prompted.
3. Open MapLedger → **Drive** → **START MAPPING SESSION** and grant:
   - **Location → While using the app → Precise ON.** (Approximate location is refused: it is not accurate enough for a mileage log.)
   - **Notifications → Allow** (needed for the tracking notification with Pause/Stop).
4. Stop Samsung from killing the recorder: **Settings › Battery › Background usage limits** → add MapLedger to **Never sleeping apps**, and **Settings › Apps › MapLedger › Battery → Unrestricted**. MapLedger warns on the Drive screen until this is done.
5. Settings → Home base → **Set home to my current location** while standing at home. Until then home is approximate (Manteno village centre, 2.5 km radius) and home-trip detection is unreliable.

The APK is a release build signed with a throwaway debug key so it installs for testing. A build from another machine will have a different key, so uninstall first (back up from Settings → Encrypted backup) before installing it. Before publishing to Google Play, add your own signing config.

### Why no "Allow all the time" location?

Tracking runs only inside a foreground service that you start from the app, with a visible notification. Android treats that as while-in-use access, so recording continues with the screen locked and while Bee Maps or Google Maps is in front — no background-location permission needed, and none is requested. If Android kills the process anyway, the trip is already saved; MapLedger marks it *interrupted*, notifies you, and lets you resume (the downtime is recorded as an unverified gap) or finish it.

## Build from source

Requirements: JDK 17+, Android SDK with platform 36 (Android Studio Ladybug or newer works).

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
# optional: a Google Maps SDK for Android key. Without it, traces draw on an offline canvas.
echo "MAPS_API_KEY=your-key" >> local.properties
./gradlew testDebugUnitTest          # 50 tests
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
```

`local.properties` is git-ignored; never commit API keys.

## Features

| Area | Implemented |
|---|---|
| **Live Drive** | Start / Pause / Resume / Stop (large one-handed buttons, also in the notification); live mileage, duration, speed, GPS quality & accuracy, unverified gap distance, today's payouts, mileage value; map follows the car; start/end odometer + photo; two-pane layout on unfolded Z Fold. |
| **Mileage engine** | Every fix written to Room before the UI updates. Rejects low-accuracy fixes, duplicates and impossible-speed spikes; suppresses stationary jitter; never joins pause segments; GPS gaps (>45 s and >250 m) are recorded as straight-line *unverified* estimates, not added to verified miles. Totals are a deterministic replay, so a restarted service rebuilds exactly what was shown. |
| **Trips** | History with filters (to classify / home review / business / personal); detail with GPS trace, odometer + photos (odometer vs GPS comparison), classification (business / mixed / personal + purpose), home-travel review, audited manual adjustments (reason required), gap list, Bee Maps upload status, audit history, per-trip GPX export, manual trips. |
| **Tax** | IRS-style log: date, times, start/end place, odometer, total/business/personal miles, purpose, vehicle, coordinates, source, confidence, adjustments. Configurable rate table (seeded: 2025 70¢; 2026 72.5¢ to Jun 30, 76¢ from Jul 1). Trips start **unclassified**; home-start/end trips are flagged and excluded until reviewed. Per-vehicle deduction method (standard / actual / undecided) prevents double-counting gas, repairs, tires, insurance and rental under the mileage rate. Missing-evidence list. SE-tax and income-tax estimate. |
| **Exports** | PDF (summary + log), Excel (log, expenses, income sheets), CSV, GPX (segments break at pauses/gaps), full JSON. Examples: [`docs/examples`](docs/examples). |
| **Money** | USD / USDC / HONEY payments with USD fair-market value at receipt (flagged when missing), base vs burst vs bonus, expected vs received; transaction CSV/XLSX import (incl. Solana explorer exports) with tx-hash de-duplication; expenses by category with receipt photos, gallons, odometer, business-use %; weekly / monthly / annual reports with net per hour and per mile. Multiple vehicles. |
| **Bee Maps** | Burst targets (manual, "use my location", CSV or GPX waypoints); automatic *passed* detection from verified GPS (never bridges gaps); **credited only when a received payment is linked**; missing-payment flag after N days; expected vs actual; repeated targets; planned-route GPX import with coverage % and overlap detection; historical road goals. |
| **Historical import** | Google Maps Timeline (on-device `Timeline.json`, Takeout Semantic Location History, `Records.json`), GPX tracks, CSV/XLSX mileage ledgers, payment CSV/XLSX. File-level and row-level duplicate protection. Imports are unclassified, keep their source, and get lower confidence. |
| **Privacy & reliability** | Offline-first; no accounts, analytics or sync. Excluded from Google cloud backup / device transfer. AES-256-GCM encrypted backup & restore (PBKDF2, passphrase never stored). Optional biometric / device-credential lock. Battery-restriction warning, permission status, WorkManager watchdog that flags dead recordings. Optional activity-recognition drive detection that only *prompts* you. |

Seeded Bee Maps road goals (week starting → km): Oct 5 70.7 · Oct 12 75.8 · Oct 19 173.1 · Oct 26 377.0 · Nov 2 1208.7 · Nov 9 1712.0 · Nov 16 8.2 (all 2026). These are recorded road-goal distances, kept apart from GPS mileage and never used in the tax log.

## Not in Phase 1 (honest gaps)

- **Firebase cloud backup** — not included (it needs your own Firebase project and `google-services.json`). Encrypted file backup covers this for now.
- **Bee Maps screenshots** — attached as evidence to a road-goal week; values are typed in. No OCR yet.
- **Bee Maps upload status** — Bee Maps has no public API for this, so it is a manual field per trip.
- **USD value of HONEY** — no price feed (offline-first); enter the fair-market value from your exchange or explorer.
- **Depreciation, QBI, state tax** — not modelled.

## Architecture

```
app/src/main/java/com/mapledger/app/
├─ MapLedgerApp.kt / MainActivity.kt   manual DI container, first-run seeding, biometric gate
├─ tracking/   TrackingService (foreground, type=location), TrackingState, WatchdogWorker, DriveDetectionReceiver
├─ domain/     DistanceEngine, GeoMath, TaxMath, Ledger (tax year, reports), BeeAnalysis, Importers   ← pure Kotlin, unit-tested
├─ io/         Gpx, Timeline, Csv, Xlsx, BackupJson, BackupCrypto                                    ← pure Kotlin, unit-tested
├─ data/db/    Room entities, DAOs, AppDatabase (schema exported to app/schemas)
├─ data/repo/  Trip, Finance, Bee, Settings (DataStore), Transfer (import/export/backup/PDF)
└─ ui/         Compose screens + ViewModels, adaptive NavigationSuiteScaffold (bottom bar / rail)
```

**Database:** schema version 1, exported to `app/schemas/com.mapledger.app.data.db.AppDatabase/1.json`. Destructive migration fallback is deliberately **off**: future schema changes must add an explicit `Migration` to `AppDatabase.MIGRATIONS`, so a mistake fails loudly in testing instead of wiping a tax log.

## Tests

`./gradlew testDebugUnitTest`

- `DistanceEngineTest` — haversine accuracy; 10-mile straight drive; noisy 30-mile home-to-home loop (±1%); stationary jitter; duplicates; GPS spikes; low accuracy; long GPS gaps (not counted, recorded); short dropouts (joined); pause segments; crash replay equals live totals; interruption downtime becomes an unverified gap.
- `LedgerTest` — 2026 rate split on July 1; home travel excluded until reviewed; unclassified never deducted; standard vs actual (no double counting); business-use ratio; crypto FMV; miles basis precedence; SE tax; weekly per-hour/per-mile.
- `ImportersTest`, `FormatsTest`, `BackupJsonTest` — dates, Solana exports, duplicate hashes, ledgers, CSV quoting, GPX round-trip, all three Timeline formats, XLSX write/read, encryption (wrong passphrase and tampering rejected), lossless backup.
- `RepositoryRobolectricTest` — full home-to-home drive in Room → completed trip in history with home flags; recording survives process death; pause accounting; duplicate GPX/payment imports blocked; burst passed ≠ credited; encrypted backup → restore.
- `UiSmokeTest`, `FoldableSmokeTest` — launch `MainActivity` and visit every screen (phone and Z Fold sizes).
- `ExampleExportsTest` — produces `docs/examples` from synthetic data.
