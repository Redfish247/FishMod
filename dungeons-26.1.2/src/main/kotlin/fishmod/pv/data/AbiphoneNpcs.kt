/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.neu.NeuRepoCache
import fishmod.utils.TextStrip
import java.util.Locale

/** Maps Abiphone contact ids → NEU NPC skull item ids. */
object AbiphoneNpcs {
	const val TOTAL_CONTACTS = 84
	private val ALIASES: Map<String, String> = mapOf(
		"pet_sitter" to "KAT_NPC",
		"community_shop" to "ELIZABETH_NPC",
		"thaumaturgist" to "HEX_NPC",
		"arrow_forger" to "JAX_NPC",
		"drill_fuel_mechanic" to "JOAN_NPC",
		"forge_foreman" to "FORGE_FOREMAN_NPC",
		"plumber" to "PLUMBER_JOE_NPC",
		"fairy" to "TIA_THE_FAIRY_NPC",
		"slayer" to "MADDOX_THE_SLAYER_NPC",
		"gemstone" to "X_NPC",
		"telekinesis_applier" to "HOVER_EGG_NPC",
		"trevor_the_trapper" to "TRAPPER_NPC",
		"shady_bartender" to "SHADY_BARTENDER_NPC",
		"bartender" to "BARTENDER_NPC",
		"queen_mismyla" to "QUEEN_MISMYLA_NPC",
		"queen" to "QUEEN_NYX_NPC",
		"st_jerry" to "ST_JERRY_NPC",
		"wool_weaver" to "WOOL_WEAVER_NPC",
		"lumber_merchant" to "LUMBER_MERCHANT_NPC",
		"captain_ahone" to "CAPTAIN_AHONE_NPC",
		"pet_collector" to "PET_COLLECTOR_NPC",
		"pet_trainer" to "PET_TRAINER_NPC",
		"blacksmith" to "BLACKSMITH_NPC",
		"gatekeeper" to "GATEKEEPER_NPC",
		"kuudra_gatekeeper" to "KUUDRA_GATEKEEPER_NPC",
		"clerk_seraphine" to "CLERK_SERAPHINE_NPC",
		"mad_redstone_engineer" to "MAD_REDSTONE_ENGINEER_NPC",
		"pesthunter_phillip" to "PESTHUNTER_PHILLIP_NPC",
		"spider_tamer" to "SPIDER_TAMER_NPC",
		"feast_baker_scott" to "BAKER_SCOTT_NPC",
		"feast_chef_ted" to "CHEF_TED_NPC",
		"frozen_alex" to "FROZEN_ALEX_NPC",
		"junker_joel" to "JUNKER_JOEL_NPC",
		"jake_lab" to "JAKE_NPC",
		"spooky" to "FEAR_MONGERER_NPC",
		"trinity" to "SISTER_TRINITY_NPC",
		"tony" to "TONY_NPC",
		"dalir" to "DALIR_NPC",
		"anita" to "ANITA_NPC",
		"shaggy" to "SHAGGY_NPC",
		"jacob" to "JACOB_NPC"
	)

	/** NEU contact names whose skull item isn't simply {@code NAME_NPC}. */
	private val CONTACT_ICONS: Map<String, String> = mapOf(
		"philip" to "PESTHUNTER_PHILLIP_NPC",
		"scott" to "FEAST_BAKER_SCOTT_NPC",
		"ted" to "FEAST_CHEF_TED_NPC",
		"trinity" to "SISTER_TRINITY_NPC"
	)
	/** Hypixel contact ids that match neither the NEU name nor its callNames. */
	private val API_TO_CONTACT: Map<String, String> = mapOf(
		"pet_sitter" to "kat",
		"arrow_forger" to "jax",
		"queen" to "queen_nyx",
		"forge_foreman" to "fred",
		"plumber" to "plumber_joe",
		"pesthunter_phillip" to "philip",
		"trevor_the_trapper" to "trevor",
		"jake_lab" to "jake",
		"feast_baker_scott" to "scott",
		"feast_chef_ted" to "ted"
	)
	private val NON_ALNUM = Regex("[^a-z0-9]+")
	private val EDGE_UNDERSCORES = Regex("^_+|_+$")
	private val LEADING_DASH = Regex("^-\\s*")

