//import androidx.glance.appwidget.compose

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Add this line to register the KSP plugin
    alias(libs.plugins.ksp) apply false

    alias(libs.plugins.hilt.android) apply false
}