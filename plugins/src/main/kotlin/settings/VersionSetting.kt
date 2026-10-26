package settings

import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke

fun Project.getVersionMod(): String {

    providers.environmentVariable("VERSION").orNull?.takeIf(String::isNotBlank)?.let {
        return it
    }
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        val versionDetails = extra["versionDetails"] as Closure<VersionDetails>
        val details = versionDetails()
        details.version
    }.getOrDefault("unknown")
}
