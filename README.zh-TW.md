# Narvive

> **Narrative + Alive** —— 一款以 **Jetpack Compose** 打造的 Android 閱讀器，深度整合 **BYOK（Bring Your Own Key）AI** 能力。
> 支援 EPUB / TXT / PDF 三種格式，圍繞閱讀提供標註、翻譯、改寫、續寫、角色對話、人物關係圖、時間軸演進圖等 AI 輔助功能，並內建書架管理、閱讀統計、雲端備份（WebDAV）與可下載字型。

[English](README.md) | [简体中文](README.zh-CN.md) | **繁體中文**

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.03.01-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](LICENSE)
[![CI](https://github.com/k1anyang/Narvive/actions/workflows/build.yml/badge.svg)](https://github.com/k1anyang/Narvive/actions/workflows/build.yml)

| 書架 | 閱讀器 | 選取操作 | AI 對話（跨書） | 筆記中心 |
| --- | --- | --- | --- | --- |
| ![書架](docs/screenshots/library.png) | ![閱讀器](docs/screenshots/reader.png) | ![選取操作](docs/screenshots/selection.png) | ![AI 對話](docs/screenshots/ai_chat.png) | ![筆記中心](docs/screenshots/notes.png) |

---

## 目錄

- [專案簡介](#專案簡介)
- [核心功能特色](#核心功能特色)
- [技術棧](#技術棧)
- [執行環境需求](#執行環境需求)
- [安裝與部署](#安裝與部署)
- [快速上手](#快速上手)
- [設定項說明](#設定項說明)
- [介面語言](#介面語言)
- [隱私](#隱私)
- [專案目錄結構](#專案目錄結構)
- [主要模組功能介紹](#主要模組功能介紹)
- [使用範例](#使用範例)
- [貢獻指南](#貢獻指南)
- [授權條款](#授權條款)

---

## 專案簡介

Narvive 是一個單模組 Android 應用程式（`com.narvive.app`），目標是把「閱讀」與「AI」深度結合：

- **多格式閱讀**：EPUB 以 [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) 渲染，TXT 為自研章節切分 + Compose 渲染，PDF 為骨架實作（**early MVP**）。
- **本機優先**：書籍、標註、書籤、工作階段等全部存放於本機 Room 資料庫，可離線使用；支援 ZIP 備份與 WebDAV 雲端同步。
- **BYOK AI**：不內建任何模型服務，使用者自備 API Key（DeepSeek / OpenAI / Gemini 或任意 OpenAI 相容 / Anthropic 協定服務），設定後即可啟用全站的 AI 功能。
- **三語介面**：简体中文 / 繁體中文 / English，於設定內即時切換，並搭配簡體與繁體兩套中文字型。
- **隱私友善**：API Key 以 `EncryptedSharedPreferences` 加密儲存，且**絕不寫入備份檔**。

---

## 核心功能特色

### 書架與書籍管理

- 匯入 **EPUB / PDF / TXT**，支援系統檔案選擇器（SAF）與「開啟方式 / 分享」外部匯入。
- 多選匯入 + 匯入預覽：自動辨識格式、檔案大小，以 **SHA-256 去重**，重複檔案給予明確提示。
- EPUB 中繼資料解析：書名、作者、簡介、封面（封面解碼後壓縮為 WebP 節省空間）。
- 格狀 / 清單兩種檢視，可調整欄數；排序（書名 / 作者 / 最近閱讀 / 匯入時間 / 進度）與搜尋。
- **合集（分組）**：建立 / 重新命名 / 刪除合集、批次加入合集、內建「我喜歡」分組、未分組篩選。
- 批次刪除（可同時清理快取）、最近在讀入口。

### 閱讀器

- 統一 `ReaderController` 抽象：EPUB / TXT / PDF 共用同一套 HUD、設定、標註與 AI 互動。
- EPUB：Readium 分頁 / 上下捲動模式、目錄、CFI 定位、進度列以「字元位置」錨定（排版／間距變化不漂移）。
- TXT：自動編碼辨識（UTF-8 / GBK / GB18030）、正規表達式切章、捲動 / 分頁渲染、字元級精準進度。
- 閱讀主題（紙張 / 羊皮紙 / 護眼綠 / 深色 / 黑色 / 自訂）、日／夜間模式、護眼模式、亮度（可跟隨系統）。
- 排版：字級、行距、邊距、對齊、段距、首行縮排；EPUB 可選擇「尊重出版方排版」或由閱讀器全權接管。
- 翻頁動畫（左右 / 上下 / 無）、自動翻頁（掃描線，速度可調）、音量鍵翻頁、螢幕恆亮、休息提醒。
- 中英雙字型獨立設定，字型可線上下載。
- 目錄抽屜、進度列拖曳跳轉、上一章 / 下一章、位置回退、書內全文搜尋（結果可跳轉 + 閃爍定位）。
- 書籤：位置即時比對（同頁／同區塊），附章節名稱與預覽文字。

### 標註、翻譯與筆記

- 選取區操作：**螢光標註**（多色）、**筆記**（自動附帶黃色標註）、**翻譯**（結果快取，紅底線內嵌顯示）、**問 AI**、**改寫 / 續寫**、**角色對話**。
- 翻譯快取：同一「書 + 原文 + 目標語言」只呼叫一次 AI。
- 筆記中心：彙整螢光標註 / 筆記 / 翻譯 / AI 儲存 / 角色對話 / 改寫六類標註，支援依類型、依書篩選、搜尋、刪除，匯出單筆或全部為 Markdown。

### AI 能力（BYOK）

- **底部 AI 全域對話**（不綁定書籍）：
  - 跨書 `@` 選書（最多 5 本）或「全部書庫」彙整上下文（僅中繼資料、無正文）。
  - 個人化問候 + 隨機建議卡片（可依 AI 偏好風格渲染）。
  - 意圖路由：統計 / 推薦 / 指定書名（`《》` 或全名）/ 進度 / 模糊書名，命中後注入本機閱讀資料。英文介面下意圖路由為**中英聯集**比對，英文提問同樣命中。
  - 工作階段重新命名 / 置頂 / 刪除，首輪完成後自動產生 ≤12 字標題。
- **書籍內「問 AI」**：上下文三檔可切換（**選取區 / 本章 / 全書**），本章過長自動分塊（≤5 萬字全量，超長分 8000 字塊，AI 選最相關 2 塊）。
- **快捷指令**：解釋 / 翻譯 / 總結本章 / 詞彙 / 人物關係圖 / 時間軸演進圖（圖表為結構化 JSON + Canvas 繪製，支援縮放、拖曳、節點高亮）。
- **角色扮演**：角色卡 AI 擷取（身分 / 性格 / 語氣 / 知識邊界），一本書多個工作階段，對話片段可儲存為筆記，不劇透已讀進度。
- **改寫 / 續寫**：獨立頁面，自動取章節上下文（續寫取上文 1200 字，改寫取前後各 600 字），結果落庫可匯出。
- **Provider 降級鏈（FallbackChain）**：依優先順序逐個嘗試，連續失敗 3 次自動降級，支援手動恢復。
- **提示詞可設定**：12 套提示詞範本均可在設定中編輯與重設。**AI 輸出語言跟隨介面語言**：系統提示詞、12 套預設範本、問候語與建議卡、意圖路由均依當前介面語言取值；使用者自行修改過的提示詞不會被語言切換覆蓋。
- **AI 偏好**：自動歸納最近對話產生偏好檔案（簡潔 / 正式 / 友善、是否使用表情符號、常用術語），或手動填寫。

### 介面語言

- 支援 **简体中文（預設）/ 繁體中文 / English**，於**設定 → 語言**切換（獨立頁面，每個語言一列，目前語言帶勾選標記），即時生效；切換後**停留在目前頁面**，導覽狀態自動恢復。
- 以 AndroidX 官方 per-app language（`AppCompatDelegate.setApplicationLocales`）實作，語言偏好由 AppCompat 持久化；Android 13+ 會同步至系統的「設定 → 應用程式 → Narvive → 語言」。
- 中文字形依語言分派**兩套 HarmonyOS Sans 字族**（SC / TC），繁體介面使用繁體字形。
- 機制細節、資源組織方式與**新增一門語言的步驟**見 [docs/i18n.md](docs/i18n.md)。

### 統計

- 今日 / 本週 / 連續閱讀天數 / 累計讀完；日 / 週 / 月三種長條圖（可前後翻看，回溯 6 個月）；已讀完書籍清單。

### 設定與資料

- **AI 設定**：管理 Provider（預設 + 自訂）、測試連線、依協定取得模型清單、優先順序調整、降級恢復。
- **字型**：自 Gitee 目錄線上下載字型（斷點續傳、大小校驗），中英字型獨立套用（EPUB 走 CSS 字型堆疊，TXT 走 Compose Typeface）。
- **備份**：匯出 / 還原 ZIP（含書籍檔案、封面、標註、書籤、合集與部分設定；**不含 API Key**），依 hash 去重合併。
- **WebDAV**：雲端備份上傳 / 清單 / 還原，自動同步（切到背景時），密碼加密儲存，僅保留最近 5 個備份。
- **儲存空間**：書籍 / 封面 / 字型 / 快取四類占用統計與快取清理。
- **語言**：設定首頁「外觀」群組內的獨立入口，進入後選擇 简体中文 / 繁體中文 / English。
- **問題回饋**：設定首頁「關於與隱私」群組末端的獨立入口，提供電子郵件、GitHub Issue、GitHub Discussions 三個管道，並可一鍵複製診斷資訊（應用程式版本 / 介面語言 / 裝置型號 / Android 版本 / ABI；**不含書籍內容與 API Key**）。「關於 Narvive」頁的開發者卡片同時提供電子郵件與 GitHub 首頁（`github.com/k1anyang`）連結。

---

## 技術棧

| 分類 | 技術 | 版本 |
| --- | --- | --- |
| 語言 | Kotlin | 2.3.20 |
| UI | Jetpack Compose（Material 3） | BOM 2026.03.01 |
| 建置 | Android Gradle Plugin / Gradle | AGP 9.3.0 / Gradle 9.5.0 |
| 架構元件 | Navigation Compose / Lifecycle / ViewModel | 2.9.0 / 2.8.7 |
| 依賴注入 | Hilt + KSP | 2.60.1 / 2.3.6 |
| 本機資料庫 | Room | 2.8.4 |
| 偏好儲存 | DataStore Preferences | 1.1.3 |
| 安全儲存 | Security Crypto（EncryptedSharedPreferences） | 1.1.0 |
| 序列化 | kotlinx.serialization | 1.9.0 |
| 網路 | OkHttp + okhttp-sse | 4.12.0 |
| 圖片 | Coil | 2.7.0 |
| 閱讀引擎 | Readium Kotlin Toolkit（shared/streamer/navigator） | 3.3.0 |
| EPUB 解析 | Jsoup | 1.18.1 |
| 多語言 | AppCompat（per-app language） | 1.7.1 |

**架構模式**：MVVM + Repository + 單模組，採用 Hilt 依賴注入、Kotlin Flow / StateFlow 響應式狀態管理、Room Flow 資料觀察、kotlinx.serialization 做 JSON 編解碼。

```
UI (Compose Screen + ViewModel)
        │ 依賴
Domain (Model + Repository 介面)
        ▲
Data (Room DAO/Entity + Repository 實作 + DataStore + Keystore)  ←→  Service (AI / Reader / Font / Backup / WebDAV / Storage)
```

---

## 執行環境需求

- Android **8.0（API 26）** 以上裝置。
- **JDK 21** 為執行 Gradle 所必需（`gradle/gradle-daemon-jvm.properties` 固定 `toolchainVersion=21`）。`app/build.gradle.kts` 中的 `JavaVersion.VERSION_17` 指的是**位元碼目標版本**，與執行建置所需的 JDK 不是同一件事；以 JDK 17 啟動 Gradle 會直接失敗。
- `compileSdk 36`，`targetSdk 35`，`minSdk 26`。
- Gradle 9.5.0（使用專案自帶 wrapper，無需手動安裝）。
- 建議以實機測試閱讀器與 AI 功能（模擬器亦可，但 EPUB 字型注入、WebView 等效果以實機為準）。
- Debug APK 體積約 **59 MB**，因為打包了簡體（SC）與繁體（TC）兩套完整中文字族，合計約 36 MB。

---

## 安裝與部署

### 1. 取得原始碼

```bash
git clone https://github.com/k1anyang/Narvive.git Narvive
cd Narvive
```

### 2. 設定本機環境

確認 `local.properties` 指向你的 Android SDK（若不存在，以 Android Studio 開啟專案時會自動產生）：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

### 3. 編譯與安裝 Debug 套件

Windows：

```powershell
.\gradlew.bat :app:compileDebugKotlin   # 編譯 Kotlin（快速校驗）
.\gradlew.bat :app:assembleDebug        # 建置 Debug APK
.\gradlew.bat :app:installDebug         # 安裝到已連線裝置
```

macOS / Linux：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

或直接以 Android Studio 開啟專案，點擊 **Run ▶** 選擇目標裝置執行。

產物路徑：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`

---

## 快速上手

1. **匯入書籍**：開啟 App → 書架頁 → 右上角匯入 → 選擇 EPUB / TXT / PDF 檔案（或從系統「開啟方式 / 分享」直接傳送到 Narvive）。
2. **開始閱讀**：點擊書籍進入閱讀器，點螢幕中央喚出 HUD，可調整目錄 / 字級（Aa）/ 亮度 / 主題。
3. **設定 AI**：設定 → AI 設定 → 選擇預設 Provider（DeepSeek / OpenAI / Gemini）或新增自訂 Provider → 填入 API Key → 「測試連線」→「取得模型清單」選擇模型。
4. **使用 AI**：
   - 閱讀器內長按選取文字 → 問 AI / 翻譯 / 螢光標註 / 筆記 / 改寫 / 續寫 / 角色對話。
   - 底部 AI 分頁直接對話，輸入 `@` 引用多本書或勾選「全部書庫」。
5. **查看筆記 / 統計**：底部「筆記」「統計」分頁查看彙整標註與閱讀資料。
6. **備份**：設定 → 備份，匯出 / 還原；設定 → 服務 → WebDAV 設定雲端同步。
7. **切換語言**：設定 → 語言，選擇 简体中文 / 繁體中文 / English。

---

## 設定項說明

所有偏好透過 `NarviveDataStore`（DataStore Preferences）持久化；API Key 與 WebDAV 密碼透過 `ApiKeyStore` / `WebDavStore`（EncryptedSharedPreferences）加密儲存。

### AI Provider 設定

儲存於 `ai_providers_json`（不含 Key），預設 Provider：

| id | 名稱 | Base URL | 預設模型 | 協定 |
| --- | --- | --- | --- | --- |
| `deepseek` | DeepSeek | `https://api.deepseek.com` | `deepseek-flash` | OpenAI Chat |
| `openai` | OpenAI | `https://api.openai.com` | `gpt-4o-mini` | OpenAI Chat |
| `gemini` | Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.5-flash` | OpenAI Chat |

支援協定（`AiProtocol`）：

- `OPENAI_CHAT` —— OpenAI Chat Completions
- `OPENAI_RESPONSES` —— OpenAI Responses API
- `ANTHROPIC` —— Anthropic Messages

> 預設 Provider 僅允許修改模型、開關與優先順序（名稱 / URL / 協定鎖定）；自訂 Provider 全欄位可改。

### 提示詞範本（PROMPT 設定）

12 套可編輯範本，預留位置由 `PromptRenderer` 取代：系統提示、角色扮演、改寫、續寫、翻譯、角色卡、四個快捷指令、人物關係圖、時間軸。

### AI 偏好

- `ai_pref_enabled`：是否啟用偏好客製化。
- `ai_pref_mode`：`auto`（自動歸納）/ `manual`（手動填寫）。
- `ai_pref_profile_json`：偏好檔案（summary / style / useEmoji / terms）。

### 閱讀設定（關鍵項）

| 鍵 | 含義 | 預設值 |
| --- | --- | --- |
| `reading_theme` | 閱讀主題 | `paper` |
| `font_size` / `line_height` | 字級 / 行距（書籍層級覆寫） | 17 / 1.4 |
| `font_family_cjk` / `font_family_latin` | 中 / 英字型 id | `system` |
| `alignment` | 對齊 | `justify` |
| `publisher_styles` | EPUB 是否尊重出版方排版 | `true` |
| `page_flip_animation` | 翻頁動畫 | `slide` |
| `eye_protection` | 護眼模式 | `false` |
| `progress_display_mode` | 進度顯示 | `percentage` |
| `dark_theme` | 外觀主題 | `system` |
| `library_view` / `library_sort` | 書架檢視 / 排序 | `grid` / `LAST_READ` |

完整鍵定義見 `NarviveDataStore.kt` 的 `companion object`。

> **介面語言不在 DataStore 中**：它由 AndroidX AppCompat 自行持久化（Android 13+ 走系統 `LocaleManager`，更低版本走 AppCompat 的 `SharedPreferences`），以避免雙來源衝突。

---

## 介面語言

Narvive 提供三語介面：**简体中文（預設）/ 繁體中文 / English**。

- **切換位置**：設定 → 語言（獨立頁面，每個語言一列，目前語言帶勾選標記）。
- **即時生效且不閃爍**：切換語言**不重建 Activity**（`configChanges="locale|layoutDirection"` + 自研 `onConfigurationChanged`），視窗不銷毀，因此沒有閃爍；`NavController` 狀態保持，**停留在目前頁面**。
- **代價與編碼規則**：ViewModel 會跨越語言變化存活，因此**本地化文案不能快取成普通 `String`** —— 凡可能跨語言變化繼續顯示的值，必須用 `@StringRes Int` 或 `UiMessage`（渲染時才解析）。已遷移的位置、允許保留 `String` 的少數瞬時提示、以及實作坑點見 [docs/i18n.md](docs/i18n.md) §2.1。
- **系統同步**：`res/xml/locales_config.xml` 經 `AndroidManifest.xml` 的 `android:localeConfig` 引用，Android 13+ 的「系統設定 → 應用程式 → Narvive → 語言」也能看到這三個選項並與應用程式內選擇保持一致。
- **AI 輸出語言跟隨介面**：系統提示詞、12 套預設提示詞範本、問候語與建議卡、意圖路由正規表達式（中英聯集）均依當前介面語言取值。**使用者自行修改過的提示詞屬於使用者資料，語言切換絕不覆蓋**。
- **中文字形分派**：介面字族為 HarmonyOS Sans，依語言選擇 SC / TC 兩套字族（`ui/theme/Type.kt` 的 `narviveTypography(traditionalChinese)`，於 `NarviveTheme` 中經 `MaterialTheme.typography` 下發，消費端零改動）。兩套字符合計約 36 MB。

> 閱讀正文（EPUB / TXT / PDF）**不走**介面字族，沿用 `service/font` 的獨立字型系統，由使用者在字型設定中自選，兩者互不影響。

**開發細節**（資源目錄組織、鍵命名慣例、`stringResource` 作用域鐵律、`UiMessage`，以及新增一門語言的完整步驟）見 [docs/i18n.md](docs/i18n.md)。

---

## 專案目錄結構

```
Narvive
├── gradle/
│   ├── libs.versions.toml          # 版本目錄（依賴／外掛統一管理）
│   └── wrapper/                    # Gradle Wrapper（9.5.0）
├── app/
│   ├── build.gradle.kts            # 應用模組建置腳本
│   ├── proguard-rules.pro          # 混淆規則
│   └── src/main/
│       ├── AndroidManifest.xml     # 清單（權限／進入點／FileProvider／localeConfig）
│       ├── res/
│       │   ├── values/             # 資源：預設語言（简体中文）
│       │   ├── values-en/          # 資源：English
│       │   ├── values-b+zh+Hant/   # 資源：繁體中文
│       │   ├── font/               # HarmonyOS Sans SC + TC（各 Regular/Medium/Bold）
│       │   ├── raw/                # 字型授權文本、字型服務金鑰庫
│       │   └── xml/                # locales_config / file_paths / network_security_config
│       └── java/com/narvive/app/
│           ├── MainActivity.kt     # 單 Activity 進入點 + 外部匯入 + WebDAV 自動同步
│           ├── di/                 # Hilt 模組
│           ├── data/               # datastore / keystore / local(Room) / repository
│           ├── domain/             # model / repository 介面
│           ├── service/            # ai / reader / font / 匯入 / 備份 / WebDAV / 儲存
│           └── ui/
│               ├── prefs/          # AppLanguage（介面語言列舉與切換）
│               ├── message/        # UiMessage（跨層可翻譯訊息）
│               ├── navigation/     # 路由與底部導覽
│               ├── theme/          # 主題（Color/Theme/Type/Shape/Spacing/Motion）
│               ├── components/     # 通用元件
│               └── screen/         # 各頁面（library/reader/chat/notes/stats/settings/...）
└── docs/                           # 文件（DESIGN.md 視覺規範 / i18n.md 多語言說明）
```

---

## 主要模組功能介紹

### `ui/screen/library` — 書架

`LibraryViewModel` 組合書籍 / 合集 / 成員關係 / 篩選偏好四路 Flow，衍生出排序、搜尋、篩選後的清單；`LibraryScreen` 提供格狀／清單切換、合集管理、多選批次操作與匯入確認對話框。

### `service/reader` — 閱讀引擎

- `ReaderController`：統一介面（進度 / 章節 / Locator / 翻頁 / 搜尋 / 設定 / 章文本）。
- `EpubReaderController`：封裝 Readium，處理目錄擷取、字元加權進度、標註 / 翻譯裝飾（CFI 錨定）、自研書內搜尋。
- `TxtReaderController`：編碼辨識 + 正規表達式切章 + 字元位移進度 + 扁平分塊捲動渲染。
- `PdfReaderController`：以 `androidx.pdf` 為基礎的骨架實作（MVP）。
- `ChapterTextExtractor` / `TocLoader`：於閱讀器外取得章文本 / 目錄（供改寫、續寫、AI、詳細頁使用）。

### `ui/screen/reader` — 閱讀頁

`ReaderViewModel` 是閱讀器核心狀態中樞：載入書籍、合成設定、採集 controller 狀態、恢復進度、管理選取區 / 標註 / 筆記 / 翻譯 / 書籤 / 搜尋 / 自動翻頁 / 字型注入。搭配 `ReaderScreen` 與 `components/` 下大量面板（Aa 面板、亮度、目錄、搜尋、選取區氣泡、翻譯卡、標註選單、自訂主題等）。

### `service/ai` — AI 基礎設施

- `AiService`：統一封裝三種協定的非串流與 SSE 串流請求，錯誤轉為可讀提示（Key 錯誤 / 位址錯誤 / 參數錯誤）。
- `FallbackChain`：Provider 優先順序 + 降級鏈，失敗自動切換，連續失敗 3 次降級。
- `PromptTemplates` / `PromptRenderer` / `PromptService`：提示詞定義、預留位置渲染、讀寫（DataStore）。
- `AiText` / `PromptDefaultsI18n` / `PromptLocaleProvider`：依當前介面語言提供系統提示詞、問候語與建議卡、意圖路由正規表達式，以及 12 套範本的多語言預設值。
- `GraphModels`：人物關係圖 / 時間軸的結構化 JSON 解析。
- `AiProfileStore` / `AiProfile`：AI 偏好持久化與自動歸納。
- `CharacterCardExtractor` / `TranslationCache`：角色卡擷取、翻譯快取查詢。

### `ui/screen/chat` — AI 對話

- `GlobalChatViewModel` + `GlobalChatScreen`：底部全域 AI（跨書上下文、意圖路由、工作階段管理、自動標題、偏好歸納）。
- `ChatViewModel` + `ChatScreen` / `ChatContent`：書籍內 AI（範圍切換、快捷指令、圖表產生）。
- `GraphSheet`：人物關係圖 / 時間軸 Canvas 繪製（縮放、拖曳、高亮、固定圖例）。
- `RoleplayViewModel` + 相關畫面：角色扮演工作階段與角色卡。

### `ui/screen/rewrite` — 改寫 / 續寫

`RewriteViewModel` 透過 `ChapterTextExtractor` 取章節上下文，依模式組裝上下文視窗（續寫上文 1200 / 改寫前後各 600），結果落庫為 `REWRITE` 標註，支援另存筆記與匯出 TXT。

### `ui/screen/notes` / `stats` — 筆記 / 統計

- `NotesViewModel`：彙整標註、依類型 / 書篩選、搜尋、刪除、匯出 Markdown。
- `StatsViewModel`：依閱讀工作階段計算今日／本週時長、連續天數、讀完數，並繪製日／週／月長條圖。

### `ui/screen/settings` — 設定

- `AiSettingsViewModel` / `AiSettingsScreen`：Provider 管理、測試連線、取得模型、優先順序、降級恢復。
- `PromptSettingsScreen` / `AiPreferencesScreen`：提示詞編輯、AI 偏好。
- `AppearanceScreen`：外觀主題、外觀模式、書架格狀欄數。
- `LanguageScreen`：介面語言選擇（每個語言一列，目前語言帶勾選標記）。
- `FeedbackScreen`：問題回饋（電子郵件 / GitHub Issue / GitHub Discussions + 診斷資訊複製）。
- `StorageScreen` / `WebDavScreen` / `BackupScreen` / `FontSettingsScreen` / `MoreSettingsScreen` 等。

### `service/font` — 字型

- `FontCatalogRepository`：自 Gitee `fonts.json` 取得目錄（離線回退本機快取），過濾不可下載項。
- `FontDownloadManager`：OkHttp 串流下載 + 斷點續傳（Range）+ 大小校驗。
- `FontResolver`：id → 檔案 / CSS 注入 / Compose Typeface；EPUB 中英雙字型走 CSS 字型堆疊，TXT 走單一 Typeface。
- `FontFileServer` / `FontStorage` / `FontStatRepair`：本機 HTTP 服務與字型儲存輔助。

### `service` 其餘 — 資料與同步

- `BookImportService`：檔案偵測 / 去重 / 匯入 / EPUB 中繼資料與封面擷取。
- `BackupService`：ZIP 備份 / 還原（id 重新對應、hash 去重、設定快照，API Key 不入包）。
- `WebDavService`：MKCOL / PUT / PROPFIND / GET / DELETE，自動同步與遠端清理。
- `StorageService` / `AppCacheService`：儲存統計與快取清理。

---

## 隱私

- **本機優先**：無帳號、無註冊、無行為埋點、無分析 SDK。書庫、筆記與閱讀紀錄全部留在裝置上。
- **BYOK**：AI 唯一的網路出口是你自己設定的服務商。請求由裝置直連你填寫的端點，不經過任何中間伺服器。
- **金鑰處理**：API Key 透過 `EncryptedSharedPreferences` 加密落盤，並被排除在備份包之外。
- 閱讀、標註與統計功能可完全離線使用。

---

## 使用範例

### 範例 1：閱讀器內 AI 翻譯

1. 開啟一本英文 EPUB，長按選取一段文字。
2. 在彈出的操作列點擊「翻譯」。
3. 首次呼叫走 AI 翻譯並寫入 `TRANSLATION` 標註（紅底線）；再次翻譯同一句直接命中快取，零額外呼叫。

### 範例 2：產生人物關係圖

1. 進入某本書的「問 AI」（書籍詳細頁 → 問 AI，或閱讀器內 AI 面板）。
2. 切換範圍為「本章」或「全書」。
3. 點擊快捷指令「人物關係圖」，AI 輸出結構化 JSON，`GraphSheet` 解析後繪製可縮放、拖曳、節點高亮的 Canvas 關係圖。

### 範例 3：底部 AI 跨書對話

1. 底部切到「AI」分頁。
2. 輸入 `@` 彈出內嵌選書面板（**不收起鍵盤**），勾選最多 5 本書，或勾選「全部書庫」。
3. 提問「幫我總結這幾本書的共同主題」，AI 依所選書籍的中繼資料（書名 / 作者 / 進度 / 簡介）回答。

### 範例 4：改寫 / 續寫

1. 閱讀器內長按選取文字 → 「改寫」或「續寫」。
2. 進入改寫頁，輸入指令（例如「改得更口語化」）。
3. 系統自動帶上書名 / 作者 / 章節名 + 選取區前後上下文（續寫上文 1200 字，改寫前後各 600 字），產生結果可另存筆記或匯出 TXT。

### 範例 5：備份與還原

1. 設定 → 備份 → 匯出，產生 `NarviveBackup-YYYYMMDD.zip`（含書籍、封面、標註、書籤、合集與部分設定）。
2. 換機或重新安裝後：設定 → 備份 → 匯入，依 hash 去重合併；同名不同 hash 的書保留兩者並加上「（匯入）」後綴。

### 範例 6：切換介面語言

1. 設定 → 語言。
2. 選擇「English」或「简体中文」，介面文字立即更新，並停留在目前設定頁。
3. 回到書架 / 閱讀器 / AI 頁，全站文字與 AI 輸出語言均已切換；Android 13+ 可在系統「應用程式 → Narvive → 語言」看到同一選擇。

---

## 貢獻指南

歡迎參與 Narvive 的貢獻！完整規範見 **[CONTRIBUTING.md](CONTRIBUTING.md)**，要點如下：

1. **Fork 專案**，從 `main`（或預設分支）切出功能分支，命名如 `feature/xxx`、`fix/xxx`。
2. **程式碼風格**：遵循專案統一的 Kotlin 官方風格（`kotlin.code.style=official`），保持與現有程式碼一致的命名與分層。
3. **架構慣例**：
   - 頁面使用 `@HiltViewModel` + `StateFlow`（`MutableStateFlow` 私有、`StateFlow` 公開）。
   - 資料存取透過 `domain/repository` 介面，實作放在 `data/repository`。
   - 跨頁面資料持久化優先使用 DataStore / Room，敏感資訊走 `keystore`。
4. **本地化**：所有使用者可見文字必須走 `strings.xml`，禁止在 Kotlin 中硬編碼中文；新增文字需**同時更新三個語言目錄**（`values/`、`values-en/`、`values-b+zh+Hant/`）。詳見 [docs/i18n.md](docs/i18n.md)。
5. **提交訊息**：使用清晰的中文或英文提交說明，描述「做了什麼、為什麼」。
6. **測試**：改動核心邏輯（如 AI 解析、分塊、進度計算、備份合併）請補充或更新單元測試（`app/src/test`）。
7. **提交 PR**：附上改動說明、影響範圍與必要的驗證截圖 / 說明（閱讀器與 AI 功能建議實機驗證）。
8. **資料庫變更**：新增欄位請遵循既有 `MIGRATION_n_n+1` 模式遞增版本號，並在 `NarviveDatabase` 中註冊遷移。

提交前請確保：

```powershell
.\gradlew.bat :app:compileDebugKotlin   # 編譯通過
.\gradlew.bat :app:assembleDebug        # 完整打包通過（可捕捉資源合併與 i18n 問題）
```

---

## 授權條款

Narvive 以 **Apache License 2.0** 發布，詳見 [LICENSE](LICENSE)。

本專案依賴的第三方函式庫遵循各自的開源授權條款（AndroidX、Jetpack Compose、Hilt、Room、OkHttp、Coil、Readium Kotlin Toolkit、Jsoup 等）。應用程式內「設定 → 關於與隱私 → 開源授權」頁面（`LicensesScreen`）用於展示第三方授權資訊。

隨應用程式打包的 **HarmonyOS Sans** 字型（簡體 SC 與繁體 TC 兩套）依 HarmonyOS Sans Fonts License Agreement 使用，授權文本隨套件提供：`app/src/main/res/raw/harmonyos_sans_license.txt`。
