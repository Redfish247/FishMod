package fishmod.features.garden

import fishmod.compat.curScreen
import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.features.hud.fishmodCustomDataTag
import fishmod.features.mining.Mining
import fishmod.utils.FishUtil
import fishmod.utils.data.ItemTools
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatRaw
import fishmod.utils.events.registerPacket
import fishmod.utils.rendering.DrawHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EquipmentSlot
import fishmod.features.garden.GardenExtraSettings as X

// Mantid bonus, spray expiry, Phantomleaf, DNA Analyzer and SkyMart copper value (logic ideas from SkyHanni / NoFrills)
object GardenAddons {

    // from SkyHanni MantidKillDisplay: each pest kill counts 10 min, max 20
    private const val MANTID_MAX = 20
    private const val MANTID_MS = 600_000L
    private val mantidKills = ArrayDeque<Long>()
    private val PEST_KILL = Regex("""received (\d+)x (.+?) for killing an? ([A-Za-z ]+)!""")

    // from SkyHanni GardenPlotApi tab pattern: "Spray: Compost (1m 3s)" / "Spray: None"
    private val SPRAY_TIME = Regex("""\((?:(\d+)m)?\s*(?:(\d+)s)?\)""")
    private var sprayLeft = -1
    private var sprayWarned = false

    // from NoFrills PhantomleafSolver: a "(...❤...)" subtitle opens a 2s window for basedrum volume hints
    private var phantomUntil = 0L
    private var phantomVolume = 0f

    // from SkyHanni DnaAnalyzerSolver: menu title ends with " DNA", board in slots 9..44
    private var dnaSwap: Pair<Int, Int>? = null
    private var dnaKey = ""

    private var tick = 0

