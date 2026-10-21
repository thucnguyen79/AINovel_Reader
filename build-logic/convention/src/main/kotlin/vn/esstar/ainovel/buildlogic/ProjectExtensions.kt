package vn.esstar.ainovel.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Access to the shared version catalog from convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.versionInt(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

/**
 * Derives a namespace from the Gradle project path, e.g. `:core:common` -> `vn.esstar.ainovel.core.common`.
 * Modules can still override `android.namespace` explicitly.
 */
val Project.derivedNamespace: String
    get() {
        val suffix = path.trim(':').split(':').joinToString(".") { it.replace('-', '_') }
        return if (suffix.isEmpty()) "vn.esstar.ainovel" else "vn.esstar.ainovel.$suffix"
    }
