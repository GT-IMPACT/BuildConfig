package settings

import org.gradle.api.Project
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.kotlin.dsl.*

internal fun Project.configureLocalReobfuscation() {
    val mod = extensions.getByType<ModExtension>()
    configurations.configureEach {
        if (name == "reobfJarInputConfiguration") {
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, mod.javaRelease)
        }
        if (name == "reobfElements") {
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, mod.javaRelease)
        }
    }
    // RFG recreates MCP dependencies as external Maven modules for SRG resolution.
    listOf("reobfJarConfiguration", "obfuscatedRuntimeClasspath").forEach { configurationName ->
        val configuration = configurations[configurationName]
        configuration.attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, mod.javaRelease)
        }
        configuration.resolutionStrategy.dependencySubstitution {
            // Look up coordinates at resolution time: configure-on-demand may evaluate
            // a sibling only when RFG resolves its MCP parent configuration.
            all {
                val requestedModule = requested as? ModuleComponentSelector
                if (requestedModule != null) {
                    val local = rootProject.allprojects.firstOrNull {
                        it != this@configureLocalReobfuscation && it.plugins.hasPlugin("java") &&
                            it.group.toString() == requestedModule.group && it.name == requestedModule.module
                    }
                    if (local != null) {
                        useTarget(project(local.path))
                        artifactSelection { withoutArtifactSelectors() }
                    }
                }
            }
        }
    }
}
