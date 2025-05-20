plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.bejam"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bejam"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.example.bejam"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.messaging.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("net.openid:appauth:0.11.1") // Für OAuth2 mit PKCE
    implementation("com.squareup.okhttp3:okhttp:4.10.0") // Für HTTP-Requests
    implementation("com.squareup.moshi:moshi:1.13.0") // JSON-Verarbeitung
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.github.bumptech.glide:glide:4.15.1")      // latest as of today
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.google.android.exoplayer:exoplayer:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
}