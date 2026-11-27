package fishmod.features.rift

import fishmod.compat.center
import fishmod.compat.curScreen
import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatRaw
import fishmod.utils.events.registerPacket
import fishmod.utils.rendering.DrawHooks
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.rift.RiftSettings as S

// Rift timer, vermin tracker, Kloon hacking solver, timite expiry and Living Cave defense blocks (logic ideas from SkyHanni)
object RiftExtras {

    // from SkyHanni RiftTimer: the XP level is the Rift time in seconds
    private var maxTime = 0
    private var tick = 0

    // from SkyHanni VerminTracker
    private val VACUUMED = Regex("""You vacuumed a (Silverfish|Spider|Fly)!""")

    // from SkyHanni KloonHacking
    private val HACKING_TITLES = setOf("Hacking", "Hacking (As seen on CSI)")
    private val answers = ArrayList<String>()

    // from SkyHanni TimiteHelper: blue panes evolve 31s after appearing
    private val timite = HashMap<Long, Long>()
    private const val TIMITE_MS = 31_000L

    // from SkyHanni LivingCaveDefenseBlocks
    private val DEFENSE_MOBS = setOf("Autonull", "Autocap", "Autochest", "Autopants", "Autoboots")
    private class Defense(val pos: BlockPos, val mob: Player)
    private val defense = ArrayList<Defense>()

