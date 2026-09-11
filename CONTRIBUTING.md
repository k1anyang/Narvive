# Contributing to Narvive

Thanks for your interest in improving Narvive. This guide covers the development environment, the conventions this project follows, and what to check before opening a pull request.

Questions are welcome — reach the maintainer at **k1anyang@163.com**.

---

## 1. Development environment

| Tool | Requirement |
| --- | --- |
| JDK | **21** — required to run Gradle (`gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`) |
| Android SDK | `compileSdk 36`, `targetSdk 35`, `minSdk 26` |
| Gradle | 9.5.0 — use the committed wrapper, no manual install |
| Android Studio | Recent stable release with AGP 9.3.0 / Kotlin 2.3.20 support |

> `JavaVersion.VERSION_17` in `app/build.gradle.kts` is the **bytecode target**, not the JDK used to run the build. Starting Gradle with JDK 17 fails.

Point the build at your SDK by creating `local.properties` in the project root (Android Studio generates it for you):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

---

## 2. Getting started

1. Fork the repository and clone your fork.
2. Create a branch off `main`:
   - `feature/<short-description>` for new functionality
   - `fix/<short-description>` for bug fixes
3. Make your change, keeping it as focused as possible — one concern per pull request.
4. Verify locally (§6), then open a pull request against `main`.

Write commit messages that say **what changed and why**, in clear English or Chinese:

```
fix: keep reader progress stable when line height changes

The character-weighted progress was recomputed from the paginated
position, so changing typography shifted the reported percentage.
Anchor it to the CFE/character offset instead.
```

---

## 3. Code style and architecture

- **Kotlin official code style** (`kotlin.code.style=official` in `gradle.properties`). Match the naming, layering and formatting of the surrounding code.
- **Screen state**: use `@HiltViewModel` with a **private** `MutableStateFlow` and a **public** `StateFlow`. Do not expose mutable state.
- **Data access**: go through the interfaces in `domain/repository`; implementations live in `data/repository`.
- **Persistence**: cross-screen preferences belong in DataStore or Room; sensitive values (API keys, passwords) go through `data/keystore`.
- **Design system**: use the tokens in `ui/theme` (colour, typography, spacing, shape, elevation, motion) rather than hardcoded values. `docs/DESIGN.md` is the single source of truth for visual work, and UI changes must follow it.
- **Comments**: the codebase is commented in Chinese. Keep comments accurate; update them when you change the code they describe.

### Database changes

Schema changes must follow the existing migration pattern:

1. Increment the database version in `NarviveDatabase`.
2. Add a `MIGRATION_<n>_<n+1>` object that performs the change.
3. **Register the migration** in `NarviveDatabase` — an unregistered migration fails at runtime.

---

## 4. Localization

Narvive ships a trilingual interface (简体中文 / 繁體中文 / English). Every user-visible string must be localizable. Full mechanism and design notes: **[docs/i18n.md](docs/i18n.md)**.

### The rules

**Every user-visible string goes through `strings.xml`.** Never hardcode Chinese (or any language) in Kotlin. Even a one-word label.

```kotlin
// ✗ wrong
Text("删除")

// ✓ right
Text(stringResource(R.string.notes_delete))
```

**String keys are `<module>_<meaning>`**, lower case with underscores, and the prefix must match the resource file they live in (`strings_reader.xml` → `reader_*`). This keeps keys from colliding across modules and makes `grep` locate a string's owner quickly.

**Parameterized strings use positional placeholders in a single resource.** Never concatenate sentence fragments — word order differs between languages, so each locale must be free to arrange the whole sentence.

```xml
<string name="library_selected_count">已选 %1$d 本</string>
<string name="library_selected_count">%1$d selected</string>
```

```kotlin
// ✗ wrong: English word order cannot be expressed
Text(stringResource(R.string.prefix) + count + stringResource(R.string.suffix))

// ✓ right: one resource per sentence
Text(stringResource(R.string.library_selected_count, count))
```

**Escape apostrophes in XML as `\'`.** An unescaped apostrophe in an English string makes **aapt2 fail the build** with `unescaped apostrophe`. Also escape `&` as `&amp;` and `<` as `&lt;`. Chinese corner brackets `「」` and curly quotes `“”` need no escaping.

**`stringResource` may only be called in `@Composable` scope.** Do not call it inside `remember { }`, `derivedStateOf { }`, `LaunchedEffect { }` or event callbacks such as `onClick = { }` — those are ordinary lambdas, not composables. Resolve the text in composable scope first, then hand the resolved `String` to the effect or callback. Outside Compose, use `context.getString(...)`.

**A private helper that returns localized text must be marked `@Composable`** — for example `formatSize(...)`, `relativeTime(...)`, `typeLabel(...)`. This is an allowed and necessary change, provided every call site sits in composable scope.

