import java.util.regex.Pattern.compile

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.healthtrack"
    compileSdk = 34

    packagingOptions {
        exclude("META-INF/versions/9/OSGI-INF/MANIFEST.MF");
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
