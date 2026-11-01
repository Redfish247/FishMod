package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Giant

// Estimates your share of each boss's health: health drops shortly after your own hit/aim are credited to you.
object BossDamageCalc {

    private class Boss(val name: String, var lastHp: Float, val maxHp: Float) {
        var mine = 0f
        var total = 0f
    }

    private const val CREDIT_TICKS = 8
    private val bosses = LinkedHashMap<Int, Boss>()
    private var ticks = 0L
    private var lastActionTick = -1000L
    private var lastTargetId = -1

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { bosses.clear(); false }
        AttackEntityCallback.EVENT.register { _, level, _, entity, _ ->
            if (level.isClientSide) { lastActionTick = ticks; lastTargetId = entity.id }
            InteractionResult.PASS
        }
        UseItemCallback.EVENT.register(UseItemCallback { _, level, _ ->
            if (level.isClientSide) { lastActionTick = ticks; lastTargetId = -1 }
            InteractionResult.PASS
        })
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            ticks++
            if (!FishSettings.bossDamageCalcEnabled || !Location.inDungeon()) return@register
            FishDiag.guard("BossDamageCalc.1", "boss health scan failed") { scan(mc) }
        }
        Events.ON_RUN_END.register {
            if (FishSettings.bossDamageCalcEnabled && Location.inDungeon())
                Scheduler.scheduleTask(Runnable { FishDiag.guard("BossDamageCalc.2", "boss damage print failed") { print() } }, 20)
            false
        }
    }

    private fun isBoss(e: LivingEntity) = e is WitherBoss || e is EnderDragon || e is Giant

    private fun scan(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val looking = (mc.hitResult as? net.minecraft.world.phys.EntityHitResult)?.entity?.id
        for (e in level.entitiesForRendering()) {
            if (e !is LivingEntity || !isBoss(e)) continue
            val hp = e.health
            val b = bosses.getOrPut(e.id) { Boss(e.name.string, hp, e.maxHealth.coerceAtLeast(1f)) }
            val drop = b.lastHp - hp
            b.lastHp = hp
            if (drop <= 0f) continue
            b.total += drop
            val recent = ticks - lastActionTick <= CREDIT_TICKS
            val aimed = lastTargetId == e.id || looking == e.id || player.distanceTo(e) < 6f
            if (recent && aimed) b.mine += drop
        }
    }

    private fun print() {
        val list = bosses.values.filter { it.total > 0f }
        if (list.isEmpty()) return
        val sb = StringBuilder("§6Boss Damage §7(estimate)")
        for (b in list) {
            val pct = b.mine / b.maxHp * 100f
            sb.append("\n§c").append(b.name).append("§7: §e")
                .append("%.0f".format(b.mine)).append("§7/§e").append("%.0f".format(b.maxHp))
                .append(" §7HP (§a").append("%.1f".format(pct)).append("%§7)")
        }
        Misc.addChatMessage(Component.literal(sb.toString()))
        bosses.clear()
    }
}
