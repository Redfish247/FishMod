/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import java.util.Locale

object LegacyChatFormatting {
	@JvmStatic
	fun byName(raw: String?): ChatFormatting? {
		if (raw.isNullOrBlank()) return null
		return try {
			ChatFormatting.valueOf(raw.trim().uppercase(Locale.ROOT))
		} catch (_: IllegalArgumentException) {
			null
		}
	}

	@JvmStatic
	fun isColor(formatting: ChatFormatting?): Boolean =
		formatting != null && TextColor.fromLegacyFormat(formatting) != null

	@JvmStatic
	fun rgb(formatting: ChatFormatting?): Int? =
		formatting?.let { TextColor.fromLegacyFormat(it)?.value }

	@JvmStatic
	fun name(formatting: ChatFormatting?): String =
		formatting?.name?.lowercase(Locale.ROOT) ?: ""
}
