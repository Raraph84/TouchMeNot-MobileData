plugins {
    id("com.android.application")
}

android {
    namespace = "com.djay.touchmenot_mm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.djay.touchmenot_mm"
        minSdk = 26
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(files("libs/xposed-api-82.jar"))
}


