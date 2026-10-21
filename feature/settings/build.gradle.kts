plugins {
    alias(libs.plugins.ainovel.android.feature)
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.tts.api)
}
