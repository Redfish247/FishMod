package fishmod.features.mining

import fishmod.features.AlertTitles
import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import fishmod.features.mining.MiningSettings as S

// Mute high heat panting, honeyhive reminder + Queen Bee title (strings from SkyHanni)
object MiningForagingMisc {
    // from SkyHanni HoneyhiveReminder
    private const val HIVE_LOOTED = "You stick your hand into the honeyhive and feel around..."
    private const val QUEEN_BEE = "QUEEN BEE! The Honeyhive instantly refilled with loot!"
    private val HEAT = Regex("Heat: \\D*(\\d+)")
    private var hiveReadyAt = 0L
    private var tick = 0

    fun init() {
        FishEvents.sound.register { snd, volume, pitch ->
            // from SkyHanni HighHeatSound: wolf pant, pitch 0, volume 1, below y 65 at 90+ heat
            S.miningMuteHeat && snd.location.path == "entity.wolf.pant" && pitch == 0f && volume == 1f && highHeat()
        }
        FishEvents.chat.register { c ->
            if (S.foragingHoneyhive && SkyblockLocation.`in`(SkyblockLocation.TORRHUS_CANYON)) {
                val s = c.chatPlain.trim()
                if (s == HIVE_LOOTED) hiveReadyAt = System.currentTimeMillis() + S.foragingHoneyhiveMin * 60_000L
                else if (s == QUEEN_BEE && S.foragingQueenBee) {
                    AlertTitles.show(Component.literal("§6Honeyhive Instantly Refilled!"), Component.empty(), 1500)
                    SoundManager.play(SoundManager.preset("pling"), 1f)
                }
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (++tick % 20 != 0 || hiveReadyAt == 0L || !S.foragingHoneyhive) return@register
            if (System.currentTimeMillis() >= hiveReadyAt && Minecraft.getInstance().player != null) {
                hiveReadyAt = 0L
                FishUtil.addChatMessage(Component.literal("§eHoneyhives are ready to be collected."))
            }
        }
    }

    // from NoFrills GemstoneDesyncFix: islands where lone gemstone panes desync
    @JvmStatic
    fun fullPane(state: net.minecraft.world.level.block.state.BlockState): net.minecraft.world.level.block.state.BlockState {
        if (!S.miningGemDesyncFix || state.block !is net.minecraft.world.level.block.StainedGlassPaneBlock) return state
        if (!(SkyblockLocation.`in`(SkyblockLocation.DWARVEN_MINES) || SkyblockLocation.`in`(SkyblockLocation.CRYSTAL_HOLLOWS) ||
                SkyblockLocation.`in`(SkyblockLocation.MINESHAFT) || SkyblockLocation.`in`(SkyblockLocation.CRIMSON_ISLE) || SkyblockLocation.`in`(SkyblockLocation.THE_RIFT))) return state
        val n = net.minecraft.world.level.block.CrossCollisionBlock.NORTH; val e = net.minecraft.world.level.block.CrossCollisionBlock.EAST
        val s = net.minecraft.world.level.block.CrossCollisionBlock.SOUTH; val w = net.minecraft.world.level.block.CrossCollisionBlock.WEST
        if (state.getValue(n) || state.getValue(e) || state.getValue(s) || state.getValue(w)) return state
        return state.setValue(n, true).setValue(e, true).setValue(s, true).setValue(w, true)
    }

    private fun highHeat(): Boolean {
        if (!SkyblockLocation.`in`(SkyblockLocation.CRYSTAL_HOLLOWS)) return false
        val pl = Minecraft.getInstance().player ?: return false
        if (pl.y > 65.0) return false
        val line = Mining.sidebar.firstOrNull { it.contains("Heat:") } ?: return false
        return (HEAT.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0) >= 90
    }
}
