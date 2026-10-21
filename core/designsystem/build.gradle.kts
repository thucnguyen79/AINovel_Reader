plugins {
    alias(libs.plugins.ainovel.android.library.compose)
}

dependencies {
    api(libs.androidx.compose.material.icons.extended)
    api(libs.coil.compose)
    api(libs.coil.network.okhttp)
}
