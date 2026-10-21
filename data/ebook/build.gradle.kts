plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.storage)
    implementation(projects.domain)
    implementation(libs.jsoup)
    implementation(libs.pdfbox.android)
}
