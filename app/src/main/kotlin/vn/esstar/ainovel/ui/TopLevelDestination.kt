package vn.esstar.ainovel.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass
import vn.esstar.ainovel.R
import vn.esstar.ainovel.feature.home.HomeRoute
import vn.esstar.ainovel.feature.library.LibraryRoute
import vn.esstar.ainovel.feature.settings.SettingsRoute
import vn.esstar.ainovel.feature.translate.TranslateRoute

/** The four bottom-navigation tabs. */
enum class TopLevelDestination(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    val route: KClass<*>,
) {
    HOME(Icons.Default.Home, R.string.tab_home, HomeRoute::class),
    LIBRARY(Icons.AutoMirrored.Filled.MenuBook, R.string.tab_library, LibraryRoute::class),
    TRANSLATE(Icons.Default.Translate, R.string.tab_translate, TranslateRoute::class),
    SETTINGS(Icons.Default.Settings, R.string.tab_settings, SettingsRoute::class),
}
