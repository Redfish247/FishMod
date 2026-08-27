package fishmod.features.dungeon.f7.dragons

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

/** Packet decoders for the M7 dragons (ported from NoammAddons' DragonCheck). */
object DragonCheck {

    private val HEALTH_TOKEN = Regex("\\d+(?:\\.\\d+)?[bBmMkK]")
    private const val HEALTH_DATA_ID = 9

    fun handleSpawnPacket(p: ClientboundLevelParticlesPacket, tick: Long) {
        if (p.particle.type !== ParticleTypes.FLAME) return
        if (p.x % 1.0 != 0.0 || p.z % 1.0 != 0.0) return
        if (p.count != 20 || p.y != 19.0 || p.maxSpeed != 0f || !p.isOverrideLimiter) return
        if (p.xDist != 2f || p.yDist != 3f || p.zDist != 2f) return

        val spawning = mutableListOf<WitherDragon>()
        for (d in WitherDragon.real) {
            if (d.state == WitherDragonState.SPAWNING) { spawning.add(d); continue }
            if (p.x in d.xRange && p.z in d.zRange) {
                d.state = WitherDragonState.SPAWNING
                d.timeToSpawn = 100
                d.spawnedTick = tick
                spawning.add(d)
            }
        }
        if (spawning.isNotEmpty()) WitherDragons.priorityDragon = DragonPriority.findPriority(spawning)
    }

    fun dragonSpawn(p: ClientboundAddEntityPacket, tick: Long) {
        if (p.type != EntityType.ENDER_DRAGON) return
        val v = Vec3(p.x, p.y, p.z)
        val inBox = WitherDragon.real.firstOrNull {
            it.state == WitherDragonState.SPAWNING &&
                v.x in it.box.minX..it.box.maxX && v.z in it.box.minZ..it.box.maxZ
        }
        if (inBox != null) { inBox.setAlive(p.id, tick); return }
        WitherDragon.real.firstOrNull { it.state == WitherDragonState.ALIVE && it.entity == null && it.entityId == p.id }
            ?.let { it.entityId = p.id }
    }

    fun dragonUpdate(p: ClientboundSetEntityDataPacket, tick: Long) {
        val d = WitherDragon.byEntityId(p.id()) ?: return
        if (d.entity == null || d.entity?.isAlive != true) {
            d.entity = Minecraft.getInstance().level?.getEntity(p.id()) as? EnderDragon
        }
        val hp = p.packedItems().firstOrNull { it.id() == HEALTH_DATA_ID }?.value as? Float ?: return
        d.health = hp
        if (hp <= 0f && d.state != WitherDragonState.DEAD) d.setDead(false, tick)
    }

    fun dragonSprayed(p: ClientboundSetEquipmentPacket, tick: Long) {
        if (p.slots.none { it.second.item === Items.PACKED_ICE }) return
        val stand = Minecraft.getInstance().level?.getEntity(p.entity) as? ArmorStand ?: return
        for (d in WitherDragon.real) {
            val e = d.entity ?: continue
            if (d.sprayedTick != null || d.state != WitherDragonState.ALIVE || stand.distanceTo(e) > 8.0) continue
            d.sprayedTick = tick - d.spawnedTick
        }
    }

    /** Scoreboard fallback: is this dragon still listed with non-zero health on the sidebar? */
    fun isAliveOnScoreboard(d: WitherDragon): Boolean {
        val sb = Minecraft.getInstance().level?.scoreboard ?: return true
        val obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return true
        for (score in sb.listPlayerScores(obj)) {
            val team = sb.getPlayersTeam(score.owner())
            val line = PlayerTeam.formatNameForTeam(team, net.minecraft.network.chat.Component.literal(score.owner()))
                .string.replace(Regex("§."), "")
            if (line.contains(d.displayName, ignoreCase = true) && HEALTH_TOKEN.find(line)?.value != "0") return true
        }
        return false
    }
}
