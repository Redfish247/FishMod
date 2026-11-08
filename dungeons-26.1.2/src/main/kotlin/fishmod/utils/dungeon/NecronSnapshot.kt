package fishmod.utils.dungeon

import fishmod.compat.hud
import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.mixin.accessors.LerpingBossEventAccessor
import fishmod.utils.FishConstants
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand

// Shared Necron bar + nametag read, refreshed at most every 2 ticks for NecronDeath and BossPhase.
object NecronSnapshot {
    private var lastTick = Long.MIN_VALUE
    private var lastTag = false
    @JvmField var barPct: Float? = null
    @JvmField var tag: String? = null

    private fun refresh(mc: Minecraft, withTag: Boolean) {
        val now = mc.hud.guiTicks.toLong()
        if (now - lastTick < 2 && (!withTag || lastTag)) return
        lastTick = now
        lastTag = withTag
        val bars = (mc.hud.bossOverlay as BossBarHudAccessor).bossBars
        barPct = null
        if (bars != null) for (b in bars.values) {
            if (!b.name.string.contains("Necron")) continue
            if (FishConstants.STRIP_COLOR_REGEX.replace(b.name.string, "").trim() != "Necron") continue
            barPct = (b as? LerpingBossEventAccessor)?.targetPercent
        }
        tag = null
        if (withTag) {
            val level = mc.level ?: return
            for (e in level.entitiesForRendering()) {
                if (e !is ArmorStand || !e.hasCustomName()) continue
                val n = e.customName?.string ?: continue
                if (!n.contains("Necron")) continue
                tag = FishConstants.STRIP_COLOR_REGEX.replace(n, "")
                break
            }
        }
    }

    fun bar(mc: Minecraft): Float? { refresh(mc, false); return barPct }
    fun nametag(mc: Minecraft): String? { refresh(mc, true); return tag }
}
