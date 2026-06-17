package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maxor crystal spawn countdown + "place crystal" reminder. Ported from blade-addons; the
 * personal-best timing the original recorded is dropped (no PersonalBests in FishMod), but the
 * crystal-placed detection is kept so the reminder dismisses when you place it.
 */
public class CrystalSpawn {

    private static final Pattern RELIC_PICK_UP = Pattern.compile("(\\w+) picked up an Energy Crystal!$");
    private static final int REMINDER_TICK = 240;
    private static final int TICK_SPAWN = 34;

    private static boolean pickedUp = false;
    private static int tick = 0;
    private static int tickSincePicked = 0;

    public static void init() {
        Events.ON_GAME_MESSAGE.register(text -> {
            if (!Location.inDungeon() || !Phase.inP1()
                    || (!Floor7.enableCrystalSpawnTime && !Floor7.crystalPlaceReminder)) return false;
            String string = text.getString();
            if (string.equals("[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!")
                    || string.equals("[BOSS] Maxor: YOU TRICKED ME!")) {
                tick = TICK_SPAWN;
                return false;
            }
            Matcher matcher = RELIC_PICK_UP.matcher(string);
            if (matcher.find() && EntityUtil.isClientPlayer(matcher.group(1))) {
                pickedUp = true;
            }
            return false;
        });
        Events.ON_SERVER_TICK.register(() -> {
            tick = Math.max(tick - 1, 0);
            if (pickedUp) tickSincePicked++;
            return false;
        });
        Events.ON_LOCATION_CHANGE.register(newLocation -> {
            tick = 0; tickSincePicked = 0; pickedUp = false;
            return false;
        });
        Events.ON_ENTITY_SPAWNED.register((entity, world) -> {
            if (!pickedUp || !Location.inDungeon() || !Floor7.enableCrystalSpawnTime || !Phase.inP1()) return false;
            if (entity instanceof EndCrystalEntity crystal) {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player == null) return false;
                if (Misc.getDistance(player, crystal) < 6 && crystal.getY() == 224.375) {
                    pickedUp = false;
                    tickSincePicked = 0;
                }
            }
            return false;
        });
    }

    public static boolean display() {
        return tick > 0 && Location.inDungeon() && Phase.inP1() && Floor7.enableCrystalSpawnTime;
    }

    public static void render(HUDComponent component, DrawContext context) {
        RenderUtils.drawTimer(component, context, tick, Constants.LIGHT_PURPLE);
    }

    public static boolean displayNotification() {
        return (Floor7.instantlyDisplayCrystalReminder || tickSincePicked > REMINDER_TICK)
                && Location.inDungeon() && Phase.inP1() && Floor7.crystalPlaceReminder && pickedUp;
    }

    public static void renderNotification(HUDComponent component, DrawContext context) {
        RenderUtils.drawCenteredText(context, component, Text.literal("§bPlace Crystal!"));
    }
}
