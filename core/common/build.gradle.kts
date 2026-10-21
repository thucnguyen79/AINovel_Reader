plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.timber)
}
