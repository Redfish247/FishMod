package fishmod.features.dungeon.f7.dragons

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.FishHudEditor
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * M7 Wither Dragons QoL. Spawn timers, skip boxes, health, kill priority, aim-assist and
 * post-kill stats.
 */
object WitherDragons {

    @Volatile var priorityDragon: WitherDragon = WitherDragon.NONE
    private const val SCOREBOARD_GRACE = 40
    private var tick = 0L

    private fun on() = FishSettings.witherDragonsEnabled && Phase.inP5()

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            "Wither Dragon Timer",
            { hudX() }, { v -> FishSettings.witherDragonsHudX = v },
            { FishSettings.witherDragonsHudY }, { v -> FishSettings.witherDragonsHudY = v },
            46, 12,
            { FishSettings.witherDragonsHudScale }, { v -> FishSettings.witherDragonsHudScale = v },
            { FishSettings.witherDragonsEnabled && FishSettings.witherDragonsTimerHud },
        )

        Events.ON_WORLD_CHANGE.register {
            priorityDragon = WitherDragon.NONE
            WitherDragon.resetAll()
            tick = 0
            false
        }

        Events.ON_PACKET.register { packet ->
            if (on()) when (packet) {
                is ClientboundLevelParticlesPacket -> DragonCheck.handleSpawnPacket(packet, tick)
                is ClientboundAddEntityPacket -> DragonCheck.dragonSpawn(packet, tick)
                is ClientboundSetEntityDataPacket -> DragonCheck.dragonUpdate(packet, tick)
                is ClientboundSetEquipmentPacket -> DragonCheck.dragonSprayed(packet, tick)
                is ClientboundSoundPacket -> DragonCheck.trackArrows(packet, tick)
                is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::onBlock)
            }
            false
        }

        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (on() && entity is EnderDragon) {
                WitherDragon.byEntityId(entity.id)?.let { if (it.state == WitherDragonState.ALIVE) { it.entity = null; it.offScoreboardTicks = 0 } }
            }
        }

        Events.ON_SERVER_TICK.register {
            tick++
            if (on()) serverTick()
            false
        }

        RenderingEvents.NO_DEPTH_LINE.register { ctx, m, vc ->
            if (!on()) return@register
            renderLines(ctx, m, vc)
            renderText(ctx, m)   // text via the same END_MAIN pass — AFTER_TRANSLUCENT drains the collector too early
        }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (on()) renderFills(m, vc) }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!on() || !state.`is`(Blocks.AIR)) return
        WitherDragon.real.firstOrNull { it.bottomChin.x == pos.x && it.bottomChin.z == pos.z && it.state != WitherDragonState.DEAD }
            ?.setDead(false, tick)
    }

    private fun serverTick() {
        for (d in WitherDragon.real) {
            if (d.state == WitherDragonState.SPAWNING) {
                d.timeToSpawn--
                if (d.timeToSpawn <= -20) d.setDead(true, tick)
            }
            if (d.state == WitherDragonState.ALIVE && d.entity == null) {
                if (DragonCheck.isAliveOnScoreboard(d)) d.offScoreboardTicks = 0
                else if (d.offScoreboardTicks < SCOREBOARD_GRACE) d.offScoreboardTicks++
                else d.setDead(false, tick)
            }
        }
    }

    /** M7 dragon spawn alert — title (priority colour) + optional sound + party call. */
    fun onDragonsSpawning(spawning: List<WitherDragon>) {
        if (!on()) return
        val prio = priorityDragon
        if (FishSettings.witherDragonsSpawnAlert && prio != WitherDragon.NONE) {
            val subtitle = spawning.joinToString(" ") { "§${it.colorCode}${it.name}" }
            fishmod.utils.Misc.forceTitle(
                Component.literal("§${prio.colorCode}§l${prio.name}"),
                Component.literal(subtitle),
            )
        }
        if (FishSettings.witherDragonsSpawnSound)
            fishmod.utils.Misc.sendSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f)
        if (FishSettings.witherDragonsSpawnParty && prio != WitherDragon.NONE)
            fishmod.utils.ChatQueue.enqueue("pc ${prio.name} dragon priority")
    }

    /** Post-kill stats message. */
    fun onDragonDead(d: WitherDragon, atTick: Long) {
        if (!FishSettings.witherDragonsEnabled || !FishSettings.witherDragonsSendStats) return
        val stats = buildList {
            add("&7Time: &6${"%.1f".format((atTick - d.spawnedTick) / 20.0)}s")
            if (d == priorityDragon) add("&fArrows: &6${d.arrowsHit}")
            d.sprayedTick?.let { add("&bSprayed: &c${it}t") }
        }
        if (stats.isNotEmpty()) modMessage("&${d.colorCode}${d.name}: &f${stats.joinToString(" &7| ")}")
    }

    private fun renderLines(ctx: LevelRenderContext, m: PoseStack, vc: VertexConsumer) {
        for (d in WitherDragon.real) {
            if (FishSettings.witherDragonsSkipBox && d.state != WitherDragonState.DEAD) {
                RenderUtils.renderOutline(m, vc, d.box, rgba(d.rgb, 1f))
            }
            if (FishSettings.witherDragonsAimAssist && d.state == WitherDragonState.SPAWNING) {
                aimPoint(d)?.let { RenderUtils.renderOutline(m, vc, AABB(it.x - .15, it.y - .15, it.z - .15, it.x + .15, it.y + .15, it.z + .15), RenderUtils.toFloats(FishSettings.witherDragonsAimColor)) }
            }
        }
        if (FishSettings.witherDragonsTracer && priorityDragon != WitherDragon.NONE && priorityDragon.state == WitherDragonState.SPAWNING) {
            RenderUtils.renderLineTo(ctx, m, vc, priorityDragon.spawnPos.add(0.5, 3.5, 0.5), FishSettings.witherDragonsAimColor)
        }
    }

    private fun renderFills(m: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.witherDragonsSkipBox || !FishSettings.witherDragonsBoxFill) return
        for (d in WitherDragon.real) {
            if (d.state != WitherDragonState.DEAD) RenderUtils.renderFilled(m, vc, d.box, rgba(d.rgb, 0.18f))
        }
    }

    private fun renderText(ctx: LevelRenderContext, m: PoseStack) {
        for (d in WitherDragon.real) {
            if (FishSettings.witherDragonsHealth && d.state == WitherDragonState.ALIVE) {
                d.entity?.let { e ->
                    RenderUtils.renderText(ctx, m, Component.literal(formatHealth(d.health)),
                        Vec3(e.x, e.y - 1.0, e.z), 0.05f)
                }
            }
            if (FishSettings.witherDragonsTimerWorld && d.state == WitherDragonState.SPAWNING && d.timeToSpawn > 0) {
                RenderUtils.renderText(ctx, m,
                    Component.literal("§${d.colorCode}${d.name}: ${timerText(d.timeToSpawn)}"),
                    d.box.center, 0.05f)
            }
        }
    }

    /** Editor-box X: resolves the "&lt;0 = auto-centre" sentinel to a real pixel so the drag box lands on it. */
    private fun hudX(): Int {
        if (FishSettings.witherDragonsHudX >= 0) return FishSettings.witherDragonsHudX
        val mc = Minecraft.getInstance()
        return ((mc.window.guiScaledWidth - 46 * FishSettings.witherDragonsHudScale) / 2.0).toInt().coerceAtLeast(0)
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tickCounter: net.minecraft.client.DeltaTracker) {
        if (!on() || !FishSettings.witherDragonsTimerHud) return
        val d = priorityDragon
        if (d == WitherDragon.NONE || d.state != WitherDragonState.SPAWNING || d.timeToSpawn <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val txt = "§${d.colorCode}${timerText(d.timeToSpawn)}"
        val sc = FishSettings.witherDragonsHudScale.toFloat()
        val w = mc.font.width(txt) * sc
        val x = if (FishSettings.witherDragonsHudX < 0) (mc.window.guiScaledWidth - w) / 2f else FishSettings.witherDragonsHudX.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(x, FishSettings.witherDragonsHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, txt, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }

    private fun rgba(rgb: Int, a: Float) = floatArrayOf(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f, a)

    private fun timerText(t: Int): String = when (FishSettings.witherDragonsTimerStyle) {
        0 -> "${t * 50}ms"
        1 -> "${"%.1f".format(t / 20f)}s"
        else -> "${t}t"
    }

    private fun formatHealth(h: Float): String {
        val c = when {
            h >= 750_000_000 -> "§a"; h >= 500_000_000 -> "§e"; h >= 250_000_000 -> "§6"; else -> "§c"
        }
        val s = when {
            h >= 1_000_000_000 -> { val b = h / 1_000_000_000; if (b > 1) "%.1fb".format(b) else "${b.toInt()}b" }
            h >= 1_000_000 -> "${(h / 1_000_000).toInt()}m"
            h >= 1_000 -> "${(h / 1_000).toInt()}k"
            else -> "${h.toInt()}"
        }
        return c + s
    }

    /** Arrow-lead ballistic solve for the ice-spray aim point. */
    private fun aimPoint(d: WitherDragon): Vec3? {
        val p = Minecraft.getInstance().player ?: return null
        val target = d.spawnPos.add(0.5, 3.5, 0.5)
        val distSq = target.distanceToSqr(p.eyePosition)
        var dist = 0.0; var speed = 3.0; var yVel = 0.0; var drop = 0.0
        repeat(160) {
            dist += speed; speed *= 0.99
            drop += yVel; yVel -= 0.05; yVel *= 0.99
            if (dist * dist >= distSq) return target.subtract(0.0, drop, 0.0)
        }
        return null
    }
}
