# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Chapter retrieval pipeline (`service/ai/retrieval/`)** — the long-chapter AI paths no longer rely on a single "let the model pick 2 chunks" call. Chunking is now newline-independent (paragraph → sentence → hard cut, with sentence-aligned overlap), sizes come from a **token budget** instead of the fixed 8,000 characters, the overview carries locally extracted cues (characters present, time markers), and the number of injected chunks adapts to the budget instead of being hard-coded to 2 (Q&A) / 3 (charts). All local work runs on `Dispatchers.Default`, is cancellable, keeps chunks as `(start, end)` ranges rather than text copies, and caches one index per chapter (LRU 2) keyed by content hash.
- **Coverage-style local compression** — summarising, character graphs and timelines need *coverage*, not similarity: a top-k pick reads 3 of 25 chunks and then answers as if it had read the chapter. Those paths now compress the whole chapter locally to the budget (every chunk keeps at least one sentence, gaps marked with `……`) and still cost **one** call, instead of N+1 calls for a per-chunk map-reduce.
- **Zero-token lexical layer** — a single-pass, query-driven BM25-lite score over the chunk ranges. It costs no tokens, needs no persistent index, and is used for two things: a conservative fast path (skip the model reranker when the question contains a word unique to the text, e.g. a character name) and a fallback when the reranker call fails.
- **Three-state chunk selection** — `ChunkPick` now distinguishes *overview is enough* / *picked these* / *call failed*, and parsing only accepts a bare number list. Retrieval failures degrade to local keyword matches and say so in the prompt, instead of being presented to the model as "the user's question is broad".
- **Retrieval progress and degradation are now visible in the UI** — the wait before the first token shows "Finding the relevant passages…" / "Condensing this chapter…" instead of a generic "thinking", and when automatic passage selection fails, a dismissible banner above the message list says so (the answer itself is also told to be honest about the gap).
- **Per-provider context size** — the AI settings sheet gained a context-window field (in K tokens, blank = follow the preset), defaulting to 64K for DeepSeek, 128K for OpenAI and 1M for Gemini. It decides whether a long chapter is sent whole or retrieved first, so a small-context model degrades gracefully instead of erroring out.
- **Local time budget for low-end devices** — index building, sentence scoring and lexical matching run under a 400 ms soft deadline. Past it the work degrades instead of stalling: character-name extraction is dropped (cues only), coverage compression falls back to "first sentence of every chunk" (coverage guarantee preserved), and the lexical scan yields to plain model reranking.
- **Whole-book retrieval over chapter summaries** — `chapter_summary_cache`, previously written by nothing, is now fed incrementally: after a long-chapter answer the app caches a one-call summary of that chapter (skipped when already cached, and never blocking the answer), and "whole book" questions run the same two-stage retrieval over those summaries. Nothing is pre-generated, so a book never costs hundreds of calls up front; if no summary exists yet the previous behaviour (bibliographic metadata + table of contents, and told to the model as such) is unchanged. Character graph and timeline in whole-book scope use the same cache, sampled evenly across the book so coverage spans all of it.
- `RetrievalBenchmarkTest` — a reproducible JVM benchmark that prints the real cost of the local work so the low-end budget can be checked rather than assumed: a 200k-character chapter indexes in ~41 ms, matches lexically in ~38 ms and compresses in ~50 ms; a 500k-character chapter in ~55 / ~14 / ~70 ms.
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
- **Language-switch flicker removed by suppressing the Activity recreation.** Letting AppCompat recreate the activity exposed a frame of the system's default window colour between teardown and redraw (black on a light theme, white on a dark one), and that frame cannot be covered from app code — matching `windowBackground` only helps the cold-start frame, and a fade-in made it worse. `MainActivity` now declares `android:configChanges="locale|layoutDirection"` and handles `onConfigurationChanged` itself, so the window is never torn down. `attachBaseContext` still applies the persisted per-app locale so the first frame of any creation is already correct. The Android 13+ system app-language entry is unaffected (`res/xml/locales_config.xml`).
  - **Consequence, and the rule that follows:** ViewModels now survive a language change, so localised text cached in state would go stale. Every value that can outlive a switch was migrated to `@StringRes Int` or `UiMessage` (resolved at render time): `StatsViewModel.chartLabel` and `FinishedBook.dateLabel`, `GlobalChatViewModel` greeting/suggestions plus a new `useEmoji` flag (the emoji suffix is now appended while rendering), `RoleplayViewModel` chapter/knowledge labels, `CharacterCardViewModel.chapterLabel`, the roleplay session preview, `ReaderViewModel.fatalError`, `RewriteViewModel.error` and `FontSettingsViewModel.error`. Suggestion prompts are now built on tap instead of being baked into the action. Short-lived dialog/snackbar errors are deliberately left as `String`. See `docs/i18n.md` §2.1.
