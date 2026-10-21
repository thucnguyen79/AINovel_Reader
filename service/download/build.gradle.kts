plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.storage)
    implementation(projects.core.network)
    implementation(projects.domain)
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
