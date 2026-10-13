@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.accident.space/repository/maven-public/") {
            mavenContent {
                includeGroup("space.impact")
                includeGroupByRegex("space\\.impact\\..+")
            }
        }
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        maven("https://maven.minecraftforge.net")
        mavenLocal()
    }

    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

include(":plugins")
