plugins {
    alias(libs.plugins.ainovel.jvm.library)
}

dependencies {
    implementation(projects.domain)
    api(libs.kotlinx.coroutines.core)
}
