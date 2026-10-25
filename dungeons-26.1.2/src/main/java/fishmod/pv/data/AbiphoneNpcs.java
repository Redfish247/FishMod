/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.neu.NeuRepoCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps Abiphone contact ids → NEU NPC skull item ids. */
public final class AbiphoneNpcs {
	public static final int TOTAL_CONTACTS = 84;
	private static final Map<String, String> ALIASES = Map.ofEntries(
		Map.entry("pet_sitter", "KAT_NPC"),
		Map.entry("community_shop", "ELIZABETH_NPC"),
		Map.entry("thaumaturgist", "HEX_NPC"),
		Map.entry("arrow_forger", "JAX_NPC"),
		Map.entry("drill_fuel_mechanic", "JOAN_NPC"),
		Map.entry("forge_foreman", "FORGE_FOREMAN_NPC"),
		Map.entry("plumber", "PLUMBER_JOE_NPC"),
		Map.entry("fairy", "TIA_THE_FAIRY_NPC"),
		Map.entry("slayer", "MADDOX_THE_SLAYER_NPC"),
		Map.entry("gemstone", "X_NPC"),
		Map.entry("telekinesis_applier", "HOVER_EGG_NPC"),
		Map.entry("trevor_the_trapper", "TRAPPER_NPC"),
		Map.entry("shady_bartender", "SHADY_BARTENDER_NPC"),
		Map.entry("bartender", "BARTENDER_NPC"),
		Map.entry("queen_mismyla", "QUEEN_MISMYLA_NPC"),
		Map.entry("queen", "QUEEN_NYX_NPC"),
		Map.entry("st_jerry", "ST_JERRY_NPC"),
		Map.entry("wool_weaver", "WOOL_WEAVER_NPC"),
		Map.entry("lumber_merchant", "LUMBER_MERCHANT_NPC"),
		Map.entry("captain_ahone", "CAPTAIN_AHONE_NPC"),
		Map.entry("pet_collector", "PET_COLLECTOR_NPC"),
		Map.entry("pet_trainer", "PET_TRAINER_NPC"),
		Map.entry("blacksmith", "BLACKSMITH_NPC"),
		Map.entry("gatekeeper", "GATEKEEPER_NPC"),
		Map.entry("kuudra_gatekeeper", "KUUDRA_GATEKEEPER_NPC"),
		Map.entry("clerk_seraphine", "CLERK_SERAPHINE_NPC"),
		Map.entry("mad_redstone_engineer", "MAD_REDSTONE_ENGINEER_NPC"),
		Map.entry("pesthunter_phillip", "PESTHUNTER_PHILLIP_NPC"),
		Map.entry("spider_tamer", "SPIDER_TAMER_NPC"),
		Map.entry("feast_baker_scott", "BAKER_SCOTT_NPC"),
		Map.entry("feast_chef_ted", "CHEF_TED_NPC"),
		Map.entry("frozen_alex", "FROZEN_ALEX_NPC"),
		Map.entry("junker_joel", "JUNKER_JOEL_NPC"),
		Map.entry("jake_lab", "JAKE_NPC"),
		Map.entry("spooky", "FEAR_MONGERER_NPC"),
		Map.entry("trinity", "SISTER_TRINITY_NPC"),
		Map.entry("tony", "TONY_NPC"),
		Map.entry("dalir", "DALIR_NPC"),
		Map.entry("anita", "ANITA_NPC"),
		Map.entry("shaggy", "SHAGGY_NPC"),
		Map.entry("jacob", "JACOB_NPC")
	);

	/** NEU contact names whose skull item isn't simply {@code NAME_NPC}. */
	private static final Map<String, String> CONTACT_ICONS = Map.of(
		"philip", "PESTHUNTER_PHILLIP_NPC",
		"scott", "FEAST_BAKER_SCOTT_NPC",
		"ted", "FEAST_CHEF_TED_NPC",
		"trinity", "SISTER_TRINITY_NPC"
	);
	/** Hypixel contact ids that match neither the NEU name nor its callNames. */
	private static final Map<String, String> API_TO_CONTACT = Map.ofEntries(
		Map.entry("pet_sitter", "kat"),
		Map.entry("arrow_forger", "jax"),
		Map.entry("queen", "queen_nyx"),
		Map.entry("forge_foreman", "fred"),
		Map.entry("plumber", "plumber_joe"),
		Map.entry("pesthunter_phillip", "philip"),
		Map.entry("trevor_the_trapper", "trevor"),
		Map.entry("jake_lab", "jake"),
		Map.entry("feast_baker_scott", "scott"),
		Map.entry("feast_chef_ted", "ted")
	);

