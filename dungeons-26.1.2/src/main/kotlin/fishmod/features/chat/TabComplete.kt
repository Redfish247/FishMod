package fishmod.features.chat

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import fishmod.features.extras.ExtrasSettings
import fishmod.utils.data.NeuRepo
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

    @JvmStatic
    fun suggest(text: String): Suggestions? {
        if (!ExtrasSettings.tabComplete || !text.startsWith("/")) return null
        val start = text.lastIndexOf(' ') + 1
        if (start == 0) return null
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

    // Tab-list players in this lobby (party mates, nearby players), minus yourself and NPC entries
    private fun players(): List<String> {
        val mc = Minecraft.getInstance()
        val self = mc.player?.gameProfile?.name()
        return mc.connection?.onlinePlayers.orEmpty().mapNotNull { it.profile.name() }
            .filter { NAME.matches(it) && it != self }.distinct()
    }
}
