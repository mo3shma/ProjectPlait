pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only Tesseract4Android (Arabic text reading) comes from here.
        maven("https://jitpack.io") { content { includeGroup("com.github.adaptech-cz.Tesseract4Android") } }
    }
}

rootProject.name = "Plait"
include(":app")
