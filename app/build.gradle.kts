import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun readAiConfig(name: String, defaultValue: String): String {
    return providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: defaultValue
}

fun asBuildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.in6222.litsync"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.in6222.litsync"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AI_PROVIDER", asBuildConfigString(readAiConfig("AI_PROVIDER", "BIGMODEL")))
        buildConfigField("String", "AI_BASE_URL", asBuildConfigString(readAiConfig("AI_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/")))
        buildConfigField("String", "AI_MODEL", asBuildConfigString(readAiConfig("AI_MODEL", "glm-4.7-flash")))
        buildConfigField("String", "AI_API_KEY", asBuildConfigString(readAiConfig("AI_API_KEY", "")))
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
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.work.runtime)
    implementation(libs.retrofit)
    implementation(libs.tikxml.core)
    implementation(libs.tikxml.annotation)
    implementation(libs.tikxml.retrofit)
    annotationProcessor(libs.tikxml.processor)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.viewpager2)
    implementation(libs.logging.interceptor)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
