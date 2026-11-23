package fishmod.features.chat

import fishmod.features.extras.*

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import fishmod.features.extras.ExtrasSettings
import fishmod.features.extras.PartyCommandHint
import fishmod.features.party.PartyCommandHandler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.NeuRepo
import fishmod.utils.networth.ItemsDb
import fishmod.features.dungeon.trackers.CroesusPrices
import net.minecraft.client.Minecraft
import java.util.Locale

object TabComplete {
    private val WARPS = listOf("hub", "castle", "wizard", "crypt", "stonks", "da", "taylor", "museum")
    private val PLAYER_CMDS = setOf("p", "party", "pv", "msg", "w", "tell", "visit", "invite", "trade", "ah", "f", "friend", "g")
    private val PARTY_SUBS = setOf("kick", "transfer", "promote", "demote", "invite")
    private val NAME = Regex("^[A-Za-z0-9_]{3,16}$")
    private const val MAX = 60
    private val BZ_CMDS = setOf("bz", "bazaar")
    private val AHS_CMDS = setOf("ahs", "ahsearch")

    private val FLOORS = listOf("e") + (1..7).map { "f$it" } + (1..7).map { "m$it" }
    private val WS = Regex("\\s+")

    @JvmStatic
    fun partySuggest(text: String): Suggestions? {
        if (!FishSettings.partyCommandsEnabled || !FishSettings.pcArgHints) return null
        val m = PartyCommandHint.PREFIX.find(text) ?: return null
        val base = m.range.last + 1
        val rest = text.substring(base)
        val sp = rest.indexOf(' ')
        val start: Int
        val pool: List<String>
        if (sp < 0) {
            start = base
            pool = PartyCommandHandler.USAGE.keys.toList()
        } else {
            val cmd = rest.substring(0, sp)
            val usage = PartyCommandHandler.USAGE[cmd] ?: return null
            start = text.lastIndexOf(' ') + 1
            val done = text.substring(base + sp + 1, start).trim().split(WS).filter { it.isNotEmpty() }
            val slot = if (done.isEmpty()) 0 else done.size - 1 + PartyCommandHandler.hintSlotsForFirstArg(cmd, done[0])
            if (slot >= usage.size) return null
            pool = argOptions(usage[slot]) + if (usage[slot].contains("empty for yourself") && slot + 1 < usage.size) argOptions(usage[slot + 1]) else emptyList()
        }
        val frag = text.substring(start).lowercase(Locale.ROOT)
        val hits = pool.asSequence().filter { it.lowercase(Locale.ROOT).startsWith(frag) }.distinct().sorted().take(MAX).toList()
        if (hits.isEmpty()) return null
        val b = SuggestionsBuilder(text, start)
        hits.forEach { b.suggest(it) }
        return b.build()
    }

    private fun argOptions(key: String): List<String> = when {
        key.startsWith("<player") -> players()
        key.startsWith("<floor") -> FLOORS
        key.contains('|') -> key.removeSurrounding("<", ">").split('|')
        else -> emptyList()
    }

    @JvmStatic
    fun suggest(text: String): Suggestions? {
        if (!ExtrasSettings.tabComplete || !text.startsWith("/")) return null
        val start = text.lastIndexOf(' ') + 1
        if (start == 0) return null
        itemNames(text)?.let { return it }
        val args = text.substring(1, start).trim().split(' ').filter { it.isNotEmpty() }
        val cmd = args.firstOrNull()?.lowercase(Locale.ROOT) ?: return null
        val frag = text.substring(start).lowercase(Locale.ROOT)
        val pool: List<String> = when {
            cmd == "warp" && args.size == 1 -> WARPS
            cmd == "viewrecipe" && args.size == 1 -> if (NeuRepo.isLoaded()) NeuRepo.ids().toList() else emptyList()
            (cmd == "p" || cmd == "party") && args.size == 2 && args[1].lowercase(Locale.ROOT) in PARTY_SUBS -> players()
            cmd in PLAYER_CMDS && args.size == 1 -> players()
            else -> return null
        }
        val hits = pool.asSequence().filter { it.lowercase(Locale.ROOT).startsWith(frag) }.sorted().take(MAX).toList()
        if (hits.isEmpty()) return null
        val b = SuggestionsBuilder(text, start)
        hits.forEach { b.suggest(it) }
        return b.build()
    }

    private fun itemNames(text: String): Suggestions? {
        val sp = text.indexOf(' ')
        val cmd = text.substring(1, sp).lowercase(Locale.ROOT)
        val bz = cmd in BZ_CMDS
        if (!bz && cmd !in AHS_CMDS) return null
        if (!ItemsDb.isLoaded()) return null
        val frag = text.substring(sp + 1).lowercase(Locale.ROOT)
        val hits = ItemsDb.names().asSequence()
            .filter { it.lowercase(Locale.ROOT).startsWith(frag) }
            .filter { n -> !bz || ItemsDb.idFor(n)?.let { CroesusPrices.bazaarPrice(it, true) } != null }
            .distinct().sorted().take(MAX).toList()
        if (hits.isEmpty()) return null
        val b = SuggestionsBuilder(text, sp + 1)
        hits.forEach { b.suggest(it) }
        return b.build()
    }

    private fun players(): List<String> {
        val mc = Minecraft.getInstance()
        val self = mc.player?.gameProfile?.name()
        return mc.connection?.onlinePlayers.orEmpty().mapNotNull { it.profile.name() }
            .filter { NAME.matches(it) && it != self }.distinct()
    }
}
