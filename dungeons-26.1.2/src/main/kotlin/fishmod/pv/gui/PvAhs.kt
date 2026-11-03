package fishmod.pv.gui

import fishmod.features.extras.ExtrasSettings
import net.minecraft.client.Minecraft

/** One click on a suggestion in our own screens = one /ahs search. */
object PvAhs {
	private val CODES = Regex("§.")

	@JvmStatic
	fun search(item: String?): Boolean {
		if (!ExtrasSettings.suggestionClickAhs || item.isNullOrBlank()) {
			return false
		}
		val connection = Minecraft.getInstance().connection ?: return false
		val clean = item.replace(CODES, "").trim()
		connection.sendCommand("ahs $clean")
		return true
	}
}
