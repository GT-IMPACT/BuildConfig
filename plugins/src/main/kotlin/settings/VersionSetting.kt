package settings

import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke

fun Project.getVersionMod(): String {

    for (variable in listOf("RELEASE_VERSION", "VERSION")) {
        val override = providers.environmentVariable(variable).orNull
        if (!override.isNullOrBlank()) {
            return override
        }
    }
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        val versionDetails = extra["versionDetails"] as Closure<VersionDetails>
        val details = versionDetails()
        details.lastTag?.takeIf(String::isNotBlank) ?: details.version.removeSuffix(".dirty")
    }.getOrDefault("unknown")
}