	@JvmRecord
	data class Undiscovered(val name: String, val neuId: String, val requirement: List<String>)

	/** NEU Abiphone contacts with no matching entry in the player's contact ids. */
	@JvmStatic
	fun undiscovered(contactIds: Collection<String?>?): List<Undiscovered> {
		// Owning every real contact wins over NEU name matching (NEU lists extra entries).
		if (contactIds != null && contactIds.size >= TOTAL_CONTACTS) {
			return listOf()
		}
		val ids = HashSet<String>()
		val idNeu = HashSet<String>()
		val tokens = HashSet<String>()
		for (raw in contactIds.orEmpty()) {
			val id = norm(raw)
			if (id.isEmpty()) {
				continue
			}
			ids.add(id)
			ids.add(API_TO_CONTACT[id] ?: id)
			idNeu.add(neuId(id))
			tokens.addAll(id.split("_"))
		}
		val out = ArrayList<Undiscovered>()
		for ((rawKey, value) in NeuRepoCache.abiphoneContacts().entrySet()) {
			if (!value.isJsonObject) {
				continue
			}
			val def = value.asJsonObject
			val key = norm(rawKey)
			val icon = CONTACT_ICONS[key] ?: (key.uppercase(Locale.ROOT) + "_NPC")
			var found = ids.contains(key) || idNeu.contains(icon) ||
				// Single-word names show up inside longer ids, e.g. trevor_the_trapper or feast_chef_ted.
				(key.indexOf('_') < 0 && tokens.contains(key))
			for (call in strings(def, "callNames")) {
				found = found or ids.contains(norm(call))
			}
			if (!found) {
				val requirement = ArrayList<String>()
				for (line in strings(def, "requirement")) {
					val plain = LEADING_DASH.replaceFirst(TextStrip.plain(line), "").trim()
					if (plain.isNotEmpty()) {
						requirement.add(plain)
					}
				}
				out.add(Undiscovered(rawKey, icon, java.util.List.copyOf(requirement)))
			}
		}
		out.sortWith(Comparator.comparing({ u: Undiscovered -> u.name }, String.CASE_INSENSITIVE_ORDER))
		return out
	}

	/** In-game contact name for a Hypixel contact id (e.g. {@code slayer} → Maddox the Slayer), or null if unknown. */
	@JvmStatic
	fun displayName(contactId: String?): String? = displayName(contactId, NeuRepoCache.abiphoneContacts())

	@JvmStatic
	fun displayName(contactId: String?, contacts: JsonObject?): String? {
		val id = norm(contactId)
		if (id.isEmpty() || contacts == null) {
			return null
		}
		val key = API_TO_CONTACT[id] ?: id
		var byCallName: String? = null
		for ((rawName, value) in contacts.entrySet()) {
			val name = norm(rawName)
			if (name == key || name == id) {
				return rawName
			}
			if (byCallName == null && value.isJsonObject) {
				for (call in strings(value.asJsonObject, "callNames")) {
					val c = norm(call)
					if (c == id || c == key) {
						byCallName = rawName
						break
					}
				}
			}
		}
		return byCallName
	}

	private fun norm(text: String?): String {
		if (text == null) {
			return ""
		}
		return EDGE_UNDERSCORES.replace(NON_ALNUM.replace(text.lowercase(Locale.ROOT), "_"), "")
	}

	private fun strings(obj: JsonObject, key: String): List<String> {
		if (!obj.has(key) || !obj.get(key).isJsonArray) {
			return listOf()
		}
		return obj.getAsJsonArray(key).filter { it.isJsonPrimitive }.map { it.asString }
	}

	@JvmStatic
	fun neuId(contactId: String?): String {
		if (contactId.isNullOrBlank()) {
			return ""
		}
		val key = contactId.lowercase(Locale.ROOT)
		return ALIASES[key] ?: (key.uppercase(Locale.ROOT) + "_NPC")
	}
}
