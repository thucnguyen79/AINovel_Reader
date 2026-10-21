package vn.esstar.ainovel.feature.translate

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TranslateRoute

fun NavController.navigateToTranslate(navOptions: NavOptions? = null) = navigate(TranslateRoute, navOptions)

fun NavGraphBuilder.translateScreen() {
    composable<TranslateRoute> {
        TranslateScreen()
    }
}
