/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.slayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.Leveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mayor / city-project slayer XP and quest-price modifiers. */
public record SlayerMayorMods(
	boolean aatroxXp,
	boolean aatroxPrice,
	boolean cityProject,
	boolean auraXp,
	boolean auraPrice
) {
	public static SlayerMayorMods none() {
		return new SlayerMayorMods(false, false, false, false, false);
	}

	public static SlayerMayorMods from(JsonObject electionRoot) {
		if (electionRoot == null) {
			return none();
		}
		boolean aatroxXp = false;
		boolean aatroxPrice = false;
		boolean cityProject = false;
		boolean auraXp = false;
		boolean auraPrice = false;
		for (Perk perk : collectPerks(electionRoot)) {
			String blob = (perk.name + " " + perk.description).toLowerCase(Locale.ROOT);
			if (blob.contains("slayer xp buff")
				|| (blob.contains("slayer") && hasBarePercent(blob, 25) && blob.contains("xp"))) {
				aatroxXp = true;
			}
			if (blob.contains("slashed")
				|| (blob.contains("slayer") && (blob.contains("half price") || (hasBarePercent(blob, 50) && blob.contains("price"))))
				|| (blob.contains("starting slayer") && blob.contains("half"))) {
				aatroxPrice = true;
			}
			if (blob.contains("city project")
				|| blob.contains("cityproject")
				|| blob.contains("slayer discount")
				|| blob.contains("slayer discounts")
				|| (blob.contains("slayer") && hasBarePercent(blob, 5)
					&& (blob.contains("price") || blob.contains("cost") || blob.contains("discount") || blob.contains("cheaper")))) {
				cityProject = true;
			}
			if (blob.contains("work smarter")
				|| (blob.contains("slayer") && hasBarePercent(blob, 50) && blob.contains("xp"))
				|| (blob.contains("skill") && hasBarePercent(blob, 50) && blob.contains("slayer"))) {
				auraXp = true;
			}
			if (blob.contains("fundraising")
				|| blob.contains("taxes and costs")
				|| (blob.contains("vastly increased") && blob.contains("cost"))
				|| ((blob.contains("2x") || blob.contains("twice")) && (blob.contains("price") || blob.contains("cost")))) {
				auraPrice = true;
			}
		}
		return new SlayerMayorMods(aatroxXp, aatroxPrice, cityProject, auraXp, auraPrice);
	}

	public double xpMultiplier() {
		double mult = 1.0;
		if (this.aatroxXp) {
			mult *= 1.25;
		}
		if (this.auraXp) {
			mult *= 1.50;
		}
		return mult;
	}

	public double priceMultiplier() {
		double mult = 1.0;
		if (this.aatroxPrice) {
			mult *= 0.50;
		}
		if (this.cityProject) {
			mult *= 0.95;
		}
		if (this.auraPrice) {
			mult *= 2.0;
		}
		return mult;
	}

	public boolean any() {
		return this.aatroxXp || this.aatroxPrice || this.cityProject || this.auraXp || this.auraPrice;
	}

	public List<String> labels() {
		List<String> out = new ArrayList<>();
		if (this.aatroxXp) {
			out.add("Aatrox +25% XP");
		}
		if (this.aatroxPrice) {
			out.add("Aatrox -50% price");
		}
		if (this.cityProject) {
			out.add("City Project -5% price");
		}
		if (this.auraXp) {
			out.add("Aura +50% XP");
		}
		if (this.auraPrice) {
			out.add("Aura 2x price");
		}
		return out;
	}

	private record Perk(String name, String description) {
	}

	/** True when {@code n%} appears as its own number, so {@code 5%} does not match {@code 25%} / {@code 50%}. */
	private static boolean hasBarePercent(String blob, int n) {
		if (blob == null || blob.isBlank()) {
			return false;
		}
		String token = n + "%";
		int from = 0;
		while (from < blob.length()) {
			int at = blob.indexOf(token, from);
			if (at < 0) {
				return false;
			}
			if (at == 0 || !Character.isDigit(blob.charAt(at - 1))) {
				return true;
			}
			from = at + 1;
		}
		return false;
	}

	private static List<Perk> collectPerks(JsonObject root) {
		List<Perk> out = new ArrayList<>();
		addPerks(out, Leveling.obj(root.get("mayor")));
		JsonObject mayor = Leveling.obj(root.get("mayor"));
		if (mayor != null) {
			addPerks(out, Leveling.obj(mayor.get("minister")));
		}
		addPerks(out, Leveling.obj(root.get("minister")));
		return out;
	}

	private static void addPerks(List<Perk> out, JsonObject obj) {
		if (obj == null) {
			return;
		}
		JsonElement raw = obj.get("perks");
		if (raw == null || !raw.isJsonArray()) {
			return;
		}
		JsonArray perks = raw.getAsJsonArray();
		for (JsonElement el : perks) {
			JsonObject perk = Leveling.obj(el);
			if (perk == null) {
				continue;
			}
			String name = perk.has("name") && perk.get("name").isJsonPrimitive()
				? perk.get("name").getAsString() : "";
			String desc = perk.has("description") && perk.get("description").isJsonPrimitive()
				? perk.get("description").getAsString() : "";
			out.add(new Perk(name, desc));
		}
	}
}
