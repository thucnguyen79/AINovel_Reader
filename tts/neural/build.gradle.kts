plugins {
    alias(libs.plugins.ainovel.android.library)
    alias(libs.plugins.ainovel.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.storage)
    implementation(projects.tts.api)
    implementation(projects.tts.system)
    implementation(libs.sherpa.onnx.android)
    implementation(libs.commons.compress)
}
