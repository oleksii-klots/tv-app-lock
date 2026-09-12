plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: env-driven so the keystore never lives in the repo.
// Local/CI debug builds work without any of this.
val releaseKeystore = System.getenv("RELEASE_KEYSTORE")

android {
    namespace = "com.alexk.tvlock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alexk.tvlock"
        minSdk = 21
        targetSdk = 34
        versionCode = 15
        versionName = "1.14"
    }

    if (!releaseKeystore.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (!releaseKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
}
