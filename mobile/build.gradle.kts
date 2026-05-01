plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    maven { url = uri("https://maven.fabric.io/public") }
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

android {
    compileSdk = 36
    namespace = "com.siliconlabs.bledemo"

    defaultConfig {
        minSdk = 30
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZE=0N")
            }
        }
    }

    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")

            jniLibs { useLegacyPackaging = true }
        }
    }



    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")

            ndk {
                abiFilters.add("armeabi-v7a")
                abiFilters.add("arm64-v8a")
                abiFilters.add("x86_64")
                abiFilters.add("x86")
            }
        }
        debug {
            isDebuggable = true
        }
    }



    applicationVariants.all{
        assembleProvider.get().doLast{
            copy{
                from("Builds/${rootProject.name}/${project.name}/outputs/apk/Si-Connect/release/mobile-Si-Connect-release.apk")
                into ("Builds")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        // Or, if you prefer, you can continue to check for errors in release builds,
        // but continue the build even when errors are found:
        abortOnError = false
    }

    val versionDim = "version"
    flavorDimensions.add(versionDim)

    productFlavors {
        create("Si-Connect") {
            dimension = versionDim
            applicationId = "com.siliconlabs.bledemo"
            versionCode = 76
            versionName = "3.2.2"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    kapt {
        correctErrorTypes = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("libs")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    applicationVariants.all {
        outputs.all {
            val apkName = "mobile-${name}-${versionName}.apk"
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName = apkName
        }
    }

}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies {
    // Exclude local gdx jars so we rely on maven artifacts with natives
    implementation(fileTree("libs") {
        include("*.jar", "*.so")
        exclude("gdx*.jar", "gdx-backend-android*.jar")
    })

    // LibGDX updated to 1.13.5
    val gdxVersion = "1.13.5"
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    // This dependency is downloaded from the Google’s Maven repository.
    // Make sure you also include that repository in your project's build.gradle file.
    implementation("com.google.android.play:feature-delivery:2.1.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:app-update:2.1.0")


    // For Kotlin users, also import the Kotlin extensions library for Play Feature Delivery:
    implementation("com.google.android.play:feature-delivery-ktx:2.1.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    // androidx
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // UI components
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.3")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("io.github.g00fy2.quickie:quickie-bundled:1.11.0")

    implementation("com.google.android.flexbox:flexbox:3.0.0")
    //MPAndroidChart is added as jar library file
    //implementation("com.github.PhilJay:MPAndroidChart:v3.0.3")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.3")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:2.9.3")

    // Dependency injection
    implementation("com.google.dagger:hilt-android:2.57.1")
    kapt("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("com.google.dagger:hilt-android:2.51.1")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    // View binding
    implementation("com.github.kirich1409:viewbindingpropertydelegate-noreflection:1.5.9")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Parsing
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("androidx.activity:activity:1.10.1")

    // Only used for Int.pow() method in a couple of places
    implementation("com.google.guava:guava:33.4.8-android")

    // Coil
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // instrumented tests
    testImplementation("junit:junit:4.13.2")
    androidTestUtil("androidx.test:orchestrator:1.6.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    //Matter
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    //DevKitSensor917 Demo
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")

    implementation ("com.daimajia.swipelayout:library:1.2.0@aar")
    //Material Design
    implementation("com.google.android.material:material:1.12.0")

    implementation ("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation ("io.github.mayzs:paho.mqtt.android:1.2.1")
    implementation ("androidx.work:work-runtime:2.8.1")


    //Channel Sounding
    implementation("com.github.skydoves:balloon:1.4.7")

    //JetPack compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose")
    // Material 3
    implementation("androidx.compose.material3:material3")
    // Optional – Material Icons (if you need them)
    implementation("androidx.compose.material:material-icons-extended")
    // Lifecycle integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.pranavpandey.android:dynamic-toasts:4.3.0")

}
