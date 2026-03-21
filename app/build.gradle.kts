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
                envProps[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
            }
        }
    }
}
fun env(key: String): String = envProps.getProperty(key) ?: System.getenv(key) ?: ""

android {
    namespace = "com.team7.taskflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.team7.taskflow"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Inject .env values into BuildConfig ────────────────────────
        buildConfigField("String", "SUPABASE_URL",         "\"${env("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",    "\"${env("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${env("GOOGLE_WEB_CLIENT_ID")}\"")
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
    implementation(libs.coordinatorlayout)
    implementation(libs.cardview)
    implementation(libs.recyclerview)
    implementation(libs.core.ktx)
    implementation(libs.swiperefreshlayout)

    // Supabase REST API
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.json)
    implementation(libs.play.services.auth)

    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
