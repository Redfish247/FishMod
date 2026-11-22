package fishmod.features.extras

import fishmod.utils.FishMsg
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.core.registries.BuiltInRegistries
import fishmod.features.extras.ExtrasSettings as S

// Trevor the Trapper: line to the hint area, then to where the animal was last seen
object TrevorHelper {
    // from SkyHanni (TrevorFeatures)
    private val QUEST = Regex("""^\[NPC] Trevor: You can find your (\w+) animal near the (.+)\.$""")
    private val HEIGHT = Regex("""^The target is around (\d+) blocks (above|below), at a (\d+) degrees angle!$""")
    private const val DIED = "Return to the Trapper soon to get a new animal to hunt!"
    private const val TIMEOUT = "You ran out of time and the animal disappeared!"
    // from SkyHanni (TrevorSolver animalHealths), doubled values cover Derpy
    private val HEALTHS = setOf(100, 200, 500, 1000, 2000, 5000, 10000, 30000).flatMap { listOf(it, it * 2) }.toSet()
    // from SkyHanni (TrapperMobArea)
    private val AREAS = mapOf(
        "Oasis" to Vec3(126.0, 77.0, -456.0),
        "Mushroom Gorge" to Vec3(300.0, 80.0, -509.0),
        "Overgrown Mushroom Cave" to Vec3(242.0, 60.0, -389.0),
        "Desert Settlement" to Vec3(184.0, 86.0, -384.0),
        "Glowing Mushroom Cave" to Vec3(199.0, 50.0, -512.0),
        "Desert Mountain" to Vec3(255.0, 148.0, -518.0),
    )

    private val ANIMALS = setOf("cow", "horse", "sheep", "pig", "rabbit", "chicken")
    private var questUntil = 0L
    @Volatile private var areaName: String? = null
    @Volatile private var area: Vec3? = null
    @Volatile private var lastSeen: Vec3? = null
    private var tick = 0

    fun init() {
        FishEvents.chat.register { text ->
            if (!S.trevorHelper) return@register false
            val s = text.chatPlain.trim()
            QUEST.find(s)?.let {
                questUntil = System.currentTimeMillis() + 10 * 60_000L
                areaName = it.groupValues[2]; area = AREAS[it.groupValues[2]]; lastSeen = null
                FishUtil.forceTitle(Component.literal("§b" + it.groupValues[1]), Component.literal("§e" + it.groupValues[2]), 2500)
            }
            HEIGHT.find(s)?.let {
                val py = Minecraft.getInstance().player?.blockY ?: return@let
                val h = it.groupValues[1].toInt()
                val y = if (it.groupValues[2] == "above") py + h else py - h
                FishUtil.addChatMessage(Component.literal(FishMsg.prefix() + "§bTrevor target near §eY " + y))
            }
            if (s == DIED || s == TIMEOUT) reset()
            false
        }
        FishEvents.worldChange.register { reset(); false }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }
        RenderHooks.GIZMO.register { _ ->
            try { render() } catch (t: Throwable) { FishDiag.fail("TrevorHelper.2", "trevor render failed", t) }
        }
    }

    // No live outline: Eli wants only the last-seen line (kept for FishEntityRenderMixin)
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun glowColor(e: Entity): Int = EntityRenderState.NO_OUTLINE

    private fun reset() { questUntil = 0; area = null; areaName = null; lastSeen = null }

    private fun render() {
        if (!S.trevorHelper || System.currentTimeMillis() > questUntil) return
        val seen = lastSeen
        val dest = seen ?: area ?: return
        val c = S.trevorGlowColor or 0xFF000000.toInt()
        val box = if (seen != null) AABB.ofSize(dest.add(0.0, 0.5, 0.0), 1.0, 1.0, 1.0) else AABB.ofSize(dest.add(0.0, 1.0, 0.0), 2.0, 2.0, 2.0)
        FishDraw.gizmoBox(box, (0x40 shl 24) or (c and 0xFFFFFF), c, true)
        FishDraw.gizmoLine(FishDraw.cameraLineStart(0.2), box.center, c, 2f, true)
        val label = if (seen != null) "§aLast seen" else "§e" + (areaName ?: "")
        FishDraw.gizmoText(Component.literal(label), box.center.add(0.0, 1.5, 0.0), 1f, -1, true)
    }

    private fun onTick(mc: Minecraft) {
        if (++tick % 10 != 0) return
        val level = mc.level
        val player = mc.player
        if (!S.trevorHelper || level == null || player == null || System.currentTimeMillis() > questUntil) return
        try {
            for (e in level.entitiesForRendering()) {
                if (e !is LivingEntity || !e.isAlive) continue
                if (BuiltInRegistries.ENTITY_TYPE.getKey(e.type).path !in ANIMALS) continue
                if (e.maxHealth.toInt() !in HEALTHS || !player.hasLineOfSight(e)) continue
                if (lastSeen == null) FishUtil.forceTitle(Component.literal("§2Animal spotted!"), Component.literal(""), 1500)
                lastSeen = e.position()
                break
            }
        } catch (t: Throwable) {
            FishDiag.fail("TrevorHelper.1", "trevor scan failed", t)
        }
    }
}
