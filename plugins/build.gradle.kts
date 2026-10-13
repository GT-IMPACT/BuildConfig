plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.build.logic.kotlin.stdlib)
    implementation(libs.build.logic.kotlin.reflect)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.gitversion)
    implementation(libs.gradle.retro)
    implementation(libs.gradle.shadow)
    implementation(libs.gradle.buildconfig)
    testImplementation(gradleTestKit())
    testImplementation(libs.test.junit)
}

version = libs.versions.conventions.get()

tasks.test {
    systemProperty("buildConfig.catalogFile", rootProject.file("libs.versions.toml").absolutePath)
}

gradlePlugin {
    plugins {
        register("jvm-setup") {
            id = "jvm-setup"
            implementationClass = "SetupJvmPlugin"
        }
        register("settings-setup") {
            id = "settings-setup"
            implementationClass = "SetupSettingsPlugin"
        }
        register("minecraft-setup") {
            id = "minecraft-setup"
            implementationClass = "SetupMinecraftPlugin"
        }
    }
    plugins {
        register("publish-setup") {
            id = "publish-setup"
            implementationClass = "SetupPublishPlugin"
        }
    }
}
