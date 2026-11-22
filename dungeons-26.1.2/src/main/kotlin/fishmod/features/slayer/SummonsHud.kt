package fishmod.features.slayer

import fishmod.features.mining.MiningHuds
import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand

// HUD listing your Necromancer summons and their HP
object SummonsHud {

    // from SkyHanni (MobFilter.summonFilter: "^(?<owner>\\w+)'s (?<name>.*) \\d+.*")
    private val SUMMON = Regex("^(\\w+)'s (.*) (\\d.*)$")

    @Volatile private var lines: List<String> = emptyList()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.summonsHud) { if (lines.isNotEmpty()) lines = emptyList(); return@register }
            if (++tick % 10 == 0) FishDiag.guard("SummonsHud.1", "summons scan failed") { scan(mc) }
        }
        FishEvents.worldChange.register { lines = emptyList(); false }
        MiningHuds.reg("Summonings", "slayer_summons", 140, 40,
            { S.summonsHudX }, { S.summonsHudX = it }, { S.summonsHudY }, { S.summonsHudY = it },
            { S.summonsHudScale }, { S.summonsHudScale = it },
            { S.summonsHud && SkyblockLocation.inSkyblock() }, { S.summonsHud },
            example = { listOf("§aSummons: §f2", "§7#1 §aTank Zombie §f12k/15k§c❤", "§7#2 §aSuper Tank Zombie §f40k/40k§c❤") },
            lines = { lines })
    }

    private fun scan(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val me = player.gameProfile.name()
        val out = ArrayList<String>()
        for (st in level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(48.0)) { it.hasCustomName() }) {
            val s = st.customName?.string?.replace(FishConstants.STRIP_COLOR_REGEX, "")?.trim() ?: continue
            val m = SUMMON.matchEntire(s) ?: continue
            if (!m.groupValues[1].equals(me, true) || '❤' !in m.groupValues[3]) continue
            out.add("§7#${out.size + 1} §a${m.groupValues[2]} §f${m.groupValues[3].replace("❤", "§c❤")}")
        }
        lines = if (out.isEmpty()) emptyList() else listOf("§aSummons: §f${out.size}") + out
    }
}
