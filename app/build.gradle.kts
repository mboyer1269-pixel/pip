plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.0.21"
}

android {
    namespace = "ca.michael.pip"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.michael.pip"
        // 31: on-device speech recognition API (FR-2.2). See README "SDK levels".
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // ACP-3: orchestrator hosts allowed at build time, one by one.
        // Empty until the owner answers SPEC Q-1: the ACP client refuses to connect.
        buildConfigField("String[]", "ACP_ALLOWLIST", "new String[]{}")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // SEC-5: minification and resource shrinking on for release.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // SEC-5: debug is clearly separate (own id suffix, label and icon badge).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    // WebSocket client for the outbound ACP connection (the platform has none).
    // See README "Dependencies".
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
