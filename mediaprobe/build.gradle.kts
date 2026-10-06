plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.project.lol.mediaprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.project.lol.mediaprobe"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