- **Copy fixes.** The English reader HUD now reads **"Prev chapter"** instead of "Previous chapter", and the Notes empty state gained horizontal padding with centred text so the longer English hints no longer hug the screen edges.
- Long chapters now split into ~1,500-token semantic chunks rather than 8,000-character newline-delimited ones; the model sees one overview line per chunk (grouped into parent blocks when a chapter has more than 40 chunks) and only the selected chunks are injected. Chapters at or under 50,000 characters keep the previous full-text path byte for byte, and the fast skip means no index is built for them at all.
- The "is this chapter too long to send whole" decision now uses the configured provider's context size rather than a fixed 128K assumption, so switching to a small-context model automatically switches long chapters to retrieval.
- **Summarise / character graph / timeline now read the whole chapter instead of the "most relevant" three chunks.** Those tasks need coverage, not similarity, so a long chapter is compressed locally to the budget first and still costs one model call (a per-chunk map-reduce would cost N+1 calls for the same coverage).
- `FallbackChain` tracks auxiliary calls (chunk selection, title generation) separately from user-facing ones: a flaky utility call no longer counts towards provider degradation.
- **Chunking collapsed to a single chunk for EPUB chapters.** `EpubReaderController.extractChapterText()` collapses every whitespace run to one space, so the old `split("\n")` chunker produced exactly one chunk — the model's `1~N` choice had a single option and the whole chapter was injected, making the documented 4%–9% compression unreachable. The new chunker aligns on sentence boundaries, so the reader's text extraction (and therefore reading progress, search and character counts) is untouched.
- **Retrieval failure was reported to the model as "the user's question is broad"**, turning a network error into a licence to answer from an 80-character-per-chunk overview. Failures are now distinguished from a genuine "overview is enough" reply.
- **Echoed overviews were parsed as a selection.** `Regex("\d+").findAll(raw).take(2)` picked up the `[1] [2]` markers whenever the model restated the overview, so "retrieval" silently always returned the first two chunks.
- **Intent routing was case-sensitive**, so the app's own English suggestion chips ("Recommend what I should read") never hit the local-data fast path. The patterns are now case-insensitive unions of Chinese and English, and are no longer branched per interface language.
- **"Regenerate" lost the intent context.** `runCompletion()` clears it every round, so regenerating a reading-report question answered without the local reading data; regeneration now resolves the intent again.
- **Fuzzy book-title matching injected unrelated books.** Any two-character window hit was treated as a mention, so ordinary English sentences containing `the`/`he` matched a book title; a mention now requires a long-enough contiguous fragment covering at least half the title.
- Removed dead helpers (`truncate`/`truncatedSuffix`, `graphVagueNote`, `graphPurposeRelationship`, `graphPurposeTimeline`).

### Notes

- `AiText` intent patterns, `IntentTitles` and the whole retrieval module are covered by unit tests (`IntentRoutingTest`, `RetrievalFlowTest`, `TextChunkerTest`), including every failure branch listed above.

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

[Unreleased]: https://github.com/k1anyang/Narvive/compare/v1.0.0-beta.2...HEAD
[1.0.0]: https://github.com/k1anyang/Narvive/releases/tag/v1.0.0-beta.2
