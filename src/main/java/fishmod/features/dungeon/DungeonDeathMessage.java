package fishmod.features.dungeon;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

    private static boolean onMessage(Component message) {
        if (!FishSettings.deathMessageEnabled) return false;
        if (Location.getCurrentLocation() != Location.DUNGEON) return false;

        String raw = message.getString();

        Matcher m = DEATH_PATTERN.matcher(raw);
        if (!m.find()) return false;

        String playerName = m.group(1) != null ? m.group(1) : m.group(2);

        // Skip the local player's own death. Hypixel writes "☠ You died/were killed..." for
        // yourself (literally "You"), so match that as well as the actual username.
        Minecraft mc = Minecraft.getInstance();
        String localName = mc.getUser().getName();
        if (playerName.equalsIgnoreCase("You") || playerName.equalsIgnoreCase(localName)) return false;

        if (FishSettings.deathMessageToParty && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc " + FishSettings.deathMessageTemplate.replace("{name}", playerName));
        }

        return false;
    }
}
