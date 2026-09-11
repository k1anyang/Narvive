# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Trilingual interface** — 简体中文 (default), 繁體中文 and English, switchable at **Settings → Language**, a page of its own. Built on AndroidX per-app language (`AppCompatDelegate.setApplicationLocales`); the choice applies immediately, preserves your navigation state, and on Android 13+ also appears under the system **Settings → Apps → Narvive → Language** entry (`res/xml/locales_config.xml`).
- **Traditional Chinese font support** — the app now bundles two HarmonyOS Sans families, SC and TC, and selects the typeface from the interface language (`narviveTypography()` in `ui/theme/Type.kt`). The bundled fonts total roughly 36 MB, which grows the debug APK to about 59 MB.
- **AI output follows the interface language** — system prompt, the 12 default prompt templates, greeting and suggestion copy, and the intent-routing patterns. The intent-routing regexes are Chinese + English unions, so English questions hit the local reading-data injection just as Chinese ones do. Prompts you have customised yourself are never overwritten by a language change.
- Module-split localization resources under `res/values/`, `res/values-en/` and `res/values-b+zh+Hant/` (`strings.xml`, `strings_library.xml`, `strings_library_vm.xml`, `strings_reader.xml`, `strings_viewer.xml`, `strings_notes_stats.xml`, `strings_stats_vm.xml`, `strings_chat.xml`, `strings_chat_vm.xml`, `strings_ai_internal.xml`, `strings_prompt_meta.xml`, `strings_fonts.xml`, `strings_vm_messages.xml`).
- [`docs/i18n.md`](docs/i18n.md) — the language mechanism, resource layout, coding rules, and a checklist for adding a new language.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — development environment, architecture conventions, localization rules and pre-PR checks.
- [`README.zh-CN.md`](README.zh-CN.md) and [`README.zh-TW.md`](README.zh-TW.md); `README.md` is now the English-primary entry point.
- **Feedback page** — **Settings → Feedback** (`FeedbackScreen`, route `settings/feedback`), listed at the end of the About & privacy group. It offers three channels: email (`k1anyang@163.com`), [GitHub Issues](https://github.com/k1anyang/Narvive/issues/new/choose) and [GitHub Discussions](https://github.com/k1anyang/Narvive/discussions). A diagnostics card previews and copies app version, interface language, device model, Android version and ABI — deliberately excluding book content and API keys.
- **Developer GitHub link** — the 开发者 / Developer card on the About page now lists the contact email **and** a clickable `github.com/k1anyang`.

### Changed

- **Language switch moved to its own page.** The selector is no longer a block inside Appearance: it is now `LanguageScreen` on the `settings/language` route, reachable from a **语言 / Language** row (showing the current language as its description) in the first group of the Settings page. The row order in that group is now **Appearance → Fonts → Language → Reading settings**, and the language list is vertical — one language per row, full width, with a check mark on the active one.
- **Default appearance theme is now `sky` (天际蓝 / Sky Blue)** instead of `paper_ink`. Updated in `AppearanceThemes.DEFAULT_ID`, the `NarviveDataStore` fallback, `SettingsUiState` and `MainActivity`. 暖纸墨 / Warm Paper remains one of the selectable themes, and its description changed to 温暖 · 羊皮纸与赤陶 / "Warm · parchment & terracotta" / 溫暖 · 羊皮紙與赤陶.
- `MainActivity` now extends `AppCompatActivity`, and the app theme parent is `Theme.AppCompat.Light.NoActionBar`. **Light** is used deliberately instead of `DayNight`: dark/light is controlled entirely by the Compose `dark_theme` preference, so window decoration never disagrees with the Compose UI.
- Added the dependency `androidx.appcompat:appcompat:1.7.1`.
- Corrected the documented JDK requirement: running Gradle needs **JDK 21** (`gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`). `JavaVersion.VERSION_17` is only the bytecode target.

### Fixed

- Error messages now retranslate when the interface language changes, instead of staying in the language they were created in.
- **Language switch: the app deliberately relies on the standard Activity recreation.** An attempt to suppress it with `android:configChanges="locale|layoutDirection"` did eliminate the flicker, but it made every cached localised string in a ViewModel go stale silently, requiring a manual refresh hook per screen. Reverted to the official behaviour, which rebuilds the ViewModels and therefore keeps copy correct by construction. See `docs/i18n.md` §2.1 for the full trade-off.
  - **Accepted cost:** one frame flashes on each language switch — black on a light theme, white on a dark one. This is the system drawing an empty window between teardown and redraw; it cannot be covered from app code, and a fade-in over it makes it worse. Do not retry either workaround.
  - **Kept mitigations:** `Theme.Narvive` sets `android:windowBackground` to a colour matching the default theme, `values-night/colors.xml` provides the dark counterpart, and `NarviveTheme` rewrites the window background at runtime from the active colour scheme's `background`. These only help the *cold-start* first frame.
- **Copy fixes.** The English reader HUD now reads **"Prev chapter"** instead of "Previous chapter", and the Notes empty state gained horizontal padding with centred text so the longer English hints no longer hug the screen edges.

## [1.0.0]

Initial release.

### Added

- Multi-format reading: **EPUB** via the Readium Kotlin Toolkit (paged and scroll modes, table of contents, CFI-anchored decorations), **TXT** with automatic encoding detection and regex chaptering, and **PDF** as an early MVP skeleton implementation.
- Reader: reading themes, day/night mode, eye protection, brightness, typography controls, page-flip animations, auto page-turn, volume-key paging, bookmarks, in-book full-text search, and progress anchored to character position.
- Bookshelf: multi-select import with SHA-256 de-duplication, EPUB metadata and cover extraction, grid and list views, sorting, search and collections.
- Annotations: multi-colour highlights, notes, cached translations, and a notes centre that aggregates six annotation types with filtering, search and Markdown export.
- **BYOK AI**: provider presets (DeepSeek / OpenAI / Gemini) plus custom providers over OpenAI Chat Completions, OpenAI Responses and Anthropic Messages, a provider fallback chain, 12 editable prompt templates, an AI preference profile, character roleplay with AI-extracted character cards, rewrite and continue, and relationship graph / timeline rendering on Canvas.
- Global AI chat with cross-book context selection and local reading-data intent routing.
- Reading statistics: daily, weekly and monthly charts, streaks, and finished-book tracking.
- Local-first storage: Room database plus ZIP backup/restore de-duplicated by hash, and WebDAV cloud sync against your own server.
- Encrypted storage of API keys and the WebDAV password via `EncryptedSharedPreferences`; credentials are never included in backup archives.
- Downloadable reader fonts from a remote catalogue.

[Unreleased]: https://github.com/k1anyang/Narvive/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/k1anyang/Narvive/releases/tag/v1.0.0
