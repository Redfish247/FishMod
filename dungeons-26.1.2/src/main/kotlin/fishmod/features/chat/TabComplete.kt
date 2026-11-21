package fishmod.features.chat

import fishmod.features.extras.*

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import fishmod.features.extras.ExtrasSettings
import fishmod.utils.data.NeuRepo
import fishmod.utils.networth.ItemsDb
import fishmod.features.dungeon.trackers.CroesusPrices
import net.minecraft.client.Minecraft
import java.util.Locale

// Client-side Tab suggestions for Hypixel commands the server doesn't complete
object TabComplete {
    // Only warps already in the repo (DianaWarp hub warps)
    private val WARPS = listOf("hub", "castle", "wizard", "crypt", "stonks", "da", "taylor", "museum")
    private val PLAYER_CMDS = setOf("p", "party", "pv", "msg", "w", "tell", "visit", "invite", "trade", "ah", "f", "friend", "g")
    private val PARTY_SUBS = setOf("kick", "transfer", "promote", "demote", "invite")
    private val NAME = Regex("^[A-Za-z0-9_]{3,16}$")
    private const val MAX = 60
    private val BZ_CMDS = setOf("bz", "bazaar")
    private val AHS_CMDS = setOf("ahs", "ahsearch")

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

    // /bz and /ahs take a multi-word item name, so the fragment is everything after the command
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

    // Tab-list players in this lobby (party mates, nearby players), minus yourself and NPC entries
    private fun players(): List<String> {
        val mc = Minecraft.getInstance()
        val self = mc.player?.gameProfile?.name()
        return mc.connection?.onlinePlayers.orEmpty().mapNotNull { it.profile.name() }
            .filter { NAME.matches(it) && it != self }.distinct()
    }
}
