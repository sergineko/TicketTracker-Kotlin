plugins { alias(libs.plugins.android.library) }

android {
    namespace = "es.sergiotech.tickettracker.core.documents"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.mlkit.text)
    implementation(libs.exif)
}
