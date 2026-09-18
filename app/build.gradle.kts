import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "in.miaolibrary.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.miaolibrary.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

val generatedLibraryAssets = layout.buildDirectory.dir("generated/libraryIcons/assets")
val generatedLibraryRes = layout.buildDirectory.dir("generated/libraryIcons/res")

val prepareLibraryIcons by tasks.registering {
    inputs.file(rootProject.file("advanced_library_icons.zip"))
    outputs.dir(generatedLibraryAssets)
    outputs.dir(generatedLibraryRes)
    doLast {
        copy {
            from(zipTree(rootProject.file("advanced_library_icons.zip")))
            include("advanced_library_icons/*.svg")
            into(generatedLibraryAssets.get().asFile)
        }
        copy {
            from(zipTree(rootProject.file("advanced_library_icons.zip"))) {
                include("advanced_library_icons/App_icon.png")
                eachFile { relativePath = RelativePath(true, "app_icon.png") }
                includeEmptyDirs = false
            }
            into(generatedLibraryRes.get().asFile.resolve("drawable"))
        }
    }
}

android.sourceSets["main"].assets.srcDir(generatedLibraryAssets)
android.sourceSets["main"].res.srcDir(generatedLibraryRes)
tasks.named("preBuild").configure { dependsOn(prepareLibraryIcons) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.caverock:androidsvg-aar:1.4")
}
