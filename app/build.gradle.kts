plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.padnakasht.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.padnakasht.nativeapp"
        minSdk = 26
        targetSdk = 35
        // CI run number keeps versionCode strictly increasing so new builds can update old installs.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing material must be supplied by the CI secret store, never committed.
    // Without configured secrets, local/CI release builds remain unsigned and must not be published.
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    val releaseKeystore = releaseKeystorePath?.let { file(it) }
    val releaseSigningConfigured = releaseKeystore != null &&
        releaseKeystore.isFile &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = requireNotNull(releaseKeystore)
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        release {
            // Shrinking stays off until Room/SQLCipher keep rules are verified on a device.
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Keep Java, Kotlin and KSP bytecode targets aligned in CI and local builds.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
