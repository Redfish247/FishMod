package fishmod.utils

import java.net.http.HttpClient
import java.time.Duration

object Http {
    @JvmField
    val CLIENT: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
}
