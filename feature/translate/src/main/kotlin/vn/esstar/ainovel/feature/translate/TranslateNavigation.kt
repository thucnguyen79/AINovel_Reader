package vn.esstar.ainovel.feature.translate

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TranslateRoute

fun NavController.navigateToTranslate(navOptions: NavOptions? = null) = navigate(TranslateRoute, navOptions)

fun NavGraphBuilder.translateScreen(
    onOpenBook: (bookId: Long) -> Unit,
    onOpenReader: (bookId: Long, chapterId: Long) -> Unit,
    onOpenBrowser: (url: String) -> Unit,
) {
    composable<TranslateRoute> {
        TranslateRoute(onOpenBook = onOpenBook, onOpenReader = onOpenReader, onOpenBrowser = onOpenBrowser)
    }
}
