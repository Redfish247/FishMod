/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import java.util.Locale;

/**
 * HOTM/HOTF saved presets under {@code skill_tree}. Slot 1 is {@code nodes.<tree>}, slot N is
 * {@code nodes.<tree>_N}; each slot is a full tree (core node included), so they must never be merged.
 */
public final class SkillTreePresets {
	public static final int MAX_SLOTS = 5;

	private SkillTreePresets() {
	}

	/** {@code selected_skill_tree_slot.<tree>}, 1 when absent. */
	public static int selectedSlot(JsonObject skillTree, String tree) {
		JsonObject selected = Leveling.obj(skillTree == null ? null : skillTree.get("selected_skill_tree_slot"));
		Float n = selected == null ? null : Leveling.num(selected.get(tree));
		int slot = n == null ? 1 : Math.round(n);
		return slot < 1 || slot > MAX_SLOTS ? 1 : slot;
	}

	public static String slotKey(String base, int slot) {
		return slot <= 1 ? base : base + "_" + slot;
	}

	/** Nodes of the selected preset, falling back to slot 1 when that preset isn't stored. */
	public static JsonObject selectedNodes(JsonObject skillTree, String tree) {
		JsonObject root = Leveling.obj(skillTree == null ? null : skillTree.get("nodes"));
		if (root == null) {
			return null;
		}
		JsonObject nodes = Leveling.obj(root.get(slotKey(tree, selectedSlot(skillTree, tree))));
		return nodes != null ? nodes : Leveling.obj(root.get(tree));
	}

	/** Tokens spent in the selected preset ({@code mountain}/{@code forest} for slot 1), or null if not stored. */
	public static Integer selectedTokensSpent(JsonObject skillTree, String tree) {
		JsonObject tokens = Leveling.obj(skillTree == null ? null : skillTree.get("tokens_spent"));
		if (tokens == null) {
			return null;
		}
		String base = switch (tree.toLowerCase(Locale.ROOT)) {
			case "mining" -> "mountain";
			case "foraging" -> "forest";
			default -> tree;
		};
		String key = slotKey(base, selectedSlot(skillTree, tree));
		if (!tokens.has(key)) {
			key = base;
		}
		Float n = tokens.has(key) ? Leveling.num(tokens.get(key)) : null;
		return n == null ? null : Math.round(n);
	}
}
