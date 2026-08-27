package fishmod.features.dungeon

import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * In-menu Party Finder helper, ported from NoammAddons' `PartyFinder` (the overlay + tooltip half —
 * the auto-kick half is intentionally left for a later pass).
 *
 *  - draws the red Dungeon-Level-Required number and the missing-class letters on each party head
 *  - rewrites each "Name: Class (lvl)" tooltip line with the player's Cata level / secrets / floor PB,
 *    fetched lazily through [HypixelApi] into a session cache, and appends a "Missing: …" line
 */
object PartyFinder {

    private val CLASSES = listOf("Archer", "Tank", "Berserk", "Healer", "Mage")
    private val MEMBER = Pattern.compile("^\\s*(\\w{1,16}):?\\s+(Archer|Tank|Berserk|Healer|Mage)\\s*\\((\\d+)\\)\\s*$")
    private val LEVEL_REQ = Pattern.compile("Dungeon Level Required:\\s*(\\d+)")
    private val FLOOR = Pattern.compile("Floor:\\s*(?:Floor\\s+)?(\\w+)")
    private val COLOR = Regex("§.")

    private val cache = ConcurrentHashMap<String, HypixelApi.DungeonData>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y -> onSlot(ctx, stack, x, y) }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> onTooltip(stack, lines) })
    }

    private fun inPartyFinder(): Boolean {
        if (!FishSettings.pfMenuEnabled) return false
        val s = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return false
        return COLOR.replace(s.title.string, "") == "Party Finder"
    }

    private fun lore(stack: ItemStack): List<String> =
        stack.get(DataComponents.LORE)?.lines()?.map { COLOR.replace(it.string, "") } ?: emptyList()

    // ── head overlay ────────────────────────────────────────────────────────

    private fun onSlot(ctx: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (!inPartyFinder() || stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) return
        if (!FishSettings.pfShowLevelReq && !FishSettings.pfShowMissingClasses) return

        var levelReq = 0
        val present = HashSet<String>()
        for (line in lore(stack)) {
            if (FishSettings.pfShowLevelReq) {
                val m = LEVEL_REQ.matcher(line)
                if (m.find()) levelReq = m.group(1).toIntOrNull() ?: levelReq
            }
            if (FishSettings.pfShowMissingClasses) {
                val m = MEMBER.matcher(line)
                if (m.matches()) present.add(m.group(2))
            }
        }

        val font = Minecraft.getInstance().font
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())

        if (FishSettings.pfShowLevelReq && levelReq > 0) {
            val s = levelReq.toString()
            pose.pushMatrix()
            pose.scale(0.75f, 0.75f)
            ctx.text(font, s, ((16f / 0.75f) - font.width(s)).toInt(), ((16f / 0.75f) - 7f).toInt(), 0xFFFF5555.toInt(), true)
            pose.popMatrix()
        }

        if (FishSettings.pfShowMissingClasses) {
            val missing = CLASSES.filter { it !in present }.map { it.first().toString() }
            if (missing.isNotEmpty() && missing.size < 5) {
                pose.pushMatrix()
                pose.scale(0.75f, 0.75f)
                ctx.text(font, missing.take(3).joinToString(""), 0, 0, 0xFF55FFFF.toInt(), true)
                if (missing.size > 3) ctx.text(font, missing.drop(3).joinToString(""), 0, 8, 0xFF55FFFF.toInt(), true)
                pose.popMatrix()
            }
        }

        pose.popMatrix()
    }

    // ── tooltip ─────────────────────────────────────────────────────────────

    private fun onTooltip(stack: ItemStack, lines: MutableList<Component>) {
        if (!inPartyFinder() || !FishSettings.pfTooltipStats || stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) return

        var floor = 0
        var master = false
        for (comp in lines) {
            val p = COLOR.replace(comp.string, "")
            if (p.contains("Master Mode")) master = true
            val fm = FLOOR.matcher(p)
            if (fm.find()) floor = parseFloor(fm.group(1))
        }

        val present = HashSet<String>()
        for (i in lines.indices) {
            val p = COLOR.replace(lines[i].string, "")
            val m = MEMBER.matcher(p)
            if (!m.matches()) continue
            val name = m.group(1)
            val cls = m.group(2)
            val lvl = m.group(3).toIntOrNull() ?: 0
            present.add(cls)
            lines[i] = Component.literal(" §b$name: §e$cls ${classColor(lvl)}$lvl${statsFor(name, floor, master)}")
        }

        if (FishSettings.pfTooltipMissingList) {
            val missing = CLASSES.filter { it !in present }
            if (missing.isNotEmpty()) lines.add(Component.literal("§cMissing: §7" + missing.joinToString(", ")))
        }
    }

    private fun statsFor(name: String, floor: Int, master: Boolean): String {
        val key = name.lowercase()
        val d = cache[key] ?: run { request(name); return " §7(…)" }
        val sb = StringBuilder(" §b(§6${d.cataLevel}§b)")
        if (FishSettings.pfShowSecrets && d.secretAverage != null) {
            sb.append(" §8[§a${d.totalSecrets}§8/§b${d.secretAverage}§8]")
        }
        if (FishSettings.pfShowPb) {
            val pb = (if (master) d.masterPbs else d.cataPbs).getOrNull(floor) ?: "N/A"
            sb.append(" §8[§9$pb§8]")
        }
        return sb.toString()
    }

    private fun request(name: String) {
        val key = name.lowercase()
        if (cache.containsKey(key) || !pending.add(key)) return
        if (pending.size > 6) { pending.remove(key); return }
        HypixelApi.getByNameSilent(name) { data ->
            cache[key] = data
            pending.remove(key)
        }
    }

    private fun parseFloor(s: String): Int = s.toIntOrNull() ?: when (s.uppercase()) {
        "I" -> 1; "II" -> 2; "III" -> 3; "IV" -> 4; "V" -> 5; "VI" -> 6; "VII" -> 7
        else -> 0
    }

    private fun classColor(level: Int): String = when {
        level >= 50 -> "§c§l"
        level >= 45 -> "§c"
        level >= 40 -> "§6"
        level >= 35 -> "§d"
        level >= 30 -> "§9"
        level >= 25 -> "§b"
        level >= 20 -> "§2"
        level >= 15 -> "§a"
        level >= 10 -> "§e"
        level >= 5 -> "§f"
        else -> "§7"
    }
}
