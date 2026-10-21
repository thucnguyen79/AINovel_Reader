import java.util.Properties

plugins {
    alias(libs.plugins.ainovel.android.application)
    alias(libs.plugins.ainovel.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: keystore.properties at the repo root (git-ignored). Without it the release build
// falls back to the debug key so local release testing still works; CI injects the file from secrets.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it).isFile } == true

android {
    namespace = "vn.esstar.ainovel"

    defaultConfig {
        applicationId = "vn.esstar.ainovel"
        versionCode = 100
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
        // sherpa-onnx/onnxruntime native libs: real phones only (x86 emulators are not a target).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // One APK per ABI (smaller downloads from GitHub Releases) plus a universal one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/DEPENDENCIES", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.storage)
    implementation(projects.domain)
    implementation(projects.data.source)
    implementation(projects.data.library)
    implementation(projects.data.ebook)
    implementation(projects.data.translate)
    implementation(projects.tts.api)
    implementation(projects.tts.system)
    implementation(projects.tts.neural)
    implementation(projects.feature.home)
    implementation(projects.feature.browser)
    implementation(projects.feature.reader)
    implementation(projects.feature.player)
    implementation(projects.feature.library)
    implementation(projects.feature.translate)
    implementation(projects.feature.settings)
    implementation(projects.service.playback)
    implementation(projects.service.download)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    androidTestImplementation(libs.androidx.test.espresso.core)
}
