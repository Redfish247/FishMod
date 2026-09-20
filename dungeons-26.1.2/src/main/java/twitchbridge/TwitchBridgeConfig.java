package twitchbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TwitchBridgeConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve("twitch-bridge.json");

	public String channel = "";

	public boolean autoConnect = true;

	public String prefix = "[Twitch] ";

	public boolean useTwitchColors = true;

	public boolean showTimestamps = false;

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
