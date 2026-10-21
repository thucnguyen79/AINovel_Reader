plugins {
    alias(libs.plugins.ainovel.android.feature)
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.storage)
    implementation(projects.tts.api)
    implementation(libs.androidx.activity.compose)
}
