plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    api(libs.androidx.datastore.preferences)
}
