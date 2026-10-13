package settings

import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*

internal fun Project.configureMod() {
    val mod = extensions.getByType<ModExtension>()
    extensions.getByType<BasePluginExtension>().archivesName.set(mod.archiveName)
    tasks.named<Jar>("jar") { archiveClassifier.set("dev") }
    configureShadow()
    configureLocalReobfuscation()
    afterEvaluate {
        val metadata = mapOf("modId" to mod.modId.get(), "modName" to mod.modName.get(),
            "modVersion" to version.toString(), "minecraftVersion" to mod.minecraftVersion.get())
        tasks.named<ProcessResources>("processResources") {
            inputs.properties(metadata)
            filesMatching("mcmod.info") { expand(metadata) }
        }
        val ats = if (mod.accessTransformers.get().isEmpty()) {
            fileTree("src/main/resources/META-INF") { include("**/*_at.cfg") }.files.sortedBy { it.name }
        } else mod.accessTransformers.get().map { file("src/main/resources/META-INF/$it") }
        ats.forEach { if (!it.isFile) throw GradleException("Access transformer does not exist: $it") }
        extensions.getByType<MCPTasks>().deobfuscationATs.from(ats)
        val attributes = mutableMapOf<String, Any>()
        if (ats.isNotEmpty()) attributes["FMLAT"] = ats.joinToString(" ") { it.name }
        val core = mod.coreModClass.get()
        if (core.isNotBlank()) {
            attributes["FMLCorePlugin"] = if (core.startsWith(mod.modGroup.get() + ".")) core else "${mod.modGroup.get()}.$core"
        }
        if ((mod.usesMixins.get() || core.isNotBlank()) && !mod.coreModOnly.get()) {
            attributes["FMLCorePluginContainsFMLMod"] = true
        }
        if (mod.usesMixins.get()) {
            attributes["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
            attributes["MixinConfigs"] = mod.mixinConfigs.get().joinToString(",")
            attributes["ForceLoadAsMod"] = !mod.coreModOnly.get()
        }
        tasks.named<Jar>("jar") { manifest.attributes(attributes) }
        // IC2 publishes only a dev jar under its original coordinates.
        configurations["reobfJarConfiguration"].dependencies.addAll(
            configurations["compileClasspath"].allDependencies
                .filter { it.group == "net.industrial-craft" && it.name == "industrialcraft-2" }
                .map { dependencies.create(libs.library("minecraft-ic2-runtime").get()) })
    }
}

internal fun Project.prepareMixinConfiguration() {
    // RFG checks mixinRefMap in its afterEvaluate callback; register ours before applying RFG.
    afterEvaluate { configureMixins(extensions.getByType<ModExtension>()) }
}

private fun Project.configureMixins(mod: ModExtension) {
    val utils = extensions.getByType<ModUtils>()
    val dependency = dependencies.variantOf(mod.mixinDependency) { classifier("dev") }
    if (mod.usesMixins.get()) {
        dependencies.add("implementation", utils.enableMixins(dependency, mod.mixinRefmap.get()))
        dependencies.add("annotationProcessor", dependency)
        dependencies.add("annotationProcessor", libs.findBundle("mixin-processor").orElseThrow {
            GradleException("Missing bundle 'mixin-processor' in the 'libs' version catalog")
        })
        pluginManager.withPlugin("org.jetbrains.kotlin.kapt") { dependencies.add("kapt", dependency) }
        val missing = mod.mixinConfigs.get().filter { !file("src/main/resources/$it").isFile }
        if (missing.isNotEmpty()) throw GradleException("Missing Mixin configs: ${missing.joinToString()}")
    } else if (mod.forceEnableMixins.get()) {
        dependencies.add("runtimeOnlyNonPublishable", dependency)
    }
    if (mod.mixinDebug.get() && (mod.usesMixins.get() || mod.forceEnableMixins.get())) {
        dependencies.add("runtimeOnlyNonPublishable", libs.library("mixin-fernflower"))
        extensions.getByType<com.gtnewhorizons.retrofuturagradle.MinecraftExtension>().extraRunJvmArguments
            .addAll("-Dmixin.debug.countInjections=true", "-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true")
    }
}
