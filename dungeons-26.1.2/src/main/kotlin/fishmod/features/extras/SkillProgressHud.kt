package fishmod.features.extras

import fishmod.utils.events.chatPlain
import fishmod.features.FishHudEditor
import fishmod.features.diana.DianaTracker
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import fishmod.features.extras.ExtrasSettings as S

// Last skill XP action bar popup: progress, XP/h and ETA to next level
object SkillProgressHud {
    // Same popup shape ActionBarCleaner hides: "+12.5 Combat (1,234/5,000)"
    private val POPUP = Regex("\\+([\\d,.]+) ([A-Za-z ]+?) \\(([\\d,.]+[kMB]?)/([\\d,.]+[kMB]?)\\)")
    private const val WINDOW_MS = 5 * 60_000L

    private var skill = ""
    private var cur = 0.0
    private var max = 0.0
    private var lastSeen = 0L
    private val gains = ArrayDeque<Pair<Long, Double>>()

    fun init() {
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (!overlay || !S.skillHud) return@register
            try { parse(msg.chatPlain) } catch (t: Throwable) { FishDiag.fail("SkillProgressHud.1", "skill popup parse failed", t) }
        }
        FishHudEditor.register("Skill Progress", { S.skillHudX }, { S.skillHudX = it }, { S.skillHudY }, { S.skillHudY = it }, 140, 30,
            { S.skillHudScale }, { S.skillHudScale = it })
        fishmod.features.HudPreview.register("Skill Progress") {
            fishmod.features.HudPreview.text(listOf("§eCombat §71.2k/5k §f(24.0%)", "§7XP/h: §f180k", "§7ETA: §f21m"), 10, color = S.skillHudColor)
        }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "skill_progress_hud")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("SkillProgressHud.2", "skill hud render failed", t) }
        }
    }

    private fun num(s: String): Double {
        val mult = when (s.lastOrNull()) { 'k' -> 1e3; 'M' -> 1e6; 'B' -> 1e9; else -> 1.0 }
        return (s.trimEnd('k', 'M', 'B').replace(",", "").toDoubleOrNull() ?: 0.0) * mult
    }

    private fun parse(text: String) {
        val m = POPUP.find(text) ?: return
        val name = m.groupValues[2].trim()
        val now = System.currentTimeMillis()
        if (name != skill) { skill = name; gains.clear() }
        gains.addLast(now to num(m.groupValues[1]))
        cur = num(m.groupValues[3]); max = num(m.groupValues[4]); lastSeen = now
    }

    private fun lines(): List<String> {
        val now = System.currentTimeMillis()
        while (gains.isNotEmpty() && now - gains.first().first > WINDOW_MS) gains.removeFirst()
        val pct = if (max > 0) cur / max * 100 else 0.0
        val out = mutableListOf("§e$skill §7${DianaTracker.short(cur)}/${DianaTracker.short(max)} §f(${"%.1f".format(pct)}%)")
        if (gains.size >= 2) {
            val span = (now - gains.first().first).coerceAtLeast(1000L)
            val perH = gains.sumOf { it.second } * 3_600_000.0 / span
            out += "§7XP/h: §f${DianaTracker.short(perH)}"
            if (perH > 0 && max > cur) {
                val mins = ((max - cur) / perH * 60).toLong()
                out += "§7ETA: §f" + if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            }
        }
        return out
    }

    private fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (!S.skillHud || !SkyblockLocation.inSkyblock() || skill.isEmpty()) return
        if (System.currentTimeMillis() - lastSeen > S.skillHudHideSeconds * 1000L) return
        val mc = Minecraft.getInstance()
        val sc = S.skillHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.skillHudX.toFloat(), S.skillHudY.toFloat())
            ctx.pose().scale(sc, sc)
            lines().forEachIndexed { i, s -> ctx.text(mc.font, Component.literal(s), 0, i * 10, S.skillHudColor, true) }
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
