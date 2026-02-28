import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ── Load .env from project root ────────────────────────────────────────────
val envFile = rootProject.file(".env")
val envProps = Properties()
if (envFile.exists()) {
    envFile.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            val idx = trimmed.indexOf('=')
            if (idx > 0) {
                val key   = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                envProps[key] = value
            }
        }
    }
}

fun env(key: String): String =
    envProps.getProperty(key) ?: System.getenv(key) ?: ""

android {
    namespace = "com.example.project_mobile"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.project_mobile"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Inject .env values into BuildConfig ────────────────────────
        buildConfigField("String", "SUPABASE_URL",      "\"${env("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${env("SUPABASE_ANON_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.okhttp)
    implementation(libs.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}