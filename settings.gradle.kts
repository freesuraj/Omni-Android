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
        maven { url = uri("https://alphacephei.com/maven/") }
    }
}

rootProject.name = "Omni-Android"

include(
    ":app",
    ":core:designsystem",
    ":core:model",
    ":core:data",
    ":feature:library",
    ":feature:audio",
    ":feature:settings",
    ":feature:dashboard",
    ":feature:quiz",
    ":feature:notes",
    ":feature:summary",
    ":feature:qa",
    ":feature:analysis",
    ":feature:paywall"
)
