package settings

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import javax.inject.Inject

/** Module-local defaults. Legacy gradle.properties remain accepted during migration. */
abstract class ModExtension @Inject constructor(private val project: Project) {
    private fun value(name: String): Provider<String> = project.provider { project.findProperty(name)?.toString() }
    private fun string(name: String, fallback: String) =
        project.objects.property(String::class.java).convention(value(name).orElse(fallback))
    private fun bool(name: String, fallback: Boolean) =
        project.objects.property(Boolean::class.java).convention(value(name).map(String::toBoolean).orElse(fallback))

    val modId: Property<String> = string("modId", project.name)
    val modName: Property<String> = string("modName", project.name)
    val modGroup: Property<String> = project.objects.property(String::class.java)
        .convention(value("modGroup").orElse(project.provider { project.group.toString() }))
    val archiveName: Property<String> = project.objects.property(String::class.java)
        .convention(value("customArchiveBaseName").filter(String::isNotBlank).orElse(modId))
    val mavenGroup: Property<String> = project.objects.property(String::class.java)
        .convention(value("mavenGroupId").orElse(modGroup))
    val artifactId: Property<String> = project.objects.property(String::class.java).convention(project.name)
    val mavenUrl: Property<String> = project.objects.property(String::class.java)
        .convention(value("mavenPublishUrl").filter(String::isNotBlank))
    val javaRelease: Property<Int> = project.objects.property(Int::class.java).convention(8)
    // Optional, task-local compiler selection. No project-wide Java/Kotlin toolchain is set.
    val compilerJavaVersion: Property<JavaLanguageVersion> = project.objects.property(JavaLanguageVersion::class.java)
    val minecraftJavaVersion: Property<Int> = project.objects.property(Int::class.java).convention(8)
    val runJavaVersion: Property<Int> = project.objects.property(Int::class.java).convention(8)
    val minecraftVersion: Property<String> = string("minecraftVersion", "1.7.10")
    val mappingsChannel: Property<String> = string("channel", "stable")
    val mappingsVersion: Property<String> = string("mappingsVersion", "12")
    val username: Property<String> = string("developmentEnvironmentUserName", "Developer")
    val injectGenerics: Property<Boolean> = bool("enableGenericInjection", false)
    val coreModClass: Property<String> = string("coreModClass", "")
    val coreModOnly: Property<Boolean> = bool("containsMixinsAndOrCoreModOnly", false)
    val accessTransformers: ListProperty<String> = project.objects.listProperty(String::class.java)
        .convention(value("accessTransformersFile").map { it.split(Regex("\\s+")).filter(String::isNotBlank) }.orElse(emptyList()))
    val useDependencyAccessTransformers: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)
    val usesMixins: Property<Boolean> = bool("usesMixins", false)
    val forceEnableMixins: Property<Boolean> = bool("forceEnableMixins", false)
    val mixinDebug: Property<Boolean> = bool("usesMixinDebug", false)
    val mixinDependency: Property<MinimalExternalModuleDependency> =
        project.objects.property(MinimalExternalModuleDependency::class.java)
            .convention(project.libs.library("mixin-unimixins"))
    val mixinRefmap: Property<String> = project.objects.property(String::class.java)
        .convention(modId.map { "mixins.$it.refmap.json" })
    val mixinConfigs: ListProperty<String> = project.objects.listProperty(String::class.java)
        .convention(modId.map { listOf("mixins.$it.json") })
    val shadeDependencies: Property<Boolean> = bool("usesShadowedDependencies", false)
    val minimizeShadedDependencies: Property<Boolean> = bool("minimizeShadowedDependencies", false)
    val relocateShadedDependencies: Property<Boolean> = bool("relocateShadowedDependencies", false)
    val includeSources: Property<Boolean> = project.objects.property(Boolean::class.java)
        .convention(value("noPublishedSources").map { !it.toBoolean() }.orElse(true))
}
