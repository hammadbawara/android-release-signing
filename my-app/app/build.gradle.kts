plugins {
    alias(libs.plugins.android.application)
    id("io.github.hammadbawara.android.release-signing")
}

android {
    namespace = "com.hammadbawara.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hammadbawara.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
