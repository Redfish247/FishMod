package fishmod.utils

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** Gate for developer-only debug commands, keyed by UUID. */
object DevOnly {

    private val DEV_UUIDS: Set<String> = setOf(
        "2abb218fada349bea6d181a2872941e2"  // RedFish2471
    )

    @JvmStatic
    fun isDev(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player == null) return false
        return DEV_UUIDS.contains(mc.player!!.uuid.toString().replace("-", "").lowercase())
    }

    @JvmStatic
    fun deny(source: FabricClientCommandSource): Boolean {
        if (isDev()) return false
        source.sendFeedback(Component.literal("§cThat command is dev-only."))
        return true
    }
}
