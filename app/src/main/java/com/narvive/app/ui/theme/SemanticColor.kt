package com.narvive.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.narvive.app.domain.model.AnnotationType

/**
 * 固定语义色板 —— 主题与明暗无关（docs/DESIGN.md §2.5）。
 *
 * 标注类型色用于跨书识别不同类型（与高亮四色同理），保持恒定；
 * 替代此前 NotesScreen / BookDetailsScreen / TocBottomSheet 的重复硬编码。
 */
object AnnotationPalette {
    /** 高亮（黄） */
    val Highlight = Color(0xFFFACC15)
    /** 笔记（蓝） */
    val Note = Color(0xFF0284C7)
    /** 翻译（粉） */
    val Translation = Color(0xFFF472B6)
    /** AI 保存（天蓝） */
    val AiAnswer = Color(0xFF38BDF8)
    /** 角色对话（紫） */
    val Roleplay = Color(0xFFA78BFA)
    /** 改写（天蓝） */
    val Rewrite = Color(0xFF38BDF8)
}

/** 标注类型 → 固定语义色 */
fun annotationTypeColor(type: AnnotationType): Color = when (type) {
    AnnotationType.HIGHLIGHT -> AnnotationPalette.Highlight
    AnnotationType.NOTE -> AnnotationPalette.Note
    AnnotationType.TRANSLATION -> AnnotationPalette.Translation
    AnnotationType.AI_ANSWER -> AnnotationPalette.AiAnswer
    AnnotationType.ROLEPLAY -> AnnotationPalette.Roleplay
    AnnotationType.REWRITE -> AnnotationPalette.Rewrite
}

/**
 * 通用状态语义色（docs/DESIGN.md §2.5 / §12）。
 *
 * 说明：错误色随外观主题（MaterialTheme.colorScheme.error）；
 * 成功/危险/警告为跨主题统一语义色，此处以固定值收编，避免散写。
 */
object SemanticColors {
    /** 成功绿（浅色基准） */
    val Success = Color(0xFF16A34A)
    /** 危险/删除红 */
    val Danger = Color(0xFFEF4444)

    /* 警告横幅（浅黄底，跨主题统一） */
    val WarningBg = Color(0xFFFEF9C3)
    val WarningBorder = Color(0xFFFDE047)
    val WarningIcon = Color(0xFFCA8A04)
    val WarningText = Color(0xFF854D0E)
}
