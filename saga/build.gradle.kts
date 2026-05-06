plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.remmerw"
version = "0.2.1"


kotlin {

    android {
        namespace = "io.github.remmerw.saga"
        compileSdk = 37
        minSdk = 27

        withHostTest {  }

        // Opt-in to enable and configure device-side (instrumented) tests
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxArm64()
    linuxX64()
    //wasmJs()
    //wasmWasi()
    js {
        browser()
        nodejs()
    }



    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}




mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "saga", version.toString())

    pom {
        name = "saga"
        description = "In-Memory Database Library"
        inceptionYear = "2025"
        url = "https://github.com/remmerw/saga/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "remmerw"
                name = "Remmer Wilts"
                url = "https://github.com/remmerw/"
            }
        }
        scm {
            url = "https://github.com/remmerw/saga/"
            connection = "scm:git:git://github.com/remmerw/saga.git"
            developerConnection = "scm:git:ssh://git@github.com/remmerw/saga.git"
        }
    }
}
