# Narvive

> **Narrative + Alive** — a local-first Android reader with BYOK AI built into the reading flow: EPUB / TXT / PDF, plus highlights, translation, rewriting, continuation, character roleplay, relationship graphs and timelines.

**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.03.01-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](LICENSE)
[![CI](https://github.com/k1anyang/Narvive/actions/workflows/build.yml/badge.svg)](https://github.com/k1anyang/Narvive/actions/workflows/build.yml)

| Library | Reader | Selection actions | AI chat (cross-book) | Notes |
| --- | --- | --- | --- | --- |
| ![Library](docs/screenshots/library.png) | ![Reader](docs/screenshots/reader.png) | ![Selection actions](docs/screenshots/selection.png) | ![AI chat](docs/screenshots/ai_chat.png) | ![Notes](docs/screenshots/notes.png) |

---

## Table of contents

- [About](#about)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Build a release APK](#build-a-release-apk)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Interface language](#interface-language)
- [Privacy](#privacy)
- [Project structure](#project-structure)
- [Key modules](#key-modules)
- [Usage examples](#usage-examples)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

---

## About

Narvive is a single-module Android app (`com.narvive.app`) built around one idea: put **reading** and **AI** in the same place.

- **Multi-format reading** — EPUB rendered through the [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit), TXT with our own chapter splitting and Compose rendering, and PDF as a **skeleton implementation** (early MVP).
- **Local-first** — books, annotations, bookmarks and conversations all live in a local Room database and work offline; ZIP backup plus WebDAV sync are available when you want them.
- **BYOK AI** — no model service is bundled. Bring a key for DeepSeek / OpenAI / Gemini or any OpenAI-compatible or Anthropic-compatible endpoint, and every AI feature unlocks.
- **Trilingual interface** — 简体中文 / 繁體中文 / English, switchable in-app, with matching Simplified and Traditional Chinese typefaces.
- **Privacy-friendly** — API keys are encrypted with `EncryptedSharedPreferences` and are **never written into a backup archive**.

---

## Features

**Multi-format reading** — **EPUB** via the [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) (paged and scroll modes, table of contents, CFI-anchored highlights and translations, progress that does not drift when typography changes); **TXT** with automatic encoding detection (UTF-8 / GBK / GB18030) and regex chaptering; **PDF** as an **early MVP** — `PdfReaderController` is a skeleton implementation, so expect rough edges. Reading themes, day-night mode, eye protection, brightness, typography controls, page-flip animations, auto page-turn, volume-key paging, bookmarks and in-book full-text search.

**Local-first data** — books, annotations, bookmarks, conversations and stats live in a local Room database and work fully offline. ZIP backup / restore (de-duplicated by file hash) and WebDAV sync against your own server.

**BYOK AI** — no model service is bundled; add a key for DeepSeek / OpenAI / Gemini, or any OpenAI-compatible or Anthropic-compatible endpoint, and every AI feature unlocks.

- Highlight, translate, rewrite, continue or ask AI from the reader's selection bar. Translations are cached per book + source text, so a sentence never costs a second call.
- Character roleplay with AI-extracted character cards (identity, personality, tone, knowledge boundary) that never spoil past your reading progress.
- Relationship graphs and timelines rendered from structured JSON on a zoomable, draggable Canvas; a provider fallback chain that auto-demotes after three consecutive failures; 12 editable prompt templates; and an AI preference profile summarised from recent conversations.

**Trilingual interface** — 简体中文 / 繁體中文 / English, switchable at **Settings → Language**, with matching Simplified and Traditional Chinese typefaces. See [`docs/i18n.md`](docs/i18n.md).

**Reading stats** — today / this week / streak / books finished, with day, week and month bar charts reaching back six months.

**In-app feedback** — **Settings → Feedback** offers email, GitHub Issues and GitHub Discussions, and can copy a diagnostics summary (app version, interface language, device model, Android version, ABI) to paste into a report. It deliberately excludes book content and API keys. **Settings → About Narvive** lists both the contact email and a link to the developer's GitHub profile (`github.com/k1anyang`).

---

## Tech Stack

| Area | Technology | Version |
| --- | --- | --- |
| Language | Kotlin | 2.3.20 |
| UI | Jetpack Compose (Material 3) | BOM 2026.03.01 |
| Build | Android Gradle Plugin / Gradle | AGP 9.3.0 / Gradle 9.5.0 |
| DI | Hilt + KSP | 2.60.1 / 2.3.6 |
| Storage | Room + DataStore Preferences | 2.8.4 / 1.1.3 |
| Network | OkHttp + okhttp-sse | 4.12.0 |
| Images | Coil | 2.7.0 |
| Reading engine | Readium Kotlin Toolkit (shared / streamer / navigator) | 3.3.0 |
| EPUB parsing | Jsoup | 1.18.1 |

**Architecture**: MVVM + Repository, single module (`com.narvive.app`), Hilt for DI, Kotlin Flow / StateFlow for reactive state, Room Flow for data observation, kotlinx.serialization for JSON. Compose screens and ViewModels sit on top of `domain` (models + repository interfaces), with `data` (Room, DataStore, Keystore) and `service` (AI, reader, font, backup, WebDAV, storage) underneath.

---

## Requirements

- **Android 8.0 (API 26)** or newer; `compileSdk 36`, `targetSdk 35`.
- **JDK 21** is required to run Gradle — `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`. `JavaVersion.VERSION_17` in `app/build.gradle.kts` is the bytecode target, not the JDK that runs the build.
- The Gradle wrapper (9.5.0) is committed, so no manual Gradle install is needed.
- A physical device is recommended for reader work: EPUB font injection and WebView behaviour differ from emulators.

> The debug APK is ~59 MB, because two full CJK typeface families (HarmonyOS Sans SC and TC, ~36 MB together) are bundled.

---

## Getting Started

```bash
git clone https://github.com/k1anyang/Narvive.git
cd Narvive
```

Point the build at your Android SDK by creating `local.properties` in the project root (Android Studio generates it when you open the project):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Windows (use `./gradlew` instead of `.\gradlew.bat` on macOS / Linux):

```powershell
.\gradlew.bat :app:compileDebugKotlin   # fast compile check
.\gradlew.bat :app:assembleDebug        # build the debug APK
.\gradlew.bat :app:installDebug         # install on a connected device
```

Or open the project in Android Studio and press **Run ▶**. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Build a release APK

```powershell
.\gradlew.bat :app:assembleRelease
```

**There is no release signing configuration in `app/build.gradle.kts`.** `assembleRelease` therefore produces an **unsigned** APK (`app-release-unsigned.apk`) that cannot be installed as-is — sign it yourself before distributing. Release builds enable R8 minification (`isMinifyEnabled = true`); rules live in `app/proguard-rules.pro`.

When a version tag is pushed, CI attaches **two** APKs to the GitHub Release: the unsigned R8-optimised release APK, and a debug-signed APK that installs directly for trying the app out.

---

## Quick start

1. **Import a book** — Library → the import action in the top bar, then pick an EPUB / TXT / PDF file. You can also send a file to Narvive from the system share sheet or an "Open with" intent.
2. **Start reading** — tap a book. Tap the middle of the screen to bring up the HUD, where the table of contents, font size (Aa), brightness and theme live.
3. **Set up AI** — Settings → AI setup → pick a preset provider (DeepSeek / OpenAI / Gemini) or add a custom one → paste your API key → **Test connection** → **Fetch model list** and choose a model.
4. **Use the AI** — in the reader, long-press to select text, then ask AI, translate, highlight, annotate, rewrite, continue or start a roleplay. The bottom **AI** tab chats across books: type `@` to reference up to five books, or tick the whole library.
5. **Review notes and stats** — the **Notes** and **Stats** tabs aggregate annotations and reading data.
6. **Back up** — Settings → Backup to export or restore, and Settings → Services → WebDAV to sync to your own server.

---

## Configuration

Preferences are persisted through `NarviveDataStore` (DataStore Preferences); **API keys and the WebDAV password are stored separately via `EncryptedSharedPreferences`.**

Preset AI providers (stored in `ai_providers_json`, never with the key):

| id | Name | Base URL | Default model | Protocol |
| --- | --- | --- | --- | --- |
| `deepseek` | DeepSeek | `https://api.deepseek.com` | `deepseek-flash` | OpenAI Chat |
| `openai` | OpenAI | `https://api.openai.com` | `gpt-4o-mini` | OpenAI Chat |
| `gemini` | Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.5-flash` | OpenAI Chat |

Supported protocols (`AiProtocol`): `OPENAI_CHAT` (OpenAI Chat Completions), `OPENAI_RESPONSES` (OpenAI Responses API), `ANTHROPIC` (Anthropic Messages).

> Preset providers only allow editing the model, the enabled flag and priority (name / URL / protocol are locked). Custom providers are fully editable.

12 prompt templates are editable in **Settings → Prompt**, with placeholders substituted by `PromptRenderer`. API keys are encrypted with `EncryptedSharedPreferences` and are **never written into a backup archive** — a backup ZIP holds books, covers, annotations, bookmarks, collections and some settings, but no credentials.

---

## Interface language

Narvive ships a trilingual interface: **简体中文 (default) / 繁體中文 / English.**

Switch it at **Settings → Language**, a page of its own (one language per row, with a check mark on the active one). The change applies immediately and your navigation state is preserved, so you stay on the settings page. The choice is persisted by AndroidX AppCompat; on Android 13+ it also appears under the system **Settings → Apps → Narvive → Language** entry.

AI replies follow the interface language too — the system prompt, the 12 default templates, greeting and suggestion copy, and the intent-routing patterns are all localised. Prompts you have customised yourself are never overwritten by a language change.

**No activity recreation, and therefore no flicker.** Letting AppCompat recreate the activity on a locale change exposes a frame of the system's default window colour between teardown and redraw — black on a light theme, white on a dark one — and that frame cannot be covered from app code (matching `windowBackground` or adding a fade-in only affects what is drawn *after* it; the fade made it worse). `MainActivity` therefore declares `android:configChanges="locale|layoutDirection"` and handles `onConfigurationChanged` itself, so the window is never torn down. This does **not** affect the Android 13+ system app-language entry, which is driven by `res/xml/locales_config.xml` independently.

The trade-off is that ViewModels now survive a language change, so **localised text must never be cached as a plain `String`** — anything that can outlive a switch belongs in `@StringRes Int` or `UiMessage` so it resolves at render time. That rule, the places already migrated, and the few short-lived exceptions are documented in [`docs/i18n.md`](docs/i18n.md) §2.1.

Mechanism, the module-split resource layout, the coding rules and the steps for **adding a new language**: [`docs/i18n.md`](docs/i18n.md).

---

## Privacy

- **Local-first**: no account, no sign-up, no telemetry, no analytics SDK. Your library, notes and reading history stay on the device.
- **BYOK**: the only network egress for AI is your own provider. Requests go from the device straight to the endpoint you configured, never through an intermediary server.
- **Key handling**: API keys are encrypted at rest via `EncryptedSharedPreferences` and excluded from backups.
- Reading, annotating and stats work entirely offline.

---

## Project structure

```
Narvive
├── gradle/                         # libs.versions.toml (version catalog) + wrapper (9.5.0)
├── app/
│   ├── build.gradle.kts            # app module build script
│   ├── proguard-rules.pro          # R8 rules
│   └── src/main/
│       ├── AndroidManifest.xml     # permissions / entry / FileProvider / orientation / localeConfig
│       ├── res/
│       │   ├── values/             # resources: default locale (Simplified Chinese)
│       │   ├── values-en/          # resources: English
│       │   ├── values-b+zh+Hant/   # resources: Traditional Chinese
│       │   ├── values-night/       # dark-mode window background for first frame
│       │   ├── font/               # HarmonyOS Sans SC + TC (Regular / Medium / Bold each)
│       │   ├── raw/                # font licence text, font-server keystore
│       │   └── xml/                # locales_config / file_paths / network_security_config
│       └── java/com/narvive/app/
│           ├── MainActivity.kt     # single-Activity entry + external import + WebDAV auto-sync
│           ├── NarviveApp.kt       # Application (Hilt + crash handling + WebView debugging)
│           ├── CrashHandler.kt     # global crash handler
│           ├── core/               # AppLang (shared language identifier enum)
│           ├── di/                 # Hilt modules (database / repository bindings)
│           ├── data/               # datastore / keystore / local (Room entities, DAOs, migrations) / repository
│           ├── domain/             # model (domain models) + repository (interfaces)
│           ├── service/            # ai / reader / font / import / backup / WebDAV / storage / cache
│           └── ui/
│               ├── prefs/          # AppLanguage (interface-language enum and switching)
│               ├── message/        # UiMessage (localisable messages across layers)
│               ├── navigation/     # routes and bottom navigation
│               ├── theme/          # theme (Color / Theme / Type / Shape / Spacing / Motion)
│               ├── components/     # shared components (streaming cursor, swipe rows, loaders)
│               └── screen/         # screens (library / reader / chat / notes / stats / settings / …)
└── docs/                           # DESIGN.md (visual spec) / i18n.md (localization guide)
```

---

## Key modules

### `ui/screen/library` — library

`LibraryViewModel` combines four flows (books, collections, membership, filter preferences) into the sorted, searched and filtered list; `LibraryScreen` provides grid/list switching, collection management, multi-select batch actions and the import confirmation dialog.

### `service/reader` — reading engine

- `ReaderController` — the unifying interface (progress / chapters / locators / paging / search / settings / chapter text).
- `EpubReaderController` — wraps Readium: TOC extraction, character-weighted progress, highlight and translation decorations anchored by CFI, plus a custom in-book search.
- `TxtReaderController` — encoding detection, regex chaptering, character-offset progress, flattened chunked scroll rendering.
- `PdfReaderController` — skeleton implementation built on `androidx.pdf` (MVP).
- `ChapterTextExtractor` / `TocLoader` — fetch chapter text and TOC outside the reader, for rewrite, continuation, AI and the details screen.

### `ui/screen/reader` — reading screen

`ReaderViewModel` is the state hub for the reader: loading the book, composing settings, collecting controller state, restoring progress, and managing selection, highlights, notes, translation, bookmarks, search, auto page-turn and font injection. `ReaderScreen` and the panels under `components/` (Aa panel, brightness, TOC, search, selection bubble, translation card, highlight menu, custom theme) sit on top of it.

### `service/ai` — AI infrastructure

- `AiService` — wraps both non-streaming and SSE streaming requests for the three protocols and turns errors into readable messages (key error / URL error / parameter error).
- `FallbackChain` — provider priority with an automatic demotion chain; three consecutive failures demote a provider.
- `PromptTemplates` / `PromptRenderer` / `PromptService` — prompt definitions, placeholder rendering, and read/write against DataStore.
- `AiText` / `PromptDefaultsI18n` / `PromptLocaleProvider` — supply the system prompt, greetings and suggestions, intent-routing patterns, and the 12 templates' default text in the current interface language.
- `GraphModels` — structured-JSON parsing for relationship graphs and timelines.
- `AiProfileStore` / `AiProfile` — persistence and auto-summarisation of the AI preference profile.
- `CharacterCardExtractor` / `TranslationCache` — character-card extraction and translation-cache lookup.

### `ui/screen/chat` — AI conversation

- `GlobalChatViewModel` + `GlobalChatScreen` — the bottom global AI tab (cross-book context, intent routing, conversation management, auto titles, preference summarisation).
- `ChatViewModel` + `ChatScreen` / `ChatContent` — the in-book AI panel (scope switching, quick commands, graph generation).
- `GraphSheet` — Canvas rendering for relationship graphs and timelines (zoom, drag, highlight, fixed legend).
- `RoleplayViewModel` and its screens — role-play sessions and character cards.
- `CharacterCardScreen` — generate and edit character cards.

### `ui/screen/rewrite` — rewrite and continue

`RewriteViewModel` pulls chapter context through `ChapterTextExtractor` and assembles a context window per mode (1200 characters of preceding text for continuation, 600 on each side for rewriting). Results are stored as `REWRITE` annotations and can be saved as notes or exported as text.

### `ui/screen/notes` / `stats` — notes and statistics

- `NotesViewModel` — aggregates annotations, filters by type and book, searches, deletes and exports Markdown.
- `StatsViewModel` — computes today/weekly duration, streak and books finished from reading sessions, and draws day/week/month bar charts.

### `ui/screen/settings`

- `AiSettingsViewModel` / `AiSettingsScreen` — provider management, connection test, model fetching, priority and demotion recovery.
- `PromptSettingsScreen` / `AiPreferencesScreen` — prompt editing and AI preferences.
- `AppearanceScreen` / `LanguageScreen` / `FeedbackScreen` / `StorageScreen` / `WebDavScreen` / `BackupScreen` / `FontSettingsScreen` / `MoreSettingsScreen`.

### `service/font` — fonts

- `FontCatalogRepository` — fetches the catalogue from a Gitee `fonts.json`, with a local cache as an offline fallback.
- `FontDownloadManager` — OkHttp streaming download with HTTP Range resume and size validation.
- `FontResolver` — maps an id to a file, a CSS `@font-face` injection, or a Compose `Typeface`; EPUB uses a CSS font stack for the CJK/Latin pair while TXT uses a single Typeface.
- `FontFileServer` / `FontStorage` / `FontStatRepair` — the local HTTP server used by the EPUB WebView and font storage helpers.

### Remaining `service` — data and sync

- `BookImportService` — format detection, SHA-256 de-duplication, import, and EPUB metadata/cover extraction.
- `BackupService` — ZIP export and restore (id remapping, hash-based de-duplication, settings snapshot; API keys never enter the archive).
- `WebDavService` — MKCOL / PUT / PROPFIND / GET / DELETE, auto-sync and remote pruning.
- `StorageService` / `AppCacheService` — storage accounting and cache clearing.

---

## Usage examples

### Example 1 — AI translation inside the reader

1. Open an English EPUB and long-press to select a passage.
2. Tap **Translate** in the action bubble.
3. The first call goes to the AI and is stored as a `TRANSLATION` annotation (shown with a red underline). Translating the same sentence again hits the cache, so it costs nothing.

### Example 2 — Relationship graph

1. Open **Ask AI** for a book (from the book details page, or the AI panel inside the reader).
2. Switch the scope to **This chapter** or **Whole book**.
3. Tap the **Relationship graph** quick command. The AI returns structured JSON, which `GraphSheet` parses into a zoomable, draggable Canvas graph with node highlighting.

### Example 3 — Cross-book chat in the bottom AI tab

1. Switch to the **AI** tab at the bottom.
2. Type `@` to open the inline book picker (the keyboard stays up). Tick up to five books, or tick the whole library.
3. Ask something like "summarise the themes these books share". The AI answers from the selected books' metadata (title, author, progress, summary).

### Example 4 — Rewrite and continue

1. In the reader, long-press to select text, then choose **Rewrite** or **Continue**.
2. On the rewrite screen, enter an instruction such as "make it more conversational".
3. The book title, author and chapter name plus the surrounding context are attached automatically (1200 characters of preceding text for continuation, 600 on each side for rewriting). The result can be saved as a note or exported as plain text.

### Example 5 — Backup and restore

1. Settings → Backup → Export produces `NarviveBackup-YYYYMMDD.zip` (books, covers, annotations, bookmarks, collections and some settings).
2. On a new device: Settings → Backup → Import merges by hash. Books with the same title but a different hash are both kept, with a suffix added to the imported title.

### Example 6 — Switching the interface language

1. Settings → Language.
2. Pick **English** or **繁體中文**. Copy updates immediately and you stay on the same page.
3. Go back to the library, reader or AI tab — both the interface and the AI's reply language have switched. On Android 13+ the same choice appears under the system **Settings → Apps → Narvive → Language** entry.

---

## Documentation

- [README.zh-CN.md](README.zh-CN.md) — 简体中文说明 · [README.zh-TW.md](README.zh-TW.md) — 繁體中文說明
- [docs/i18n.md](docs/i18n.md) — interface languages, resource layout, adding a new language
- [docs/DESIGN.md](docs/DESIGN.md) — visual design specification
- [CONTRIBUTING.md](CONTRIBUTING.md) — contribution guide, including the localization rules
- [CHANGELOG.md](CHANGELOG.md) — release history

---

## Contributing

Contributions are welcome. Fork the repository, branch from `main` as `feature/*` or `fix/*`, keep to the project's Kotlin style and architecture conventions, and open a pull request describing what changed, why, and how you verified it.

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Every user-visible string must go through `strings.xml` — never hardcode Chinese in Kotlin, and update all three locale folders when adding strings. Full rules, including the localization section, are in [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

Narvive is released under the **Apache License 2.0**. See [LICENSE](LICENSE).

Bundled third-party components keep their own licences (AndroidX, Jetpack Compose, Hilt, Room, OkHttp, Coil, Readium Kotlin Toolkit, Jsoup and others); in-app, **Settings → About & Privacy → Open source licences** (`LicensesScreen`) lists them.

The bundled **HarmonyOS Sans** typefaces (Simplified and Traditional Chinese) are used under the HarmonyOS Sans Fonts License Agreement; the licence text ships with the app at `app/src/main/res/raw/harmonyos_sans_license.txt`.
