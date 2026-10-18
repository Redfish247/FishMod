/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.overview;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.SkyBlockStats;
import java.util.ArrayList;
import java.util.List;

/** Calculated stat rows for skill overviews, with what the estimate leaves out spelled out on hover. */
public final class SkillStatRows {
	private SkillStatRows() {
	}

	public static SummaryCards.Stat row(PlayerStatsSnapshot stats, String id, List<String> exclusions) {
		SkyBlockStats.StatStyle style = SkyBlockStats.stat(id);
		PlayerStatsSnapshot.Entry entry = find(stats, id);
		boolean present = entry != null && entry.present();
		String value = present ? format(entry.value().getAsDouble()) : "-";
		List<PvTooltip.Line> tip = new ArrayList<>(SkyBlockStats.tooltipLines(id, value));
		tip.add(PvTooltip.Line.blank());
		if (present) {
			for (String line : exclusions) {
				tip.add(PvTooltip.Line.meta(line));
			}
		} else {
			tip.add(PvTooltip.Line.meta("Not in the API"));
		}
		return new SummaryCards.Stat(style.fullName(), value, style.color(), present ? PvDraw.COLOR_WHITE : PvDraw.COLOR_MUTED, tip);
	}

	private static PlayerStatsSnapshot.Entry find(PlayerStatsSnapshot stats, String id) {
		if (stats == null) {
			return null;
		}
		for (PlayerStatsSnapshot.Entry entry : stats.entries()) {
			if (id.equals(entry.id())) {
				return entry;
			}
		}
		return null;
	}

	private static String format(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.05) {
			return FormatUtil.commas(Math.round(value));
		}
		return FormatUtil.oneDecimal(value);
	}
}
