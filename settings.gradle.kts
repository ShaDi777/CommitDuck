import org.gradle.kotlin.dsl.ivy
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "commit-duck"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.0"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.20.0"
}

dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        ivy {
            name = "IntelliJGitHub"
            url = uri(
                "https://github.com/JetBrains/intellij-community/releases/download"
            )

            patternLayout {
                artifact("idea/[revision]/idea-[revision](-[classifier]).[ext]")
            }

            metadataSources {
                artifact()
            }

            content {
                includeModule("idea", "idea")
            }
        }


        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
