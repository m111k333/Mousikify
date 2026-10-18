pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mousikify"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:scanner",
    ":core:media",
    ":core:designsystem",
    ":feature:home",
    ":feature:library",
    ":feature:player",
    ":feature:search",
    ":feature:equalizer"
)
