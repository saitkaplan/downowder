plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.saitkaplan.downowder"
    // Android 16: the latest well-settled release. The app still runs on
    // Android 17; only its newest behavior rules are not targeted yet.
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.saitkaplan.downowder"
        // 29: scoped storage, so MediaStore can write to Download/ with no
        // storage permission.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
