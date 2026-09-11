package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.ui.theme.LocalElevation
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors

/**
 * 翻译标注管理卡（原型屏 37）：标题 + 缓存命中标识 + meta 行 + 重新翻译/问AI/删除。
 * 点按正文中的红色译文时弹出。按钮用扁平 Material 图标（无 emoji）。
 */
@Composable
fun TranslationCard(
    annotation: Annotation,
    fromCache: Boolean,
    onRetranslate: () -> Unit,
    onAskAi: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(LocalElevation.current.level3, NarviveShape.Md)
            .clip(NarviveShape.Md)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.reader_translation_annotation),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (fromCache) {
                Text(
                    stringResource(R.string.reader_cache_hit),
                    color = SemanticColors.Success,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(NarviveShape.Xs)
                        .background(SemanticColors.Success.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            annotation.selectedText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        annotation.translation?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.reader_translation_meta, annotation.selectedText.length),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(Icons.Rounded.Refresh, stringResource(R.string.reader_retranslate), onRetranslate, Modifier.weight(1f))
            CardButton(Icons.Rounded.AutoAwesome, stringResource(R.string.reader_ask_ai), onAskAi, Modifier.weight(1f))
            CardButton(Icons.Rounded.Delete, stringResource(R.string.reader_delete), onDelete, Modifier.weight(1f), isError = true)
        }
    }
}

@Composable
private fun CardButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    Row(
        modifier
            .height(36.dp)
            .clip(NarviveShape.Sm)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