    fun init() {
        Garden.hud("Mantid Bonus", "garden_mantid", 120, 20,
            { X.gardenMantidHudX }, { X.gardenMantidHudX = it }, { X.gardenMantidHudY }, { X.gardenMantidHudY = it },
            { X.gardenMantidHudScale }, { X.gardenMantidHudScale = it },
            { X.gardenMantid && Garden.inGarden() && wearingMantid() }, { X.gardenMantid },
            example = { mantidLines(12, 95_000) }) { mantidLines(mantidKills.size, mantidKills.firstOrNull()?.let { it - System.currentTimeMillis() } ?: 0) }
        FishEvents.chat.register { t ->
            if (X.gardenMantid && Garden.inGarden() && PEST_KILL.containsMatchIn(Garden.strip(t.chatRaw)) && wearingMantid()) {
                mantidKills.addLast(System.currentTimeMillis() + MANTID_MS)
                while (mantidKills.size > MANTID_MAX) mantidKills.removeFirst()
            }
            false
        }
        FishEvents.worldChange.register { Minecraft.getInstance().execute { mantidKills.clear(); sprayLeft = -1; phantomUntil = 0 }; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            val now = System.currentTimeMillis()
            while (mantidKills.isNotEmpty() && mantidKills.first() < now) mantidKills.removeFirst()
            if (tick++ % 20 == 0 && X.gardenSprayAlert && Garden.inGarden()) checkSpray()
            if (X.gardenPhantomleaf && now < phantomUntil && phantomVolume >= 0.94f)
                FishUtil.forceTitle(Component.literal(if (phantomVolume >= 0.99f) "§aFound Phantomleaf" else "§eClose to Phantomleaf"), Component.empty(), 300)
            if (X.gardenDna && tick % 4 == 0) scanDna(mc)
        }
        registerPacket(ClientboundSetSubtitleTextPacket::class.java, ClientboundSoundPacket::class.java, gate = { X.gardenPhantomleaf && Garden.inGarden() }) { p ->
            when (p) {
                is ClientboundSetSubtitleTextPacket -> {
                    val s = Garden.strip(p.text().string).trim()
                    if (s.startsWith("(") && s.contains("❤") && s.endsWith(")")) { phantomUntil = System.currentTimeMillis() + 2000; phantomVolume = 0f }
                }
                is ClientboundSoundPacket -> if (System.currentTimeMillis() < phantomUntil && p.sound.value() == SoundEvents.NOTE_BLOCK_BASEDRUM.value()) phantomVolume = p.volume
            }
            false
        }
        DrawHooks.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y ->
            val sw = dnaSwap ?: return@register
            if (!X.gardenDna || stack.isEmpty || !dnaMenu()) return@register
            val slot = DrawHooks.currentSlot ?: return@register
            if (slot.container === Minecraft.getInstance().player?.inventory) return@register
            if (slot.index == sw.first || slot.index == sw.second) ctx.fill(x, y, x + 16, y + 16, X.gardenDnaColor)
        }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> skyMartTooltip(stack, lines) })
    }

    private fun wearingMantid(): Boolean {
        val me = Minecraft.getInstance().player ?: return false
        return listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).any {
            me.getItemBySlot(it).fishmodCustomDataTag()?.getStringOr("modifier", "") == "mantid"
        }
    }

    private fun mantidLines(n: Int, nextMs: Long): List<String> {
        val c = Garden.legacy(X.gardenMantidColor)
        val out = arrayListOf("${c}Mantid Bonus: ${if (n < MANTID_MAX) "§c" else "§a"}$n§7/§a$MANTID_MAX")
        if (n > 0) out += "§7Next expires: §b${nextMs / 60000}m ${nextMs / 1000 % 60}s"
        return out
    }

    private fun checkSpray() {
        val line = Mining.tabLine("Spray:") ?: run { sprayLeft = -1; return }
        val m = SPRAY_TIME.find(line)
        val left = m?.let { (it.groupValues[1].toIntOrNull() ?: 0) * 60 + (it.groupValues[2].toIntOrNull() ?: 0) } ?: -1
        if (line.substringAfter(":").trim().startsWith("None")) {
            if (sprayLeft in 0..90) Mining.title(Garden.legacy(X.gardenSprayAlertColor) + "Spray expired!", "", 2000, true)
            sprayLeft = -1; sprayWarned = false; return
        }
        if (left > X.gardenSprayAlertSeconds) sprayWarned = false
        else if (left >= 0 && !sprayWarned) { sprayWarned = true; Mining.title(Garden.legacy(X.gardenSprayAlertColor) + "Spray ends in ${left}s", "", 2000, true) }
        sprayLeft = left
    }

    private fun screen() = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*>
    private fun dnaMenu() = screen()?.let { fishmod.utils.ScreenTitle.plain(it).trim().endsWith(" DNA") } == true

    // Colour code right before "DNA" in the formatted name
    private fun dnaColor(raw: String): Char? {
        val i = raw.indexOf("DNA"); if (i < 2) return null
        return raw.lastIndexOf('§', i - 1).takeIf { it >= 0 && it + 1 < raw.length }?.let { raw[it + 1] }?.takeIf { it in "cea9" }
    }

    private fun scanDna(mc: Minecraft) {
        if (!dnaMenu()) { dnaSwap = null; dnaKey = ""; return }
        val slots = screen()?.menu?.slots ?: return
        if (slots.size < 45) return
        val cols = List(9) { CharArray(4) }
        for (i in 9..44) cols[i % 9][i / 9 - 1] = dnaColor(slots[i].item.hoverName.chatRaw) ?: run { dnaSwap = null; return }
        val key = cols.joinToString("") { String(it) }
        if (key == dnaKey) return
        dnaKey = key
        dnaSwap = if (cols.any { it.toSet().size != 4 }) null else DnaSolver.nextSwap(cols)?.let { (a, b) -> a.first + (a.second + 1) * 9 to b.first + (b.second + 1) * 9 }
    }

    private fun skyMartTooltip(stack: net.minecraft.world.item.ItemStack, lines: MutableList<Component>) {
        if (!X.gardenSkyMart || stack.isEmpty) return
        val scr = screen() ?: return
        if (!fishmod.utils.ScreenTitle.plain(scr).startsWith("SkyMart ")) return
        val lore = Garden.lore(stack)
        val copper = lore.firstNotNullOfOrNull { COPPER.find(it.trim()) }?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return
        if (copper <= 0) return
        val id = ItemTools.getId(stack) ?: return
        val price = CroesusPrices.bazaarPrice(id, true)?.takeIf { it > 0 } ?: CroesusPrices.price(id)
        if (price <= 0) return
        lines += Component.literal("${Garden.legacy(X.gardenSkyMartColor)}Coins per copper: §6${Garden.short(price / copper)}")
    }

    // from SkyHanni readLoreCosts: a cost line like "25 Copper"
    private val COPPER = Regex("""^([\d,]+) Copper$""")
}

