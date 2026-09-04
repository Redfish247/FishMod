package twitchbridge;

import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Twitch Bridge" FishMod feature. Owns the single {@link TwitchIrcClient} connection and the
 * shared {@link TwitchBridgeConfig}, driven by the /fm screen toggle and the {@code /twitch ...}
 * commands. Channel/appearance settings live in {@code config/twitch-bridge.json}; the master
 * on/off is {@link FishSettings#twitchBridgeEnabled} (persisted with the rest of FishMod).
 */
public final class TwitchBridgeClient {

	public static final Logger LOGGER = LoggerFactory.getLogger("twitch-bridge");

	private static TwitchBridgeConfig config;
	private static TwitchIrcClient client;

	private TwitchBridgeClient() {}

	/** Called once from {@code FishModInit.onInitialize()}. */
	public static void init() {
		config = TwitchBridgeConfig.load();
		TwitchCommands.register();

		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> disconnect());

		if (FishSettings.twitchBridgeEnabled && config.autoConnect && !config.channel.isBlank()) {
			// delay so the game finishes starting first
			new Thread(() -> {
				sleep(3000);
				connect(config.channel);
			}, "twitch-bridge-autoconnect").start();
		}
	}

	public static TwitchBridgeConfig config() {
		return config;
	}

	/** /fm toggle: flip the master switch and connect/disconnect to match. */
	public static synchronized void setEnabled(boolean on) {
		FishSettings.twitchBridgeEnabled = on;
		if (on) {
			if (config.channel.isBlank()) {
				ChatOutput.info(config, "enabled — set a channel with /twitch channel <name>");
			} else {
				connect(config.channel);
			}
		} else {
			disconnect();
		}
	}

	/** /fm channel field: store the channel and (re)connect if the feature is on. */
	public static synchronized void setChannel(String name) {
		String target = name == null ? "" : name.trim().toLowerCase();
		if (target.startsWith("#")) target = target.substring(1);
		if (target.startsWith("@")) target = target.substring(1);
		config.channel = target;
		config.save();
		if (FishSettings.twitchBridgeEnabled && !target.isBlank()) {
			connect(target);
		} else if (target.isBlank()) {
			disconnect();
		}
	}

	public static synchronized void connect(String channel) {
		String target = channel == null ? config.channel : channel.trim().toLowerCase();
		if (target.startsWith("#")) target = target.substring(1);
		if (target.startsWith("@")) target = target.substring(1);

		if (target.isBlank()) {
			ChatOutput.info(config, "no channel set — use /twitch channel <name>");
			return;
		}

		if (client != null && client.isStarted() && client.channel().equals(target)) {
			ChatOutput.info(config, "already connected to #" + target);
			return;
		}

		disconnect();
		config.channel = target;
		config.save();

		client = new TwitchIrcClient(config, target);
		client.start();
	}

	public static synchronized void disconnect() {
		if (client != null) {
			client.stop();
			client = null;
		}
	}

	public static synchronized boolean isConnected() {
		return client != null && client.isStarted();
	}

	public static synchronized String currentChannel() {
		return client != null ? client.channel() : null;
	}

	public static synchronized void reload() {
		boolean wasConnected = isConnected();
		disconnect();
		config = TwitchBridgeConfig.load();
		ChatOutput.info(config, "config reloaded");
		if (wasConnected && !config.channel.isBlank()) {
			connect(config.channel);
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
