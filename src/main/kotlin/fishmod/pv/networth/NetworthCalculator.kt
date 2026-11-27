/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.utils.TextStrip
import java.util.Locale

object NetworthCalculator {
    @JvmStatic
    fun calculate(member: JsonObject?, profileRoot: JsonObject?, museumMember: JsonObject?): NetworthBreakdown {
        if (member == null) return NetworthBreakdown.empty("No profile member")
        return calculate(
            member, profileRoot, museumMember,
            InventoryDecoder.parseCategories(member, museumMember), true, NetworthMode.SoulboundFilter.ALL,
        )
    }

    @JvmStatic
    fun calculate(
        member: JsonObject?,
        profileRoot: JsonObject?,
        museumMember: JsonObject?,
        categories: Map<String, List<InventoryDecoder.Stack>>?,
    ): NetworthBreakdown = calculate(member, profileRoot, museumMember, categories, true, NetworthMode.SoulboundFilter.ALL)

    @JvmStatic
    fun calculate(
        member: JsonObject?,
        profileRoot: JsonObject?,
        museumMember: JsonObject?,
        categories: Map<String, List<InventoryDecoder.Stack>>?,
        includeCosmetics: Boolean,
    ): NetworthBreakdown = calculate(member, profileRoot, museumMember, categories, includeCosmetics, NetworthMode.SoulboundFilter.ALL)

    @JvmStatic
    fun calculate(
        member: JsonObject?,
        profileRoot: JsonObject?,
        museumMember: JsonObject?,
        categories: Map<String, List<InventoryDecoder.Stack>>?,
        mode: NetworthMode?,
    ): NetworthBreakdown {
        val m = mode ?: NetworthMode.NORMAL
        return calculate(member, profileRoot, museumMember, categories, m.includeCosmetics, m.soulboundFilter)
    }

    @JvmStatic
    fun calculate(
        member: JsonObject?,
        profileRoot: JsonObject?,
        museumMember: JsonObject?,
        categories: Map<String, List<InventoryDecoder.Stack>>?,
        includeCosmetics: Boolean,
        filter: NetworthMode.SoulboundFilter?,
    ): NetworthBreakdown {
        NetworthData.ensureLoaded()
        if (member == null) return NetworthBreakdown.empty("No profile member")
        val soulboundFilter = filter ?: NetworthMode.SoulboundFilter.ALL
        val cats = categories ?: InventoryDecoder.parseCategories(member, museumMember)

        val lines = ArrayList<NetworthBreakdown.Line>()
        var total = 0.0

        for ((key, stacks) in cats) {
            if (key == "pets") continue
            val museumCategory = key == "museum"
            val sacksCategory = key == "sacks"
            var sum = 0.0
            val items = ArrayList<NetworthBreakdown.ItemLine>()
            for (stack in stacks) {
                if (!soulboundFilter.accepts(stack.soulbound || museumCategory)) continue
                val worth = ItemWorth.value(stack, includeCosmetics)
                if (worth <= 0) continue
                sum += worth
                items.add(NetworthBreakdown.ItemLine(
                    stack.id,
                    if (sacksCategory) prettyItem(stack.id) else itemLabel(stack),
                    maxOf(0L, stack.count.toLong()),
                    worth,
                ))
            }
            if (sum > 0) {
                items.sortByDescending { it.value }
                lines.add(NetworthBreakdown.Line(key, sum, items))
                total += sum
            }
        }

        val petItems = ArrayList<NetworthBreakdown.ItemLine>()
        val pets = petsValue(member, includeCosmetics, soulboundFilter, petItems)
        if (pets > 0) {
            petItems.sortByDescending { it.value }
            lines.add(NetworthBreakdown.Line("pets", pets, petItems))
            total += pets
        }

        if (soulboundFilter.includesLiquid()) {
            val purse = purse(member)
            val bank = bank(profileRoot, member)
            if (purse > 0) {
                lines.add(NetworthBreakdown.Line("purse", purse))
                total += purse
            }
            if (bank > 0) {
                lines.add(NetworthBreakdown.Line("bank", bank))
                total += bank
            }
        }

        val anyItems = cats.values.any { it.isNotEmpty() }
        val note = if (!anyItems && pets <= 0) "Inventory API may be off" else ""
        lines.sortByDescending { it.value }
        return NetworthBreakdown(total, lines, note)
    }

    private fun petsValue(
        member: JsonObject,
        includeCosmetics: Boolean,
        filter: NetworthMode.SoulboundFilter,
        items: MutableList<NetworthBreakdown.ItemLine>,
    ): Double {
        val pets: JsonArray = obj(member.get("pets_data"))?.get("pets")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: member.get("pets")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return 0.0
        var total = 0.0
        for (element in pets) {
            if (!element.isJsonObject) continue
            val pet = element.asJsonObject
            if (!filter.accepts(PetWorth.isSoulbound(pet))) continue
            val worth = PetWorth.value(pet, includeCosmetics)
            total += worth
            if (worth > 0) {
                val type = primString(pet, "type")
                val tier = primString(pet, "tier")
                val label = (if (tier.isBlank()) "" else prettyItem(tier) + " ") + prettyItem(type)
                items.add(NetworthBreakdown.ItemLine(type, label, 1L, worth))
            }
        }
        return total
    }

    private fun primString(obj: JsonObject, key: String): String {
        val value = obj.get(key)
        return if (value != null && value.isJsonPrimitive) value.asString else ""
    }

    private fun itemLabel(stack: InventoryDecoder.Stack): String {
        val name = stack.displayName
        if (!name.isNullOrBlank()) {
            val plain = TextStrip.plain(name).trim()
            if (plain.isNotBlank()) return plain
        }
        return prettyItem(stack.id)
    }

    @JvmStatic
    fun purse(member: JsonObject): Double {
        obj(member.get("currencies"))?.let { currencies ->
            num(currencies.get("coin_purse"))?.let { return it }
            num(currencies.get("purse"))?.let { return it }
        }
        return num(member.get("coin_purse")) ?: 0.0
    }

    @JvmStatic
    @JvmOverloads
    fun bank(profileRoot: JsonObject?, member: JsonObject? = null): Double {
        val coop = obj(profileRoot?.get("banking"))?.let { num(it.get("balance")) } ?: 0.0
        val personal = personalBank(member)
        if (coop <= 0) return personal
        if (personal <= 0) return coop
        return maxOf(coop, personal)
    }

    private fun personalBank(member: JsonObject?): Double =
        obj(member?.get("profile"))?.let { num(it.get("bank_account")) } ?: 0.0

    private fun obj(element: JsonElement?): JsonObject? =
        if (element != null && element.isJsonObject) element.asJsonObject else null

    private fun num(element: JsonElement?): Double? {
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        return element.asDouble
    }

    private fun prettyItem(id: String?): String {
        if (id.isNullOrBlank()) return "?"
        return InventoryDecodeSupport.prettyWords(id)
    }
}
