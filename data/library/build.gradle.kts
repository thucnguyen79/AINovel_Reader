plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.storage)
    implementation(projects.core.datastore)
    implementation(projects.domain)

    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
