package settings

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependency
import org.gradle.plugin.use.PluginDependencySpec

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().find("libs").orElseThrow {
        GradleException("Import BuildConfig/libs.versions.toml as the 'libs' version catalog in settings.gradle(.kts)")
    }

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { GradleException("Missing library '$alias' in the 'libs' version catalog") }

fun VersionCatalog.plugin(alias: String): Provider<PluginDependency> =
    findPlugin(alias).orElseThrow { GradleException("Missing plugin '$alias' in the 'libs' version catalog") }

fun PluginManager.alias(notation: Provider<PluginDependency>) = apply(notation.get().pluginId)

fun PluginDependenciesSpec.id(notation: Provider<PluginDependency>): PluginDependencySpec = id(notation.get().pluginId)
