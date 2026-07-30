package fishmod.features

import fishmod.utils.HypixelApi
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Crowd-sourced player reputation — a community "vouch / shitter list". Any player can be tagged by
 * UUID (they don't need the mod): `/vouch` for a good carry, `/shitter` to flag a
 * ditcher/scammer, `/unrep` to clear your tag. `/rep <player>` shows the aggregate, and
 * `/rep` with no name scans your current lobby and lists anyone who's been flagged.
 *
 * One vote per voter per target (re-voting overwrites), so counts can't be stuffed. Backed by the
 * worker /rep route (worker-reputation-snippet.js); commands report cleanly if it isn't deployed.
 */
object Reputation {

    // Lowercased IGNs of flagged (net-negative) players currently in the lobby, refreshed by a poll.
    private val flaggedIgns: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private const val POLL_TICKS = 20 * 30 // ~30s between flag refreshes
    private var pollTick = 0

    /** Registers the background poll that keeps the in-lobby flagged set fresh (for the tab ✗ marker). */
    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.repFlagsEnabled) {
                if (flaggedIgns.isNotEmpty()) flaggedIgns.clear()
                return@register
            }
            if (++pollTick < POLL_TICKS) return@register
            pollTick = 0
            pollFlags(mc)
        }
    }

    private fun pollFlags(mc: Minecraft) {
        val connection = mc.getConnection() ?: return
        val uuidToName = HashMap<String, String>()
        for (e in connection.getOnlinePlayers()) {
            val gp = e.getProfile()
            if (gp == null || gp.id() == null || gp.name() == null || gp.name().isBlank()) continue
            uuidToName[gp.id().toString().replace("-", "")] = gp.name()
        }
        if (uuidToName.isEmpty()) return
        HypixelApi.fetchReps(uuidToName.keys) { reps ->
            mc.execute {
                val next = HashSet<String>()
                for (entry in reps.entries) {
                    val rd: HypixelApi.RepData = entry.value
                    if (rd.down() > rd.up()) {
                        val name = uuidToName[entry.key]
                        if (name != null) next.add(name.lowercase())
                    }
                }
                flaggedIgns.clear()
                flaggedIgns.addAll(next)
            }
        }
    }

    /**
     * Appends a red ✘ to any flagged player's name found in an on-screen text (tab list / scoreboard).
     * Cheap: the flagged set is usually empty and only ever holds players in your current lobby.
     */
    @JvmStatic
    fun decorateTab(text: Component?): Component? {
        if (!FishSettings.repFlagsEnabled || text == null || flaggedIgns.isEmpty()) return text
        val s = text.getString()
        if (s.isEmpty() || s.endsWith("✘")) return text
        val lower = s.lowercase()
        for (name in flaggedIgns) {
            if (lower.contains(name)) {
                val out = text.copy()
                out.append(Component.literal(" §c✘"))
                return out
            }
        }
        return text
    }

    /** Resolve an IGN to a dashless UUID (cache first, then Mojang), then run `cb` (null on miss). */
    private fun withUuid(name: String, cb: (String?) -> Unit) {
        val cached = HypixelApi.getCachedUuid(name)
        if (cached != null) {
            cb(cached.replace("-", ""))
            return
        }
        HypixelApi.resolveUuidAsync(name) { uuid -> cb(uuid?.replace("-", "")) }
    }

    /** Cast (or clear) the local player's vote on `name`. dir ∈ "up" | "down" | "none". */
    @JvmStatic
    fun vote(name: String, dir: String) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val voter = player.getUUID().toString().replace("-", "")
        Misc.addChatMessage(Component.literal("§7[Rep] resolving §f$name§7…"))
        withUuid(name) { uuid ->
            if (uuid == null) {
                mc.execute { Misc.addChatMessage(Component.literal("§c[Rep] couldn't find player §f$name")) }
                return@withUuid
            }
            HypixelApi.voteRep(voter, uuid, name, dir) { up, down ->
                mc.execute {
                    if (up < 0) {
                        Misc.addChatMessage(Component.literal("§c[Rep] vote failed — worker route not reachable."))
                        return@execute
                    }
                    val v = if (dir == "up") "§avouched" else if (dir == "down") "§cflagged" else "§7cleared vote on"
                    Misc.addChatMessage(Component.literal("§7[Rep] $v §f$name §8— ${counts(up, down)}"))
                }
            }
        }
    }

    /** Print the aggregate reputation for a single player. */
    @JvmStatic
    fun lookup(name: String) {
        val mc = Minecraft.getInstance()
        Misc.addChatMessage(Component.literal("§7[Rep] looking up §f$name§7…"))
        withUuid(name) { uuid ->
            if (uuid == null) {
                mc.execute { Misc.addChatMessage(Component.literal("§c[Rep] couldn't find player §f$name")) }
                return@withUuid
            }
            HypixelApi.fetchReps(setOf(uuid)) { reps ->
                mc.execute {
                    val rd = reps[uuid]
                    if (rd == null || (rd.up() == 0 && rd.down() == 0)) {
                        Misc.addChatMessage(Component.literal("§7[Rep] §f$name §7has no reputation yet."))
                    } else {
                        Misc.addChatMessage(Component.literal("§7[Rep] §f$name §8— ${counts(rd.up(), rd.down())} ${verdict(rd)}"))
                    }
                }
            }
        }
    }

    /** Scan the current lobby's tab list and list any flagged (net-negative) players. */
    @JvmStatic
    fun listNearby() {
        val mc = Minecraft.getInstance()
        val connection = mc.getConnection()
        if (connection == null) {
            Misc.addChatMessage(Component.literal("§c[Rep] Not in a world."))
            return
        }
        val uuidToName = HashMap<String, String>()
        for (e in connection.getOnlinePlayers()) {
            val gp = e.getProfile()
            if (gp == null || gp.id() == null || gp.name() == null || gp.name().isBlank()) continue
            uuidToName[gp.id().toString().replace("-", "")] = gp.name()
        }
        if (uuidToName.isEmpty()) {
            Misc.addChatMessage(Component.literal("§7[Rep] No players found in tab."))
            return
        }
        Misc.addChatMessage(Component.literal("§7[Rep] scanning §f${uuidToName.size} §7players…"))
        HypixelApi.fetchReps(uuidToName.keys) { reps ->
            mc.execute {
                val flagged = ArrayList<String>()
                for (entry in reps.entries) {
                    val rd: HypixelApi.RepData = entry.value
                    if (rd.down() > rd.up()) {
                        val who = uuidToName.getOrDefault(entry.key, rd.name())
                        flagged.add("§f$who §8(${counts(rd.up(), rd.down())}§8)" + (if (isShitter(rd)) " §4§lSHITTER" else ""))
                    }
                }
                if (flagged.isEmpty()) {
                    Misc.addChatMessage(Component.literal("§a[Rep] No flagged players in this lobby."))
                    return@execute
                }
                Misc.addChatMessage(Component.literal("§c[Rep] §l${flagged.size} flagged player(s) here:"))
                for (line in flagged) Misc.addChatMessage(Component.literal("  $line"))
            }
        }
    }

    private fun counts(up: Int, down: Int): String = "§a+$up §c-$down"

    private fun isShitter(rd: HypixelApi.RepData): Boolean = rd.down() >= 3 && rd.down() > rd.up() * 2

    private fun verdict(rd: HypixelApi.RepData): String {
        if (isShitter(rd)) return "§4§lSHITTER"
        if (rd.up() - rd.down() >= 3) return "§2§ltrusted"
        return ""
    }
}
