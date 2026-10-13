@file:Suppress("UnstableApiUsage")

package settings

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*

internal fun Project.minecraftSettings(
    extension: MinecraftExtension,
) {
    val mod = extensions.getByType<ModExtension>()
    repositories.repositories()
    with(extension) {
        mcVersion.set(mod.minecraftVersion)
        mcpMappingChannel.set(mod.mappingsChannel)
        mcpMappingVersion.set(mod.mappingsVersion)
        javaCompatibilityVersion.set(mod.javaRelease)
        jvmLanguageVersion.set(mod.minecraftJavaVersion.map(JavaLanguageVersion::of))
        username.set(mod.username)
        injectMissingGenerics.set(mod.injectGenerics)
        useDependencyAccessTransformers.set(mod.useDependencyAccessTransformers)
        groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
    }
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<RunMinecraftTask>().configureEach {
        javaLauncher.set(toolchains.launcherFor {
            languageVersion.set(mod.runJavaVersion.map(JavaLanguageVersion::of))
        })
    }
    val runtime = configurations.create("runtimeOnlyNonPublishable") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    val dev = configurations.create("devOnlyNonPublishable") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    configurations["compileOnly"].extendsFrom(dev)
    runtime.extendsFrom(dev)
    configurations["runtimeClasspath"].extendsFrom(runtime)
    configurations["testRuntimeClasspath"].extendsFrom(runtime)
}
