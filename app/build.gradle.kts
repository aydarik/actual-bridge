plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "de.gumerbaev.actual"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val appVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
    val appVersionName = project.findProperty("versionName") as? String ?: "1.0.0"

    defaultConfig {
        applicationId = "de.gumerbaev.actual"
        minSdk = 33
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:${libs.versions.coreKtx.get()}")
        force("androidx.core:core-ktx:${libs.versions.coreKtx.get()}")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}