pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
//    versionCatalogs {
//        create("libs") {
//            from(files("../gradle/libs.versions.toml"))
//        }
//    }
}

rootProject.name = "Competra"
include(":app")
include(":domain")
include(":data:navigation")
include(":feature:profile")
include(":feature:events")
include(":feature:center")
include(":feature:onboarding")
include(":feature:diary")
include(":feature:clubs")
include(":feature:rating")
include(":core:designsystem")
include(":data:local")
include(":data:remote")
include(":data:remote")
include(":data")
include(":core:resources")
include(":utils")
include(":core:nfchelper")
include(":core:ui")
include(":core:eventdetails")
include(":core:sync")
include(":core:analytics")
include(":core:tracking")
