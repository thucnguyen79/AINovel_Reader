package vn.esstar.ainovel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import vn.esstar.ainovel.feature.browser.browserScreen
import vn.esstar.ainovel.feature.browser.navigateToBrowser
import vn.esstar.ainovel.feature.home.HomeRoute
import vn.esstar.ainovel.feature.home.homeScreen
import vn.esstar.ainovel.feature.home.navigateToHome
import vn.esstar.ainovel.feature.library.bookDetailScreen
import vn.esstar.ainovel.feature.library.libraryScreen
import vn.esstar.ainovel.feature.library.navigateToBookDetail
import vn.esstar.ainovel.feature.library.navigateToLibrary
import vn.esstar.ainovel.feature.player.MiniPlayer
import vn.esstar.ainovel.feature.player.navigateToPlayer
import vn.esstar.ainovel.feature.player.playerScreen
import vn.esstar.ainovel.feature.reader.navigateToReader
import vn.esstar.ainovel.feature.reader.readerScreen
import vn.esstar.ainovel.feature.settings.navigateToSettings
import vn.esstar.ainovel.feature.settings.settingsScreen
import vn.esstar.ainovel.feature.translate.navigateToTranslate
import vn.esstar.ainovel.feature.translate.translateScreen

@Composable
fun AINovelApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBars = currentDestination.isTopLevel()

    Scaffold(
        // Inner screens have their own Scaffold/TopAppBar that handle insets; only the bottom bars live here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBars) {
                Column {
                    MiniPlayer(onOpenPlayer = { navController.navigateToPlayer() })
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentDestination.isRouteInHierarchy(destination.route)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTopLevel(destination) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val openBook: (Long) -> Unit = { navController.navigateToBookDetail(it) }
        val openReader: (Long, Long) -> Unit = { bookId, chapterId -> navController.navigateToReader(bookId, chapterId) }
        val back: () -> Unit = { navController.popBackStack() }
        val openPlayer: () -> Unit = { navController.navigateToPlayer() }

        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .padding(padding)
                .then(if (showBars) Modifier.statusBarsPadding() else Modifier),
        ) {
            homeScreen(onOpenBook = openBook, onOpenReader = openReader, onOpenBrowser = navController::navigateToBrowser)
            libraryScreen(onOpenBook = openBook, onOpenReader = openReader)
            translateScreen()
            settingsScreen()
            bookDetailScreen(onBack = back, onOpenReader = openReader, onOpenPlayer = openPlayer)
            browserScreen(onBack = back, onOpenBook = openBook, onOpenReader = openReader, onOpenPlayer = openPlayer)
            readerScreen(onBack = back, onOpenPlayer = openPlayer)
            playerScreen(onBack = back)
        }
    }
}

private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    val options = navOptions {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (destination) {
        TopLevelDestination.HOME -> navigateToHome(options)
        TopLevelDestination.LIBRARY -> navigateToLibrary(options)
        TopLevelDestination.TRANSLATE -> navigateToTranslate(options)
        TopLevelDestination.SETTINGS -> navigateToSettings(options)
    }
}

private fun NavDestination?.isRouteInHierarchy(route: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

private fun NavDestination?.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { isRouteInHierarchy(it.route) }
