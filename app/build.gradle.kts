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
        // versionCode 规则：major * 10000 + minor * 100 + patch
        // 1.5.0 → 10500。和 iOS 版保持同一个版本号。
        versionCode = 10503
        versionName = "1.5"
    }

    signingConfigs {
        create("release") {
            // 这是**公开的、开源项目共用的**密钥，密码写在 README 里。
            //
            // 为什么不用私钥：周目是靠 GitHub Release 分发无签名/自签名包的，
            // 如果每次都用不同密钥签，用户就没法覆盖升级（Android 会拒绝签名不一致的包）。
            // 公开密钥的代价是别人也能签一个同名包——但本项目本来就不声称 APK 来源可信，
            // 想验证来源请自己从源码编译，或者对比 Release 里附的 sha256。
            storeFile = file("../keystore/zhoumu.jks")
            storePassword = "zhoumu2026"
            keyAlias = "zhoumu"
            keyPassword = "zhoumu2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
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
