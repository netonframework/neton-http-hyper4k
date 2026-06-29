# neton-http-hyper4k

Optional Tokio + Hyper server adapter for Neton on Kotlin/Native.

This project is independent from the Neton framework repository. Applications choose the adapter
at compile time by passing its constructor to the Neton HTTP component:

```kotlin
dependencies {
    implementation("com.netonframework:neton-http-hyper4k:0.1.0")
}
```

```kotlin
import neton.http.http
import neton.http.hyper4k.Hyper4kHttpAdapter

Neton.run(args) {
    http(::Hyper4kHttpAdapter) {
        port = 8080
    }
}
```

The default `http { }` overload remains Ktor. Adapter selection is not an `application.conf`
setting because Kotlin/Native links implementations at compile time.

For local development, place `neton`, `hyper4k`, and this repository in the same parent directory,
then run:

```bash
../neton/gradlew -p . macosArm64Test
```
