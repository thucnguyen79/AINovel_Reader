package vn.esstar.ainovel.feature.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import vn.esstar.ainovel.feature.library.detail.BookDetailRoute

@Serializable
data object LibraryRoute

@Serializable
data class BookDetailRoute(val bookId: Long)

fun NavController.navigateToLibrary(navOptions: NavOptions? = null) = navigate(LibraryRoute, navOptions)

fun NavController.navigateToBookDetail(bookId: Long) = navigate(BookDetailRoute(bookId))

fun NavGraphBuilder.libraryScreen(
    onOpenBook: (bookId: Long) -> Unit,
    onOpenReader: (bookId: Long, chapterId: Long) -> Unit,
) {
    composable<LibraryRoute> {
        LibraryRoute(onOpenBook = onOpenBook, onOpenReader = onOpenReader)
    }
}

fun NavGraphBuilder.bookDetailScreen(
    onBack: () -> Unit,
    onOpenReader: (bookId: Long, chapterId: Long) -> Unit,
) {
    composable<BookDetailRoute> {
        BookDetailRoute(onBack = onBack, onOpenReader = onOpenReader)
    }
}
