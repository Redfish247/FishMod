package fishmod.cosmetic

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.features.item.ItemCustomizationStore
import fishmod.features.item.ItemCustomizationStore.AnimatedDye
import fishmod.features.item.ItemCustomizationStore.ArmorTrimId
import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.concurrent.ConcurrentHashMap

/** Shares your item customs (keyed by vanilla item type, since Hypixel hides other players' item uuids) and paints other players' on their worn/held items. */
object RemoteItems {

    private class Custom(val name: String?, val model: String?, val dye: Int?, val animated: AnimatedDye?, val trim: ArmorTrimId?)

    private val byPlayer: MutableMap<String, Map<String, Custom>> = ConcurrentHashMap()
    private val SLOTS = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)
    private const val MAX_PAYLOAD = 7500
    private const val CHECK_TICKS = 100
    private val GSON = Gson()

    private var lastUploaded: String? = null
    private var tick = 0

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> lastUploaded = null }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try {
                if (++tick % CHECK_TICKS == 0) uploadIfChanged(mc)
                applyToWorld(mc)
            } catch (e: Exception) { FishDiag.fail("RemoteItems.1", "remote items tick failed", e) }
        }
    }

    @JvmStatic
    fun clearAll() {
        byPlayer.clear()
        ItemCustomizationStore.clearRemote()
        lastUploaded = null
    }

    private fun vanillaId(st: ItemStack): String = BuiltInRegistries.ITEM.getKey(st.item).toString()

    private fun buildOwnPayload(mc: Minecraft): String {
        val p = mc.player ?: return "[]"
        val arr = JsonArray()
        val seen = HashSet<String>()
        val stacks = ArrayList<ItemStack>()
        for (slot in SLOTS) stacks.add(p.getItemBySlot(slot))
        for (i in 0 until p.inventory.containerSize) stacks.add(p.inventory.getItem(i))
        for (st in stacks) {
            if (st.isEmpty) continue
            val uuid = ItemUtil.getUuid(st) ?: continue
            val c = ItemCustomizationStore.own(uuid)
            if (c.isEmpty()) continue
            val v = vanillaId(st)
            if (!seen.add(v)) continue
            val o = JsonObject()
            o.addProperty("v", v)
            c.name?.let { o.addProperty("n", it) }
            c.model?.let { o.addProperty("m", it) }
            c.dye?.let { o.addProperty("d", it) }
            c.animated?.let { o.add("a", GSON.toJsonTree(it)) }
            c.trim?.let { o.addProperty("tm", it.material); o.addProperty("tp", it.pattern) }
            arr.add(o)
            if (arr.toString().length > MAX_PAYLOAD) { arr.remove(arr.size() - 1); break }
        }
        return arr.toString()
    }

    private fun uploadIfChanged(mc: Minecraft) {
        if (!FishSettings.remoteItemsEnabled || mc.player == null) return
        val payload = buildOwnPayload(mc)
        if (payload == lastUploaded) return
        if (lastUploaded == null && payload == "[]") { lastUploaded = payload; return }
        lastUploaded = payload
        HypixelApi.uploadItems(mc.player!!.uuid.toString().replace("-", ""), payload)
        RemoteSync.forceSync()
    }

    @JvmStatic
    fun acceptItems(queried: Set<String>, payloads: Map<String, String>) {
        if (!FishSettings.remoteItemsEnabled) { clearAll(); return }
        // drop players who left the tab so the map doesn't grow across lobbies
        byPlayer.keys.retainAll(queried)
        for (u in queried) {
            val payload = payloads[u]
            val parsed = if (payload.isNullOrEmpty()) emptyMap() else parse(payload)
            if (parsed.isEmpty()) byPlayer.remove(u) else byPlayer[u] = parsed
            for ((v, c) in parsed) ItemCustomizationStore.setRemote(synthetic(u, v), c.name, c.model, c.dye, c.animated, c.trim)
        }
    }

    private fun synthetic(player: String, vanilla: String) = "fmr:$player:$vanilla"

    private fun parse(json: String): Map<String, Custom> {
        val out = HashMap<String, Custom>()
        try {
            for (e in JsonParser.parseString(json).asJsonArray) {
                try { // one bad entry must not drop the rest
                    val o = e.asJsonObject
                    val v = o.get("v")?.asString ?: continue
                    val animated = o.get("a")?.takeIf { it.isJsonObject }?.let { GSON.fromJson(it, AnimatedDye::class.java) }
                    val trim = if (o.has("tm") && o.has("tp")) ArmorTrimId(o.get("tm").asString, o.get("tp").asString) else null
                    out[v] = Custom(o.get("n")?.asString, o.get("m")?.asString, o.get("d")?.asInt, animated, trim)
                } catch (ex: Exception) { FishDiag.fail("RemoteItems.3", "bad shared item entry", ex) }
            }
        } catch (ex: Exception) { FishDiag.fail("RemoteItems.2", "bad shared items payload", ex) }
        return out
    }

    private fun applyToWorld(mc: Minecraft) {
        if (!FishSettings.remoteItemsEnabled || byPlayer.isEmpty()) return
        val level = mc.level ?: return
        val me = mc.player ?: return
        for (p in level.players()) {
            if (p === me) continue
            val customs = byPlayer[p.uuid.toString().replace("-", "")] ?: continue
            for (slot in SLOTS) {
                val st = p.getItemBySlot(slot)
                if (st.isEmpty) continue
                val v = vanillaId(st)
                if (!customs.containsKey(v)) continue
                val syn = synthetic(p.uuid.toString().replace("-", ""), v)
                if (ItemUtil.getUuid(st) == syn) continue
                val tag = st.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: net.minecraft.nbt.CompoundTag()
                tag.putString("uuid", syn)
                st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            }
        }
    }
}
