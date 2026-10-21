rootProject.name = "SkCine"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.skriptlang.org/releases") { name = "skriptlang" }
        maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc" }
    }
}

include(
    "skcine-core",
    "skcine-api",
    "skcine-platform",
    "skcine-skript",
    "skcine-plugin",
)
