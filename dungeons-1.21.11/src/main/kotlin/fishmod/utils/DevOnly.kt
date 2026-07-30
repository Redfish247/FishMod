package fishmod.utils

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

/** Gate for developer-only debug commands; only UUIDs in DEV_UUIDS can run them. */
object DevOnly {

    /** Allowed dev UUIDs (no dashes, lowercase). */
    private val DEV_UUIDS: Set<String> = setOf(
        "2abb218fada349bea6d181a2872941e2"  // RedFish2471
    )

    @JvmStatic
    fun isDev(): Boolean {
        val mc = MinecraftClient.getInstance()
        if (mc.player == null) return false
        return DEV_UUIDS.contains(mc.player!!.uuid.toString().replace("-", "").lowercase())
    }

    /** Returns true if the caller is NOT a dev, after sending a "dev-only" message. */
    @JvmStatic
    fun deny(source: FabricClientCommandSource): Boolean {
        if (isDev()) return false
        source.sendFeedback(Text.literal("§cThat command is dev-only."))
        return true
    }
}
