package fishmod.utils

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Fallback "update available" source used by [InstallHeartbeat] when the fishmod.dev worker
 * broadcast has no latestVersion set — queries Modrinth directly so a release no longer needs a
 * manual worker.js bump. Picks the newest published version for this Minecraft version + Fabric.
 */
object UpdateChecker {

    private const val PROJECT_ID = "7dRE7dga"
    private const val MC_VERSION = "26.1.2" // keep in sync with gradle.properties minecraft_version
    private val HTTP: HttpClient = HttpClient.newHttpClient()

    /** Links for the update box when the notice comes from here rather than the worker. */
    val links: Map<String, String> = mapOf(
        "modrinth" to "https://modrinth.com/mod/$PROJECT_ID/versions",
        "github" to "https://github.com/Redfish247/FishModDungeons/releases/latest",
    )

    /** Calls back (off-thread) with the newest matching Modrinth version_number, or null on any failure. */
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
                    .maxByOrNull { it.get("date_published").asString } // ISO-8601 UTC -> lexical == chronological
                    ?.get("version_number")?.asString
            }.getOrNull())
        }
    }
}
