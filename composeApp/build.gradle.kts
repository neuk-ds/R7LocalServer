import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val applicationVersion = "2.2.3"
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/build-info/kotlin")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir(generatedBuildInfoDir)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.logback.classic)
            implementation(libs.cronet.embedded)
            implementation(libs.kotlin.csv)
            implementation(libs.apache.poi.ooxml)
        }
    }
}


compose.desktop {
    application {
        mainClass = "ru.mrnds.r7localserver.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "R7LocalServer"
            packageVersion = applicationVersion

            modules("java.naming")

            windows {
                iconFile.set(project.file("src/jvmMain/composeResources/files/icon.ico"))
                perUserInstall = true
                shortcut = true
                menu = true
                dirChooser = true
            }

            linux {
                iconFile.set(project.file("src/jvmMain/composeResources/files/icon.png"))
            }
        }
    }
}

tasks.register<Exec>("packageInnoSetup") {
    group = "distribution"
    description = "Build Windows installer with Inno Setup"

    dependsOn("createDistributable")

    commandLine(
        "C:\\Program Files (x86)\\Inno Setup 6\\ISCC.exe",
        "/DAppVersion=$applicationVersion",
        project.file("installer/r7localserver.iss").absolutePath
    )
}

val generateBuildInfo by tasks.registering(GenerateBuildInfoTask::class) {
    description = "Generate build info Kotlin file"
    appVersion.set(applicationVersion)
    outputDir.set(generatedBuildInfoDir)
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateBuildInfo)
}

abstract class GenerateBuildInfoTask : DefaultTask() {
    @get:Input
    abstract val appVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.file("ru/mrnds/r7localserver/AppBuildInfo.kt")
            .get()
            .asFile

        file.parentFile.mkdirs()

        file.writeText(
            """
            package ru.mrnds.r7localserver

            object AppBuildInfo {
                const val VERSION = "${appVersion.get()}"
            }
            """.trimIndent()
        )
    }
}