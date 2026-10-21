plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
    alias(libs.plugins.ainovel.android.room)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
}
