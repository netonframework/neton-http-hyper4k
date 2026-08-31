import neton.core.Neton
import neton.http.http
import neton.http.hyper4k.Hyper4kHttpAdapter
import neton.routing.*

/**
 * Neton TechEmpower-style benchmark application (Hyper4k engine).
 *
 * The exact same routes are used for the Ktor CIO and May4k bench apps; the only
 * difference between the three apps is the adapter passed to http(...).
 */
fun main(args: Array<String>) {
    Neton.run(args) {

        http(::Hyper4kHttpAdapter) {
            port = 8090
        }

        routing {
            get("/plaintext") {
                "Hello, World!"
            }
            get("/json") {
                mapOf("message" to "Hello, World!")
            }
        }
    }
}
