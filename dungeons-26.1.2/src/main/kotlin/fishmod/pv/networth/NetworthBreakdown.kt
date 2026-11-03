/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.price.ItemPricer
import java.util.Locale

class NetworthBreakdown(total: Double, categories: List<Line>, note: String?) {
    @get:JvmName("total")
    val total: Double = total
    @get:JvmName("categories")
    val categories: List<Line> = categories.toList()
    @get:JvmName("note")
    val note: String = note ?: ""

    data class ItemLine(
        @get:JvmName("id") val id: String?,
        @get:JvmName("label") val label: String,
        @get:JvmName("count") val count: Long,
        @get:JvmName("value") val value: Double,
    )

    class Line @JvmOverloads constructor(
        @get:JvmName("name") val name: String,
        @get:JvmName("value") val value: Double,
        items: List<ItemLine>? = emptyList(),
    ) {
        @get:JvmName("items")
        val items: List<ItemLine> = items?.toList() ?: emptyList()
    }

    /** Same breakdown with another headline total (FishMod networth). */
    fun withTotal(total: Double): NetworthBreakdown = NetworthBreakdown(total, categories, note)

    fun tooltipLines(): List<String> = tooltipStyledLines().map { line -> line.spans().joinToString("") { it.text } }

    /** [showItems] adds per-item rows for every category (Home passes left shift). */
    @JvmOverloads
    fun tooltipStyledLines(mode: NetworthMode? = null, showItems: Boolean = false): List<PvTooltip.Line> {
        val lines = ArrayList<PvTooltip.Line>()
        lines.add(PvTooltip.Line(listOf(
            PvTooltip.Span.of("Networth: ", PvDraw.COLOR_WHITE),
            PvTooltip.Span.bold(FormatUtil.commas(Math.round(total)), PvDraw.COLOR_GOLD),
        )))
        if (mode != null) lines.add(PvTooltip.Line.of(modeLine(mode), PvDraw.COLOR_MUTED))
        lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED))
        var anyItems = false
        for (line in categories) {
            if (line.value <= 0) continue
            lines.add(PvTooltip.Line(listOf(
                PvTooltip.Span.of(title(line.name) + ": ", PvDraw.COLOR_WHITE),
                PvTooltip.Span.of(FormatUtil.commas(Math.round(line.value)), PvDraw.COLOR_GOLD),
                PvTooltip.Span.of(" (" + share(line.value) + ")", PvDraw.COLOR_MUTED),
            )))
            val items = line.items
            if (items.isEmpty()) continue
            anyItems = true
            if (!showItems) continue
            val shown = minOf(MAX_ITEM_ROWS, items.size)
            for (item in items.subList(0, shown)) {
                val countBit = if (item.count > 1L) " ×" + FormatUtil.commas(item.count) else ""
                lines.add(PvTooltip.Line(listOf(
                    PvTooltip.Span.of("  " + item.label + countBit + ": ", PvDraw.COLOR_MUTED),
                    PvTooltip.Span.of(FormatUtil.shortCoins(Math.round(item.value).toDouble()), PvDraw.COLOR_GOLD),
                )))
            }
            if (items.size > shown) {
                var rest = 0.0
                for (i in shown until items.size) rest += items[i].value
                lines.add(PvTooltip.Line.of(
                    "  +" + (items.size - shown) + " more items: " + FormatUtil.shortCoins(Math.round(rest).toDouble()),
                    PvDraw.COLOR_MUTED,
                ))
            }
        }
        if (anyItems && !showItems) lines.add(PvTooltip.Line.of("Hold L-shift for item values", PvDraw.COLOR_MUTED))
        if (note.isNotBlank()) lines.add(PvTooltip.Line.of(note, PvDraw.COLOR_MUTED))
        lines.add(PvTooltip.Line.of("Prices: Athen, SkyHelper fallback", PvDraw.COLOR_MUTED))
        lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED))
        lines.add(profileValueLine())
        lines.add(PvTooltip.Line.of("(Price estimated with cookies)", PvDraw.COLOR_MUTED))
        lines.add(PvTooltip.Line.of("I do not condone IRL Trading", PvDraw.COLOR_MUTED))
        if (mode != null) {
            lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED))
            val cosmetic = mode.includeCosmetics
            val unsoulbound = mode.soulboundFilter == NetworthMode.SoulboundFilter.ONLY_UNSOULBOUND
            lines.add(PvTooltip.Line.of("Left click: " + if (cosmetic) "non-cosmetic" else "cosmetic", PvDraw.COLOR_MUTED))
            lines.add(PvTooltip.Line.of("Right click: " + if (unsoulbound) "all items" else "unsoulbound only", PvDraw.COLOR_MUTED))
        }
        return lines
    }

    /** NEU-style IRL estimate: networth → booster cookies (bazaar) → gems (325/cookie) → USD via the 675-gem / $4.99 pack. */
    private fun profileValueLine(): PvTooltip.Line {
        val usd = profileValueUsd(total)
            ?: return PvTooltip.Line(listOf(
                PvTooltip.Span.of("Profile Value: ", PvDraw.COLOR_MUTED),
                PvTooltip.Span.of("-", 0xFFFF5555.toInt()),
            ))
        return PvTooltip.Line(listOf(
            PvTooltip.Span.of("Profile Value: ", PvDraw.COLOR_WHITE),
            PvTooltip.Span.bold("$" + FormatUtil.commas(Math.round(usd)), 0xFF55FF55.toInt()),
        ))
    }

    private fun share(value: Double): String = if (total <= 0) "0%" else FormatUtil.percent(value / total)

    companion object {
        /** SkyBlock gem shop: gems per booster cookie. */
        private const val GEMS_PER_COOKIE = 325.0

        /** Hypixel store pack: gems for $4.99. */
        private const val GEMS_PER_PACK = 675.0
        private const val USD_PER_PACK = 4.99
        private const val MAX_ITEM_ROWS = 12

        @JvmStatic
        fun empty(note: String?): NetworthBreakdown = NetworthBreakdown(0.0, emptyList(), note)

        @JvmStatic
        fun modeLine(mode: NetworthMode): String {
            val cosmetics = if (mode.includeCosmetics) "cosmetics included" else "cosmetics excluded"
            val soulbound = if (mode.soulboundFilter == NetworthMode.SoulboundFilter.ONLY_UNSOULBOUND) "soulbound excluded" else "soulbound included"
            return "Mode: " + mode.display + " (" + cosmetics + ", " + soulbound + ")"
        }

        @JvmStatic
        fun profileValueUsd(networthCoins: Double): Double? {
            if (networthCoins <= 0) return 0.0
            val cookiePrice = ItemPricer.price("BOOSTER_COOKIE")
            if (cookiePrice <= 0) return null
            val cookies = networthCoins / cookiePrice
            return (cookies * GEMS_PER_COOKIE / GEMS_PER_PACK) * USD_PER_PACK
        }

        private fun title(id: String): String {
            if (id.equals("storage", ignoreCase = true)) return "Backpacks"
            if (id.equals("sacks_bag", ignoreCase = true)) return "Sacks Bag"
            return id.split("_").filter { it.isNotBlank() }.joinToString(" ") { part ->
                part[0].uppercaseChar() + part.substring(1).lowercase(Locale.ROOT)
            }
        }
    }
}
