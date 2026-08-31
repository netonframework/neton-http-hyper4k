pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "neton-http-hyper4k"

include("examples:bench")

if (file("../neton").isDirectory) includeBuild("../neton")
if (file("../hyper4k").isDirectory) includeBuild("../hyper4k")
