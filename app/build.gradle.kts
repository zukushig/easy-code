plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.codetree.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codetree.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // MVP 阶段无 release keystore 时用 debug 签名，保证产物可安装（正式签名由 CI secrets 注入）
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":runtime:api"))
    implementation(project(":runtime:safe-shell"))
    implementation(project(":feature:learn"))
    implementation(project(":feature:dictionary"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:library"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 组合根构造 OpenAiCompatibleProvider 需要 OkHttpClient 类型（core:data 以 implementation 引入，不透出）
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
