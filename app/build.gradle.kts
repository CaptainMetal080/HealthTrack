plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.healthtrack"
    compileSdk = 34
    packagingOptions {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    defaultConfig {
        applicationId = "com.example.healthtrack"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation (libs.mpandroidchart)
    implementation(libs.constraintlayout)
    implementation(libs.androidx.bluetooth)
    implementation(libs.identity.jvm)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation("com.google.firebase:firebase-auth:23.1.0")
    implementation ("com.google.firebase:firebase-database:20.0.5") // For Firebase Realtime Database
    implementation("com.google.firebase:firebase-firestore:24.11.1") // For Firestore
    implementation ("com.google.firebase:firebase-storage:20.0.0")  // For Firebase Storage if you're uploading files
    implementation ("com.google.firebase:firebase-analytics:21.0.0")
    implementation ("com.google.firebase:firebase-messaging:23.1.1")
}

apply(plugin = "com.google.gms.google-services")

