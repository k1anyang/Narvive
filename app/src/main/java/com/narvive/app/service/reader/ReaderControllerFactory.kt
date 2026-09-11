package com.narvive.app.service.reader

/**
 * 按书籍格式创建对应的 ReaderController。
 */
object ReaderControllerFactory {
    fun create(format: String): ReaderController = when (format.uppercase()) {
        "PDF" -> PdfReaderController()
        "TXT" -> TxtReaderController()
        else -> EpubReaderController() // EPUB is default
    }
}
