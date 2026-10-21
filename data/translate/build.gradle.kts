plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.storage)
    implementation(projects.domain)
}
