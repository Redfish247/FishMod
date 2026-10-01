package fishmod.features.diana

import fishmod.features.FishHudEditor
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.resources.Identifier

// Loot / Mob / Stats / Magic Find HUDs for the Diana tracker
object DianaTrackerHud {

    private val LOOT_ORDER = listOf(
        "SHIMMERING_WOOL" to "§cShimmering Wool", "MANTI_CORE" to "§cManti-core", "KING_MINOS_SHARD" to "§cKing Minos Shard",
        "FATEFUL_STINGER" to "§dFateful Stinger", "CHIMERA" to "§dChimera", "BRAIN_FOOD" to "§5Brain Food",
        "MINOS_RELIC" to "§5Minos Relic", "SPHINX_SHARD" to "§5Sphinx Shard", "BRAIDED_GRIFFIN_FEATHER" to "§5Braided Griffin Feather",
        "DAEDALUS_STICK" to "§6Daedalus Stick", "MINOTAUR_SHARD" to "§6Minotaur Shard", "GRIFFIN_FEATHER" to "§6Griffin Feather",
        "MYTHOS_FRAGMENT" to "§6Mythos Fragment", "WASHED_UP_SOUVENIR" to "§6Washed-up Souvenir", "CRETAN_URN" to "§2Cretan Urn",
        "DWARF_TURTLE_SHELMET" to "§2Dwarf Turtle Shelmet", "CROCHET_TIGER_PLUSHIE" to "§2Crochet Tiger Plushie",
        "ANTIQUE_REMEDIES" to "§2Antique Remedies", "CRETAN_BULL_SHARD" to "§2Cretan Bull Shard", "HARPY_SHARD" to "§2Harpy Shard",
        "HILT_OF_REVELATIONS" to "§9Hilt of Revelations", "ENCHANTED_GOLD" to "§9Enchanted Gold",
        "ENCHANTED_ANCIENT_CLAW" to "§9Enchanted Ancient Claw", "ANCIENT_CLAW" to "§9Ancient Claw",
    )
    private val MOB_COLOR = mapOf(
        "KING_MINOS" to "§c", "MANTICORE" to "§c", "MINOS_INQUISITOR" to "§d", "SPHINX" to "§5",
        "MINOS_CHAMPION" to "§6", "MINOTAUR" to "§6", "MINOS_HUNTER" to "§9",
    )
    // SBO order and colours; ls = also show the loot-share counter
    private class Stat(val key: String, val color: String, val label: String, val ls: Boolean = false)
    private val STATS = listOf(
        Stat("MOBS_KING_MINOS", "§c", "Mobs since King"),
        Stat("SHIMMERING_WOOL", "§c", "Kings since Wool", true),
        Stat("MOBS_MANTICORE", "§c", "Mobs since Manti"),
        Stat("MANTI_CORE", "§c", "Mantis since Core", true),
        Stat("FATEFUL_STINGER", "§c", "Mantis since Stinger", true),
        Stat("MOBS_MINOS_INQUISITOR", "§d", "Mobs since Inq"),
        Stat("CHIMERA", "§d", "Inqs since Chimera", true),
        Stat("MOBS_SPHINX", "§d", "Mobs since Sphinx"),
        Stat("BRAIN_FOOD", "§5", "Sphinxes since Food", true),
        Stat("MINOS_RELIC", "§5", "Champs since Relic"),
        Stat("DAEDALUS_STICK", "§6", "Minotaurs since Stick"),
    )

    // id null = header, never hideable; tail + tailX = value column then aligned item text
    private class Row(val id: String?, val text: String, val tail: String? = null, val tailX: Int = 0) {
        fun width(font: net.minecraft.client.gui.Font): Int =
            if (tail == null) font.width(text) else tailX + font.width(tail)
    }
    private class Hud(val gx: () -> Int, val gy: () -> Int, val gs: () -> Double, val on: () -> Boolean, val make: () -> List<Row>)
    private class Cache(var version: Int = -1, var at: Long = 0L, var rows: List<Row> = emptyList())
    private val caches = HashMap<String, Cache>()
    private val huds = LinkedHashMap<String, Hud>()
    private val STRIP = Regex("§.")

