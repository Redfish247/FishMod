package blade.addon.features.dungeon;

import blade.addon.utils.Location;
import blade.addon.utils.config.values.FishSettings;
import blade.addon.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a player dies in a dungeon, sends a customisable message
 * with their name substituted into the template via {name}.
 * Optionally broadcasts to party chat.
 */
public class DungeonDeathMessage {

    // Captures the player name from Hypixel dungeon death messages
    private static final Pattern DEATH_PATTERN = Pattern.compile(
            "☠ (\\S+) (?:was|were) killed by|☠ (\\S+) (?:died|quit)");

    public static void init() {
        Events.ON_GAME_MESSAGE.register(DungeonDeathMessage::onMessage);
    }

    private static boolean onMessage(Text message) {
        if (!FishSettings.deathMessageEnabled) return false;
        if (Location.getCurrentLocation() != Location.DUNGEON) return false;

        String raw = message.getString();

        Matcher m = DEATH_PATTERN.matcher(raw);
        if (!m.find()) return false;

        String playerName = m.group(1) != null ? m.group(1) : m.group(2);

        // Skip if it's the local player's own death
        MinecraftClient mc = MinecraftClient.getInstance();
        String localName = mc.getSession().getUsername();
        if (playerName.equalsIgnoreCase(localName)) return false;

        if (FishSettings.deathMessageToParty && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand("pc " + FishSettings.deathMessageTemplate.replace("{name}", playerName));
        }

        return false;
    }
}
