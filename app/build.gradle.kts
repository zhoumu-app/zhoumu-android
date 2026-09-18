plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zhoumu.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhoumu.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    // 桌面小组件
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // 课表的 JSON 编解码（要能在单元测试里跑，所以不用 Android 专属的 org.json）
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
