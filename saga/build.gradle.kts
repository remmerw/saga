
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.remmerw"
version = "0.1.9"


kotlin {

    androidLibrary {
        namespace = "io.github.remmerw.saga"
        compileSdk = 36
        minSdk = 27



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
    //wasmWasi()



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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
