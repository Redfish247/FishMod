package fishmod.features.dungeon

import net.minecraft.network.protocol.game.ClientboundSoundPacket
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
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @JvmStatic
    fun isSecretItem(e: ItemEntity): Boolean {
        val name = COLOR.replace(e.item.hoverName.string, "").trim()
        return ITEM_NAMES.any { name == it || name.endsWith(it) }
    }

    /** Position of a secret bat's hurt/death sound, or null if this packet isn't one. */
    @JvmStatic
    fun batSound(p: ClientboundSoundPacket): Vec3? {
        val path = p.sound.value().location.path
        if (path != "entity.bat.death" && path != "entity.bat.hurt") return null
        return Vec3(p.x, p.y, p.z)
    }
}
