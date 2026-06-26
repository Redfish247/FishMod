package fishmod.cosmetic;

import fishmod.features.ItemCustomizer;
import fishmod.features.ItemCustomizer.Custom;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multiplayer counterpart to {@link ItemCustomizer}: fetches other mod users' item customizations
 * from the proxy and re-applies them every tick to those players' worn armor + held items, so their
 * custom dye / trim / model / name / stars are visible on your screen.
 *
 * Mirrors {@link RemoteNicks}: {@link RemoteSync} refreshes the per-player customs, and a per-tick
 * pass mutates the equipment stacks (the server periodically resends equipment, so the re-apply
 * keeps the cosmetics stable, exactly like the local-inventory loop in ItemCustomizer).
 */
public final class RemoteItems {
    private RemoteItems() {}

    /** player UUID (no dashes) → (item key → Custom). */
    private static final Map<String, Map<String, Custom>> byUuid = new ConcurrentHashMap<>();

    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    public static void init() {
        // Fetching is driven by RemoteSync (combined version-gated /sync). On join we (re)publish our
        // own customs; the per-tick applyToWorld keeps received customs painted onto other players.
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> ItemCustomizer.uploadOwn());
        ClientTickEvents.END_CLIENT_TICK.register(RemoteItems::applyToWorld);
    }

    /** Force an immediate refresh from the tab list. */
    public static void forceRefresh() { RemoteSync.forceSync(); }

    /** Debug snapshot: player uuid (no dashes) → the set of item keys we have customs for. */
    public static Map<String, Set<String>> snapshotKeys() {
        Map<String, Set<String>> out = new java.util.HashMap<>();
        for (var e : byUuid.entrySet()) out.put(e.getKey(), new java.util.HashSet<>(e.getValue().keySet()));
        return out;
    }

    /** Clear all remotely-sourced item customs (called when the feature is toggled off). */
    public static void clearAll() { byUuid.clear(); }

    /**
     * Apply the result of a {@link RemoteSync} poll. {@code queried} is the full set of on-server
     * players we asked about; {@code payloads} holds only those with customs currently set. Players
     * in {@code queried} but absent from {@code payloads} have none (or just cleared them), so we
     * drop any stale entry for them.
     */
    public static void acceptItems(Set<String> queried, Map<String, String> payloads) {
        if (!FishSettings.remoteItemsEnabled) { byUuid.clear(); return; }
        for (String u : queried) {
            String payload = payloads.get(u);
            Map<String, Custom> parsed = payload == null ? Map.of() : ItemCustomizer.parsePayload(payload);
            // Re-key by the source vanilla item type — that's all a viewer can identify on another
            // player's items (Hypixel strips the SkyBlock uuid/id). Customs without a vanilla type
            // (old payloads, not yet backfilled by the owner) simply can't be matched remotely.
            Map<String, Custom> byVanilla = new java.util.HashMap<>();
            for (Custom c : parsed.values()) {
                String v = c.vanilla();
                if (v == null || v.isEmpty()) continue;
                Custom prev = byVanilla.get(v);
                // Several items can share a vanilla type (e.g. multiple bows). A viewer can't tell
                // them apart, so prefer the one that actually changes appearance over a no-op (e.g. a
                // bow left on the "bow" model) — that's why a Terminator→crossbow was losing to a bow.
                if (prev == null || (isMeaningful(c, v) && !isMeaningful(prev, v))) byVanilla.put(v, c);
            }
            if (byVanilla.isEmpty()) byUuid.remove(u);
            else byUuid.put(u, byVanilla);
        }
    }

    /** True if the custom visibly changes the item: a real model swap, dye, trim, name, or stars. */
    private static boolean isMeaningful(Custom c, String vanillaId) {
        if (c.modelId() != null && !c.modelId().isEmpty()) {
            String basePath = vanillaId.contains(":") ? vanillaId.substring(vanillaId.indexOf(':') + 1) : vanillaId;
            String modelPath = c.modelId().contains(":") ? c.modelId().substring(c.modelId().indexOf(':') + 1) : c.modelId();
            if (!modelPath.equals(basePath)) return true; // model differs from the base item → real swap
        }
        if (c.dye() >= 0) return true;
        if (c.dyeAnim() != null && !c.dyeAnim().isEmpty()) return true;
        if (c.trimMat() != null && !c.trimMat().isEmpty()) return true;
        if (c.name() != null && !c.name().isEmpty()) return true;
        return c.stars() > 0;
    }

    private static void applyToWorld(MinecraftClient mc) {
        if (!FishSettings.remoteItemsEnabled || byUuid.isEmpty()) return;
        if (mc.world == null || mc.player == null) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            Map<String, Custom> customs = byUuid.get(p.getUuid().toString().replace("-", ""));
            if (customs == null || customs.isEmpty()) continue;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack st = p.getEquippedStack(slot);
                if (st == null || st.isEmpty()) continue;
                String vid = ItemCustomizer.vanillaId(st); // match by vanilla type (see acceptItems)
                if (vid == null) continue;
                Custom c = customs.get(vid);
                // Skip skins remotely: player_head covers many distinct items (pets, talismans, …),
                // so a vanilla-type match can't pin a shared skin to the right head. Skins stay local.
                if (c != null) ItemCustomizer.applyCustom(st, c, false);
            }
        }
    }
}
