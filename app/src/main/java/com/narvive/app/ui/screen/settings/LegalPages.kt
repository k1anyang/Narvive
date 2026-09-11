package com.narvive.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narvive.app.R
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.NarviveShape

/** 法务文案的更新日期。仅日期值，标签走 `legal_updated_at` 以便翻译。 */
private const val LEGAL_UPDATED_DATE = "2026-08-14"

@Composable
fun PrivacyPolicyScreen(onBackClick: () -> Unit) {
    TabPageScaffold(
        title = stringResource(R.string.legal_privacy_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        LegalBody(
            padding = padding,
            sections = listOf(
                R.string.legal_privacy_h1 to R.string.legal_privacy_b1,
                R.string.legal_privacy_h2 to R.string.legal_privacy_b2,
                R.string.legal_privacy_h3 to R.string.legal_privacy_b3,
                R.string.legal_privacy_h4 to R.string.legal_privacy_b4,
                R.string.legal_privacy_h5 to R.string.legal_privacy_b5,
                R.string.legal_privacy_h6 to R.string.legal_privacy_b6,
                R.string.legal_privacy_h7 to R.string.legal_privacy_b7,
            ),
        )
    }
}

@Composable
fun UserAgreementScreen(onBackClick: () -> Unit) {
    TabPageScaffold(
        title = stringResource(R.string.legal_terms_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        LegalBody(
            padding = padding,
            sections = listOf(
                R.string.legal_terms_h1 to R.string.legal_terms_b1,
                R.string.legal_terms_h2 to R.string.legal_terms_b2,
                R.string.legal_terms_h3 to R.string.legal_terms_b3,
                R.string.legal_terms_h4 to R.string.legal_terms_b4,
                R.string.legal_terms_h5 to R.string.legal_terms_b5,
                R.string.legal_terms_h6 to R.string.legal_terms_b6,
            ),
        )
    }
}

@Composable
fun LicensesScreen(onBackClick: () -> Unit) {
    TabPageScaffold(
        title = stringResource(R.string.legal_licenses_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.legal_licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            licenses.forEach { (name, descRes, license) ->
                LicenseCard(name, stringResource(descRes), license)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LegalBody(padding: androidx.compose.foundation.layout.PaddingValues, sections: List<Pair<Int, Int>>) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.legal_updated_at, LEGAL_UPDATED_DATE), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        sections.forEach { (titleRes, bodyRes) ->
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LicenseCard(name: String, desc: String, license: String) {
    Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
        Column(Modifier.padding(14.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 第三方依赖清单。
 *
 * 项目名与许可证名是专有名词，**不翻译**；只有功能描述走字符串资源。
 */
private val licenses: List<Triple<String, Int, String>> = listOf(
    Triple("Jetpack Compose / Material 3 / AndroidX", R.string.license_androidx, "Apache License 2.0"),
    Triple("Room", R.string.license_room, "Apache License 2.0"),
    Triple("Hilt / Dagger", R.string.license_hilt, "Apache License 2.0"),
    Triple("Navigation Compose", R.string.license_navigation, "Apache License 2.0"),
    Triple("DataStore", R.string.license_datastore, "Apache License 2.0"),
    Triple("Jetpack Security", R.string.license_security, "Apache License 2.0"),
    Triple("Readium", R.string.license_readium, "BSD 3-Clause License"),
    Triple("OkHttp", R.string.license_okhttp, "Apache License 2.0"),
    Triple("Coil", R.string.license_coil, "Apache License 2.0"),
    Triple("jsoup", R.string.license_jsoup, "MIT License"),
    Triple("kotlinx.serialization", R.string.license_serialization, "Apache License 2.0"),
    Triple("HarmonyOS Sans", R.string.license_font, "HarmonyOS Sans Fonts License Agreement"),
)
