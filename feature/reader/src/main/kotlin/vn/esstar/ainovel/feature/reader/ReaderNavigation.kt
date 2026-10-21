package vn.esstar.ainovel.feature.reader

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class ReaderRoute(val bookId: Long, val chapterId: Long)

fun NavController.navigateToReader(bookId: Long, chapterId: Long, navOptions: NavOptions? = null) =
    navigate(ReaderRoute(bookId, chapterId), navOptions)

fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable<ReaderRoute> {
        ReaderRoute(onBack = onBack)
    }
}
