package fishmod.features;

import fishmod.utils.Misc;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BridgeBot {

    // Matches guild chat after stripping colors:
    // "Guild > [optional rank] BotName: [optional rank] PlayerName: message"
    // Also handles Discord bridge style "Guild > BotName: Player > message"
    private static Pattern pattern = buildPattern();

    private static Pattern buildPattern() {
        if (FishSettings.bridgeBotName.isBlank()) return Pattern.compile("(?!)"); // never matches
        String bot = Pattern.quote(FishSettings.bridgeBotName.trim());
        // "Guild > [rank] BotName [guild_rank]: PlayerName » message"
        // rank before bot and guild_rank after bot are both optional
        // separator between player and message can be »  :  >  |  etc.
        return Pattern.compile(
            "^Guild > (?:\\[\\S+\\] )?" + bot + "(?:\\s+\\[[^\\]]+\\])?: (\\S+)[^\\w]+(.+)$"
        );
    }

    public static void rebuildPattern() {
        pattern = buildPattern();
    }

    public static void init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !FishSettings.bridgeBotEnabled || FishSettings.bridgeBotName.isBlank()) return true;

            String plain = message.getString().replaceAll("§.", "").trim();
            Matcher m = pattern.matcher(plain);
            if (!m.matches()) return true;

            String player = m.group(1);
            String text   = m.group(2);
            Misc.addChatMessage(Text.literal("§2Guild > §r§a[Bridge] §r" + player + "§r: " + text));
            return false; // suppress original bot message
        });
    }
}
