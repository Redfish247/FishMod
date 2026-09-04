package twitchbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain data class persisted as {@code config/twitch-bridge.json}.
 *
 * <p>Everything here is read-only Twitch viewing, so there is deliberately no OAuth token /
 * client-id field — the IRC client connects anonymously.</p>
 */
public class TwitchBridgeConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve("twitch-bridge.json");

	/** Twitch channel (login name) to follow, e.g. "shroud". Empty = not configured yet. */
	public String channel = "";

	/** Connect automatically on game start when {@link #channel} is set. */
	public boolean autoConnect = true;

	/** Text shown before every bridged line in chat. */
	public String prefix = "[Twitch] ";

	/** Use each chatter's own Twitch name colour; when false, names are light purple. */
	public boolean useTwitchColors = true;

	/** Prefix every line with a local HH:mm timestamp. */
	public boolean showTimestamps = false;

	/** Also bridge sub / raid / announcement notices (USERNOTICE), not just normal chat. */
	public boolean showEvents = true;

	public static TwitchBridgeConfig load() {
		try {
			if (Files.exists(PATH)) {
				String json = Files.readString(PATH, StandardCharsets.UTF_8);
				TwitchBridgeConfig cfg = GSON.fromJson(json, TwitchBridgeConfig.class);
				if (cfg != null) {
					cfg.normalise();
					return cfg;
				}
			}
		} catch (IOException | RuntimeException e) {
			TwitchBridgeClient.LOGGER.warn("[TwitchBridge] Could not read config, using defaults", e);
		}
		TwitchBridgeConfig cfg = new TwitchBridgeConfig();
		cfg.save();
		return cfg;
	}

	public void save() {
		try {
			normalise();
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TwitchBridgeClient.LOGGER.warn("[TwitchBridge] Could not write config", e);
		}
	}

	private void normalise() {
		if (channel == null) channel = "";
		channel = channel.trim().toLowerCase();
		if (channel.startsWith("#")) channel = channel.substring(1);
		if (channel.startsWith("@")) channel = channel.substring(1);
		if (prefix == null) prefix = "";
	}
}
