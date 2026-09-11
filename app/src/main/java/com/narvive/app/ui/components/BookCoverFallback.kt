package com.narvive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** 无封面书籍的统一占位封面：渐变底 + 书名横排居中，位置在从下往上 0.7 高度处 */
@Composable
fun BookCoverFallback(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = MaterialTheme.typography.labelSmall.fontSize,
) {
    BoxWithConstraints(
        modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary,
                ),
            ),
        ),
    ) {
        val bottomGap = maxHeight * 0.618f
        Text(
            text = title.ifBlank { "未命名" },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomGap)
                .padding(horizontal = 6.dp),
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
