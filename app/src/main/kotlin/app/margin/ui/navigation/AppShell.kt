@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.margin.R
import app.margin.domain.settings.AppSettings
import app.margin.feature.ai.AskNotesScreen
import app.margin.feature.attachments.AttachmentsScreen
import app.margin.feature.editor.EditorScreen
import app.margin.feature.editor.RevisionsScreen
import app.margin.feature.home.HomeScreen
import app.margin.feature.knowledge.KnowledgeScreen
import app.margin.feature.notes.NotesListScreen
import app.margin.feature.onboarding.OnboardingScreen
import app.margin.feature.organization.FoldersScreen
import app.margin.feature.organization.TagsScreen
import app.margin.feature.search.SearchScreen
import app.margin.feature.settings.AiSettingsScreen
import app.margin.feature.settings.DataSettingsScreen
import app.margin.feature.settings.PrivacySettingsScreen
import app.margin.feature.settings.SettingsScreen
import app.margin.feature.tasks.TasksScreen
import app.margin.feature.templates.TemplateBuilderScreen
import app.margin.feature.templates.TemplateFormScreen
import app.margin.feature.templates.TemplatesScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Navigation actions available to every screen. */
interface Navigator {
    fun open(route: String)
    fun back()
    fun openDrawer()
    /** Replace the current screen (e.g. template form → created note). */
    fun replace(route: String)
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
    TopLevel(Routes.notes(Collection.All), R.string.nav_notes, Icons.AutoMirrored.Outlined.Notes),
    TopLevel(Routes.TASKS, R.string.nav_tasks, Icons.Outlined.Checklist),
    TopLevel(Routes.search(), R.string.nav_search, Icons.Outlined.Search),
)

private fun isTopLevel(route: String?) = route == Routes.HOME || route == Routes.NOTES || route == Routes.TASKS || route == Routes.SEARCH

@Composable
fun AppShell(startDestination: String, settings: AppSettings, navigationRequests: Flow<String>) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val navigator = remember(nav, scope, drawer) {
        object : Navigator {
            override fun open(route: String) = nav.navigate(route) { launchSingleTop = true }
            override fun back() { nav.popBackStack() }
            override fun openDrawer() { scope.launch { drawer.open() } }
            override fun replace(route: String) {
                nav.popBackStack()
                nav.navigate(route) { launchSingleTop = true }
            }
        }
    }

    LaunchedEffect(navigationRequests) { navigationRequests.collect { navigator.open(it) } }

    fun goTopLevel(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = isTopLevel(currentRoute),
        drawerContent = { LibraryDrawer(onOpen = { r -> scope.launch { drawer.close() }; navigator.open(r) }) },
    ) {
        Scaffold(
            bottomBar = {
                if (!wide && isTopLevel(currentRoute)) {
                    NavigationBar {
                        topLevel.forEach { t ->
                            NavigationBarItem(
                                selected = routeMatches(currentRoute, t.route),
                                onClick = { goTopLevel(t.route) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                if (wide && isTopLevel(currentRoute)) {
                    NavigationRail {
                        Spacer(Modifier.height(16.dp))
                        topLevel.forEach { t ->
                            NavigationRailItem(
                                selected = routeMatches(currentRoute, t.route),
                                onClick = { goTopLevel(t.route) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.label)) },
                            )
                        }
                    }
                }
                AppNavHost(nav, startDestination, navigator, Modifier.weight(1f))
            }
        }
    }
}

private fun routeMatches(current: String?, target: String): Boolean = current != null && current.substringBefore('?') == target.substringBefore('?')

@Composable
private fun LibraryDrawer(onOpen: (String) -> Unit) {
    ModalDrawerSheet(Modifier.width(300.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
            app.margin.ui.components.BrandLockup(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp), markSize = 40.dp)
            @Composable
            fun item(label: Int, icon: ImageVector, route: String) = NavigationDrawerItem(
                label = { Text(stringResource(label)) }, icon = { Icon(icon, null) }, selected = false,
                onClick = { onOpen(route) }, modifier = Modifier.padding(horizontal = 12.dp),
            )
            item(R.string.inbox, Icons.Outlined.Inbox, Routes.notes(Collection.Inbox))
            item(R.string.pinned, Icons.Outlined.PushPin, Routes.notes(Collection.Pinned))
            item(R.string.favorites, Icons.Outlined.StarOutline, Routes.notes(Collection.Favorites))
            item(R.string.folders, Icons.Outlined.Folder, Routes.FOLDERS)
            item(R.string.tags, Icons.AutoMirrored.Outlined.Label, Routes.TAGS)
            item(R.string.templates, Icons.Outlined.Dashboard, Routes.templates())
            item(R.string.attachments, Icons.Outlined.AttachFile, Routes.ATTACHMENTS)
            item(R.string.knowledge, Icons.Outlined.Hub, Routes.KNOWLEDGE)
            item(R.string.ask_notes, Icons.Outlined.AutoAwesome, Routes.ASK)
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            item(R.string.archive, Icons.Outlined.Archive, Routes.notes(Collection.Archive))
            item(R.string.trash, Icons.Outlined.Delete, Routes.notes(Collection.Trash))
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            item(R.string.settings, Icons.Outlined.Settings, Routes.SETTINGS)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, start: String, n: Navigator, modifier: Modifier) {
    NavHost(nav, startDestination = start, modifier = modifier) {
        composable(Routes.ONBOARDING) { OnboardingScreen(onFinished = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }) }
        composable(Routes.HOME) { HomeScreen(n) }
        composable(Routes.NOTES, arguments = listOf(navArgument("collection") { type = NavType.StringType; defaultValue = "all" })) {
            NotesListScreen(n, Collection.decode(it.arguments?.getString("collection")))
        }
        composable(Routes.EDITOR, arguments = listOf(
            navArgument("noteId") { type = NavType.StringType },
            navArgument("preview") { type = NavType.BoolType; defaultValue = false },
        )) { EditorScreen(n) }
        composable(Routes.REVISIONS, arguments = listOf(navArgument("noteId") { type = NavType.StringType })) { RevisionsScreen(n) }
        composable(Routes.SEARCH, arguments = listOf(
            navArgument("q") { type = NavType.StringType; defaultValue = "" },
            navArgument("folder") { type = NavType.StringType; defaultValue = "" },
        )) { SearchScreen(n) }
        composable(Routes.FOLDERS) { FoldersScreen(n) }
        composable(Routes.TAGS) { TagsScreen(n) }
        composable(Routes.TEMPLATES, arguments = listOf(navArgument("pickFor") { type = NavType.StringType; defaultValue = "" })) { TemplatesScreen(n) }
        composable(Routes.TEMPLATE_FORM, arguments = listOf(navArgument("templateId") { type = NavType.StringType })) { TemplateFormScreen(n) }
        composable(Routes.TEMPLATE_BUILDER, arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { TemplateBuilderScreen(n) }
        composable(Routes.TASKS) { TasksScreen(n) }
        composable(Routes.ATTACHMENTS) { AttachmentsScreen(n) }
        composable(Routes.KNOWLEDGE) { KnowledgeScreen(n) }
        composable(Routes.ASK) { AskNotesScreen(n) }
        composable(Routes.SETTINGS) { SettingsScreen(n) }
        composable(Routes.SETTINGS_AI) { AiSettingsScreen(n) }
        composable(Routes.SETTINGS_PRIVACY) { PrivacySettingsScreen(n) }
        composable(Routes.SETTINGS_DATA) { DataSettingsScreen(n) }
    }
}
