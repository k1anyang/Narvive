package com.narvive.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.narvive.app.ui.screen.chat.ChatScreen
import com.narvive.app.ui.screen.chat.GlobalChatScreen
import com.narvive.app.ui.screen.chat.RoleplayChatScreen
import com.narvive.app.ui.screen.details.BookDetailsScreen
import com.narvive.app.ui.screen.library.LibraryScreen
import com.narvive.app.ui.screen.notes.NotesScreen
import com.narvive.app.ui.screen.reader.ReaderScreen
import com.narvive.app.ui.screen.rewrite.RewriteScreen
import com.narvive.app.ui.screen.settings.AiSettingsScreen
import com.narvive.app.ui.screen.settings.SettingsScreen
import com.narvive.app.ui.screen.stats.StatsScreen
import com.narvive.app.ui.theme.NarviveMotion

object Routes {
    const val BOOK_DETAIL = "book/{bookId}"
    const val READER = "reader/{bookId}?locator={locator}"
    const val CHAT = "chat/{bookId}"
    const val AI_SETTINGS = "ai_settings"
    const val PROMPT_SETTINGS = "settings/prompts"
    const val AI_PREFERENCES = "settings/ai_preferences"
    const val FONT_SETTINGS = "font_settings"
    const val MORE_SETTINGS = "more_settings"
    const val APPEARANCE = "settings/appearance"
    const val LANGUAGE = "settings/language"
    const val FEEDBACK = "settings/feedback"
    const val BACKUP = "settings/backup"
    const val ABOUT = "settings/about"
    const val SERVICES = "settings/services"
    const val WEBDAV = "settings/webdav"
    const val STORAGE = "settings/storage"
    const val PRIVACY = "settings/privacy"
    const val USER_AGREEMENT = "settings/user_agreement"
    const val LICENSES = "settings/licenses"
    const val ROLEPLAY = "roleplay/{bookId}/{sessionId}"
    const val ROLEPLAY_SESSIONS = "roleplay_sessions/{bookId}"
    const val ROLEPLAY_CARD = "roleplay_card/{bookId}?name={name}&sessionId={sessionId}"
    const val REWRITE = "rewrite/{bookId}?text={text}&mode={mode}"
    fun bookDetail(bookId: String) = "book/$bookId"
    fun reader(bookId: String, locator: String? = null) =
        "reader/$bookId" + (locator?.let { "?locator=${android.net.Uri.encode(it)}" } ?: "")
    fun chat(bookId: String) = "chat/$bookId"
    fun roleplay(bookId: String, sessionId: String = "new") = "roleplay/$bookId/$sessionId"
    fun roleplaySessions(bookId: String) = "roleplay_sessions/$bookId"
    fun roleplayCard(bookId: String, name: String = "", sessionId: String = "") =
        "roleplay_card/$bookId?name=${android.net.Uri.encode(name)}&sessionId=$sessionId"
    fun rewrite(bookId: String, text: String = "", mode: String = "rewrite") =
        "rewrite/$bookId?text=${android.net.Uri.encode(text)}&mode=$mode"
}

