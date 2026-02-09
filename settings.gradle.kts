pluginManagement {
    repositories {
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
    }
}

rootProject.name = "cobblemon-spawning-rei"

include("common")
include("fabric")
include("neoforge")
