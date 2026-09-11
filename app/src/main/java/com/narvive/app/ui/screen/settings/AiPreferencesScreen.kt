package com.narvive.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.SemanticColors

/** AI 偏好（主 AI 页客制化）：开关 + 自动(归纳)/手动(自填) 互斥 + 查看/清除 */
@Composable
fun AiPreferencesScreen(
    onBackClick: () -> Unit,
    viewModel: AiPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val inductStatus by viewModel.inductStatus.collectAsState()
    var manualText by remember { mutableStateOf("") }

    LaunchedEffect(state.mode, state.profile.summary) {
        if (state.mode == "manual" && manualText.isBlank()) {
            manualText = state.profile.summary
        }
    }

    TabPageScaffold(
        title = stringResource(R.string.ai_pref_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            // 总开关
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ai_pref_enable), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.ai_pref_enable_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            if (state.enabled) {
                Spacer(Modifier.height(16.dp))

                // 模式（自动/手动 互斥）
                Row {
                    FilterChip(
                        selected = state.mode == "auto",
                        onClick = { viewModel.setMode("auto") },
                        label = { Text(stringResource(R.string.ai_pref_mode_auto)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = state.mode == "manual",
                        onClick = { viewModel.setMode("manual") },
                        label = { Text(stringResource(R.string.ai_pref_mode_manual)) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (state.mode == "auto") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.ai_pref_current), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                state.profile.summary.ifBlank { stringResource(R.string.ai_pref_empty) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.profile.summary.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = viewModel::refreshAuto, enabled = inductStatus != InductStatus.Loading) {
                                    when (inductStatus) {
                                        InductStatus.Loading -> {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                            Text(stringResource(R.string.ai_pref_inducting), modifier = Modifier.padding(start = 8.dp))
                                        }
                                        InductStatus.Success -> Text(stringResource(R.string.ai_pref_induct_success), color = SemanticColors.Success)
                                        else -> Text(stringResource(R.string.ai_pref_induct_now))
                                    }
                                }
                                if (state.profile.summary.isNotBlank()) {
                                    Spacer(Modifier.padding(horizontal = 8.dp))
                                    OutlinedButton(onClick = viewModel::clearProfile) { Text(stringResource(R.string.ai_pref_clear)) }
                                }
                            }
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.ai_pref_manual_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualText,
                                onValueChange = { manualText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.ai_pref_manual_hint)) },
                                minLines = 3,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.setManualProfile(manualText) },
                                enabled = manualText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.common_save)) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.ai_pref_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
