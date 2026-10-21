plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.data.library)
    implementation(projects.data.source)
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
