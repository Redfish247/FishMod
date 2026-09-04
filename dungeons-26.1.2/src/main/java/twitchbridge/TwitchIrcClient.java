package twitchbridge;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anonymous, read-only Twitch IRC client.
 *
 * <p>Connects to {@code irc.chat.twitch.tv:6697} over TLS as a {@code justinfanNNNN} guest — this
 * is Twitch's supported way to read a channel's chat without any account, token or client id.
 * Runs its own daemon thread with automatic exponential-backoff reconnect while started.</p>
 */
public final class TwitchIrcClient {

	private static final String HOST = "irc.chat.twitch.tv";
	private static final int PORT = 6697;
	private static final int READ_TIMEOUT_MS = 360_000; // Twitch PINGs well within 5 min; else reconnect.

	private final TwitchBridgeConfig config;
	private final String channel;

	private volatile boolean started;
	private volatile Thread thread;
	private volatile Socket socket;

	public TwitchIrcClient(TwitchBridgeConfig config, String channel) {
		this.config = config;
		this.channel = channel.toLowerCase(Locale.ROOT);
	}

	public String channel() {
		return channel;
	}

	public boolean isStarted() {
		return started;
	}

	public synchronized void start() {
		if (started) return;
		started = true;
		Thread t = new Thread(this::runLoop, "twitch-bridge-irc");
		t.setDaemon(true);
		thread = t;
		t.start();
	}

	public synchronized void stop() {
		started = false;
		closeSocketQuietly();
		Thread t = thread;
		if (t != null) t.interrupt();
		thread = null;
	}

	private void runLoop() {
		int attempt = 0;
		while (started) {
			try {
				connectAndListen();
				attempt = 0; // clean disconnect (e.g. Twitch RECONNECT) — retry immediately
			} catch (IOException e) {
				if (!started) break;
				attempt++;
				long delay = Math.min(30_000L, 2_000L * (1L << Math.min(attempt - 1, 4)));
				ChatOutput.info(config, "disconnected from #" + channel + " (" + e.getMessage()
						+ ") — retrying in " + (delay / 1000) + "s");
				sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void connectAndListen() throws IOException, InterruptedException {
		String nick = "justinfan" + ThreadLocalRandom.current().nextInt(10_000, 99_999);

		Socket s = SSLSocketFactory.getDefault().createSocket(HOST, PORT);
		s.setSoTimeout(READ_TIMEOUT_MS);
		socket = s;

		try (BufferedWriter out = new BufferedWriter(
				new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
			 BufferedReader in = new BufferedReader(
				new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {

			send(out, "CAP REQ :twitch.tv/tags twitch.tv/commands");
			send(out, "NICK " + nick);
			send(out, "JOIN #" + channel);

			ChatOutput.info(config, "connected to #" + channel);

			String line;
			while (started && (line = in.readLine()) != null) {
				handleLine(out, line);
			}
		} finally {
			closeSocketQuietly();
		}
	}

	private void handleLine(BufferedWriter out, String raw) throws IOException {
		if (raw.startsWith("PING")) {
			send(out, "PONG" + raw.substring(4));
			return;
		}

		IrcMessage msg = IrcMessage.parse(raw);
		switch (msg.command) {
			case "PRIVMSG" -> {
				String name = msg.tag("display-name", msg.nick);
				String color = msg.tags.get("color");
				String text = msg.trailing;
				boolean action = false;
				if (text.startsWith("ACTION ") && text.endsWith("")) {
					text = text.substring(8, text.length() - 1);
					action = true;
				}
				if (!text.isBlank()) {
					ChatOutput.chat(config, name, color, text, action);
				}
			}
			case "USERNOTICE" -> {
				if (config.showEvents) {
					String sysMsg = msg.tag("system-msg", "").replace("\\s", " ");
					if (!sysMsg.isBlank()) ChatOutput.event(config, sysMsg);
				}
			}
			case "NOTICE" -> {
				if (!msg.trailing.isBlank()) ChatOutput.info(config, msg.trailing);
			}
			case "RECONNECT" -> {
				throw new IOException("server asked to reconnect");
			}
			default -> { /* 001/353/366/CAP/etc — ignore */ }
		}
	}

	private static void send(BufferedWriter out, String line) throws IOException {
		out.write(line);
		out.write("\r\n");
		out.flush();
	}

	private void closeSocketQuietly() {
		Socket s = socket;
		socket = null;
		if (s != null) {
			try {
				s.close();
			} catch (IOException ignored) {
			}
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
