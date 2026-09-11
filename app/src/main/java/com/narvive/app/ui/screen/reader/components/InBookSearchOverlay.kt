package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narvive.app.R
import com.narvive.app.service.reader.SearchResult
import com.narvive.app.ui.theme.NarviveShape
import kotlinx.coroutines.delay

/**
 * 书内搜索覆盖层（原型屏 search / search-empty）。
 * 结果卡：章节 chip + 章标题·进度% + 关键词黄色高亮摘录；无结果态含提示。
 */
@Composable
fun InBookSearchOverlay(
    isSearching: Boolean,
    results: List<SearchResult>,
    onSearch: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 防抖搜索
    LaunchedEffect(query) {
        if (query.isBlank()) {
            searched = false
            onSearch("")
            return@LaunchedEffect
        }
        delay(300)
        searched = true
        onSearch(query)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(top = 44.dp),
    ) {
        // 搜索栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back))
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(NarviveShape.Md)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    placeholder = { Text(stringResource(R.string.reader_search_placeholder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            Text(
                stringResource(R.string.reader_cancel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        when {
            isSearching -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
            searched && results.isEmpty() -> {
                // 无结果态（原型 search-empty）
                Column(
                    Modifier.fillMaxWidth().padding(top = 72.dp, start = 40.dp, end = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔍", fontSize = 36.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.reader_search_no_result, query), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.reader_search_no_result_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            results.isNotEmpty() -> {
                Text(
                    stringResource(R.string.reader_search_result_count, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results) { result ->
                        SearchResultCard(result = result, query = query, onClick = { onResultClick(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, query: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(NarviveShape.Md)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            result.chapterTitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(NarviveShape.Xs)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "${(result.progressPercent * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            highlightKeyword(result.excerpt, query),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 摘录中的关键词黄色高亮（原型 rgba(250,204,21,.4)） */
private fun highlightKeyword(excerpt: String, keyword: String): AnnotatedString {
    val builder = AnnotatedString.Builder(excerpt)
    if (keyword.isNotBlank()) {
        var index = excerpt.indexOf(keyword, ignoreCase = true)
        while (index >= 0) {
            builder.addStyle(
                SpanStyle(background = Color(0x66FACC15)),
                index, index + keyword.length,
            )
            index = excerpt.indexOf(keyword, index + 1, ignoreCase = true)
        }
    }
    return builder.toAnnotatedString()
}
