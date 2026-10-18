package fishmod.pv.api;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Auth for the fishmod.dev Hypixel proxy: shared mod token plus the local player's id. */
public final class FishProxyAuth {
	private static final String MOD_TOKEN = "fishmod123";

	private FishProxyAuth() {}

	public static boolean applyAuthHeaders(HttpRequest.Builder builder, URI uri) {
		var mc = Minecraft.getInstance();
		String caller = mc.player != null ? mc.player.getUUID().toString().replace("-", "") : "";
		builder.header("User-Agent", "Mozilla/5.0").header("X-FishMod-Token", MOD_TOKEN).header("X-FishMod-Caller", caller);
		return true;
	}

	public static Optional<String> userFacingFailure() {
		return Optional.empty();
	}

	public static void notifyPlayerIfNeeded() {}

	public static void handleUnauthorized(HttpRequest request, String body, long ms) {}

	public static CompletableFuture<Optional<String>> bearerTokenAsync() {
		return CompletableFuture.completedFuture(Optional.empty());
	}
}
