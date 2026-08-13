import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

private fun String.isPreRelease(): Boolean =
    Regex("(?i)(?:alpha|beta|rc|cr|preview)\\d*|[.\\-]m\\d+").containsMatchIn(this)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Informe bajo demanda: descubre actualizaciones, pero no modifica el build.
    id("io.github.ben-manes.versions.settings") version "0.61.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "A5Launcher"
include(":app")

gradle.rootProject {
    tasks.withType(DependencyUpdatesTask::class.java).configureEach {
        // Sólo propone versiones de publicación; las candidatas requieren una
        // revisión explícita de compatibilidad con el Navifly antes de actualizar.
        revision = "release"
        outputFormatter = "json"
        outputDir = "build/reports/dependencies"
        reportfileName = "updates"
        rejectVersionIf {
            candidate.version.isPreRelease() && !currentVersion.isPreRelease()
        }
    }
}
