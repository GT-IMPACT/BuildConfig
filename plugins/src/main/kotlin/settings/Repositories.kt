package settings

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.maven

internal fun RepositoryHandler.repositories() {
    mavenCentral()
    maven("https://maven.accident.space/repository/maven-public/") {
        content {
            includeGroupByRegex("space\\.impact(\\..*)?")
            includeGroup("com.github.GTNewHorizons")
            includeGroup("com.github.Azanor")
            includeGroup("net.industrial-craft")
        }
        metadataSources {
            mavenPom()
            artifact()
        }
        if (!System.getenv("IMPACT_MAVEN_USER").isNullOrBlank()) {
            credentials {
                username = System.getenv("IMPACT_MAVEN_USER")
                password = System.getenv("IMPACT_MAVEN_PASSWORD")
            }
        }
    }
    maven("https://nexus.gtnewhorizons.com/repository/public/")
    maven("https://maven.minecraftforge.net")
    maven("https://gregtech.overminddl1.com/")
    maven("https://cursemaven.com") { content { includeGroup("curse.maven") } }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
    gradlePluginPortal()
}
