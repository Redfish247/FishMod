package twitchbridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal parser for a single line of the IRCv3 wire format that Twitch speaks:
 *
 * <pre>@tag=value;tag2=value2 :nick!user@host COMMAND param1 param2 :trailing text</pre>
 *
 * Only the pieces the bridge needs are extracted.
 */
public final class IrcMessage {

	public final Map<String, String> tags;
	public final String nick;      // may be empty
	public final String command;   // e.g. PRIVMSG, PING, USERNOTICE, RECONNECT
	public final String param0;    // first non-trailing param (usually "#channel"); may be empty
	public final String trailing;  // text after " :"; may be empty

	private IrcMessage(Map<String, String> tags, String nick, String command, String param0, String trailing) {
		this.tags = tags;
		this.nick = nick;
		this.command = command;
		this.param0 = param0;
		this.trailing = trailing;
	}

	public static IrcMessage parse(String line) {
		String rest = line;
		Map<String, String> tags = Collections.emptyMap();

		if (rest.startsWith("@")) {
			int sp = rest.indexOf(' ');
			String tagBlock = rest.substring(1, sp < 0 ? rest.length() : sp);
			rest = sp < 0 ? "" : rest.substring(sp + 1);
			tags = parseTags(tagBlock);
		}

		String nick = "";
		if (rest.startsWith(":")) {
			int sp = rest.indexOf(' ');
			String prefix = rest.substring(1, sp < 0 ? rest.length() : sp);
			rest = sp < 0 ? "" : rest.substring(sp + 1);
			int bang = prefix.indexOf('!');
			nick = bang >= 0 ? prefix.substring(0, bang) : prefix;
		}

		String trailing = "";
		int trailIdx = rest.indexOf(" :");
		if (rest.startsWith(":")) {
			trailing = rest.substring(1);
			rest = "";
		} else if (trailIdx >= 0) {
			trailing = rest.substring(trailIdx + 2);
			rest = rest.substring(0, trailIdx);
		}

		String[] parts = rest.isEmpty() ? new String[0] : rest.split(" ");
		String command = parts.length > 0 ? parts[0] : "";
		String param0 = parts.length > 1 ? parts[1] : "";

		return new IrcMessage(tags, nick, command.toUpperCase(), param0, trailing);
	}

	private static Map<String, String> parseTags(String block) {
		Map<String, String> map = new HashMap<>();
		for (String pair : block.split(";")) {
			if (pair.isEmpty()) continue;
			int eq = pair.indexOf('=');
			if (eq < 0) {
				map.put(pair, "");
			} else {
				map.put(pair.substring(0, eq), unescapeTag(pair.substring(eq + 1)));
			}
		}
		return map;
	}

	/** IRCv3 tag value un-escaping (\s -> space, \: -> ;, \\ -> \, \r \n stripped). */
	private static String unescapeTag(String v) {
		if (v.indexOf('\\') < 0) return v;
		StringBuilder sb = new StringBuilder(v.length());
		for (int i = 0; i < v.length(); i++) {
			char c = v.charAt(i);
			if (c == '\\' && i + 1 < v.length()) {
				char n = v.charAt(++i);
				switch (n) {
					case 's' -> sb.append(' ');
					case ':' -> sb.append(';');
					case 'r', 'n' -> { /* drop */ }
					case '\\' -> sb.append('\\');
					default -> sb.append(n);
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public String tag(String key, String fallback) {
		String v = tags.get(key);
		return v == null || v.isEmpty() ? fallback : v;
	}
}
