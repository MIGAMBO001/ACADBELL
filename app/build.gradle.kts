plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.acadbell.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.acadbell.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Optional release signing. The keystore is NOT committed to source control.
        // Generate one with:
        //   keytool -genkeypair -v -keystore app/acadbell-release.jks -alias acadbell \
        //           -keyalg RSA -keysize 2048 -validity 10000
        // If the file is absent, release builds simply fall back to unsigned output.
        create("release") {
            val keystore = file("acadbell-release.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("ACADBELL_STORE_PASSWORD") ?: "acadbell"
                keyAlias = System.getenv("ACADBELL_KEY_ALIAS") ?: "acadbell"
                keyPassword = System.getenv("ACADBELL_KEY_PASSWORD") ?: "acadbell"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (file("acadbell-release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room — local persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore — preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager — background maintenance / alarm recovery
    implementation("androidx.work:work-runtime-ktx:2.9.0")


    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
