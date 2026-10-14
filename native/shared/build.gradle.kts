plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

/*
 * Everything the user sees lives here (commonMain): screens, design system, playlist, lyrics,
 * update checks. Platform work (audio engine, files, installer) sits behind the `Platform`
 * interface, so an iOS target only has to implement that interface.
 */
kotlin {
    android {
        namespace = "uz.kawaii.radio.shared"
        compileSdk = 37
        minSdk = 24
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.coil.compose)
        }
    }
}

compose.resources {
    packageOfResClass = "uz.kawaii.radio.res"
    publicResClass = true
}
