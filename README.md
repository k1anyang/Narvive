# Narvive

> **Narrative + Alive** — a local-first Android reader with BYOK AI built into the reading flow: EPUB / TXT / PDF, plus highlights, translation, rewriting, continuation, character roleplay, relationship graphs and timelines.

**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.03.01-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](LICENSE)
[![CI](https://github.com/k1anyang/Narvive/actions/workflows/build.yml/badge.svg)](https://github.com/k1anyang/Narvive/actions/workflows/build.yml)

| Library | Reader | AI Graphs |
| --- | --- | --- |
| ![Library](docs/screenshots/library.png) | ![Reader](docs/screenshots/reader.png) | ![Relationship graph](docs/screenshots/graph.png) |

> Screenshots are not committed yet — see [`docs/screenshots/README.md`](docs/screenshots/README.md) for the capture list.

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

## Configuration

Preferences are persisted through `NarviveDataStore` (DataStore Preferences); **API keys and the WebDAV password are stored separately via `EncryptedSharedPreferences`.**

Preset AI providers (stored in `ai_providers_json`, never with the key):

| id | Name | Base URL | Default model | Protocol |
| --- | --- | --- | --- | --- |
| `deepseek` | DeepSeek | `https://api.deepseek.com` | `deepseek-chat` | OpenAI Chat |
| `openai` | OpenAI | `https://api.openai.com` | `gpt-4o-mini` | OpenAI Chat |
| `gemini` | Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.0-flash` | OpenAI Chat |

Supported protocols (`AiProtocol`): `OPENAI_CHAT` (OpenAI Chat Completions), `OPENAI_RESPONSES` (OpenAI Responses API), `ANTHROPIC` (Anthropic Messages).

> Preset providers only allow editing the model, the enabled flag and priority (name / URL / protocol are locked). Custom providers are fully editable.

12 prompt templates are editable in **Settings → Prompt**, with placeholders substituted by `PromptRenderer`. API keys are encrypted with `EncryptedSharedPreferences` and are **never written into a backup archive** — a backup ZIP holds books, covers, annotations, bookmarks, collections and some settings, but no credentials.

---

## Interface language

Narvive ships a trilingual interface: **简体中文 (default) / 繁體中文 / English.**

Switch it at **Settings → Language**, a page of its own (one language per row, with a check mark on the active one). The change applies immediately and your navigation state is preserved, so you stay on the settings page. The choice is persisted by AndroidX AppCompat; on Android 13+ it also appears under the system **Settings → Apps → Narvive → Language** entry.

The activity **is** still recreated on a language change — that is standard AppCompat/Android behaviour, and it is what keeps the Android 13+ system app-language integration working. Two measures absorb the visual flicker rather than avoiding the recreation: the window background is set to a colour matching the current theme (both in `themes.xml` and at runtime from the active colour scheme), and the root content runs a single 180 ms alpha fade-in when the composition is entered.

AI replies follow the interface language too — the system prompt, the 12 default templates, greeting and suggestion copy, and the intent-routing patterns are all localised. Prompts you have customised yourself are never overwritten by a language change.

Mechanism, the module-split resource layout, the coding rules and the steps for **adding a new language**: [`docs/i18n.md`](docs/i18n.md).

---

## Privacy

- **Local-first**: no account, no sign-up, no telemetry, no analytics SDK. Your library, notes and reading history stay on the device.
- **BYOK**: the only network egress for AI is your own provider. Requests go from the device straight to the endpoint you configured, never through an intermediary server.
- **Key handling**: API keys are encrypted at rest via `EncryptedSharedPreferences` and excluded from backups.
- Reading, annotating and stats work entirely offline.

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
