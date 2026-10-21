package vn.esstar.ainovel.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Enables Jetpack Compose in an application module. Caller must apply `org.jetbrains.kotlin.plugin.compose`. */
internal fun Project.configureAndroidCompose(extension: ApplicationExtension) {
    extension.buildFeatures { compose = true }
    addComposeDependencies()
}

/** Enables Jetpack Compose in a library module. Caller must apply `org.jetbrains.kotlin.plugin.compose`. */
internal fun Project.configureAndroidCompose(extension: LibraryExtension) {
    extension.buildFeatures { compose = true }
    addComposeDependencies()
}

private fun Project.addComposeDependencies() {
    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
    }
}
