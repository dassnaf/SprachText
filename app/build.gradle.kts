plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.kdvstudio.sprachtext"
    compileSdk = 34
    defaultConfig {
        applicationId = "de.kdvstudio.sprachtext"
        minSdk = 33
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("sprachtext.keystore")
            storePassword = "sprachtext"
            keyAlias = "sprachtext"
            keyPassword = "sprachtext"
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
