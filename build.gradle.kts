import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "org.komlir"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        create("CL", "2026.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.cmake")
        bundledPlugin("com.intellij.nativeDebug")
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            <h3>0.2.0</h3>
            <ul>
              <li>Run MLIR files and folders in CLion's test window, with nested folder suites, source navigation, and rerun failed tests.</li>
              <li>Support multiple RUN directives, continued lines, quoted arguments, and pipelines with accurate failure and cancellation reporting.</li>
              <li>Build required CMake test tools once per run using the selected profile, and preserve FileCheck output during single-file debugging.</li>
              <li>Add optional project-level MLIR language server support using a CMake target or executable path, with build and restart controls.</li>
              <li>Show live LSP diagnostics and operation hover information, and clear stale diagnostics when the server stops.</li>
              <li>Integrate LSP operation, type, and SSA completion, preserving dialect and reference prefixes when inserting suggestions.</li>
              <li>Prefer LSP operation suggestions when available and automatically show local symbol completion after typing @.</li>
            </ul>
        """.trimIndent()
    }
    buildSearchableOptions = false
}

tasks {
    withType<Test> {
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    buildSearchableOptions {
        enabled = false
    }
}
