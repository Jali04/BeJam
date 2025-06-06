// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id ("androidx.navigation.safeargs") version "2.5.3" apply false
    id("org.jetbrains.kotlin.jvm")     version "2.1.0" apply false
}