@Composable
fun NarviveNavHost(
    modifier: Modifier = Modifier,
    onReaderExit: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val tabRoutes = NarviveTab.entries.map { it.route }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = 80.dp + navBarsBottom
    val aiKeyboardOpen = currentRoute == NarviveTab.Ai.route && imeBottom > bottomBarHeight
    val showBottomBar = currentRoute in tabRoutes && !aiKeyboardOpen
    val fullScreenRoutes = setOf(
        Routes.READER, Routes.CHAT, Routes.ROLEPLAY, Routes.ROLEPLAY_SESSIONS,
        Routes.ROLEPLAY_CARD, Routes.REWRITE, Routes.FONT_SETTINGS, Routes.MORE_SETTINGS,
        Routes.PROMPT_SETTINGS, Routes.SERVICES, Routes.WEBDAV, Routes.STORAGE, Routes.PRIVACY,
        Routes.USER_AGREEMENT, Routes.LICENSES, Routes.LANGUAGE, Routes.FEEDBACK,
    )
    val isFullScreen = fullScreenRoutes.any { currentRoute?.startsWith(it.substringBefore("{")) == true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
                exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(),
            ) {
                NarviveTabBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController, startDestination = NarviveTab.Library.route,
                modifier = when {
                    isFullScreen -> Modifier
                    currentRoute in tabRoutes -> Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    else -> Modifier
                },
                enterTransition = { fadeIn(tween(NarviveMotion.Medium)) }, exitTransition = { fadeOut(tween(NarviveMotion.Medium)) },
            ) {
            composable(NarviveTab.Library.route) { LibraryScreen(onBookClick = { navController.navigate(Routes.bookDetail(it)) }) }
            composable(NarviveTab.Notes.route) {
                NotesScreen(onNoteClick = { bookId, locator -> navController.navigate(Routes.reader(bookId, locator)) })
            }
            composable(NarviveTab.Ai.route) {
                GlobalChatScreen(
                    onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                    onOpenBookDetail = { navController.navigate(Routes.bookDetail(it)) },
                    onOpenBookAt = { id, locator -> navController.navigate(Routes.reader(id, locator)) },
                )
            }
            composable(NarviveTab.Stats.route) { StatsScreen() }
            composable(NarviveTab.Settings.route) {
                SettingsScreen(
                    onAiClick = { navController.navigate(Routes.AI_SETTINGS) },
                    onPromptClick = { navController.navigate(Routes.PROMPT_SETTINGS) },
                    onAiPreferencesClick = { navController.navigate(Routes.AI_PREFERENCES) },
                    onAppearanceClick = { navController.navigate(Routes.APPEARANCE) },
                    onFontClick = { navController.navigate(Routes.FONT_SETTINGS) },
                    onLanguageClick = { navController.navigate(Routes.LANGUAGE) },
                    onReadingSettingsClick = { navController.navigate(Routes.MORE_SETTINGS) },
                    onBackupClick = { navController.navigate(Routes.BACKUP) },
                    onServicesClick = { navController.navigate(Routes.SERVICES) },
                    onStorageClick = { navController.navigate(Routes.STORAGE) },
                    onAboutClick = { navController.navigate(Routes.ABOUT) },
                    onPrivacyClick = { navController.navigate(Routes.PRIVACY) },
                    onUserAgreementClick = { navController.navigate(Routes.USER_AGREEMENT) },
                    onLicensesClick = { navController.navigate(Routes.LICENSES) },
                    onFeedbackClick = { navController.navigate(Routes.FEEDBACK) },
                )
            }

            composable(Routes.AI_SETTINGS) { AiSettingsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.PROMPT_SETTINGS) { com.narvive.app.ui.screen.settings.PromptSettingsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.AI_PREFERENCES) { com.narvive.app.ui.screen.settings.AiPreferencesScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.FONT_SETTINGS) { com.narvive.app.ui.screen.fonts.FontSettingsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.MORE_SETTINGS) { com.narvive.app.ui.screen.reader.MoreSettingsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.APPEARANCE) { com.narvive.app.ui.screen.settings.AppearanceScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.LANGUAGE) { com.narvive.app.ui.screen.settings.LanguageScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.FEEDBACK) { com.narvive.app.ui.screen.settings.FeedbackScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.BACKUP) { com.narvive.app.ui.screen.settings.BackupScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.ABOUT) { com.narvive.app.ui.screen.settings.AboutScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.SERVICES) {
                com.narvive.app.ui.screen.settings.ServicesScreen(
                    onBackClick = { navController.popBackStack() },
                    onWebdavClick = { navController.navigate(Routes.WEBDAV) },
                )
            }
            composable(Routes.WEBDAV) { com.narvive.app.ui.screen.settings.WebDavScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.STORAGE) { com.narvive.app.ui.screen.settings.StorageScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.PRIVACY) { com.narvive.app.ui.screen.settings.PrivacyPolicyScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.USER_AGREEMENT) { com.narvive.app.ui.screen.settings.UserAgreementScreen(onBackClick = { navController.popBackStack() }) }
            composable(Routes.LICENSES) { com.narvive.app.ui.screen.settings.LicensesScreen(onBackClick = { navController.popBackStack() }) }

            composable(
                Routes.BOOK_DETAIL,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                enterTransition = { fadeIn(tween(NarviveMotion.Medium)) },
                exitTransition = { fadeOut(tween(NarviveMotion.Fast)) },
                popEnterTransition = { fadeIn(tween(NarviveMotion.Medium)) },
                popExitTransition = {
                    fadeOut(tween(NarviveMotion.Fast)) + slideOutVertically(targetOffsetY = { it / 20 }, animationSpec = tween(NarviveMotion.Fast))
                },
            ) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                BookDetailsScreen(
                    bookId = id,
                    onBackClick = { navController.popBackStack() },
                    onOpenBook = { navController.navigate(Routes.reader(id)) },
                    onAskAi = { navController.navigate(Routes.chat(id)) },
                    onRoleplay = { navController.navigate(Routes.roleplaySessions(id)) },
                    onOpenBookAt = { locator -> navController.navigate(Routes.reader(id, locator)) },
                )
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("locator") { type = NavType.StringType; defaultValue = "" },
                ),
                enterTransition = { fadeIn(tween(NarviveMotion.Medium)) },
                exitTransition = { fadeOut(tween(NarviveMotion.Fast)) },
                popEnterTransition = { fadeIn(tween(NarviveMotion.Medium)) },
                popExitTransition = { fadeOut(tween(NarviveMotion.Medium)) },
            ) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                val locator = entry.arguments?.getString("locator").orEmpty()
                ReaderScreen(
                    bookId = id,
                    overrideLocator = locator.ifBlank { null },
                    onBackClick = {
                        navController.popBackStack()
                        onReaderExit()
                    },
                    onRewriteClick = { text, mode -> navController.navigate(Routes.rewrite(id, text, mode)) },
                    onRoleplayClick = { navController.navigate(Routes.roleplaySessions(id)) },
                    onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                    onOpenFontSettings = { navController.navigate(Routes.FONT_SETTINGS) },
                    onOpenMoreSettings = { navController.navigate(Routes.MORE_SETTINGS) },
                )
            }
            composable(Routes.CHAT, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                ChatScreen(
                    bookId = id,
                    onBackClick = { navController.popBackStack() },
                    onOpenRewrite = { text, mode -> navController.navigate(Routes.rewrite(id, text, mode)) },
                    onOpenRoleplay = { navController.navigate(Routes.roleplaySessions(id)) },
                    onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                )
            }
            composable(
                Routes.ROLEPLAY_SESSIONS,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                com.narvive.app.ui.screen.chat.RoleplaySessionsScreen(
                    bookId = id,
                    onBackClick = { navController.popBackStack() },
                    onOpenSession = { sid -> navController.navigate(Routes.roleplay(id, sid)) },
                    onCreateSession = { name -> navController.navigate(Routes.roleplayCard(id, name)) },
                )
            }
            composable(
                Routes.ROLEPLAY_CARD,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("sessionId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                val name = entry.arguments?.getString("name").orEmpty()
                val sid = entry.arguments?.getString("sessionId").orEmpty()
                com.narvive.app.ui.screen.chat.CharacterCardScreen(
                    bookId = id,
                    characterName = name,
                    sessionId = sid.ifBlank { null },
                    onBackClick = { navController.popBackStack() },
                    onStartChat = { newSid ->
                        navController.navigate(Routes.roleplay(id, newSid)) {
                            popUpTo(Routes.ROLEPLAY_SESSIONS)  // 卡片页出栈，聊天室返回键直达会话列表
                        }
                    },
                )
            }
            composable(Routes.ROLEPLAY, arguments = listOf(navArgument("bookId") { type = NavType.StringType }, navArgument("sessionId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                val sid = entry.arguments?.getString("sessionId") ?: return@composable
                RoleplayChatScreen(
                    bookId = id,
                    sessionId = sid.ifEmpty { null },
                    onBackClick = { navController.popBackStack() },
                    onEditCard = { charName, sessionId -> navController.navigate(Routes.roleplayCard(id, charName, sessionId)) },
                )
            }
            composable(
                Routes.REWRITE,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("text") { type = NavType.StringType; defaultValue = "" },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "rewrite" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("bookId") ?: return@composable
                val text = entry.arguments?.getString("text").orEmpty()
                val mode = entry.arguments?.getString("mode").orEmpty().ifEmpty { "rewrite" }
                RewriteScreen(bookId = id, originalText = text, mode = mode, onBackClick = { navController.popBackStack() })
            }
            }
            BookOpenOverlay()
        }
    }
}

@Composable
private fun NarviveTabBar(currentRoute: String?, onTabSelected: (NarviveTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        NarviveTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            val label = stringResource(tab.label)
            NavigationBarItem(
                selected = selected, onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, label) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
