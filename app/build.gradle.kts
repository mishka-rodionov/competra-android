import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    kotlin("kapt")
}

// Ключи AppMetrica читаются из local.properties (не коммитятся).
// В CI или у разработчика без ключей значение будет пустым — приложение
// тогда не активирует SDK (см. CompetraApp.onCreate()).
val appMetricaProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val appMetricaKeyDebug: String = appMetricaProps.getProperty("APPMETRICA_API_KEY_DEBUG", "")
val appMetricaKeyRelease: String = appMetricaProps.getProperty("APPMETRICA_API_KEY_RELEASE", "")

// Подпись release-сборки читается из keystore.properties (не коммитится, см. keystore.properties.example)
// либо из переменных окружения KEYSTORE_* (для CI). Если ничего не задано — release будет
// подписан отладочным ключом Android Studio, что не годится для загрузки в сторы.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}
fun keystoreProp(propName: String, envName: String): String =
    keystoreProps.getProperty(propName) ?: System.getenv(envName) ?: ""

val releaseStoreFile: String = keystoreProp("storeFile", "KEYSTORE_FILE")
val releaseStorePassword: String = keystoreProp("storePassword", "KEYSTORE_STORE_PASSWORD")
val releaseKeyAlias: String = keystoreProp("keyAlias", "KEYSTORE_KEY_ALIAS")
val releaseKeyPassword: String = keystoreProp("keyPassword", "KEYSTORE_KEY_PASSWORD")
val hasReleaseSigningConfig: Boolean = releaseStoreFile.isNotBlank()

android {
    namespace = "com.competra.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.competra"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Измерение «магазин»: один applicationId на все сторы, разные source set'ы
    // под стор-специфику (HMS/RuStore SDK и т.п. добавляются отдельными задачами).
    flavorDimensions += "store"
    productFlavors {
        create("gplay") { dimension = "store" }
        create("rustore") { dimension = "store" }
        create("huawei") { dimension = "store" }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
            buildConfigField("String", "APPMETRICA_API_KEY", "\"$appMetricaKeyDebug\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "APPMETRICA_API_KEY", "\"$appMetricaKeyRelease\"")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.adaptive)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(project(":data:navigation"))
    implementation(project(":data:local"))
    implementation(project(":data:remote"))

    implementation(project(":domain"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:events"))
    implementation(project(":feature:center"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:diary"))
    implementation(project(":feature:clubs"))
    implementation(project(":feature:rating"))
    implementation(project(":core:resources"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:nfchelper"))
    implementation(project(":core:ui"))
    implementation(project(":core:eventdetails"))
    implementation(project(":core:sync"))
    implementation(project(":core:analytics"))
    implementation(project(":core:tracking"))

    implementation(libs.kotlinx.serialization.json)

    //compose navigation
    implementation(libs.compose.navigation)

    //
    implementation(libs.ui)
    implementation(libs.androidx.material)
    implementation(kotlin("reflect"))

    //koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.navigation)
    implementation(libs.koin.compose)
    implementation(libs.koin.workmanager)

    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.chucker.library)
    releaseImplementation(libs.library.no.op)

    //firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
}