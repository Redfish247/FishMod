package fishmod.cosmetic

import fishmod.features.ItemCustomizer
import fishmod.features.ItemCustomizer.Custom
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Multiplayer counterpart to [ItemCustomizer]: fetches other mod users' item customizations
 * from the proxy and re-applies them every tick to those players' worn armor + held items, so their
 * custom dye / trim / model / name / stars are visible on your screen.
 *
 * Mirrors [RemoteNicks]: [RemoteSync] refreshes the per-player customs, and a per-tick
 * pass mutates the equipment stacks (the server periodically resends equipment, so the re-apply
 * keeps the cosmetics stable, exactly like the local-inventory loop in ItemCustomizer).
 */
object RemoteItems {

    /** player UUID (no dashes) -> (item key -> Custom). */
    private val byUuid: MutableMap<String, Map<String, Custom>> = ConcurrentHashMap()

    private val SLOTS = arrayOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    )

    @JvmStatic
    fun init() {
        // Fetching is driven by RemoteSync (combined version-gated /sync). On join we (re)publish our
        // own customs; the per-tick applyToWorld keeps received customs painted onto other players.
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> ItemCustomizer.uploadOwn() }
        ClientTickEvents.END_CLIENT_TICK.register { applyToWorld(it) }
    }

    /** Force an immediate refresh from the tab list. */
    @JvmStatic
    fun forceRefresh() {
        RemoteSync.forceSync()
    }

    /** Debug snapshot: player uuid (no dashes) -> the set of item keys we have customs for. */
    @JvmStatic
    fun snapshotKeys(): Map<String, Set<String>> {
        val out = HashMap<String, Set<String>>()
        for ((k, v) in byUuid) out[k] = HashSet(v.keys)
        return out
    }

    /** Clear all remotely-sourced item customs (called when the feature is toggled off). */
    @JvmStatic
    fun clearAll() {
        byUuid.clear()
    }

    /**
     * Apply the result of a [RemoteSync] poll. `queried` is the full set of on-server
     * players we asked about; `payloads` holds only those with customs currently set. Players
     * in `queried` but absent from `payloads` have none (or just cleared them), so we
     * drop any stale entry for them.
     */
    @JvmStatic
    fun acceptItems(queried: Set<String>, payloads: Map<String, String>) {
        if (!FishSettings.remoteItemsEnabled) {
            byUuid.clear()
            return
        }
        for (u in queried) {
            val payload = payloads[u]
            val parsed: Map<String, Custom> = if (payload == null) emptyMap() else ItemCustomizer.parsePayload(payload)
            // Re-key by the source vanilla item type — that's all a viewer can identify on another
            // player's items (Hypixel strips the SkyBlock uuid/id). Customs without a vanilla type
            // (old payloads, not yet backfilled by the owner) simply can't be matched remotely.
            val byVanilla = HashMap<String, Custom>()
            for (c in parsed.values) {
                val v = c.vanilla()
                if (v == null || v.isEmpty()) continue
                val prev = byVanilla[v]
                // Several items can share a vanilla type (e.g. multiple bows). A viewer can't tell
                // them apart, so prefer the one that actually changes appearance over a no-op (e.g. a
                // bow left on the "bow" model) — that's why a Terminator->crossbow was losing to a bow.
                if (prev == null || (isMeaningful(c, v) && !isMeaningful(prev, v))) byVanilla[v] = c
            }
            if (byVanilla.isEmpty()) byUuid.remove(u) else byUuid[u] = byVanilla
        }
    }

    /** True if the custom visibly changes the item: a real model swap, dye, trim, name, or stars. */
    private fun isMeaningful(c: Custom, vanillaId: String): Boolean {
        if (c.modelId() != null && c.modelId()!!.isNotEmpty()) {
            val basePath = if (vanillaId.contains(":")) vanillaId.substring(vanillaId.indexOf(':') + 1) else vanillaId
            val modelId = c.modelId()!!
            val modelPath = if (modelId.contains(":")) modelId.substring(modelId.indexOf(':') + 1) else modelId
            if (modelPath != basePath) return true // model differs from the base item -> real swap
        }
        if (c.dye() >= 0) return true
        if (c.trimMat() != null && c.trimMat()!!.isNotEmpty()) return true
        if (c.name() != null && c.name()!!.isNotEmpty()) return true
        return c.stars() > 0
    }

    private fun applyToWorld(mc: MinecraftClient) {
        if (!FishSettings.remoteItemsEnabled || byUuid.isEmpty()) return
        if (mc.world == null || mc.player == null) return
        for (p: PlayerEntity in mc.world!!.players) {
            if (p === mc.player) continue
            val customs = byUuid[p.uuid.toString().replace("-", "")] ?: continue
            if (customs.isEmpty()) continue
            for (slot in SLOTS) {
                val st = p.getEquippedStack(slot)
                if (st == null || st.isEmpty) continue
                val vid = ItemCustomizer.vanillaId(st) ?: continue // match by vanilla type (see acceptItems)
                val c = customs[vid]
                // Skip skins remotely: player_head covers many distinct items (pets, talismans, …),
                // so a vanilla-type match can't pin a shared skin to the right head. Skins stay local.
                if (c != null) ItemCustomizer.applyCustom(st, c, false)
            }
        }
    }
}
