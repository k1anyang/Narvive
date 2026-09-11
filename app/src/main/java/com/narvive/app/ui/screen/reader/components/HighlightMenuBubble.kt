package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.ui.theme.LocalElevation
import com.narvive.app.ui.theme.NarviveShape

/**
 * 高亮点按菜单（原型屏 36）：改色 + 删除 + 笔记/复制/翻译/问 AI/改写/续写（顺序与选区气泡一致，
 * 「删除」位于「笔记」左方），菜单可横向滚动；长宽与选区气泡统一（fillMaxWidth）。
 */
@Composable
fun HighlightMenuBubble(
    annotation: Annotation,
    onChangeColor: (Color) -> Unit,
    onAddNote: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onAskAi: () -> Unit = {},
    onRewrite: () -> Unit = {},
    onContinue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .bubblePopIn()
            .shadow(LocalElevation.current.level3, NarviveShape.Md)
            .clip(NarviveShape.Md)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(BubbleMenuWidth)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 改色：4 色圆点横排
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HighlightColors.forEach { (color, _) ->
                            val current = annotation.color?.let { Color(it.toInt()) } == color
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (current) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onChangeColor(color) },
                            )
                        }
                    }
                    Text(stringResource(R.string.reader_change_color), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
                }
                // 删除（位于笔记左方）
                MenuAction(icon = Icons.Rounded.Delete, label = stringResource(R.string.reader_delete), tint = MaterialTheme.colorScheme.error, onClick = onDelete)
                MenuAction(icon = Icons.Rounded.EditNote, label = stringResource(R.string.reader_note), onClick = onAddNote)
                MenuAction(icon = Icons.Rounded.ContentCopy, label = stringResource(R.string.reader_copy), onClick = onCopy)
                MenuAction(icon = Icons.Rounded.Translate, label = stringResource(R.string.reader_translate), tint = MaterialTheme.colorScheme.primary, onClick = onTranslate)
                MenuAction(icon = Icons.Rounded.AutoAwesome, label = stringResource(R.string.reader_ask_ai), tint = MaterialTheme.colorScheme.primary, onClick = onAskAi)
                MenuAction(icon = Icons.Rounded.Edit, label = stringResource(R.string.reader_rewrite), tint = MaterialTheme.colorScheme.tertiary, onClick = onRewrite)
                MenuAction(icon = Icons.AutoMirrored.Rounded.NoteAdd, label = stringResource(R.string.reader_continue), tint = MaterialTheme.colorScheme.tertiary, onClick = onContinue)
            }
        }
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(NarviveShape.Sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}
