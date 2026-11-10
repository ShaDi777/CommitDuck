import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.2.0"
}

group = "com.commitduck"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val coroutinesDebug: Configuration by configurations.creating

dependencies {
    intellijPlatform {
        local("/Applications/IntelliJ IDEA CE 2.app")
    }
    // On macOS the `local(...)` platform adds the IDE root (`Contents`) to the classpaths
    // instead of the jars in `Contents/lib`, leaving `com.intellij.*` unresolved at compile
    // time and making the IDE unable to boot under `runIde`. Explicitly add the platform jars
    // to the compile classpath so the Kotlin compiler can see the SDK.
    compileOnly(fileTree("/Applications/IntelliJ IDEA CE 2.app/Contents/lib") { include("*.jar") })

    // runIde attaches a kotlinx-coroutines-debug java agent (via a manifest-only stub jar at
    // build/coroutines-javaagent.jar). The agent's Premain-Class is loaded from the run classpath,
    // so provide the real artifact there; otherwise the IDE fails with
    // "ClassNotFoundException: kotlinx.coroutines.debug.AgentPremain".
    add("coroutinesDebug", "org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.8.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        // On macOS `local(...)` emits `-Xbootclasspath/a:/Applications/.../lib/nio-fs.jar`
        // (missing the `Contents/` segment), so the IDE can't find the FileSystemProvider
        // and dies before startup. Add the correct boot classpath entry.
        jvmArgs("-Xbootclasspath/a:/Applications/IntelliJ IDEA CE 2.app/Contents/lib/nio-fs.jar")
        // Provide the kotlinx-coroutines-debug classes for the java agent (see dependencies).
        classpath += configurations[coroutinesDebug.name]
    }
}

intellijPlatform {
    // `instrumentCode` needs `java-compiler-ant-tasks` from a blocked repo on this machine.
    // A pure-Kotlin plugin doesn't require IntelliJ's Java instrumentation, so disable it.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261.0"
            untilBuild = "261.*"
        }
    }
}