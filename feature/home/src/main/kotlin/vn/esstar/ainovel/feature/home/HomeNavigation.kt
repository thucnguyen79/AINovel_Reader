package vn.esstar.ainovel.feature.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavController.navigateToHome(navOptions: NavOptions? = null) = navigate(HomeRoute, navOptions)

fun NavGraphBuilder.homeScreen(
    onOpenUrl: (String) -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(onOpenUrl = onOpenUrl)
    }
}