**Use `UiMessage` (or `@StringRes Int`) whenever localizable text is cached outside a composable.** This project suppresses the standard Activity recreation on a language change (`android:configChanges="locale|layoutDirection"`), which removes a one-frame flicker but means **ViewModels survive the switch**. A plain `String` resolved with `getString(...)` at assignment time will therefore stay in the old language, silently.

The rule, in one question: **can this value still be on screen after a language change?**

- **Yes** → it must be `@StringRes Int` (plain labels) or `UiMessage` (`Res(id, args)` resolved at render time; `Raw(text)` for data, server text or AI output). See `ui/message/UiMessage.kt` and `docs/i18n.md` §2.1.
- **No** — a dialog or snackbar dismissed within seconds → a `String` is acceptable, because the stale window is negligible. `ChatViewModel.error`, `GlobalChatViewModel.error`, `RoleplayViewModel.error`, `CharacterCardViewModel.error` and the library import errors are deliberate examples. **Do not use them as a precedent for new code.**

Two implementation gotchas worth knowing before you hit them:

- `UiMessage.text()` is `@Composable` and **cannot** be called inside a `remember { }` lambda. Resolve it in composable scope first, then hand the `String` to `remember`.
- When you must write **plain text** into the database or into an AI prompt (a session's chapter title, a character card's knowledge boundary), call `UiMessage.resolve(context)` at the moment you write it.

**`UiMessage` alone is not enough — some values also need recomputing.** `UiMessage` only guarantees that a value is *resolved* at render time; it does not make the value itself change. Anything that is **picked from a pool**, **aggregated into a display object**, or **built through a language-dependent fallback** must additionally re-derive on a language change:

```kotlin
observeLanguageChanges(localeProvider) { refreshGreeting(...) }   // one line, in the ViewModel
```

`observeLanguageChanges` lives in `service/ai/PromptDefaultsI18n.kt`. Call it from the ViewModel's `init` — **never** add a per-screen refresh hook in a composable. `docs/i18n.md` §2.1 lists which cases need it and which do not.

Do **not** re-introduce the Activity recreation to dodge this rule: that trades a silent staleness bug for a flicker the user sees on every switch. Do **not** add per-screen "refresh on language change" hooks either — with the rule above there is nothing to refresh by hand.

**Never branch logic on display text.** Do not write `if (message.contains("成功"))` — it breaks the moment the string is translated. Carry a structured flag (for example `TestResult(ok: Boolean, message: String)`) instead.

### When you add a string

Add it to **all three locale folders**, keeping the same key and the same placeholder order:

```
app/src/main/res/values/strings_<module>.xml              ← 简体中文 (default)
app/src/main/res/values-en/strings_<module>.xml           ← English
app/src/main/res/values-b+zh+Hant/strings_<module>.xml    ← 繁體中文
```

If you only add it to the default folder, the other two locales silently fall back to Simplified Chinese.

### Interface language and AI

- Language is switched with AndroidX per-app language; the preference is persisted by AppCompat and deliberately **not** duplicated into `NarviveDataStore`. The selector is its own page — `LanguageScreen` on the `settings/language` route, reached from the **Language** row on the Settings page — so do not add language UI back into `AppearanceScreen`.
- AI output language follows the interface language via `AiText` / `PromptDefaultsI18n` / `PromptLocaleProvider`.
- **User-customised prompts are user data and must never be overwritten by a language change** (`PromptService.get()` resolves user value over localised default).
- The intent-routing regexes are Chinese + English **unions**. Only add branches — deleting the Chinese keywords silently breaks the feature for Chinese users.

---

## 5. Adding a new language

See [docs/i18n.md](docs/i18n.md) §8 for the full checklist. In short: copy the `values-en/` folder to `values-<tag>/`, translate the `<string>` contents without touching `name` attributes or placeholder indexes, add the locale to `res/xml/locales_config.xml`, and extend the language enum and the AI copy tables.

---

## 6. Before opening a pull request

Both of these must pass:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

`compileDebugKotlin` catches XML escaping errors, `stringResource` scope errors and missing keys. `assembleDebug` additionally catches resource merge conflicts, duplicate keys, and `localeConfig` / font packaging problems.

Unit tests live in `app/src/test`; instrumented tests in `app/src/androidTest`. If you change core logic — AI response parsing, chapter chunking, progress calculation, backup merge — add or update unit tests.

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### Pull request checklist

- [ ] The change is focused and described (what changed, why, impact).
- [ ] `compileDebugKotlin` and `assembleDebug` pass.
- [ ] New or changed user-visible text exists in all three locale folders.
- [ ] No hardcoded Chinese added in Kotlin.
- [ ] No logic branches on display text.
- [ ] Reader, AI and UI behaviour verified on a physical device where relevant (screenshots or a short description help reviewers a lot).
- [ ] Database changes are accompanied by a registered migration.

---

## 7. Licence

By contributing, you agree that your contributions are licensed under the **Apache License 2.0**, the licence of this project.
