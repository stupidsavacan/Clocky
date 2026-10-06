plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Keep the AOSP package namespace during the direct-port phase so
    // source imports and relative manifest class names remain unchanged.
    namespace = "com.android.deskclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stupidsavacan.clocky"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-aosp-port"
    }

    buildFeatures {
        buildConfig = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Robolectric smoke tests inflate real app resources/themes (Issue #31 regression guard).
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.collection:collection:1.4.5")
    implementation("androidx.arch.core:core-common:2.2.0")
    implementation("androidx.lifecycle:lifecycle-common:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.percentlayout:percentlayout:1.0.0")
    implementation("androidx.transition:transition:1.5.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.legacy:legacy-support-core-ui:1.0.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.legacy:legacy-support-v13:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Pure JVM tests cover deterministic Clocky logic; device/launcher behavior stays a Desktop handoff.
    testImplementation("junit:junit:4.13.2")
    // Robolectric covers resource/theme inflation that pure JVM tests cannot (e.g. Material views
    // under the configured Activity theme). It is not a substitute for launcher/device checks.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
