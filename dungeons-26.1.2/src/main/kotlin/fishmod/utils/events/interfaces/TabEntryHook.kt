package fishmod.utils.events.interfaces

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket

fun interface TabEntryHook {
    fun onNewPlayerEntry(receivedEntry: ClientboundPlayerInfoUpdatePacket.Entry): Boolean
}
