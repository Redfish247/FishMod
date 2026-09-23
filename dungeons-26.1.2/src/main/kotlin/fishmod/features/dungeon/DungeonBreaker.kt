package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack

// Odin-style Dungeonbreaker charges HUD + a sound when a block hit with it breaks.
object DungeonBreaker {

    private const val ITEM_ID = "DUNGEONBREAKER"
    private val CHARGES = Regex("Charges:\\s*(\\d+)\\s*/\\s*(\\d+)")
    private const val BREAK_WINDOW_TICKS = 20

    private var charges = -1
    private var maxCharges = -1
    private var scanTick = 0
    private var loggedLore = false

    // Blocks hit with the breaker, waiting to see them turn to air.
    private val pending = HashMap<BlockPos, Int>()

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            "Dungeon Breaker",
            { FishSettings.dungeonBreakerHudX }, { v -> FishSettings.dungeonBreakerHudX = v },
            { FishSettings.dungeonBreakerHudY }, { v -> FishSettings.dungeonBreakerHudY = v },
            80, 10,
            { FishSettings.dungeonBreakerHudScale }, { v -> FishSettings.dungeonBreakerHudScale = v }
        )

        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, level, hand, pos, _ ->
            if (level.isClientSide && FishSettings.dungeonBreakerEnabled && FishSettings.dungeonBreakerSoundEnabled && activeHere() &&
                isBreaker(player.getItemInHand(hand)) && !level.getBlockState(pos).isAir
            ) {
                pending[pos.immutable()] = BREAK_WINDOW_TICKS
            }
            InteractionResult.PASS
        })

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val player = mc.player
            val level = mc.level
            if (player == null || level == null) {
                charges = -1; maxCharges = -1; pending.clear()
                return@register
            }
            if (pending.isNotEmpty()) {
                val it = pending.entries.iterator()
                while (it.hasNext()) {
                    val e = it.next()
                    if (level.getBlockState(e.key).isAir) {
                        playBreakSound()
                        it.remove()
                    } else if (e.value <= 1) {
                        it.remove()
                    } else {
                        e.setValue(e.value - 1)
                    }
                }
            }
            if (!FishSettings.dungeonBreakerEnabled || !FishSettings.dungeonBreakerHudEnabled) return@register
            if (++scanTick < 5) return@register
            scanTick = 0
            scanCharges()
        }
    }

    private fun activeHere(): Boolean =
        if (FishSettings.dungeonBreakerDungeonOnly) Location.inDungeon() else Location.inSkyblock()

    private fun isBreaker(stack: ItemStack): Boolean =
        !stack.isEmpty && ItemUtil.getId(stack) == ITEM_ID

    private fun playBreakSound() {
        fishmod.utils.debug.Debug.LOGGER.info("[DungeonBreaker] break sound")
        SoundManager.play2D(
            SoundManager.preset(FishSettings.dungeonBreakerSoundName),
            FishSettings.dungeonBreakerSoundVolume.coerceIn(0, 500) / 100f,
            FishSettings.dungeonBreakerSoundPitch.toFloat().coerceIn(0f, 2f),
            "dungeonBreaker", 30,
        )
    }

    private fun scanCharges() {
        val player = Minecraft.getInstance().player ?: return
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (!isBreaker(stack)) continue
            val lore = stack.get(DataComponents.LORE)?.lines() ?: continue
            if (!loggedLore) {
                loggedLore = true
                fishmod.utils.debug.Debug.LOGGER.info("[DungeonBreaker] lore: " + lore.joinToString(" | ") { it.string })
            }
            for (line in lore) {
                val m = CHARGES.find(line.string) ?: continue
                val c = m.groupValues[1].toInt()
                val mx = m.groupValues[2].toInt()
                if (c != charges || mx != maxCharges) fishmod.utils.debug.Debug.LOGGER.info("[DungeonBreaker] charges $c/$mx")
                charges = c
                maxCharges = mx
                return
            }
        }
        charges = -1; maxCharges = -1
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!FishSettings.dungeonBreakerEnabled || !FishSettings.dungeonBreakerHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return
        val preview = mc.screen is FishHudEditor
        if (!preview && (charges < 0 || !activeHere())) return

        val cur = if (preview) 17 else charges
        val max = if (preview) 20 else maxCharges
        val col = when {
            cur <= 0 -> "§c"
            cur * 4 <= max -> "§6"
            else -> "§e"
        }
        val label = "§cCharges: $col$cur§7/§e$max§c⸕"

        val sc = FishSettings.dungeonBreakerHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.dungeonBreakerHudX.toFloat(), FishSettings.dungeonBreakerHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, label, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
