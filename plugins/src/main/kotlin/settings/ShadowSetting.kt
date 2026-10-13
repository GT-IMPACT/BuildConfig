package settings

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*

internal fun Project.configureShadow() {
    val mod = extensions.getByType<ModExtension>()
    extensions.configure<ShadowExtension> {
        addShadowVariantIntoJavaComponent.set(false)
        addTargetJvmVersionAttribute.set(false)
    }
    val bundled = configurations.create("shadowImplementation") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach {
        configurations[it].extendsFrom(bundled)
    }
    val shadowJar = tasks.named<ShadowJar>("shadowJar") {
        configurations.set(listOf(bundled))
        archiveClassifier.set("dev-shadow")
        // Shadow 9 merges Kotlin metadata; allow matching inputs to reach its transformer.
        filesMatching("META-INF/*.kotlin_module") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    }
    afterEvaluate {
        if (mod.shadeDependencies.get() || bundled.allDependencies.isNotEmpty()) {
            tasks.named<Jar>("jar") { archiveClassifier.set("dev-preshadow") }
            shadowJar.configure {
                archiveClassifier.set("dev")
                if (mod.minimizeShadedDependencies.get()) minimize()
                if (mod.relocateShadedDependencies.get()) {
                    relocationPrefix.set("${mod.modGroup.get()}.shadow")
                    enableAutoRelocation.set(true)
                }
            }
            listOf("apiElements", "runtimeElements").forEach {
                configurations[it].outgoing.artifacts.clear()
                configurations[it].outgoing.artifact(shadowJar)
            }
            tasks.named<ReobfuscatedJar>("reobfJar") {
                inputJar.set(shadowJar.flatMap { it.archiveFile })
            }
        }
    }
}
