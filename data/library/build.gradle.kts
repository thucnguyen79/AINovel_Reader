plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.storage)
    implementation(projects.core.datastore)
    implementation(projects.domain)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
