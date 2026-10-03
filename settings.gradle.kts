pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // The web build adds Node.js and Binaryen download locations to the root project, so project repositories are allowed.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RefinersFire"
include(":shared", ":app", ":desktop")
