/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

/**
 * ChatFormatting color helpers that work on both 26.1.2 and 26.2.
 * 26.2 removed {@code getByName}/{@code isColor}/{@code getColor}/{@code getName}.
 */
public final class LegacyChatFormatting {
	private LegacyChatFormatting() {
	}

	public static ChatFormatting byName(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return ChatFormatting.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static boolean isColor(ChatFormatting formatting) {
		return formatting != null && TextColor.fromLegacyFormat(formatting) != null;
	}

	public static Integer rgb(ChatFormatting formatting) {
		if (formatting == null) {
			return null;
		}
		TextColor color = TextColor.fromLegacyFormat(formatting);
		return color == null ? null : color.getValue();
	}

	public static String name(ChatFormatting formatting) {
		return formatting == null ? "" : formatting.name().toLowerCase(java.util.Locale.ROOT);
	}
}
