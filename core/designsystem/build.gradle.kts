plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "es.sergiotech.tickettracker.core.designsystem"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.ui)
    api(libs.compose.icons)
    implementation(libs.compose.preview)
}
