package settings

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

internal fun Project.publishSettings(
    extension: PublishingExtension,
) {
    val mod = extensions.getByType<ModExtension>()
    val publication = extension.publications.register<MavenPublication>("maven") {
        from(components["java"])
    }
    afterEvaluate {
        publication.configure {
            groupId = providers.environmentVariable("ARTIFACT_GROUP_ID").getOrElse(mod.mavenGroup.get())
            artifactId = providers.environmentVariable("ARTIFACT_ID").getOrElse(mod.artifactId.get())
            version = providers.environmentVariable("RELEASE_VERSION").getOrElse(project.version.toString())
        }
        // RFG's launcher and patched Minecraft archives must keep their own names and Minecraft version.
        val modArchiveTasks = setOf("jar", "shadowJar", "reobfJar", "sourcesJar", "javadocJar")
        tasks.withType<AbstractArchiveTask>().matching {
            it.name in modArchiveTasks
        }.configureEach {
            archiveBaseName.set(publication.map {
                it.artifactId
            })
            archiveVersion.set(publication.map {
                it.version
            })
        }
        if (mod.mavenUrl.isPresent) {
            extension.repositories.maven {
                name = "impact"
                url = uri(mod.mavenUrl.get())
                credentials {
                    username = providers.environmentVariable("IMPACT_MAVEN_USER").orNull
                    password = providers.environmentVariable("IMPACT_MAVEN_PASSWORD").orNull
                }
            }
        }
    }
}
