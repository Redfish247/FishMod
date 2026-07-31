package fishmod.features.dungeon.map

import fishmod.utils.Misc
import fishmod.utils.config.values.DungeonMapSettings
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/** Score-milestone chat/title messages. Rendered via renderHud, registered externally like sibling HUDs. */
object ScoreMessages {

    private const val FADE_MS = 200L
    private const val DURATION_MS = 1500L

    private var sent270 = false
    private var sent300 = false
    private var currentText: String? = null
    private var displayUntilMs = 0L
    private var fadeStartMs = 0L

    @JvmStatic
    val SOUND_OPTIONS = arrayOf(
        "minecraft:entity.player.levelup",
        "minecraft:block.note_block.pling",
        "minecraft:block.note_block.bell",
        "minecraft:entity.experience_orb.pickup",
        "minecraft:ui.toast.challenge_complete"
    )

    @JvmStatic
    fun reset() {
        sent270 = false
        sent300 = false
        currentText = null
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!DungeonMapSettings.mapScoreMessages) return
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        val text = currentText ?: return
        val now = System.currentTimeMillis()
        if (now >= displayUntilMs) {
            currentText = null
            return
        }
        val alpha = if (now >= fadeStartMs) Math.max(0.0f, 1.0f - (now - fadeStartMs).toFloat() / FADE_MS) else 1.0f
        renderAt(ctx, mc, resolvedX(mc), resolvedY(mc), DungeonMapSettings.mapScoreTitleScale, text, alpha)
    }

    @JvmStatic
    fun update(mc: Minecraft, score: Int) {
        if (!DungeonMapSettings.mapScoreMessages) return
        val time = DungeonScore.elapsedTime
        if (score >= 270 && !sent270) {
            sent270 = true
            if (DungeonMapSettings.mapScore270MessageEnabled) sendPartyChat(mc, chatText(DungeonMapSettings.mapScore270Message, time))
            if (DungeonMapSettings.mapScore270Title) showTitle(titleText(DungeonMapSettings.mapScore270TitleText, time))
            if (DungeonMapSettings.mapScore270ClientEnabled) sendClientMessage(DungeonMapSettings.mapScore270ClientMessage, time, false)
        }

        if (score >= 300 && !sent300) {
            sent300 = true
            if (DungeonMapSettings.mapScore300MessageEnabled) sendPartyChat(mc, chatText(DungeonMapSettings.mapScore300Message, time))
            if (DungeonMapSettings.mapScore300Title) showTitle(titleText(DungeonMapSettings.mapScore300TitleText, time))
            if (DungeonMapSettings.mapScore300ClientEnabled) sendClientMessage(DungeonMapSettings.mapScore300ClientMessage, time, true)
        }
    }

    private fun floorKey(): String? {
        val f = DungeonState.floorNumber()
        if (f < 0) return null
        return (if (DungeonState.isMasterMode()) "M" else "F") + f
    }

    private fun sendClientMessage(raw: String?, time: String, updatePb: Boolean) {
        val key = floorKey()
        // PB tracking not yet ported to this module — best (if any) comes back null until a store exists.
        val best: String? = null
        var msg = (raw ?: "").replace("<time>", time).replace('&', '§')
        val hover = if (best != null) "§bPersonal Best: §a$best" else "§7No PB yet"
        Misc.addChatMessage(net.minecraft.network.chat.Component.literal(msg)
            .withStyle { it.withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal(hover))) })
    }

    private fun chatText(raw: String?, time: String): String {
        val s = (raw ?: "").replace("<time>", time)
        return s.replace(Regex("(?i)[&§][0-9a-fk-or]"), "")
    }

    private fun titleText(raw: String?, time: String): String {
        return (raw ?: "").replace("<time>", time).replace('&', '§')
    }

    private fun sendPartyChat(mc: Minecraft, msg: String?) {
        if (mc.player != null && mc.connection != null && !msg.isNullOrBlank()) {
            mc.connection!!.sendCommand("pc $msg")
        }
    }

    private fun showTitle(text: String?) {
        if (text.isNullOrBlank()) return
        currentText = text
        if (DungeonMapSettings.mapScoreTitleSound) playConfiguredSound()
        val now = System.currentTimeMillis()
        displayUntilMs = now + DURATION_MS
        fadeStartMs = displayUntilMs - Math.min(FADE_MS, DURATION_MS)
    }

    @JvmStatic
    fun renderForEdit(g: GuiGraphicsExtractor, mc: Minecraft) {
        renderAt(g, mc, resolvedX(mc), resolvedY(mc), DungeonMapSettings.mapScoreTitleScale, previewText(), 1.0f)
    }

    private fun previewText(): String {
        var raw = DungeonMapSettings.mapScore300TitleText
        if (raw.isBlank()) raw = "300 Score"
        return raw.replace("<time>", "12m 34s").replace('&', '§')
    }

    private fun renderAt(g: GuiGraphicsExtractor, mc: Minecraft, x: Float, y: Float, scale: Float, text: String, alpha: Float) {
        val argb = ((alpha * 255.0f).toInt() shl 24) or 0xFFFFFF
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        g.pose().scale(scale, scale)
        val width = mc.font.width(text)
        g.text(mc.font, text, -width / 2, 0, argb, true)
        g.pose().popMatrix()
    }

    @JvmStatic
    fun previewWidth(mc: Minecraft): Int = mc.font.width(previewText())

    @JvmStatic
    fun resolvedX(mc: Minecraft): Float {
        val x = DungeonMapSettings.mapScoreTitleX
        return if (x < 0.0f) mc.window.guiScaledWidth / 2.0f else x
    }

    @JvmStatic
    fun resolvedY(mc: Minecraft): Float {
        val y = DungeonMapSettings.mapScoreTitleY
        return if (y < 0.0f) mc.window.guiScaledHeight / 3.0f - 12.0f else y
    }

    @JvmStatic
    fun playConfiguredSound() {
        val mc = Minecraft.getInstance()
        val sound = resolveSound(DungeonMapSettings.mapScoreTitleSoundId) ?: SoundEvents.PLAYER_LEVELUP
        val vol = Math.max(0.0f, Math.min(1.0f, DungeonMapSettings.mapScoreTitleVolume))
        val pitch = Math.max(0.5f, Math.min(2.0f, DungeonMapSettings.mapScoreTitlePitch))
        mc.player?.playSound(sound, vol, pitch)
    }

    private fun resolveSound(id: String?): SoundEvent? {
        return try {
            val loc = Identifier.tryParse(id ?: return null) ?: return null
            BuiltInRegistries.SOUND_EVENT.getOptional(loc).orElse(null)
        } catch (t: Throwable) {
            null
        }
    }
}
