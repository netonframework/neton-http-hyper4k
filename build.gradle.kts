plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    `maven-publish`
}

group = "com.netonframework"
version = "0.2.0"

repositories {
    mavenCentral()
}

kotlin {
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        val nativeMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api("com.netonstream:neton-http:1.0.0-beta2")
                api("com.netonstream:neton-core:1.0.0-beta2")
                implementation("com.netonstream:neton-logging:1.0.0-beta2")
                implementation("com.netonframework:hyper4k:0.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            }
        }
        val nativeTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // The conformance streaming checks talk BSD sockets, so they live on the
        // Apple targets rather than in nativeTest, which mingw shares.
        val appleTest by creating {
            dependsOn(nativeTest)
        }

        macosArm64Main.get().dependsOn(nativeMain)
        macosX64Main.get().dependsOn(nativeMain)
        linuxX64Main.get().dependsOn(nativeMain)
        linuxArm64Main.get().dependsOn(nativeMain)
        mingwX64Main.get().dependsOn(nativeMain)
        macosArm64Test.get().dependsOn(appleTest)
        macosX64Test.get().dependsOn(appleTest)
        linuxX64Test.get().dependsOn(nativeTest)
        linuxArm64Test.get().dependsOn(nativeTest)
        mingwX64Test.get().dependsOn(nativeTest)
    }
}
