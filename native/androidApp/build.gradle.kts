plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services) // reads google-services.json (Firebase project kawaii-radio-app)
}

android {
    namespace = "uz.kawaii.radio.kr01"
    compileSdk = 37

    defaultConfig {
        // same id + signing key as the old Capacitor app, so this build installs over it
        applicationId = "uz.kawaii.radio.kr01"
        minSdk = 24
        targetSdk = 36
        // CI passes the workflow run number, so every release is a newer versionCode
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        create("kr01") {
            storeFile = file("kr01-release.p12")
            storePassword = "kawaii-kr01"
            keyAlias = "kr01"
            keyPassword = "kawaii-kr01"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("kr01")
        }
        release {
            signingConfig = signingConfigs.getByName("kr01")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // music is streamed straight out of the APK, so keep it uncompressed
        noCompress += listOf("mp3")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp) // chat WebSocket
    implementation(libs.coil.gif) // animated chat stickers
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging) // chat push notifications
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
}
