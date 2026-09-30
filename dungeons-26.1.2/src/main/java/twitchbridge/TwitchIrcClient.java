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

public final class TwitchIrcClient {

	private static final String HOST = "irc.chat.twitch.tv";
	private static final int PORT = 6697;
	private static final int READ_TIMEOUT_MS = 360_000;

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
				long startedAt = System.currentTimeMillis();
				connectAndListen();
				if (!started) break;
				attempt = System.currentTimeMillis() - startedAt > 60_000L ? 0 : attempt + 1;
				sleep(Math.min(30_000L, 2_000L * (1L << Math.min(attempt, 4))));
			} catch (IOException e) {
				if (!started) break;
				fishmod.utils.debug.FishDiag.fail("TwitchIrcClient.1", "IRC connection to #" + channel + " dropped (attempt " + (attempt + 1) + ")", e);
				attempt++;
				long delay = Math.min(30_000L, 2_000L * (1L << Math.min(attempt - 1, 4)));
				ChatOutput.info(config, "disconnected from #" + channel + " (" + e.getMessage()
						+ ") — retrying in " + (delay / 1000) + "s");
				sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (RuntimeException e) {
				fishmod.utils.debug.FishDiag.fail("TwitchIrcClient.2", "IRC loop for #" + channel + " crashed", e);
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

		IrcMessage msg;
		try {
			msg = IrcMessage.parse(raw);
		} catch (RuntimeException e) {
			fishmod.utils.debug.FishDiag.fail("TwitchIrcClient.3", "IRC line parse failed: " + (raw.length() > 120 ? raw.substring(0, 120) : raw), e);
			return;
		}
		if (msg.command.isEmpty()) {
			fishmod.utils.debug.FishDiag.fail("TwitchIrcClient.4", "IRC line parsed to no command: " + (raw.length() > 120 ? raw.substring(0, 120) : raw));
			return;
		}
		switch (msg.command) {
			case "PRIVMSG" -> {
				String name = msg.tag("display-name", msg.nick);
				String color = msg.tags.get("color");
				String text = msg.trailing;
				boolean action = false;
				if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
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
			default -> {  }
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
			} catch (IOException e) {
				fishmod.utils.debug.FishDiag.fail("TwitchIrcClient.5", "IRC socket close failed", e);
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
