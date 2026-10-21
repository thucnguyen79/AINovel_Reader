package vn.esstar.ainovel.feature.player

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlayerRoute

fun NavController.navigateToPlayer(navOptions: NavOptions? = null) = navigate(PlayerRoute, navOptions)

fun NavGraphBuilder.playerScreen(onBack: () -> Unit, onOpenReader: (bookId: Long, chapterId: Long) -> Unit) {
    composable<PlayerRoute> {
        PlayerRoute(onBack = onBack, onOpenReader = onOpenReader)
    }
}
