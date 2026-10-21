package vn.esstar.ainovel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import vn.esstar.ainovel.core.designsystem.theme.AINovelTheme
import vn.esstar.ainovel.ui.AINovelApp
import vn.esstar.ainovel.ui.RequestNotificationPermission

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AINovelTheme {
                RequestNotificationPermission()
                AINovelApp()
            }
        }
    }
}
