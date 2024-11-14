import java.util.regex.Pattern.compile

plugins {
    alias(libs.plugins.android.application)
}


android {
    namespace = "com.example.healthtrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.healthtrack"
        minSdk = 31
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
    implementation("com.jjoe64:graphview:4.2.1"){
        exclude(group = "com.android.support", module = "support-v4")
    }
    implementation("androidx.core:core:1.13.0") {
        exclude(group = "com.android.support", module = "support-v4")
    }
    implementation(libs.constraintlayout)
    implementation("com.jjoe64:graphview:4.2.2")
    implementation(libs.androidx.bluetooth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
