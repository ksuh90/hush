plugins {
    id("com.android.application")
}

android {
    namespace = "app.hush"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.hush"
        minSdk = 33
        targetSdk = 33
        versionCode = 8
        versionName = "0.4.2"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Hush is installed by hand, so the debug key signs the release build.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        // The sherpa-onnx libraries come stripped. This stops a warning when no NDK is installed.
        jniLibs.keepDebugSymbols += "**/*.so"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Hush is not for Google Play, and the target is an arm64 phone with Android 13.
        // Version checks change when new releases come out, so they do not stop the build.
        disable += setOf(
            "ExpiredTargetSdkVersion", "OldTargetApi", "ChromeOsAbiSupport",
            "AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable",
        )
        warningsAsErrors = true
        abortOnError = true
    }
}

androidComponents {
    onVariants { variant ->
        // AGP leaves out v1 (JAR) signing when minSdk is 24 or more. The Samsung installer
        // can refuse an APK without it ("App not installed"), so sign with all schemes.
        variant.signingConfig.enableV1Signing.set(true)
        variant.signingConfig.enableV2Signing.set(true)
        variant.signingConfig.enableV3Signing.set(true)
    }
}

dependencies {
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx-static-link-onnxruntime:2.0.1@aar")
    testImplementation("junit:junit:4.13.2")
}

// Checks the release APK that is installed by hand. Run: ./gradlew checkReleaseApk
tasks.register<Exec>("checkReleaseApk") {
    dependsOn("assembleRelease")
    val tools = androidComponents.sdkComponents.sdkDirectory.get().dir("build-tools/${android.buildToolsVersion}")
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get()
    commandLine(layout.projectDirectory.file("../scripts/check-apk.sh").asFile, tools.asFile, apk.asFile)
}
