pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pip"

// Pure Kotlin logic (protocol, approval, formats). Builds and tests on any JVM.
include(":core")

// The Android app needs an Android SDK. Without one (for example a sandbox
// that cannot reach Google's Maven), only :core is configured so its tests
// still run. CI and the owner's machine always have an SDK.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK found: configuring :core only.")
}
