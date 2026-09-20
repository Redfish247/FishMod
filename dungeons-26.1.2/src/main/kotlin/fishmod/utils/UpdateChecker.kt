package fishmod.utils

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object UpdateChecker {

    private const val PROJECT_ID = "7dRE7dga"
    private const val MC_VERSION = "26.1.2"
    private val HTTP: HttpClient = HttpClient.newHttpClient()

    val links: Map<String, String> = mapOf(
        "modrinth" to "https://modrinth.com/mod/$PROJECT_ID/versions",
        "github" to "https://github.com/Redfish247/FishModDungeons/releases/latest",
    )

    fun latestVersion(cb: (String?) -> Unit) {
        CompletableFuture.runAsync {
            cb(runCatching {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/project/$PROJECT_ID/version"))
                    .header("User-Agent", "FishModDungeons/update-check (github.com/Redfish247/FishModDungeons)")
                    .timeout(Duration.ofSeconds(10)).GET().build()
                val body = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()
                JsonParser.parseString(body).asJsonArray
                    .map { it.asJsonObject }
                    .filter { v ->
                        v.getAsJsonArray("game_versions").any { it.asString == MC_VERSION } &&
                            v.getAsJsonArray("loaders").any { it.asString == "fabric" }
                    }
                    .maxByOrNull { it.get("date_published").asString }
                    ?.get("version_number")?.asString
            }.getOrNull())
        }
    }
}
