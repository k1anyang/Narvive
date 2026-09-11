package com.narvive.app.core

/**
 * 应用支持的语言（资源目录的三种语言）。
 *
 * 这是**语言标识**的单一来源，供不同层次复用，避免重复枚举：
 * - UI 层偏好与选择器：[com.narvive.app.ui.prefs.AppLanguage]
 * - AI 提示词与周边文案：[com.narvive.app.service.ai.AiText]
 * - 提示词默认模板：[com.narvive.app.service.ai.PromptDefaultsI18n]
 *
 * 判定一律基于**资源配置里的 per-app locale**（由 AppCompat 写入），
 * 因此与用户选择的界面语言始终一致。
 */
enum class AppLang { ZH_HANS, ZH_HANT, EN }
