## What does this change?

<!-- A short description of the change and the problem it solves. -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor (no behavior change)
- [ ] Localization / documentation
- [ ] Build / CI

## Affected area

<!-- e.g. reader / library / AI chat / settings / fonts -->

## How was it verified?

<!--
  Be concrete. "Works fine" is not verifiable.
  - Which build command did you run?
  - Which screens and which of the three languages did you walk through?
  - Real device or emulator, and which Android version?
-->

- [ ] `.\gradlew.bat :app:compileDebugKotlin` passes
- [ ] `.\gradlew.bat :app:assembleDebug` passes
- [ ] Unit tests pass (`.\gradlew.bat :app:testDebugUnitTest`)
- [ ] Verified on a real device / emulator (state which)

## Checklist

- [ ] No user-visible string was hardcoded — every new string went into **all three** locale folders (`values/`, `values-en/`, `values-b+zh+Hant/`), see `docs/i18n.md`
- [ ] `stringResource` is only called from `@Composable` scope (not inside `LaunchedEffect` / `remember` / event lambdas)
- [ ] If a Room entity changed, a `MIGRATION_n_n+1` was added and registered in `NarviveDatabase`
- [ ] No API key, keystore or other secret is included in the diff

## Screenshots / recordings

<!-- Required for UI changes. Please include each relevant language if you touched localized layout. -->
