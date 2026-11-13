/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import kotlin.math.roundToInt

/**
 * HOTM/HOTF saved presets under `skill_tree`. Slot 1 is `nodes.<tree>`, slot N is
 * `nodes.<tree>_N`; each slot is a full tree (core node included), so they must never be merged.
 */
object SkillTreePresets {
    const val MAX_SLOTS = 5

    @JvmStatic
    fun selectedSlot(skillTree: JsonObject?, tree: String): Int {
        val selected = Leveling.obj(skillTree?.get("selected_skill_tree_slot"))
        val n = selected?.let { Leveling.num(it.get(tree)) }
        val slot = n?.let { Math.round(it) } ?: 1
        return if (slot < 1 || slot > MAX_SLOTS) 1 else slot
    }

    @JvmStatic
    fun slotKey(base: String, slot: Int): String = if (slot <= 1) base else "${base}_$slot"

    @JvmStatic
    fun selectedNodes(skillTree: JsonObject?, tree: String): JsonObject? {
        val root = Leveling.obj(skillTree?.get("nodes")) ?: return null
        return Leveling.obj(root.get(slotKey(tree, selectedSlot(skillTree, tree)))) ?: Leveling.obj(root.get(tree))
    }

    @JvmStatic
    fun selectedTokensSpent(skillTree: JsonObject?, tree: String): Int? {
        val tokens = Leveling.obj(skillTree?.get("tokens_spent")) ?: return null
        val base = when (tree.lowercase()) {
            "mining" -> "mountain"
            "foraging" -> "forest"
            else -> tree
        }
        var key = slotKey(base, selectedSlot(skillTree, tree))
        if (!tokens.has(key)) key = base
        val n = if (tokens.has(key)) Leveling.num(tokens.get(key)) else null
        return n?.let { Math.round(it) }
    }
}
