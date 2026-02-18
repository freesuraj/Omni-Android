plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.suraj.apps.omni"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.suraj.apps.Doc2Quiz"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val uploadStoreFile = providers.gradleProperty("OMNI_UPLOAD_STORE_FILE").orNull
    val uploadStorePassword = providers.gradleProperty("OMNI_UPLOAD_STORE_PASSWORD").orNull
    val uploadKeyAlias = providers.gradleProperty("OMNI_UPLOAD_KEY_ALIAS").orNull
    val uploadKeyPassword = providers.gradleProperty("OMNI_UPLOAD_KEY_PASSWORD").orNull

    if (
        uploadStoreFile != null &&
        uploadStorePassword != null &&
        uploadKeyAlias != null &&
        uploadKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(uploadStoreFile)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        // AGP/lint bug observed when analyzing JVM test source in this scaffold.
        checkTestSources = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(project(":feature:library"))
    implementation(project(":feature:audio"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:quiz"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:summary"))
    implementation(project(":feature:qa"))
    implementation(project(":feature:analysis"))
    implementation(project(":feature:paywall"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
