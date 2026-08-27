package fishmod.features.dungeon

import fishmod.utils.FishMsg
import fishmod.utils.HypixelApi
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.PartyUtil
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.events.Events
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
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
 * In-menu Party Finder helper, ported from NoammAddons' `PartyFinder`.
 *
 *  - draws the red Dungeon-Level-Required number and the missing-class letters on each party head
 *  - green-highlights any party head that's still missing YOUR dungeon class ([myClass])
 *  - rewrites each "Name: Class (lvl)" tooltip line with the player's Cata level / secrets / floor PB,
 *    fetched lazily through [HypixelApi] into a session cache, and appends a "Missing: …" line
 *  - auto-kick: while party leader, kicks a joiner whose S+ PB / secrets miss the configured bar
 */
object PartyFinder {

    private val CLASSES = listOf("Archer", "Tank", "Berserk", "Healer", "Mage")
    private val MEMBER = Pattern.compile("^\\s*(\\w{1,16}):?\\s+(Archer|Tank|Berserk|Healer|Mage)\\s*\\((\\d+)\\)\\s*$")
    private val LEVEL_REQ = Pattern.compile("Dungeon Level Required:\\s*(\\d+)")
    private val FLOOR = Pattern.compile("Floor:\\s*(?:Floor\\s+)?(\\w+)")
    private val SELECTED_CLASS = Pattern.compile("Currently Selected:\\s*(\\w+)")
    private val COLOR = Regex("§.")

    /** Last "Currently Selected: X" seen in the Catacombs Gate menu — seeds "Auto" my-class. */
    @Volatile private var capturedClass: String? = null

    // "Party Finder > Name joined the dungeon group! (Archer Level 42)"
    private val PF_JOIN = Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group! \\(\\w+ Level \\d+\\)$")
    private val PB_LINE = Regex("^(\\d+):(\\d{2})\\s+(S\\+?)$")

    private val cache = ConcurrentHashMap<String, HypixelApi.DungeonData>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    /** lowercased names kicked this lobby — re-kicked on sight until a world change clears it. */
    private val kicked = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y -> onSlotBefore(ctx, stack, x, y) }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y -> onSlot(ctx, stack, x, y) }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> onTooltip(stack, lines) })
        ClientTickEvents.END_CLIENT_TICK.register { captureSelectedClass() }

        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.pfAutoKick) {
                val m = PF_JOIN.matcher(COLOR.replace(text.string, ""))
                if (m.find()) tryAutoKick(m.group(1))
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { kicked.clear(); false }
    }

    // ── auto kick ───────────────────────────────────────────────────────────

    private fun tryAutoKick(name: String) {
        val mc = Minecraft.getInstance()
        val self = mc.player?.name?.string ?: return
        if (name.equals(self, ignoreCase = true)) return
        if (!PartyUtil.amLeader()) return

        val key = name.lowercase()
        if (key in kicked) {
            mc.execute {
                FishMsg.send("§9AutoKick §7> re-kicking §e$name §7(previously kicked)")
                Misc.executeCommand("party kick $name")
            }
            return
        }

        val cached = cache[key]
        if (cached != null) { finishAutoKick(name, key, evaluate(cached)); return }
        HypixelApi.getByNameSilent(name) { d ->
            cache[key] = d
            finishAutoKick(name, key, evaluate(d))
        }
    }

    private fun finishAutoKick(name: String, key: String, reasons: List<String>) {
        if (reasons.isEmpty() || !kicked.add(key)) return
        Minecraft.getInstance().execute {
            if (!PartyUtil.amLeader()) { kicked.remove(key); return@execute }
            if (FishSettings.pfAutoKickInform) {
                Misc.executeCommand("pc AutoKick $name: ${reasons.joinToString(", ")}")
            } else {
                FishMsg.send("§cKicking §e$name§c: §7${reasons.joinToString(", ")}")
            }
            Misc.executeCommand("party kick $name")
        }
    }

    private fun evaluate(d: HypixelApi.DungeonData): List<String> {
        val reasons = ArrayList<String>()
        val floor = FishSettings.pfAutoKickFloor.coerceIn(1, 7)
        val master = FishSettings.pfAutoKickMaster
        val maxSec = FishSettings.pfAutoKickMaxSeconds
        val prefix = if (master) "M" else "F"

        val pb = PB_LINE.find((if (master) d.masterPbs else d.cataPbs).getOrNull(floor)?.trim() ?: "")
        when {
            pb == null || pb.groupValues[3] != "S+" ->
                reasons.add("$prefix$floor PB(no S+/${fmt(maxSec)})")
            else -> {
                val secs = pb.groupValues[1].toInt() * 60 + pb.groupValues[2].toInt()
                if (secs > maxSec) reasons.add("$prefix$floor PB(${fmt(secs)}/${fmt(maxSec)})")
            }
        }

        val minK = FishSettings.pfAutoKickMinSecretsK
        if (minK > 0 && d.totalSecrets < minK * 1000L) {
            reasons.add("Secrets(${d.totalSecrets / 1000}k/${minK}k)")
        }
        return reasons
    }

    private fun fmt(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

    private fun inPartyFinder(): Boolean {
        if (!FishSettings.pfMenuEnabled) return false
        val s = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return false
        return COLOR.replace(s.title.string, "") == "Party Finder"
    }

    private fun lore(stack: ItemStack): List<String> =
        stack.get(DataComponents.LORE)?.lines()?.map { COLOR.replace(it.string, "") } ?: emptyList()

    // ── my class (for the "can I join" highlight) ───────────────────────────

    private fun captureSelectedClass() {
        val s = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return
        if (COLOR.replace(s.title.string, "") != "Catacombs Gate") return
        for (slot in s.menu.slots) {
            for (line in lore(slot.item)) {
                val m = SELECTED_CLASS.matcher(line)
                if (m.find()) {
                    val c = m.group(1).lowercase().replaceFirstChar { it.uppercase() }
                    if (c in CLASSES) { capturedClass = c; return }
                }
            }
        }
    }

    /** The dungeon class to test parties against — explicit config, else live class, else last captured. */
    private fun myClass(): String? {
        val cfg = FishSettings.pfMyClass
        if (cfg in CLASSES) return cfg
        DungeonClass.currentClass?.let { return it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return capturedClass
    }

    // ── joinable highlight (behind the head) ────────────────────────────────

    private fun onSlotBefore(ctx: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (!inPartyFinder() || !FishSettings.pfHighlightJoinable) return
        if (stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) return
        val mine = myClass() ?: return
        val present = HashSet<String>()
        for (line in lore(stack)) {
            val m = MEMBER.matcher(line)
            if (m.matches()) present.add(m.group(2))
        }
        if (present.isEmpty() || mine in present) return
        ctx.fill(x - 1, y - 1, x + 17, y + 17, 0x6055FF55)
    }

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
