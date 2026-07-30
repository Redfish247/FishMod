package fishmod.features

import com.google.gson.JsonObject
import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import java.util.regex.Pattern

/** Hypixel's own Catacombs/class level-up menu items just show "MAX LEVEL" once you pass level 50, with no indication of overflow progress. */
object CatacombsOverflowOverlay {

    private val COLOR_STRIP: Pattern = Pattern.compile("§.")

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

    /** When true, dumps the name + lore of any Catacombs/class item seen in a menu to chat (throttled). */
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
        } catch (ignored: Exception) {
        }
    }

    private fun draw(ctx: DrawContext, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty) return
        // Hypixel appends a "✦" (and sometimes trailing punctuation) to maxed item names —
        // same convention as maxed pets — so strip it before matching, like PetHud does.
        val name = COLOR_STRIP.matcher(stack.name.string).replaceAll("")
            .replace("✦", "").replace(Regex("[!.]+$"), "").trim()

        val isCata = name.equals("Catacombs", ignoreCase = true)
        val key = if (isCata) null else CLASS_KEYS[name.lowercase()]
        if (!isCata && key == null) return

        if (debugDumpLines) dumpDebug(stack, name)

        val xp = if (isCata) selfCataXp else selfClassXp.getOrDefault(key, -1L)
        // Only decorate once actually past the level-50 cap — below that Hypixel's own progress display is fine.
        if (xp <= HypixelApi.XP_FOR_50) return
        // Loose confirmation that this is really a leveled item (wording for "maxed" lore isn't confirmed
        // exactly) — the name match above is already specific enough that this is just a safety net.
        if (!ItemUtil.containsIgnoreCaseLore(stack, "level")) return

        val levelStr = HypixelApi.formatLevel(xp)
        val mc = MinecraftClient.getInstance()
        val text = "§6$levelStr§e✦"
        val scale = 0.6f
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(x - 1f, y + 9f)
        ctx.matrices.scale(scale, scale)
        ctx.drawText(mc.textRenderer, text, 0, 0, 0xFFFFFFFF.toInt(), true)
        ctx.matrices.popMatrix()
    }

    /** Throttled chat dump of a matched item's stripped name + lore, for tuning the match/gate regexes. */
    private fun dumpDebug(stack: ItemStack, name: String) {
        val now = System.currentTimeMillis()
        val last = lastDumpAt[name]
        if (last != null && now - last < 2000) return
        lastDumpAt[name] = now

        val sb = StringBuilder("§d[fmcata] name=\"$name\"")
        val lore = stack.get(DataComponentTypes.LORE)
        if (lore != null) {
            for (line in lore.lines()) sb.append("\n§7  ").append(line.string)
        }
        Misc.addChatMessage(Text.literal(sb.toString()))
    }
}
