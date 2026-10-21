package vn.esstar.ainovel.feature.browser

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

/** In-app browser for a story website. Phase 1 replaces the placeholder with a WebView + action bar. */
@Serializable
data class BrowserRoute(val url: String)

fun NavController.navigateToBrowser(url: String) = navigate(BrowserRoute(url))

fun NavGraphBuilder.browserScreen() {
    composable<BrowserRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<BrowserRoute>()
        BrowserPlaceholder(url = route.url)
    }
}

@Composable
private fun BrowserPlaceholder(url: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.browser_placeholder, url))
    }
}
