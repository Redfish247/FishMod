/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/** External profile pages for the viewed player. */
object SocialLinks {
	private val NAME = Regex("[A-Za-z0-9_]{1,16}")
	private val HEX32 = Regex("[0-9a-f]{32}")

	enum class Site(private val label: String) {
		PLANCKE("Plancke"),
		SKYCRYPT("SkyCrypt"),
		ELITEBOT("EliteBot"),
		COFLNET("Coflnet"),
		NAMEMC("NameMC");

		fun label(): String = label
	}

	/** Always the viewed player and their selected profile, never the logged-in account. */
	@JvmRecord
	data class Target(val uuid: UUID?, val name: String?, val profileId: String?, val profileName: String?)

	/** Returns null when the target has nothing the site can look up. */
	@JvmStatic
	fun url(site: Site?, target: Target?): String? {
		if (site == null || target == null) {
			return null
		}
		val undashed = target.uuid?.toString()?.replace("-", "")
		val name = validName(target.name)
		val player = undashed ?: name?.let { segment(it) }
		return when (site) {
			Site.PLANCKE -> player?.let { "https://plancke.io/hypixel/player/stats/$it" }
			Site.SKYCRYPT -> player?.let {
				// SkyCrypt rejects profile ids, only the profile name selects a profile.
				val profile = blankToNull(target.profileName)
				"https://sky.shiiyu.moe/stats/" + it + (if (profile == null) "" else "/" + segment(profile))
			}
			Site.ELITEBOT -> player?.let {
				val profile = profileUuid(target.profileId)
					?: blankToNull(target.profileName)?.let { name -> segment(name) }
				"https://eliteskyblock.com/@" + it + (if (profile == null) "" else "/$profile")
			}
			Site.COFLNET -> undashed?.let { "https://sky.coflnet.com/player/$it" }
			Site.NAMEMC -> if (target.uuid != null) {
				"https://namemc.com/profile/" + target.uuid
			} else {
				name?.let { "https://namemc.com/profile/" + segment(it) }
			}
		}
	}

	@JvmStatic
	fun segment(value: String): String =
		URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

	private fun validName(name: String?): String? {
		val trimmed = name?.trim() ?: return null
		return if (NAME.matches(trimmed)) trimmed else null
	}

	private fun profileUuid(profileId: String?): String? {
		val undashed = profileId?.trim()?.replace("-", "")?.lowercase(Locale.ROOT) ?: return null
		return if (HEX32.matches(undashed)) undashed else null
	}

	private fun blankToNull(value: String?): String? =
		if (value.isNullOrBlank()) null else value.trim()
}
