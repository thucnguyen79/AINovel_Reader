plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)
    api(libs.okhttp)
    api(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.logging)
    api(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    implementation(libs.androidx.webkit)

    testImplementation(libs.okhttp.mockwebserver)
}
