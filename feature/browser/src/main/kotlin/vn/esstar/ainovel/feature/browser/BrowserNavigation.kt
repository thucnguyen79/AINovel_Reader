package vn.esstar.ainovel.feature.browser

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** In-app browser for a story website, with a "read this chapter" action bar. */
@Serializable
data class BrowserRoute(val url: String)

fun NavController.navigateToBrowser(url: String) = navigate(BrowserRoute(url))

fun NavGraphBuilder.browserScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: Long) -> Unit,
    onOpenReader: (bookId: Long, chapterId: Long) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    composable<BrowserRoute> {
        BrowserRoute(onBack = onBack, onOpenBook = onOpenBook, onOpenReader = onOpenReader, onOpenPlayer = onOpenPlayer)
    }
}
