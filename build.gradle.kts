plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "domain/src/**/*.kt", "core/*/src/**/*.kt")
        ktfmt("0.61").kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "core/*/*.gradle.kts", "domain/*.gradle.kts")
        ktfmt("0.61").kotlinlangStyle()
    }
}
