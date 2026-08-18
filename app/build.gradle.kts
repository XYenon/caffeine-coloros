plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "bid.xyenon.caffeine.coloros"
    compileSdk = 35

    defaultConfig {
        applicationId = "bid.xyenon.caffeine.coloros"
        minSdk = 28
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
        multiDexKeepProguard = file("multidex-rules.pro")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
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
    compileOnly(libs.xposed.api)
    compileOnly(libs.libxposed.api)
    implementation(libs.dexkit)
    implementation(libs.gson)
}
