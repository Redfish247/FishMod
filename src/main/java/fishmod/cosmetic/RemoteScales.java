package fishmod.cosmetic;

import fishmod.utils.config.values.FishSettings;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds other players' shared render sizes (uuid without dashes → {x,y,z} scale), filled by
 * {@link RemoteSync}. The local counterpart is just the {@code playerSize*} config; this is the
 * multiplayer view, mirroring {@link RemoteNicks}/{@code RemoteItems}.
 *
 * The wire value is a comma string: "x,y,z" (or a single "s" for legacy uniform sizes).
 */
public final class RemoteScales {
    private RemoteScales() {}

    private static final Map<String, float[]> byUuid = new ConcurrentHashMap<>();

    /** Shared {x,y,z} for a player, or null if they have none (render at 1,1,1). */
    public static float[] get(String uuidNoDashes) { return byUuid.get(uuidNoDashes); }

    /** Drop all remotely-sourced sizes (called when sharing is toggled off). */
    public static void clearAll() { byUuid.clear(); }

    /**
     * Apply a {@link RemoteSync} poll. {@code queried} is every on-server uuid we asked about;
     * {@code scales} holds only those with a size set. A uuid in {@code queried} but absent from
     * {@code scales} (or a 1,1,1 value) means "no custom size", so any stale entry is dropped.
     */
    public static void acceptScales(Set<String> queried, Map<String, String> scales) {
        if (!FishSettings.playerSizeShared) { byUuid.clear(); return; }
        for (String u : queried) {
            float[] xyz = parse(scales.get(u));
            if (xyz == null) byUuid.remove(u); else byUuid.put(u, xyz);
        }
    }

    /** Parse "x,y,z" (or "s") → clamped {x,y,z}, or null when absent/identity/malformed. */
    private static float[] parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        try {
            float x, y, z;
            if (parts.length == 1) {
                x = y = z = Float.parseFloat(parts[0].trim());
            } else if (parts.length == 3) {
                x = Float.parseFloat(parts[0].trim());
                y = Float.parseFloat(parts[1].trim());
                z = Float.parseFloat(parts[2].trim());
            } else {
                return null;
            }
            x = clamp(x); y = clamp(y); z = clamp(z);
            if (x <= 0f || y <= 0f || z <= 0f) return null;
            if (x == 1.0f && y == 1.0f && z == 1.0f) return null;
            return new float[] { x, y, z };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static float clamp(float s) { return Math.max(PlayerSize.MIN, Math.min(PlayerSize.MAX, s)); }
}
