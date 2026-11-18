plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * RC42 onward: NEVER change the production applicationId suffix or the
 * signing certificate. Android requires BOTH to match for in-place updates.
 * The CI pipeline supplies private signing material through GitHub Actions
 * secrets only; never commit a private keystore to this repository.
 */
val signingFile = System.getenv("KEYPOP_SIGNING_KEYSTORE_PATH")
val signingPassword = System.getenv("KEYPOP_SIGNING_STORE_PASSWORD")
val signingAlias = System.getenv("KEYPOP_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("KEYPOP_SIGNING_KEY_PASSWORD")
val hasSigning = listOf(signingFile, signingPassword, signingAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.aetherkeys.keyboard"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aetherkeys.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.42-rc42"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("keypopStable") {
            if (hasSigning) {
                storeFile = file(signingFile!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildTypes {
        debug {
            // Fixed preview identity across future revisions. Debug builds are
            // test-only; with signing configured, even debug APKs can update.
            applicationIdSuffix = ".rc42preview"
            versionNameSuffix = "-debug-test-only"
            if (hasSigning) signingConfig = signingConfigs.getByName("keypopStable")
        }
        release {
            // Fixed, permanent production identity for RC42+ upgrades.
            applicationIdSuffix = ".stablev2"
            if (hasSigning) signingConfig = signingConfigs.getByName("keypopStable")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
