package fishmod.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3

// Secret item / bat matching, same approach as Odin + NoammAddons: item by name, bat by its sound packet.
object SecretDrops {

    private val ITEM_NAMES = listOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion",
        "Healing VIII Splash Potion", "Healing 8 Splash Potion", "Decoy", "Inflatable Jerry", "Spirit Leap",
        "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone",
        "Architect's First Draft", "Secret Dye", "Candycomb"
    )
    // Wither essence + redstone key skull owners (Odin DungeonUtils)
    private val SECRET_SKULLS = setOf("e0f3e929-869e-3dca-9504-54c666ee6f23", "fed95410-aba1-39df-9b95-1d4f361eb66e")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @JvmStatic
    fun isSecretItem(e: ItemEntity): Boolean {
        val name = COLOR.replace(e.item.hoverName.string, "").trim()
        return ITEM_NAMES.any { name == it || name.endsWith(it) }
    }

    @JvmStatic
    fun isSecretSkull(level: Level, pos: BlockPos): Boolean {
        val skull = level.getBlockEntity(pos) as? SkullBlockEntity ?: return false
        return skull.ownerProfile?.partialProfile()?.id()?.toString() in SECRET_SKULLS
    }

    @JvmStatic
    fun batSound(p: ClientboundSoundPacket): Vec3? {
        val path = p.sound.value().location.path
        if (path != "entity.bat.death" && path != "entity.bat.hurt") return null
        return Vec3(p.x, p.y, p.z)
    }
}
