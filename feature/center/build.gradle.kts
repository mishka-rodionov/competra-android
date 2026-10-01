plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android)
    id("kotlinx-serialization")
    alias(libs.plugins.compose.compiler)
    kotlin("kapt")
}

android {
    namespace = "com.competra.center"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.window.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.kotlinx.serialization.json)

    implementation(project(":data:navigation"))
    implementation(project(":data:local"))
    implementation(project(":data:remote"))
    implementation(project(":domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
    implementation(project(":core:nfchelper"))
    implementation(project(":core:ui"))
    implementation(project(":core:analytics"))
    // Экран заявок на привязку результатов живёт в подграфе деталей события — см. competitionLinkRequestsDestination.
    implementation(project(":core:eventdetails"))
    implementation(project(":utils"))

    //compose navigation
    implementation(libs.compose.navigation)
    //compose
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    //koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.navigation)
    implementation(libs.koin.compose)

    //osmdroid
    implementation(libs.osmdroid)

    //Jsoup — парсинг HTML-протокола результатов при импорте
    implementation(libs.jsoup)
}