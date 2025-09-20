import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        languageVersion = KotlinVersion.KOTLIN_2_0
        apiVersion = KotlinVersion.KOTLIN_2_0
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            languageVersion = KotlinVersion.KOTLIN_2_0
            apiVersion = KotlinVersion.KOTLIN_2_0
        }
    }

    // Kotlin/Native - Linux
    linuxX64()
    linuxArm64()

    // Kotlin/Native - Windows
    mingwX64()

    // Kotlin/Native - Apple
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()
    watchosArm64()
    watchosX64()

    sourceSets {
        commonMain {
            compilerOptions {
                languageVersion = KotlinVersion.KOTLIN_2_0
                apiVersion = KotlinVersion.KOTLIN_2_0
            }
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

// tip: it will take about 1 hour after GitHub Actions finished
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("li.songe", "json5", "0.4.0")

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
