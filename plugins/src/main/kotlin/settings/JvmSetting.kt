package settings

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils

internal val minecraftSourceSets = setOf("patchedMc", "mcLauncher", "injectedTags", "injectedInterfaces", "ideVirtualMain")

internal fun Project.jvmSettings() {
    val mod = extensions.getByType<ModExtension>()
    if (version.toString() == "unspecified") version = getVersionMod()
    val java = extensions.getByType<JavaPluginExtension>()
    val toolchains = extensions.getByType<JavaToolchainService>()
    // Local outputs are already deobfuscated (or contain no Minecraft references).
    // RFG must not transform their JAR before producer tasks have run.
    listOf("apiElements", "runtimeElements").forEach {
        configurations[it].attributes.attribute(ModUtils.DEOBFUSCATOR_TRANSFORMED, true)
    }
    java.sourceCompatibility = org.gradle.api.JavaVersion.VERSION_1_8
    java.targetCompatibility = org.gradle.api.JavaVersion.VERSION_1_8
    java.sourceSets.configureEach {
        if (name !in minecraftSourceSets) {
            tasks.named<JavaCompile>(compileJavaTaskName) {
                options.encoding = "UTF-8"
                options.release.convention(provider {
                    if (javaCompiler.get().metadata.languageVersion.asInt() >= 9) mod.javaRelease.get() else null
                })
                javaCompiler.convention(provider {
                    if (mod.compilerJavaVersion.isPresent) {
                        toolchains.compilerFor { languageVersion.set(mod.compilerJavaVersion) }.get()
                    } else toolchains.compilerFor(java.toolchain).get()
                })
            }
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions.jvmTarget.convention(mod.javaRelease.map {
                JvmTarget.fromTarget(if (it == 8) "1.8" else it.toString())
            })
        }
        tasks.configureEach {
            if (minecraftSourceSets.any { name.contains(it.replaceFirstChar(Char::uppercase)) } &&
                (name.contains("Kotlin") || name.startsWith("kapt") || name.startsWith("ksp"))) {
                enabled = false
            }
        }
    }
    afterEvaluate {
        if (mod.mavenGroup.get() != "unspecified") group = mod.mavenGroup.get()
        version = findProperty("modVersion") ?: version
        extensions.extraProperties.set("modVersion", version.toString())
        java.sourceCompatibility = org.gradle.api.JavaVersion.toVersion(mod.javaRelease.get())
        java.targetCompatibility = org.gradle.api.JavaVersion.toVersion(mod.javaRelease.get())
        if (mod.includeSources.get()) java.withSourcesJar()
    }
}
