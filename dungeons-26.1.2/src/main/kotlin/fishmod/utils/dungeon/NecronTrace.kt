package fishmod.utils.dungeon

import fishmod.utils.debug.Debug
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand

// Temporary: logs the Necron fight timeline (bar, withers, boss lines, relic spawn) to latest.log.
object NecronTrace {
    private var startMs = 0L
    private var lastBar = ""
    private var lastWithers = ""
    private var relicsLogged = false

    private fun active() = Phase.getFloor()?.endsWith("7") == true && (Phase.getPhase() == 8 || startMs != 0L && !relicsLogged)

    private fun log(msg: String) {
        if (startMs == 0L) startMs = System.currentTimeMillis()
        Debug.LOGGER.info("[NecronTrace +%.2fs] %s".format((System.currentTimeMillis() - startMs) / 1000.0, msg))
    }

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (active()) {
                val s = text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
                if (s.startsWith("[BOSS]") || s.contains("Corrupted") || s.contains("Relic")) log("chat: $s")
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { startMs = 0L; lastBar = ""; lastWithers = ""; relicsLogged = false; false }
    }

    @JvmStatic
    fun tick() {
        if (!active()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val bars = (mc.gui.bossOverlay as fishmod.mixin.accessors.BossBarHudAccessor).bossBars
        val bar = bars?.values?.joinToString(", ") {
            val pct = (it as fishmod.mixin.accessors.LerpingBossEventAccessor).targetPercent
            "'${it.name.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()}' %.1f%%".format(pct * 100f)
        } ?: "none"
        if (bar != lastBar) { lastBar = bar; log("bar: ${bar.ifEmpty { "none" }}") }

        val withers = level.entitiesForRendering().filterIsInstance<WitherBoss>().filter { it.boundingBox.ysize >= 2.0 }
            .joinToString(", ") { "id=${it.id} hp=${(it.health / it.maxHealth * 10).toInt() * 10}% inv=${it.invulnerableTicks > 0} invis=${it.isInvisible} dead=${it.isDeadOrDying}" }
        if (withers != lastWithers) { lastWithers = withers; log("withers: ${withers.ifEmpty { "none" }}") }

        if (!relicsLogged && level.entitiesForRendering().any { it is ArmorStand && it.getItemBySlot(EquipmentSlot.HEAD).hoverName.string.contains("Corrupted") }) {
            relicsLogged = true
            log("relics spawned")
        }
    }
}
