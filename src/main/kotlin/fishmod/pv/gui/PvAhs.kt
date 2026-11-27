package fishmod.pv.gui

import fishmod.utils.TextStrip

import fishmod.features.extras.ExtrasSettings
import net.minecraft.client.Minecraft

object PvAhs {

	@JvmStatic
	fun search(item: String?): Boolean {
		if (!ExtrasSettings.suggestionClickAhs || item.isNullOrBlank()) {
			return false
		}
		val connection = Minecraft.getInstance().connection ?: return false
		val clean = TextStrip.plain(item).trim()
		connection.sendCommand("ahs $clean")
		return true
	}
}
