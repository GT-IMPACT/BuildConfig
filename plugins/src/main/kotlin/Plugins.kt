import com.gtnewhorizons.retrofuturagradle.MinecraftExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import settings.minecraftSettings
import settings.publishSettings
import settings.jvmSettings
import settings.ModExtension
import settings.configureMod
import settings.prepareMixinConfiguration
import org.gradle.api.initialization.Settings
import settings.repositories
import settings.libs
import settings.plugin
import settings.alias

class SetupMinecraftPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            alias(libs.plugin("setup-jvm"))
            alias(libs.plugin("kotlin-jvm"))
            prepareMixinConfiguration()
            alias(libs.plugin("retro"))
            alias(libs.plugin("shadow"))
            alias(libs.plugin("buildconfig"))
        }
        extensions.configure<MinecraftExtension>(::minecraftSettings)
        configureMod()
    }
}

class SetupPublishPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            alias(libs.plugin("setup-jvm"))
            apply("maven-publish")
            apply("java-library")
        }
        extensions.configure<PublishingExtension>(::publishSettings)
    }
}

fun DependencyHandler.implementation(dependencyNotation: Any) =
    add("implementation", dependencyNotation)

class SetupJvmPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("java-library")
        pluginManager.alias(libs.plugin("gitversion"))
        extensions.create("impactMod", ModExtension::class.java, this)
        jvmSettings()
    }
}

class SetupSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        target.pluginManagement.repositories.repositories()
        target.dependencyResolutionManagement.repositories.repositories()
    }
}
