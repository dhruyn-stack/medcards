# MedCards — MBBS flashcards for Android

Native Kotlin / Jetpack Compose app with FSRS-5 spaced repetition, cloze deletions, images, subject + topic filing, and **integrated cross-subject review**.

No Mac, no Android Studio, no signing certificates. GitHub Actions builds the APK; you tap it on your phone and it installs.

---

## The integrated review idea

Every card has a **Subject** (Anatomy, Physiology, Pathology…) and a **Topic** (e.g. `Diabetes Mellitus`).

When two or more subjects share the same topic name, that topic appears in the **Integrate** tab. Tapping it builds a session that pulls due cards from *every* subject covering that concept and interleaves them in curriculum order — Anatomy → Physiology → Biochemistry → Pathology → Pharmacology → Medicine.

Sessions are capped (default 15 cards) so it stays one focused sitting, not a mass grind. Tap **Select** to review 2–3 topics together.

Consistent topic names are the whole trick — the editor gives you a dropdown of existing topics, and **Settings → Topics** lets you rename or merge if you've spelt one two ways.

---

## Getting the APK

1. Create a **GitHub account** and a new repository (e.g. `medcards`).
2. Upload every file in this folder, keeping the structure:
   ```
   settings.gradle.kts
   build.gradle.kts
   gradle.properties
   app/build.gradle.kts
   app/src/main/AndroidManifest.xml
   app/src/main/java/com/medcards/*.kt
   app/src/main/res/...
   .github/workflows/build-apk.yml
   ```
   In the browser: repo → **Add file → Upload files** → drag the whole folder in.
   *(If the uploader drops the hidden `.github` folder, create it by hand: **Add file → Create new file**, name it `.github/workflows/build-apk.yml`, paste the contents.)*
3. **Actions** tab → **Build APK** → **Run workflow**.
4. Wait ~4 minutes. Open the finished run → **Artifacts** → download `MedCards-apk.zip`.
5. Unzip → `MedCards.apk`.

## Installing on your phone

1. Move `MedCards.apk` to the phone (Google Drive, WhatsApp to yourself, USB cable, whatever).
2. Tap it in Files/Downloads.
3. Android will ask to allow installs from that app — **Settings → Install unknown apps → allow** for your browser or file manager, then tap the APK again.
4. Done. It's a normal app: Home Screen icon, opens offline, never expires.

Updating later: push a change, download the new APK, install over the top. Your cards survive because the app data isn't touched — but export a CSV backup first if you're nervous.

---

## Using it

**Study** — everything due, or drill one subject. Blue badge = due reviews, green = new cards.

**Integrate** — cross-subject topic sessions (see above).

**Browse** — search, filter by subject chip or topic menu, tap a card to edit.

**Stats** — streak, retention, daily review bars, 14-day forecast of upcoming load, mature-card count per subject.

**Settings** — retention target, session sizes, topic rename/merge, CSV import & export.

### Writing cards

- Plain Q/A: question in Front, answer in Back.
- **Cloze**: `Metformin activates {{c1::AMPK}}, reducing {{c2::hepatic gluconeogenesis}}.`
  That one note becomes two cards, each hiding one blank. Hints: `{{c1::AMPK::an enzyme}}`.
- **Images**: attach diagrams, histology, X-rays from your gallery (auto-resized to 1400 px).
- "Keep adding after save" lets you type a run of cards without leaving the editor.

### CSV import

Columns: `front, back, subject, topic, tags` (tags separated by `;`). Header row required, order doesn't matter. Cloze markup in `front` is expanded on import. `template.csv` here is a working example.

Export writes a CSV wherever you choose — that's your backup.

---

## Scheduling (FSRS-5)

Four buttons — Again / Hard / Good / Easy — each showing the interval it will give you. The scheduler tracks per-card *stability* (how long the memory lasts) and *difficulty*, and picks the interval where your predicted recall equals your retention target.

Sanity check of the maths as implemented: a new card answered Good schedules at 3 days, then 11, 18, 24, 31, 37 as you keep hitting Good. A lapse from a 30-day interval drops it to about 4 days.

Default target is 90%. Raising it means shorter intervals and more daily reviews; lowering it means fewer reviews and more forgetting. 85–92% is the sensible band.

---

## Editing the app later

| File | What it does |
|---|---|
| `Models.kt` | Card, cloze parser, subject list |
| `Fsrs.kt` | The scheduler |
| `Store.kt` | Persistence + queue building (integrated queue logic lives here) |
| `CsvIo.kt` | Import/export |
| `MainActivity.kt` | Theme, tab shell, shared widgets |
| `HomeScreen.kt` | Study tab |
| `IntegrateScreen.kt` | Integrate tab |
| `StudyScreen.kt` | The review screen |
| `BrowseScreen.kt` / `EditorScreen.kt` | Card management |
| `StatsScreen.kt` / `SettingsScreen.kt` | Stats and settings |

To add subjects, edit `Mbbs.subjects` in `Models.kt` — its order also controls the interleaving order in integrated sessions.

Data lives in the app's private folder (`library.json` + `images/`). Uninstalling the app deletes it, so export a CSV now and then.

Want to open it in Android Studio instead? File → Open this folder; it generates the Gradle wrapper itself and you can build straight to a connected phone.
