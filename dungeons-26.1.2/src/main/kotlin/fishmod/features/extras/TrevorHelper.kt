package fishmod.features.extras

import fishmod.utils.FishMsg
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.core.registries.BuiltInRegistries
import fishmod.features.extras.ExtrasSettings as S

// Trevor the Trapper: quest title, Talbot height hint, outlines the target animal
object TrevorHelper {
    // from SkyHanni (TrevorFeatures)
    private val QUEST = Regex("""^\[NPC] Trevor: You can find your (\w+) animal near the (.+)\.$""")
    private val HEIGHT = Regex("""^The target is around (\d+) blocks (above|below), at a (\d+) degrees angle!$""")
    private const val DIED = "Return to the Trapper soon to get a new animal to hunt!"
    private const val TIMEOUT = "You ran out of time and the animal disappeared!"
    // from SkyHanni (TrevorSolver animalHealths), doubled values cover Derpy
    private val HEALTHS = setOf(100, 200, 500, 1000, 2000, 5000, 10000, 30000).flatMap { listOf(it, it * 2) }.toSet()

    private val ANIMALS = setOf("cow", "horse", "sheep", "pig", "rabbit", "chicken")
    private var questUntil = 0L
    @Volatile private var target = -1
    private var tick = 0

    fun init() {
        FishEvents.chat.register { text ->
            if (!S.trevorHelper) return@register false
            val s = text.chatPlain.trim()
            QUEST.find(s)?.let {
                questUntil = System.currentTimeMillis() + 10 * 60_000L
                FishUtil.forceTitle(Component.literal("§b" + it.groupValues[1]), Component.literal("§e" + it.groupValues[2]), 2500)
            }
            HEIGHT.find(s)?.let {
                val py = Minecraft.getInstance().player?.blockY ?: return@let
                val h = it.groupValues[1].toInt()
                val y = if (it.groupValues[2] == "above") py + h else py - h
                FishUtil.addChatMessage(Component.literal(FishMsg.prefix() + "§bTrevor target near §eY " + y))
            }
            if (s == DIED || s == TIMEOUT) { questUntil = 0; target = -1 }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }
    }

    @JvmStatic
    fun glowColor(e: Entity): Int =
        if (target != -1 && e.id == target && S.trevorHelper) S.trevorGlowColor or 0xFF000000.toInt() else EntityRenderState.NO_OUTLINE

    private fun onTick(mc: Minecraft) {
        if (++tick % 10 != 0) return
        val level = mc.level
        if (!S.trevorHelper || level == null || System.currentTimeMillis() > questUntil) { target = -1; return }
        try {
            var found = -1
            for (e in level.entitiesForRendering()) {
                if (e !is LivingEntity || !e.isAlive) continue
                if (BuiltInRegistries.ENTITY_TYPE.getKey(e.type).path !in ANIMALS) continue
                if (e.maxHealth.toInt() in HEALTHS) { found = e.id; break }
            }
            if (found != -1 && found != target) FishUtil.forceTitle(Component.literal("§2Animal found!"), Component.literal(""), 1500)
            target = found
        } catch (t: Throwable) {
            FishDiag.fail("TrevorHelper.1", "trevor scan failed", t)
        }
    }
}
