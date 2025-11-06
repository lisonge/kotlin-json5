@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

val versionConfigure: KotlinCommonCompilerOptions.() -> Unit = {
    languageVersion = KotlinVersion.KOTLIN_2_0
    apiVersion = KotlinVersion.KOTLIN_2_0
    if (this is KotlinJvmCompilerOptions) {
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions(versionConfigure)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    compilerOptions(versionConfigure)

    androidNativeArm64()
    androidNativeX64()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js().nodejs()

    jvm { compilerOptions(versionConfigure) }

    linuxArm64()
    linuxX64()

    macosArm64()
    macosX64()

    mingwX64()

    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()

    wasmJs().nodejs()
    wasmWasi().nodejs()

    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
    watchosX64()

    sourceSets {
        commonMain {
            compilerOptions(versionConfigure)
            dependencies {
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

mavenPublishing {
    if (properties.contains("signing.keyId")) {
        publishToMavenCentral()
        signAllPublications()
    }
    coordinates("li.songe", "json5", "0.4.1")

    val repoUrl = "https://github.com/lisonge/kotlin-json5"
    pom {
        name.set("Kotlin Serialization JSON5")
        description.set("Kotlin Multiplatform Serialization JSON5 library")
        url.set(repoUrl)

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                name.set("lisonge")
                email.set("i@songe.li")
                url.set("https://github.com/lisonge")
            }
        }
        scm {
            url.set(repoUrl)
        }
    }
}
