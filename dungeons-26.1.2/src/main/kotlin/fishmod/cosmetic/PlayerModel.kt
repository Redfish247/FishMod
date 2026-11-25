package fishmod.cosmetic

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerModel {

    @JvmField val KEYS: Array<String> = arrayOf(
        "PLAYER", "PIG", "COW", "SHEEP", "CHICKEN", "WOLF", "CAT", "OCELOT", "FOX", "RABBIT",
        "HORSE", "LLAMA", "PANDA", "POLAR_BEAR", "GOAT", "TURTLE", "SQUID", "BAT",
    )

    @JvmStatic
    fun label(k: String): String = if (k == "PLAYER") "Player" else k.split('_').joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

    private val remote: MutableMap<String, String> = ConcurrentHashMap()
    private val fakes: MutableMap<UUID, Pair<String, LivingEntity>> = ConcurrentHashMap()
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun ownKey(): String = if (KEYS.contains(FishSettings.playerModel)) FishSettings.playerModel else "PLAYER"

    @JvmStatic
    fun uploadOwn() {
        PlayerSize.uploadOwn()
    }

    @JvmStatic
    fun acceptRemote(uuidNoDashes: String, key: String?) {
        if (key == null || !KEYS.contains(key) || key == "PLAYER") remote.remove(uuidNoDashes) else remote[uuidNoDashes] = key
    }

    @JvmStatic
    fun retainRemote(queried: Set<String>) {
        remote.keys.retainAll(queried)
    }

    @JvmStatic
    fun clearRemote() {
        remote.clear()
        fakes.clear()
    }

    @JvmStatic
    fun keyFor(p: Player): String {
        val mc = Minecraft.getInstance()
        val me = mc.player
        if (me != null && p.uuid == me.uuid) return ownKey()
        if (!FishSettings.playerSizeShared) return "PLAYER"
        return remote[p.uuid.toString().replace("-", "")] ?: "PLAYER"
    }

    private var nextFakeId = -1000

    @JvmStatic
    fun fakeFor(p: Player, key: String): LivingEntity? {
        if (key == "PLAYER" || failed.contains(key)) return null
        val level = p.level()
        val cur = fakes[p.uuid]
        if (cur != null && cur.first == key && cur.second.level() === level) return cur.second
        try {
            val type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(key.lowercase())).orElse(null)
            val e = type?.create(level, EntitySpawnReason.LOAD)
            if (e !is LivingEntity) { failed.add(key); return null }
            e.setId(nextFakeId--)
            fakes[p.uuid] = key to e
            return e
        } catch (t: Throwable) {
            failed.add(key)
            FishDiag.fail("PlayerModel.1", "mob model '$key' unavailable", t)
            return null
        }
    }

    @JvmStatic
    fun syncFake(f: LivingEntity, p: Player) {
        f.snapTo(p.x, p.y, p.z, p.yRot, p.xRot)
        f.xo = p.xo; f.yo = p.yo; f.zo = p.zo
        f.yRotO = p.yRotO; f.xRotO = p.xRotO
        f.yBodyRot = p.yBodyRot; f.yBodyRotO = p.yBodyRotO
        f.yHeadRot = p.yHeadRot; f.yHeadRotO = p.yHeadRotO
        f.tickCount = p.tickCount
        f.setOnGround(p.onGround())
    }
}
