package com.narvive.app.service.ai

import com.narvive.app.domain.repository.AnnotationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 翻译缓存——同一（书 + 原文 + 目标语言）查 Annotation 表去重，零重复 AI 调用。
 *
 * F2 修复后：缓存即带定位的 TRANSLATION 标注本身（不再单独写空 locator 记录），
 * 因此本类只保留查询职责，写入由 ReaderViewModel.saveTranslationAnnotation 完成。
 */
@Singleton
class TranslationCache @Inject constructor(
    private val annotationRepo: AnnotationRepository,
) {
    /** 查找缓存翻译。命中返回译文，未命中返回 null。走 DAO 精准查询（书 + 原文 + 目标语言）。 */
    suspend fun getCached(bookId: String, sourceText: String, targetLang: String = "zh"): String? =
        annotationRepo.findTranslation(bookId, sourceText, targetLang)
}