    fun init() {
        reg("Diana Loot Tracker", "diana_loot", 200, 250,
            { DianaSettings.dianaLootPosX }, { DianaSettings.dianaLootPosX = it },
            { DianaSettings.dianaLootPosY }, { DianaSettings.dianaLootPosY = it },
            { DianaSettings.dianaLootPosScale }, { DianaSettings.dianaLootPosScale = it },
            { DianaSettings.dianaLootTracker != "Off" }, ::lootRows)
        reg("Diana Mob Tracker", "diana_mobs", 160, 150,
            { DianaSettings.dianaMobPosX }, { DianaSettings.dianaMobPosX = it },
            { DianaSettings.dianaMobPosY }, { DianaSettings.dianaMobPosY = it },
            { DianaSettings.dianaMobPosScale }, { DianaSettings.dianaMobPosScale = it },
            { DianaSettings.dianaMobTracker != "Off" }, ::mobRows)
        reg("Diana Stats", "diana_stats", 140, 135,
            { DianaSettings.dianaStatsPosX }, { DianaSettings.dianaStatsPosX = it },
            { DianaSettings.dianaStatsPosY }, { DianaSettings.dianaStatsPosY = it },
            { DianaSettings.dianaStatsPosScale }, { DianaSettings.dianaStatsPosScale = it },
            { DianaSettings.dianaStatsTracker }, ::statRows)
        reg("Diana Magic Find", "diana_mf", 130, 80,
            { DianaSettings.dianaMfPosX }, { DianaSettings.dianaMfPosX = it },
            { DianaSettings.dianaMfPosY }, { DianaSettings.dianaMfPosY = it },
            { DianaSettings.dianaMfPosScale }, { DianaSettings.dianaMfPosScale = it },
            { DianaSettings.dianaMfTracker }, ::mfRows)

        // In the inventory every line shows; click one to hide/unhide it
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                forVisible { id, h -> draw(ctx, rows(id, h), h.gx(), h.gy(), h.gs(), true) }
            })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                click.button() != 0 || !onClick(click.x(), click.y())
            })
        })
    }

    private fun shown() = DianaSettings.dianaTracker && Diana.active()

    private inline fun forVisible(f: (String, Hud) -> Unit) {
        if (!shown()) return
        for ((id, h) in huds) if (h.on()) f(id, h)
    }

    private fun onClick(mx: Double, my: Double): Boolean {
        val font = Minecraft.getInstance().font
        forVisible { id, h ->
            val sc = h.gs()
            val lx = (mx - h.gx()) / sc; val ly = (my - h.gy()) / sc
            if (lx < 0 || ly < 0) return@forVisible
            val row = rows(id, h).getOrNull((ly / 10).toInt()) ?: return@forVisible
            if (row.id == null || lx > row.width(font)) return@forVisible
            DianaTracker.toggleHidden(row.id)
            return true
        }
        return false
    }

    private fun reg(
        name: String, id: String, w: Int, h: Int,
        gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit,
        gs: () -> Double, ss: (Double) -> Unit, on: () -> Boolean, lines: () -> List<Row>,
    ) {
        FishHudEditor.register(name, gx, sx, gy, sy, w, h, gs, ss, on)
        val hud = Hud(gx, gy, gs, on, lines)
        huds[id] = hud
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", id)) { ctx, _ ->
            if (!FishHudEditor.isOpen() && Minecraft.getInstance().screen !is InventoryScreen && shown() && on())
                draw(ctx, rows(id, hud), gx(), gy(), gs(), false)
        }
    }

    private fun rows(id: String, h: Hud): List<Row> {
        val c = caches.getOrPut(id) { Cache() }
        val now = System.currentTimeMillis()
        if (c.version != DianaTracker.version || now - c.at > 1000) {
            c.version = DianaTracker.version; c.at = now; c.rows = h.make()
        }
        return c.rows
    }

    // editing = inventory view: hidden rows stay in place, greyed and struck through
    private fun draw(ctx: GuiGraphicsExtractor, rows: List<Row>, x: Int, y: Int, scale: Double, editing: Boolean) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui || rows.isEmpty()) return
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale.toFloat(), scale.toFloat())
        var i = 0
        for (r in rows) {
            val hidden = r.id != null && DianaTracker.isHidden(r.id)
            if (hidden && !editing) continue
            val font = mc.font
            if (r.tail == null) {
                ctx.text(font, if (hidden) "§7§m" + r.text.replace(STRIP, "") else r.text, 0, i * 10, -1, true)
            } else {
                val strike = hidden
                val left = if (strike) "§7§m" + r.text.replace(STRIP, "") else r.text
                val right = if (strike) "§7§m" + r.tail.replace(STRIP, "") else r.tail
                ctx.text(font, left, 0, i * 10, -1, true)
                ctx.text(font, right, r.tailX, i * 10, -1, true)
            }
            i++
        }
        pose.popMatrix()
    }

    private fun title(label: String, mode: String, t: DianaTracker.Tracker) =
        "§e§l$label §7(" + (if (mode == "Event" && t.year > 0) "Year ${t.year}" else mode) + ")"

    private fun lootRows(): List<Row> {
        val mode = DianaSettings.dianaLootTracker
        val t = DianaTracker.tracker(mode) ?: return emptyList()
        val hide = DianaSettings.dianaHideUnobtained
        val font = Minecraft.getInstance().font
        val out = ArrayList<Row>()
        out += Row(null, title("Diana Loot", mode, t))

        data class ItemLine(val id: String, val value: String, val tail: String)
        val items = ArrayList<ItemLine>()
        for ((k, label) in LOOT_ORDER) {
            val n = t.item(k); val ls = t.item(k + "_LS")
            if (hide && n + ls == 0L) continue
            val coins = DianaTracker.priceOf(k) * (n + ls)
            val value = if (coins > 0) "§6${DianaTracker.short(coins)}" else ""
            val tail = "${"| " + label} §f$n" + pct(t, k, n, "") + (if (ls > 0) " §7+$ls LS" + pct(t, k, ls, "_LS") else "")
            items += ItemLine("loot:$k", value, tail)
        }
        val valueColW = items.maxOfOrNull { font.width(it.value) } ?: 0
        val itemX = valueColW + 6
        for (line in items) out += Row(line.id, line.value, line.tail, itemX)

        val burrows = t.item("TOTAL_BURROWS").toDouble()
        val profit = DianaTracker.profit(t)
        out += Row("loot:COINS", "§6Coins §6${DianaTracker.short(t.item("COINS").toDouble())}")
        out += Row("loot:BURROWS", "§7Burrows §f${"%,d".format(burrows.toLong())} §7(${"%.1f".format(DianaTracker.perHour(burrows, t))}/h)")
        out += Row("loot:PLAYTIME", "§7Playtime §f${DianaTracker.fmtTime(t.timeMs)}" + if (DianaTracker.paused()) " §c[Paused]" else "")
        out += Row("loot:PROFIT", "§eProfit §6${DianaTracker.short(profit)} §7(${DianaTracker.short(DianaTracker.perHour(profit, t))}/h)")
        return out
    }

    private val PCT_DROPS = setOf("CHIMERA", "FATEFUL_STINGER", "SHIMMERING_WOOL", "MANTI_CORE", "MINOS_RELIC", "BRAIN_FOOD")

    // Drop rate per source mob, e.g. Chimera per Inquisitor (LS drops against LS kills)
    private fun pct(t: DianaTracker.Tracker, k: String, n: Long, suffix: String): String {
        if (k !in PCT_DROPS || n <= 0) return ""
        val src = DianaTracker.drop(k).source ?: return ""
        val kills = t.mob(src + suffix)
        return if (kills > 0) " §7(${"%.2f".format(n * 100.0 / kills)}%)" else ""
    }

    private fun mobRows(): List<Row> {
        val mode = DianaSettings.dianaMobTracker
        val t = DianaTracker.tracker(mode) ?: return emptyList()
        val tot = t.mob("TOTAL_MOBS")
        val out = ArrayList<Row>()
        out += Row(null, title("Diana Mobs", mode, t))
        for (name in DianaTracker.MOBS) {
            val k = DianaTracker.key(name)
            val n = t.mob(k); val ls = t.mob(k + "_LS")
            if (DianaSettings.dianaHideUnobtained && n + ls == 0L) continue
            val pct = if (tot > 0) n * 100.0 / tot else 0.0
            out += Row("mob:$k", "${MOB_COLOR[k] ?: "§a"}$name §f$n §7(${"%.2f".format(pct)}%)" + (if (ls > 0) " §8+$ls LS" else ""))
        }
        out += Row("mob:TOTAL", "§7Total §f${"%,d".format(tot)} §8(${"%.0f".format(DianaTracker.perHour(tot.toDouble(), t))}/h)")
        return out
    }

    private fun statRows(): List<Row> =
        listOf(Row(null, "§e§lDiana Stats")) + STATS.map { s ->
            val ls = if (s.ls) "§7, ${s.color}since §7[§bLS§7]: §b${"%,d".format(DianaTracker.sinceCount(s.key + "_LS"))}" else ""
            Row("stat:${s.key}", "§7 - ${s.color}${s.label}: §b${"%,d".format(DianaTracker.sinceCount(s.key))}$ls")
        }

    private fun mfRows(): List<Row> {
        val out = arrayListOf(Row(null, "§e§lHighest Magic Find"))
        for (d in DianaTracker.DROPS) {
            if (d.source == null) continue
            val mf = DianaTracker.highestMf(d.key)
            if (DianaSettings.dianaHideUnobtained && mf == 0) continue
            out += Row("mf:${d.key}", "${d.color}${d.name}§7: §b$mf% ✯")
        }
        return out
    }
}
