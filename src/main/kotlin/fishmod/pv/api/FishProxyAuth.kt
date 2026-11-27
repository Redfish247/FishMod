package fishmod.pv.api

import java.net.URI
import java.net.http.HttpRequest
import java.util.Optional
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft

object FishProxyAuth {
    private const val MOD_TOKEN = "fishmod123"

    @JvmStatic
    fun applyAuthHeaders(builder: HttpRequest.Builder, uri: URI): Boolean {
        val caller = Minecraft.getInstance().player?.uuid?.toString()?.replace("-", "") ?: ""
        builder.header("User-Agent", "Mozilla/5.0").header("X-FishMod-Token", MOD_TOKEN).header("X-FishMod-Caller", caller)
        return true
    }

    @JvmStatic
    fun userFacingFailure(): Optional<String> =
        Optional.ofNullable(HypixelApiClient.lastFailure).map { "Profiles request failed ($it)" }

    @JvmStatic
    fun notifyPlayerIfNeeded() {}

    @JvmStatic
    fun handleUnauthorized(request: HttpRequest?, body: String?, ms: Long) {}

    @JvmStatic
    fun bearerTokenAsync(): CompletableFuture<Optional<String>> = CompletableFuture.completedFuture(Optional.empty())
}