// Fewest swaps so every colour row connects (each column may only reorder its own 4 cells); ends stay fixed
object DnaSolver {
    private val PERMS: List<IntArray> = buildList {
        fun gen(a: IntArray, l: Int) {
            if (l == 4) { add(a.copyOf()); return }
            for (i in l until 4) { a[l] = a[i].also { a[i] = a[l] }; gen(a, l + 1); a[l] = a[i].also { a[i] = a[l] } }
        }
        gen(intArrayOf(0, 1, 2, 3), 0)
    }

    private fun connects(a: CharArray, b: CharArray): Boolean = (0 until 4).all { r ->
        b[r] == a[r] || (r > 0 && b[r - 1] == a[r]) || (r < 3 && b[r + 1] == a[r])
    }

    private fun swaps(from: CharArray, to: CharArray): List<Pair<Int, Int>> {
        val pos = IntArray(4); for (i in 0 until 4) pos[from.indexOf(to[i])] = i
        val seen = BooleanArray(4); val out = ArrayList<Pair<Int, Int>>()
        for (i in 0 until 4) {
            if (seen[i]) continue
            val cyc = ArrayList<Int>(); var c = i
            while (!seen[c]) { seen[c] = true; cyc += c; c = pos[c] }
            for (k in 1 until cyc.size) out += cyc[0] to cyc[k]
        }
        return out
    }

    // Returns the next swap as (column,row) pairs, or null if solved / unsolvable
    fun nextSwap(cols: List<CharArray>): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val n = cols.size - 2
        val inf = 1000
        val dp = Array(n) { IntArray(PERMS.size) { inf } }
        val par = Array(n) { IntArray(PERMS.size) { -1 } }
        fun col(i: Int, p: Int) = CharArray(4) { cols[i + 1][PERMS[p][it]] }
        for (p in PERMS.indices) if (connects(cols[0], col(0, p))) dp[0][p] = swaps(cols[1], col(0, p)).size
        for (i in 1 until n) for (p in PERMS.indices) {
            val cur = col(i, p); val cost = swaps(cols[i + 1], cur).size
            for (q in PERMS.indices) {
                if (dp[i - 1][q] >= inf || !connects(col(i - 1, q), cur)) continue
                if (dp[i - 1][q] + cost < dp[i][p]) { dp[i][p] = dp[i - 1][q] + cost; par[i][p] = q }
            }
        }
        var last = -1; var best = inf
        for (p in PERMS.indices) if (dp[n - 1][p] < best && connects(col(n - 1, p), cols.last())) { best = dp[n - 1][p]; last = p }
        if (last < 0 || best == 0) return null
        var i = n - 1; var cur = last
        while (i >= 0) {
            val sw = swaps(cols[i + 1], col(i, cur))
            if (sw.isNotEmpty()) return (i + 1 to sw[0].first) to (i + 1 to sw[0].second)
            cur = par[i][cur]; i--
        }
        return null
    }
}
