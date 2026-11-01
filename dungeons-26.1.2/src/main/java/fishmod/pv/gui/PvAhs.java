package fishmod.pv.gui;

import fishmod.features.extras.ExtrasSettings;
import net.minecraft.client.Minecraft;

/** One click on a suggestion in our own screens = one /ahs search. */
public final class PvAhs {
	private PvAhs() {
	}

	public static boolean search(String item) {
		if (!ExtrasSettings.suggestionClickAhs || item == null || item.isBlank()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) {
			return false;
		}
		String clean = item.replaceAll("§.", "").trim();
		mc.getConnection().sendCommand("ahs " + clean);
		return true;
	}
}
