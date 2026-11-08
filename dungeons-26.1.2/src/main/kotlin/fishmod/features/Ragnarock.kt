package fishmod.features
import fishmod.compat.hudHidden

import fishmod.utils.SkyblockLocation
import fishmod.utils.FishUtil
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object Ragnarock {

    private const val CAST_PITCH = 1.4920635f
    private const val BUFF_TICKS = 200
    private val CANCELLED: Pattern =
        Pattern.compile("Ragnarock was cancelled due to (?:being hit|taking damage)!")
    private val P5_TAUNT: Pattern =
        Pattern.compile(Pattern.quote("Wither King: I no longer wish to fight"))
    private val WEAPON_STRENGTH: Pattern =
        Pattern.compile("Strength: (?:§.)*\\+([\\d,]+(?:\\.\\d+)?)")

    private const val NAME = "Rag Timer"

    private var ticksLeft = 0
    private var lastTauntMs = 0L

    @JvmStatic
    fun checkP5Taunt(raw: String?) {
        if (!FishSettings.ragnarockEnabled || !FishSettings.p5RagEnabled || raw == null) return
        if (!P5_TAUNT.matcher(raw).find()) return
        val now = System.currentTimeMillis()
        if (now - lastTauntMs < 2000) return
        lastTauntMs = now
        FishUtil.forceTitle(Component.literal("§5Rag"), Component.empty())
    }

    private fun holdingAxe(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return FishDiag.guard("Ragnarock.2", "rag axe id lookup failed") { ItemTools.getId(p.mainHandItem) == "RAGNAROCK_AXE" } ?: false
    }

    // Ability grants 1.5x the held weapon's lore Strength
    private fun strengthGained(): Int? {
        val p = Minecraft.getInstance().player ?: return null
        val lore = p.mainHandItem.get(DataComponents.LORE) ?: return null
        for (line in lore.lines()) {
            val m = WEAPON_STRENGTH.matcher(line.string)
            if (m.find()) return (m.group(1).replace(",", "").toDouble() * 1.5).toInt()
        }
        FishDiag.fail("Ragnarock.4", "no Strength line in rag lore")
        return null
    }

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.ragnarockTimerHudX }, { v -> FishSettings.ragnarockTimerHudX = v },
            { FishSettings.ragnarockTimerHudY }, { v -> FishSettings.ragnarockTimerHudY = v },
            54, 12,
            { FishSettings.ragnarockTimerScale }, { v -> FishSettings.ragnarockTimerScale = v },
            { FishSettings.ragnarockEnabled && FishSettings.ragnarockTimer },
        )

        FishEvents.sound.register { event, _, pitch ->
            if (FishSettings.ragnarockEnabled
                && pitch == CAST_PITCH && "wolf" in event.location.path && SkyblockLocation.inSkyblock() && holdingAxe()
            ) {
                val strength = if (FishSettings.ragnarockStrengthTitle || FishSettings.ragnarockStrengthParty) strengthGained() else null
                val showStrength = if (FishSettings.ragnarockStrengthTitle && strength != null) "§c+$strength Strength" else null
                if (FishSettings.ragnarockCastAlert) {
                    FishUtil.forceTitle(Component.literal("§aCasted Ragnarock"), Component.literal(showStrength ?: ""))
                } else if (showStrength != null) {
                    FishUtil.forceTitle(Component.literal(showStrength), Component.empty())
                }
                val announce = FishSettings.ragnarockCastAlert && FishSettings.ragnarockAnnounceParty
                val partyStrength = if (FishSettings.ragnarockStrengthParty) strength else null
                when {
                    announce && partyStrength != null -> fishmod.utils.ChatQueue.enqueue("pc Casted Ragnarock (+$partyStrength Strength)")
                    announce -> fishmod.utils.ChatQueue.enqueue("pc Casted Ragnarock")
                    partyStrength != null -> fishmod.utils.ChatQueue.enqueue("pc Ragnarock: +$partyStrength Strength")
                }
                if (FishSettings.ragnarockTimer) ticksLeft = BUFF_TICKS
            }
            if (ticksLeft < 0 || ticksLeft > BUFF_TICKS) FishDiag.fail("Ragnarock.3", "rag ticksLeft out of range: $ticksLeft")
            false
        }

        FishEvents.chat.register { text ->
            if (FishSettings.ragnarockEnabled && CANCELLED.matcher(text.string).find()) {
                if (FishSettings.ragnarockCancelAlert) FishUtil.forceTitle(Component.literal("§cRagnarock Cancelled"), Component.empty())
                ticksLeft = 0
            }
            false
        }

        FishEvents.serverTick.register { if (ticksLeft > 0) ticksLeft--; false }
        FishEvents.worldChange.register { ticksLeft = 0; false }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.ragnarockEnabled || !FishSettings.ragnarockTimer || ticksLeft <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        val secs = ticksLeft / 20.0
        val color = if (secs > 5) "§a" else if (secs > 2) "§e" else "§c"
        val txt = "§5Rag: $color${fishmod.utils.Fmt.f1(secs)}s"
        val sc = FishSettings.ragnarockTimerScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(FishSettings.ragnarockTimerHudX.toFloat(), FishSettings.ragnarockTimerHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, txt, 0, 0, -1, true)
        } catch (e: Exception) {
            FishDiag.fail("Ragnarock.1", "rag timer render failed (ticksLeft=$ticksLeft)", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
