import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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
        val drawableDir = generatedLibraryRes.get().asFile.resolve("drawable").apply { mkdirs() }
        val zipImage = zipTree(rootProject.file("advanced_library_icons.zip"))
            .matching { include("advanced_library_icons/App_icon.png") }
            .singleFile

        // Remove transparent outer margins from the supplied launcher artwork.
        // Android launchers can apply their own mask/scale, so giving them a
        // tightly cropped source keeps the actual logo as large as possible.
        val source = ImageIO.read(zipImage)
            ?: error("Unable to read advanced_library_icons/App_icon.png")
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val alpha = (source.getRGB(x, y) ushr 24) and 0xFF
                if (alpha > 8) {
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }

        if (right >= left && bottom >= top) {
            val cropped = source.getSubimage(
                left,
                top,
                right - left + 1,
                bottom - top + 1
            )
            ImageIO.write(cropped, "png", drawableDir.resolve("app_icon.png"))
        } else {
            ImageIO.write(source, "png", drawableDir.resolve("app_icon.png"))
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
