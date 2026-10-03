package fishmod.features.mining

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.FishHudEditor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.TabListCache
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

// Shared mining state: island/area gating, block breaks, sidebar/tab helpers, init of all mining parts
object Mining {

    fun interface BreakListener { fun onBreak(pos: BlockPos, old: BlockState, original: Boolean) }
    val breakListeners = ArrayList<BreakListener>()

    @JvmStatic var area: String = ""; private set
    @JvmStatic var sidebar: List<String> = emptyList(); private set
    private val recentTargets = HashMap<BlockPos, Long>()
    private var lastSwingMs = 0L
    private var tick = 0

    fun inMiningIsland(): Boolean = Location.`in`(Location.DWARVEN_MINES) || Location.`in`(Location.CRYSTAL_HOLLOWS) ||
        Location.`in`(Location.MINESHAFT) || Location.`in`(Location.GOLD_MINE) || Location.`in`(Location.DEEP_CAVERNS)
    fun inHollows(): Boolean = Location.`in`(Location.CRYSTAL_HOLLOWS)
    fun inShaft(): Boolean = Location.`in`(Location.MINESHAFT)
    fun inTunnels(): Boolean = Location.`in`(Location.DWARVEN_MINES) &&
        (area.contains("Glacite") || area.contains("Base Camp") || area.contains("Fossil Research"))
    fun inGlacite(): Boolean = inTunnels() || inShaft()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }
        Events.ON_PACKET.register { packet ->
            if (inMiningIsland()) when (packet) {
                is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { p, s -> onBlock(p, s) }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { recentTargets.clear(); area = ""; false }

        NucleusHighlight.init()
        MineshaftPity.init()
        GemstoneLocator.init()
        FossilSolver.init()
        MiningProfitTracker.init()
        MiningAbilities.init()
        Commissions.init()
        PowderChests.init()
        Corpses.init()
    }

    private fun onTick(mc: Minecraft) {
        if (mc.player == null) return
        if (tick++ % 20 == 0) {
            sidebar = sidebarLines(mc)
            area = sidebar.firstOrNull { it.startsWith("⏣") || it.startsWith("ф") }?.drop(1)?.trim() ?: ""
        }
        if (!inMiningIsland()) return
        // Block under the crosshair while attacking = a block we're mining
        if (mc.options.keyAttack.isDown) {
            lastSwingMs = System.currentTimeMillis()
            val hit = mc.hitResult
            if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) recentTargets[hit.blockPos.immutable()] = lastSwingMs
        }
        if (tick % 40 == 0) { val now = System.currentTimeMillis(); recentTargets.entries.removeIf { now - it.value > 3000 } }
    }

    // Runs before vanilla applies the packet, so the level still holds the old state
    private fun onBlock(pos: BlockPos, new: BlockState) {
        if (!(new.isAir || new.`is`(Blocks.BEDROCK))) return
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val old = mc.level?.getBlockState(pos) ?: return
        if (old.isAir || old.`is`(Blocks.BEDROCK)) return
        val now = System.currentTimeMillis()
        val original = recentTargets[pos]?.let { now - it < 1500 } == true
        if (!original && (now - lastSwingMs > 1500 || p.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > 49)) return
        if (original) recentTargets.remove(pos)
        val ip = pos.immutable()
        for (l in breakListeners) l.onBreak(ip, old, original)
    }

    private fun sidebarLines(mc: Minecraft): List<String> {
        val level = mc.level ?: return emptyList()
        val sb = level.scoreboard
        val obj = sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR) ?: return emptyList()
        return sb.listPlayerScores(obj).sortedByDescending { it.value() }.map { e ->
            val team = sb.getPlayersTeam(e.owner())
            val raw = if (team != null) team.playerPrefix.string + team.playerSuffix.string else e.owner()
            raw.replace(Constants.STRIP_COLOR_REGEX, "").trim()
        }
    }

    // Tab lines after a widget header (e.g. "Commissions:"), until the next blank/header line
    fun tabWidget(header: String): List<String> {
        val out = ArrayList<String>()
        var inside = false
        for (e in TabListCache.entries) {
            val s = e.stripped
            if (!inside) { if (s.trim().startsWith(header)) inside = true; continue }
            if (s.isBlank() || (!s.startsWith(" ") && s.trim().endsWith(":"))) break
            out += s.trim()
        }
        return out
    }

    fun tabLine(prefix: String): String? = TabListCache.entries.firstOrNull { it.stripped.trim().startsWith(prefix) }?.stripped?.trim()

    fun strip(s: String) = s.replace(Constants.STRIP_COLOR_REGEX, "")

    fun title(text: String, sub: String, ms: Int, sound: Boolean = false) {
        Misc.forceTitle(Component.literal(text), Component.literal(sub), ms)
        if (sound) Misc.sendSound2D(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f)
    }

    fun alpha(rgb: Int, pct: Int) = ((pct.coerceIn(0, 100) * 255 / 100) shl 24) or (rgb and 0xFFFFFF)

    fun lineStart(): Vec3 {
        val cam = Minecraft.getInstance().gameRenderer.mainCamera
        return cam.position().add(Vec3.directionFromRotation(cam.xRot(), cam.yRot()))
    }

    // 12 camera-facing edges of a box
    fun outline(ps: PoseStack, vc: VertexConsumer, b: AABB, argb: Int, px: Float) {
        val xs = doubleArrayOf(b.minX, b.maxX); val ys = doubleArrayOf(b.minY, b.maxY); val zs = doubleArrayOf(b.minZ, b.maxZ)
        for (y in ys) for (z in zs) RenderUtils.screenLine(ps, vc, Vec3(b.minX, y, z), Vec3(b.maxX, y, z), argb, px)
        for (x in xs) for (z in zs) RenderUtils.screenLine(ps, vc, Vec3(x, b.minY, z), Vec3(x, b.maxY, z), argb, px)
        for (x in xs) for (y in ys) RenderUtils.screenLine(ps, vc, Vec3(x, y, b.minZ), Vec3(x, y, b.maxZ), argb, px)
    }

    fun short(v: Double): String = fishmod.features.diana.DianaTracker.short(v)
}

// Plain line HUDs registered with the HUD editor
object MiningHuds {

    fun reg(
        name: String, id: String, w: Int, h: Int,
        gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit,
        gs: () -> Double, ss: (Double) -> Unit, on: () -> Boolean, lines: () -> List<String>,
    ) {
        FishHudEditor.register(name, gx, sx, gy, sy, w, h, gs, ss, on)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", id)) { ctx, _ ->
            if (!FishHudEditor.isOpen() && on()) draw(ctx, lines(), gx(), gy(), gs())
        }
    }

    fun draw(ctx: GuiGraphicsExtractor, lines: List<String>, x: Int, y: Int, scale: Double) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui || lines.isEmpty()) return
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale.toFloat(), scale.toFloat())
        // "value	item" lines: value right-aligned in its own column, item text after it (Diana style)
        val font = mc.font
        val colX = lines.maxOfOrNull { if ('	' in it) font.width(it.substringBefore('	')) + 6 else 0 } ?: 0
        for ((i, l) in lines.withIndex()) {
            if ('	' !in l) { ctx.text(font, l, 0, i * 10, -1, true); continue }
            val left = l.substringBefore('	')
            ctx.text(font, left, colX - 6 - font.width(left), i * 10, -1, true)
            ctx.text(font, l.substringAfter('	'), colX, i * 10, -1, true)
        }
        pose.popMatrix()
    }
}
