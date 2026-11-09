
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

        // Agora SDK Maven Repository
        maven {
            url = uri("https://download.agora.io/maven/")
        }
    }
}

rootProject.name = "Khahish"
include(":app")
