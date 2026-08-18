pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "commerce-platform"

include(":services:identity-access-service")
include(":services:catalog-service")