    fun init() {
        MiningHuds.reg("Rift Timer", "rift_timer", 90, 10,
            { S.riftTimerX }, { S.riftTimerX = it }, { S.riftTimerY }, { S.riftTimerY = it },
            { S.riftTimerScale }, { S.riftTimerScale = it },
            { S.riftTimer && Rift.inRift() && !Rift.inArea("Mirrorverse") }, { S.riftTimer },
            example = { timerLines(754, 1800) }) { timerLines(Minecraft.getInstance().player?.experienceLevel ?: 0, maxTime) }
        MiningHuds.reg("Vermin Tracker", "rift_vermin", 100, 40,
            { S.riftVerminX }, { S.riftVerminX = it }, { S.riftVerminY }, { S.riftVerminY = it },
            { S.riftVerminScale }, { S.riftVerminScale = it },
            { S.riftVermin && Rift.inArea("West Village", "Infested House") }, { S.riftVermin },
            example = { verminLines() }) { verminLines() }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val me = mc.player ?: return@register
            if (S.riftTimer && Rift.inRift() && me.experienceLevel > maxTime) maxTime = me.experienceLevel
            if (tick++ % 20 == 0 && S.riftTimite && Rift.inArea("Mountaintop")) scanTimite()
            if (S.riftHacking) scanHacking()
            if (defense.isNotEmpty()) defense.removeIf { !it.mob.isAlive || mc.level?.getBlockState(it.pos)?.isAir != false }
        }
        FishEvents.chat.register { t ->
            if (S.riftVermin && Rift.inRift()) VACUUMED.find(Mining.strip(t.chatRaw))?.let { m ->
                when (m.groupValues[1]) { "Silverfish" -> S.riftVerminSilverfish++; "Spider" -> S.riftVerminSpider++; else -> S.riftVerminFly++ }
            }
            false
        }
        FishEvents.worldChange.register { Minecraft.getInstance().execute { maxTime = 0; timite.clear(); defense.clear() }; false }
        registerPacket(ClientboundBlockUpdatePacket::class.java, gate = { S.riftDefense && Rift.inArea("Living Cave") }) { p ->
            if (p is ClientboundBlockUpdatePacket) { val pos = p.pos; val st = p.blockState; Minecraft.getInstance().execute { onBlock(pos, st) } }
            false
        }
        registerPacket(ClientboundBlockUpdatePacket::class.java, ClientboundSectionBlocksUpdatePacket::class.java, gate = { S.riftTimite && Rift.inArea("Mountaintop") }) { p ->
            val now = System.currentTimeMillis(); val placed = ArrayList<Long>()
            when (p) {
                is ClientboundBlockUpdatePacket -> if (p.blockState.block == fishmod.compat.CB.BLUE_STAINED_GLASS_PANE) placed += p.pos.asLong()
                is ClientboundSectionBlocksUpdatePacket -> p.runUpdates { pos, st -> if (st.block == fishmod.compat.CB.BLUE_STAINED_GLASS_PANE) placed += pos.asLong() }
            }
            if (placed.isNotEmpty()) Minecraft.getInstance().execute { for (k in placed) timite.merge(k, now, ::minOf) }
            false
        }
        DrawHooks.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y ->
            if (!S.riftHacking || answers.size < 5 || stack.isEmpty || !inHacking()) return@register
            val slot = DrawHooks.currentSlot ?: return@register
            if (slot.container === Minecraft.getInstance().player?.inventory) return@register
            hackColor(slot.index, Mining.strip(stack.hoverName.string).trim())?.let { ctx.fill(x, y, x + 16, y + 16, it) }
        }
        RenderHooks.GIZMO.register { _ -> render() }
    }

    private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    private fun timerLines(cur: Int, max: Int): List<String> {
        if (cur <= 0) return emptyList()
        val c = if (cur <= 60) "§c" else if (cur <= 300) "§e" else "§b"
        val tail = if (max > 0) "§7/§b${fmt(max)} §7(${cur * 100 / max}%)" else ""
        return listOf("${fishmod.features.garden.Garden.legacy(S.riftTimerColor)}Rift Time: $c${fmt(cur)}$tail")
    }

    private fun verminLines() = listOf("§7Vermin Tracker:", "§7- §e${S.riftVerminFly} §aFlies",
        "§7- §e${S.riftVerminSpider} §aSpiders", "§7- §e${S.riftVerminSilverfish} §aSilverfish")

    private fun hackScreen(): AbstractContainerScreen<*>? = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*>
    private fun inHacking() = hackScreen()?.let { fishmod.utils.ScreenTitle.plain(it).trim() in HACKING_TITLES } == true

    private fun scanHacking() {
        if (!inHacking()) { answers.clear(); return }
        if (answers.size == 5) return
        val slots = hackScreen()?.menu?.slots ?: return
        if (slots.size < 54 || (2..6).any { slots[it].item.isEmpty }) return
        answers.addAll((2..6).map { Mining.strip(slots[it].item.hoverName.string).trim() })
    }

    private fun hackColor(idx: Int, name: String): Int? {
        for (i in 0 until 5) {
            if (idx == 11 + 10 * i) return if (name == answers[i]) S.riftHackingColor else 0x80FF5555.toInt()
            if (idx in (i * 9 + 9)..(i * 9 + 17) && name == answers[i]) return 0x80FFFF55.toInt()
        }
        return null
    }

    private fun scanTimite() {
        val mc = Minecraft.getInstance(); val level = mc.level ?: return; val me = mc.player ?: return
        val c = me.blockPosition(); val pos = BlockPos.MutableBlockPos(); val now = System.currentTimeMillis()
        for (x in c.x - 15..c.x + 15) for (y in c.y - 8..c.y + 8) for (z in c.z - 15..c.z + 15) {
            pos.set(x, y, z)
            if (level.getBlockState(pos).block == fishmod.compat.CB.BLUE_STAINED_GLASS_PANE) timite.putIfAbsent(pos.asLong(), now)
        }
        timite.keys.removeIf { level.getBlockState(BlockPos.of(it)).block != fishmod.compat.CB.BLUE_STAINED_GLASS_PANE }
    }

    private fun onBlock(pos: BlockPos, st: BlockState) {
        if (st.isAir) { defense.removeIf { it.pos == pos }; return }
        if (st.block !is StainedGlassBlock && st.block != Blocks.DIAMOND_BLOCK) return
        val level = Minecraft.getInstance().level ?: return
        val mob = level.getEntitiesOfClass(Player::class.java, AABB(pos).inflate(15.0)) { p ->
            Mining.strip(p.name.string).trim() in DEFENSE_MOBS && p.health < p.maxHealth
        }.minByOrNull { it.distanceToSqr(pos.center) } ?: return
        if (defense.none { it.pos == pos }) defense += Defense(pos, mob)
    }

    private fun render() {
        if (S.riftTimite && timite.isNotEmpty()) {
            val now = System.currentTimeMillis()
            for ((k, t) in timite) {
                val left = TIMITE_MS - (now - t)
                if (left !in 0..5999) continue
                val p = BlockPos.of(k)
                FishDraw.gizmoText(Component.literal("§c" + fishmod.utils.Fmt.f1(left / 1000.0) + "s"), Vec3(p.x + 0.5, p.y + 0.6, p.z + 0.5), 0.6f, -1, false)
            }
        }
        if (S.riftDefense && defense.isNotEmpty()) {
            val c = S.riftDefenseColor
            for (d in defense) {
                FishDraw.gizmoBox(AABB(d.pos), (c and 0xFFFFFF) or 0x40000000, c, true)
                FishDraw.gizmoText(Component.literal("§bBreak!"), Vec3(d.pos.x + 0.5, d.pos.y + 1.5, d.pos.z + 0.5), 1.5f, -1, false)
                FishDraw.gizmoLine(d.mob.position().add(0.0, 0.5, 0.0), d.pos.center, c, 3f, true)
            }
        }
    }

    fun resetVermin() {
        S.riftVerminFly = 0; S.riftVerminSpider = 0; S.riftVerminSilverfish = 0
        FishUtil.addChatMessage(Component.literal("§aVermin Tracker reset."))
    }
}
