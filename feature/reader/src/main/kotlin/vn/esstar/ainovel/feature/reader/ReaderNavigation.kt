package vn.esstar.ainovel.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class ReaderRoute(val bookId: Long, val chapterId: Long)

fun NavController.navigateToReader(bookId: Long, chapterId: Long) = navigate(ReaderRoute(bookId, chapterId))

fun NavGraphBuilder.readerScreen() {
    composable<ReaderRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ReaderRoute>()
        ReaderPlaceholder(bookId = route.bookId, chapterId = route.chapterId)
    }
}

@Composable
private fun ReaderPlaceholder(bookId: Long, chapterId: Long, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.reader_placeholder, bookId, chapterId))
    }
}
