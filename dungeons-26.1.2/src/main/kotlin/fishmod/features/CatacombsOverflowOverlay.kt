package fishmod.features

import com.google.gson.JsonObject
import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

private val TRAILING_PUNCT_RE = Regex("[!.]+$")

object CatacombsOverflowOverlay {

    private val CLASS_KEYS: Map<String, String> = mapOf(
        "healer" to "healer",
        "mage" to "mage",
        "berserk" to "berserk",
        "berserker" to "berserk",
        "archer" to "archer",
        "tank" to "tank"
    )

    private var selfCataXp: Long = -1
    private val selfClassXp: MutableMap<String, Long> = HashMap()
    private var lastFetchAt: Long = 0
    private var fetchInFlight = false
    private const val REFRESH_MS = 60_000L

    @JvmField
    var debugDumpLines = false
    private val lastDumpAt: MutableMap<String, Long> = HashMap()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.catacombsOverflowEnabled || client.player == null || !Location.inSkyblock()) {
                return@register
            }
            val now = System.currentTimeMillis()
            if (fetchInFlight || now - lastFetchAt < REFRESH_MS) return@register
            lastFetchAt = now
            fetchInFlight = true
            HypixelApi.getLocalMember(client) { member -> applySelfMember(member) }
        }

        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!FishSettings.catacombsOverflowEnabled) return@register
            draw(ctx, stack, x, y)
        }
    }

    private fun applySelfMember(member: JsonObject?) {
        fetchInFlight = false
        if (member == null || !member.has("dungeons")) return
        try {
            val dungeons = member.getAsJsonObject("dungeons")
            if (dungeons.has("dungeon_types")) {
                val types = dungeons.getAsJsonObject("dungeon_types")
                if (types.has("catacombs")) {
                    val cata = types.getAsJsonObject("catacombs")
                    if (cata.has("experience")) selfCataXp = cata.get("experience").asLong
                }
            }
            if (dungeons.has("player_classes")) {
                val classes = dungeons.getAsJsonObject("player_classes")
                for (cls in arrayOf("healer", "mage", "berserk", "archer", "tank")) {
                    if (classes.has(cls)) {
                        val c = classes.getAsJsonObject(cls)
                        if (c.has("experience")) selfClassXp[cls] = c.get("experience").asLong
                    }
                }
            }
        } catch (ex: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[CatacombsOverflowOverlay] failed to parse member JSON: {}", ex.message)
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty) return
        val name = HypixelApi.STRIP_COLOR.matcher(stack.hoverName.string).replaceAll("")
            .replace("✦", "").replace(TRAILING_PUNCT_RE, "").trim()

        val isCata = name.equals("Catacombs", ignoreCase = true)
        val key = if (isCata) null else CLASS_KEYS[name.lowercase()]
        if (!isCata && key == null) return

        if (debugDumpLines) dumpDebug(stack, name)

        val xp = if (isCata) selfCataXp else selfClassXp.getOrDefault(key, -1L)
        if (xp <= HypixelApi.XP_FOR_50) return
        if (!ItemUtil.containsIgnoreCaseLore(stack, "level")) return

        val levelStr = HypixelApi.formatLevel(xp)
        val mc = Minecraft.getInstance()
        val text = "§6$levelStr§e✦"
        val scale = 0.6f
        ctx.pose().pushMatrix()
        ctx.pose().translate(x - 1f, y + 9f)
        ctx.pose().scale(scale, scale)
        ctx.text(mc.font, text, 0, 0, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }

    private fun dumpDebug(stack: ItemStack, name: String) {
        val now = System.currentTimeMillis()
        val last = lastDumpAt[name]
        if (last != null && now - last < 2000) return
        lastDumpAt[name] = now

        val sb = StringBuilder("§d[fmcata] name=\"$name\"")
        val lore = stack.get(DataComponents.LORE)
        if (lore != null) {
            for (line in lore.lines()) sb.append("\n§7  ").append(line.string)
        }
        Misc.addChatMessage(Component.literal(sb.toString()))
    }
}
