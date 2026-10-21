package vn.esstar.ainovel.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

private const val TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

/**
 * Shared Android configuration for application modules.
 * AGP 9 ships built-in Kotlin, so no `org.jetbrains.kotlin.android` plugin is applied here.
 * In the AGP 9 DSL the block methods live on the concrete extension types, hence two overloads.
 */
internal fun Project.configureKotlinAndroid(extension: ApplicationExtension) {
    extension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig {
            minSdk = libs.versionInt("minSdk")
            targetSdk = libs.versionInt("targetSdk")
            testInstrumentationRunner = TEST_RUNNER
        }
        compileOptions { configureJava() }
        lint { configureLint() }
    }
    configureKotlinCompilerOptions()
    addAndroidTestDependencies()
}

/** Shared Android configuration for library modules. */
internal fun Project.configureKotlinAndroid(extension: LibraryExtension) {
    extension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig {
            minSdk = libs.versionInt("minSdk")
            testInstrumentationRunner = TEST_RUNNER
        }
        compileOptions { configureJava() }
        lint { configureLint() }
    }
    configureKotlinCompilerOptions()
    addAndroidTestDependencies()
}

/** Shared configuration for pure Kotlin/JVM modules. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlinCompilerOptions()

    dependencies {
        add("testImplementation", libs.findLibrary("junit4").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    }
}

private fun CompileOptions.configureJava() {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = false
}

private fun Lint.configureLint() {
    abortOnError = false
    warningsAsErrors = false
}

private fun Project.addAndroidTestDependencies() {
    dependencies {
        add("testImplementation", libs.findLibrary("junit4").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
    }
}

private fun Project.configureKotlinCompilerOptions() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
            )
        }
    }
}
