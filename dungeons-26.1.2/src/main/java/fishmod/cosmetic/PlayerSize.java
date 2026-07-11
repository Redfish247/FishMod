package fishmod.cosmetic;

import fishmod.utils.HypixelApi;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Customizable player model size with independent X (width), Y (height) and Z (depth) axes. This is
 * purely a RENDER scale (a {@code matrices.scale()} applied in the player renderer) — it never touches
 * the scale attribute, hitbox or any packet, so it's safe on Hypixel and works offline too.
 *
 * Your own size is always shown to you locally when enabled. When "Share" is on it is published to the
 * shared store so other mod users render you at that size, and you render theirs — the multiplayer
 * counterpart, riding the same version-gated {@link RemoteSync} poll as nicks/items.
 */
public final class PlayerSize {
    private PlayerSize() {}

    public static final float MIN = 0.25f;
    public static final float MAX = 5.0f;

    private static final float[] IDENTITY = { 1.0f, 1.0f, 1.0f };

    public static void init() {
        // Re-publish our size on join (only does anything when sharing is enabled).
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> uploadOwn());
    }

    /** Effective render scale {x,y,z} for a player: own config locally, others' shared size when on. */
    public static float[] scaleFor(Player p) {
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && p.getUUID().equals(mc.player.getUUID());
        if (isSelf) {
            return FishSettings.playerSizeEnabled ? localSelfValue() : IDENTITY;
        }
        if (!FishSettings.playerSizeShared) return IDENTITY;
        float[] s = RemoteScales.get(p.getUUID().toString().replace("-", ""));
        return s == null ? IDENTITY : s;
    }

    /**
     * What YOU see for your own model: the raw config X/Y/Z, floored to a tiny positive so the model never
     * inverts/vanishes, but with NO upper cap. Edit {@code playerSizeScale*} in config/fishmod-settings.json
     * to any value (e.g. 100) to go huge — the GUI slider is still bounded 0.25–5.0, hand-editing isn't.
     */
    public static float[] localSelfValue() {
        if (!FishSettings.playerSizeEnabled) return IDENTITY;
        return new float[] {
            floorPos((float) FishSettings.playerSizeScaleX),
            floorPos((float) FishSettings.playerSizeScaleY),
            floorPos((float) FishSettings.playerSizeScaleZ)
        };
    }

    /** The size we broadcast to OTHER mod users: clamped to MIN..MAX so we never force a giant on them. */
    public static float[] ownShareValue() {
        if (!FishSettings.playerSizeEnabled) return IDENTITY;
        return new float[] {
            clamp((float) FishSettings.playerSizeScaleX),
            clamp((float) FishSettings.playerSizeScaleY),
            clamp((float) FishSettings.playerSizeScaleZ)
        };
    }

    /** Publish (or clear) the local player's size to the shared store. No-op when not sharing. */
    public static void uploadOwn() {
        if (!FishSettings.playerSizeShared) return;
        upload(ownShareValue());
    }

    /** Force-clear our shared size ({1,1,1} = delete server-side). Used when turning Share off. */
    public static void clearOwnShare() {
        upload(IDENTITY);
    }

    private static void upload(float[] xyz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;
        java.util.UUID id = mc.getUser().getProfileId();
        if (id == null) return;
        HypixelApi.uploadScale(id.toString().replace("-", ""), xyz[0], xyz[1], xyz[2]);
    }

    static float clamp(float s) { return Math.max(MIN, Math.min(MAX, s)); }

    /** Floor to a tiny positive so an out-of-range/zero config value never inverts or hides the model. */
    static float floorPos(float s) { return Math.max(0.01f, s); }
}
