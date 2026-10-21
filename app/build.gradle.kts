plugins {
    alias(libs.plugins.ainovel.android.application)
    alias(libs.plugins.ainovel.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "vn.esstar.ainovel"

    defaultConfig {
        applicationId = "vn.esstar.ainovel"
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*")
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