	public record Undiscovered(String name, String neuId, List<String> requirement) {
	}

	private AbiphoneNpcs() {
	}

	/** NEU Abiphone contacts with no matching entry in the player's contact ids. */
	public static List<Undiscovered> undiscovered(Collection<String> contactIds) {
		// Owning every real contact wins over NEU name matching (NEU lists extra entries).
		if (contactIds != null && contactIds.size() >= TOTAL_CONTACTS) {
			return List.of();
		}
		Set<String> ids = new HashSet<>();
		Set<String> idNeu = new HashSet<>();
		Set<String> tokens = new HashSet<>();
		for (String raw : contactIds) {
			String id = norm(raw);
			if (id.isEmpty()) {
				continue;
			}
			ids.add(id);
			ids.add(API_TO_CONTACT.getOrDefault(id, id));
			idNeu.add(neuId(id));
			tokens.addAll(List.of(id.split("_")));
		}
		List<Undiscovered> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : NeuRepoCache.abiphoneContacts().entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject def = entry.getValue().getAsJsonObject();
			String key = norm(entry.getKey());
			String icon = CONTACT_ICONS.getOrDefault(key, key.toUpperCase(Locale.ROOT) + "_NPC");
			boolean found = ids.contains(key) || idNeu.contains(icon)
				// Single-word names show up inside longer ids, e.g. trevor_the_trapper or feast_chef_ted.
				|| (key.indexOf('_') < 0 && tokens.contains(key));
			for (String call : strings(def, "callNames")) {
				found |= ids.contains(norm(call));
			}
			if (!found) {
				List<String> requirement = new ArrayList<>();
				for (String line : strings(def, "requirement")) {
					String plain = line.replaceAll("§.", "").replaceFirst("^-\\s*", "").trim();
					if (!plain.isEmpty()) {
						requirement.add(plain);
					}
				}
				out.add(new Undiscovered(entry.getKey(), icon, List.copyOf(requirement)));
			}
		}
		out.sort(Comparator.comparing(Undiscovered::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	/** In-game contact name for a Hypixel contact id (e.g. {@code slayer} → Maddox the Slayer), or null if unknown. */
	public static String displayName(String contactId) {
		return displayName(contactId, NeuRepoCache.abiphoneContacts());
	}

	static String displayName(String contactId, JsonObject contacts) {
		String id = norm(contactId);
		if (id.isEmpty() || contacts == null) {
			return null;
		}
		String key = API_TO_CONTACT.getOrDefault(id, id);
		String byCallName = null;
		for (Map.Entry<String, JsonElement> entry : contacts.entrySet()) {
			String name = norm(entry.getKey());
			if (name.equals(key) || name.equals(id)) {
				return entry.getKey();
			}
			if (byCallName == null && entry.getValue().isJsonObject()) {
				for (String call : strings(entry.getValue().getAsJsonObject(), "callNames")) {
					String c = norm(call);
					if (c.equals(id) || c.equals(key)) {
						byCallName = entry.getKey();
						break;
					}
				}
			}
		}
		return byCallName;
	}

	private static String norm(String text) {
		if (text == null) {
			return "";
		}
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private static List<String> strings(JsonObject obj, String key) {
		if (!obj.has(key) || !obj.get(key).isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement el : obj.getAsJsonArray(key)) {
			if (el.isJsonPrimitive()) {
				out.add(el.getAsString());
			}
		}
		return out;
	}

	public static String neuId(String contactId) {
		if (contactId == null || contactId.isBlank()) {
			return "";
		}
		String key = contactId.toLowerCase(Locale.ROOT);
		String alias = ALIASES.get(key);
		if (alias != null) {
			return alias;
		}
		return key.toUpperCase(Locale.ROOT) + "_NPC";
	}
}
