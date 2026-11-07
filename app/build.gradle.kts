plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dai.babyfeed"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dai.babyfeed"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    /* 固定签名：所有构建共用同一把钥匙，新版本才能直接覆盖安装。
       本地工程在 android/ 子目录、GitHub 上是平铺到根目录，两种位置都找一遍 */
    val keyStoreFile = listOf(
        rootProject.file("keystore.p12"),
        rootProject.file("../keystore.p12")
    ).firstOrNull { it.exists() }

    signingConfigs {
        create("fixed") {
            if (keyStoreFile != null) {
                storeFile = keyStoreFile
                storeType = "PKCS12"
                storePassword = "babyfeed2026"
                keyAlias = "babyfeed"
                keyPassword = "babyfeed2026"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (keyStoreFile != null) signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (keyStoreFile != null) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    // 让网页资产不压缩，WebView 加载更稳
    androidResources {
        noCompress += "html"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
