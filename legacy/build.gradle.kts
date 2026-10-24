import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "app.orchard.tv.legacy"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // Separate storage and signing identity allow a reversible trial alongside modern Aerial.
        applicationId = "app.orchard.tv.legacy"
        minSdk = 19
        targetSdk = 28
        versionCode = 2
        versionName = "1.43-legacy.2"
        testInstrumentationRunner = "app.orchard.tv.legacy.LegacyInstrumentation"
    }
    flavorDimensions += "home"
    productFlavors {
        create("standard") { dimension = "home" }
        create("guard") {
            dimension = "home"
            versionNameSuffix = "-guard"
        }
    }
    val signingFile = rootProject.file("local/legacy-signing.properties")
    signingConfigs {
        if (signingFile.exists()) {
            val values = Properties().apply { signingFile.inputStream().use { load(it) } }
            create("legacy") {
                storeFile = rootProject.file("local/" + values.getProperty("storeFile"))
                storePassword = values.getProperty("storePassword")
                keyAlias = values.getProperty("keyAlias")
                keyPassword = values.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("legacy") ?: signingConfigs.getByName("debug")
        }
    }
    testBuildType = "release"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets.getByName("main") {
        assets.srcDir("../app/src/main/assets")
        res.srcDir("src/main/res")
    }
    lint {
        warningsAsErrors = true
        // A legacy-only APK intentionally targets old Android; NewApi remains fatal.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi", "GradleDependency")
    }
}

// No Compose, AndroidX, Media3, Kotlin runtime, native libraries or multidex on Dalvik.
