package fishmod.features.dungeon

import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Handles party commands typed by the local player:
 *   .ai / .allinv  — /p settings allinvite
 *   .pb            — fetch M7 PB from Hypixel API and send to party chat
 *   .cata          — send cata level (requires API key)
 *   .rtca          — send runs-to-class-50 (requires API key)
 *   .powder        — fetch mithril/gemstone/glacite powder (requires API key via proxy)
 *   .e             — /joininstance catacombs_entrance
 *   .f1-.f7        — /joininstance catacombs_floor_X
 *   .m1-.m7        — /joininstance master_catacombs_floor_X
 */
object PartyCommandHandler {

    private val NUM_WORDS = arrayOf("one", "two", "three", "four", "five", "six", "seven")

    private val KUUDRA_TIERS = arrayOf("normal", "hot", "burning", "fiery", "infernal")

    private var dungeonEnteredAt: Long = 0

    // TPS tracking — rolling average of last 20 client tick intervals
    private val TICK_TIMES = LongArray(20)
    private var tickIdx = 0
    private var lastTickMs: Long = -1

    @JvmStatic
    fun init() {
        // When the local player sends a chat message starting with a command prefix,
        // pre-arm the suppression window so Hypixel's "Unknown party command." reply
        // (which can race ahead of the party echo) is hidden.
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            val t = message.trim()
            if (t.startsWith(".") || t.startsWith("!")) {
                ChatCommandState.lastPartyCommandAt = System.currentTimeMillis()
            }
            true
        }

        // Track when the player enters a dungeon or Kuudra (for 30s joininstance guard)
        Events.ON_LOCATION_CHANGE.register { loc ->
            if (loc == Location.DUNGEON || loc == Location.KUUDRA) dungeonEnteredAt = System.currentTimeMillis()
            false
        }

        // Server tick timing for real TPS (CommonPingS2CPacket fires once per server tick)
        Events.ON_SERVER_TICK.register {
            val now = System.currentTimeMillis()
            if (lastTickMs > 0) {
                TICK_TIMES[tickIdx % TICK_TIMES.size] = now - lastTickMs
                tickIdx++
            }
            lastTickMs = now
            false
        }
    }

    /** Returns true if s looks like a floor specifier: m1-m7, f1-f7, or e. */
    private fun isFloor(s: String?): Boolean {
        if (s == null) return false
        val l = s.lowercase()
        return l == "e" || l.matches(Regex("[fm][1-7]"))
    }

    /**
     * Called from ChatHudMixin for every party command message.
     * typer   = who typed it
     * cmd     = the command keyword
     * rawArg1 = first word after the command (may be an IGN, a floor, or null)
     * rawArg2 = second word after the command (may be a floor or null)
     *
     * For .runs the args are parsed smartly:
     *   .runs            → ign=typer,   floor=m7 (default)
     *   .runs m7         → ign=typer,   floor=m7
     *   .runs PlayerName → ign=Player,  floor=m7 (default)
     *   .runs Player m7  → ign=Player,  floor=m7
     */
    /** Back-compat overload — defaults responder to party chat ("pc "). */
    @JvmStatic
    @JvmOverloads
    fun onPartyCommand(typer: String, cmd: String, rawArg1: String?, rawArg2: String?, rawArg3: String? = null, responder: String? = "pc ") {
        val mc = MinecraftClient.getInstance()
        if (mc.networkHandler == null) return
        // Use the real account name (GameProfile), NOT getName() — a cosmetic /nick overrides
        // getName() and would break the isMe check for self-only commands (.ping/.fps/.corpse...).
        val selfName = mc.player?.gameProfile?.name()
        val isMe = selfName != null && typer.equals(selfName, ignoreCase = true)
        // Local /command lookups bypass the party-dedup so they always respond immediately.
        val isLocal = LOCAL == responder

        // Default target for non-runs commands: explicit arg or fall back to typer
        val ign = rawArg1 ?: typer

        when (cmd) {
            "help", "?" -> { if (FishSettings.pcHelp && respond(cmd, typer, isLocal)) sendCmd(mc, responder, buildHelp()) }
            // Stats lookups: respond to ANY party member's command (default target = typer if no arg)
            "rtca" -> { if (FishSettings.pcRtca && respond(cmd, typer, isLocal)) runRtcaForPlayer(mc, ign, responder) }
            "rtc" -> {
                if (FishSettings.pcRtc && respond(cmd, typer, isLocal)) {
                    val rtcIgn: String
                    val levelArg: String?
                    if (rawArg1 != null && rawArg1.matches(Regex("\\d+"))) { rtcIgn = typer; levelArg = rawArg1 }
                    else { rtcIgn = rawArg1 ?: typer; levelArg = rawArg2 }
                    runRtcForPlayer(mc, rtcIgn, levelArg, responder)
                }
            }
            "crtc" -> {
                if (FishSettings.pcCrtc && respond(cmd, typer, isLocal)) {
                    // .crtc [name] <class> [level] — smart-parse: if arg1 is a class, name defaults to typer.
                    val cIgn: String
                    val cClass: String?
                    val cLevel: String?
                    if (resolveClass(rawArg1) != null) { cIgn = typer; cClass = rawArg1; cLevel = rawArg2 }
                    else { cIgn = rawArg1 ?: typer; cClass = rawArg2; cLevel = rawArg3 }
                    runCrtcForPlayer(mc, cIgn, cClass, cLevel, responder)
                }
            }
            "cata" -> { if (FishSettings.pcCata && respond(cmd, typer, isLocal)) runCataForPlayer(mc, ign, responder) }
            "pb" -> {
                if (!FishSettings.pcPb || !respond(cmd, typer, isLocal)) return
                val pbIgn: String
                val pbFloor: String?
                if (isFloor(rawArg1)) {
                    pbIgn = typer
                    pbFloor = rawArg1
                } else {
                    pbIgn = rawArg1 ?: typer
                    pbFloor = rawArg2
                }
                runPbForPlayer(mc, pbIgn, pbFloor, responder)
            }
            "mp" -> { if (FishSettings.pcMp && respond(cmd, typer, isLocal)) runMpForPlayer(mc, rawArg1 ?: typer, responder) }
            "collection" -> {
                if (!FishSettings.pcCollection || !respond(cmd, typer, isLocal)) return
                val colIgn: String
                val colFloor: String?
                if (isFloor(rawArg1)) { colIgn = typer; colFloor = rawArg1 }
                else { colIgn = rawArg1 ?: typer; colFloor = rawArg2 }
                runCollectionForPlayer(mc, colIgn, colFloor, responder)
            }
            "secrets", "sa" -> { if (FishSettings.pcSecrets && respond(cmd, typer, isLocal)) runStatsForPlayer(mc, ign, cmd, null, responder) }
            "runs" -> {
                if (!FishSettings.pcRuns || !respond(cmd, typer, isLocal)) return
                val runsIgn: String
                val floor: String?
                if (isFloor(rawArg1)) {
                    runsIgn = typer
                    floor = rawArg1
                } else {
                    runsIgn = rawArg1 ?: typer
                    floor = rawArg2
                }
                runStatsForPlayer(mc, runsIgn, cmd, floor, responder)
            }
            "totalruns" -> { if (FishSettings.pcRuns && respond(cmd, typer, isLocal)) runTotalRunsForPlayer(mc, ign, responder) }
            // Self-only metrics: only the typer's own mod responds (data is local to each player)
            "dprofit" -> { if (FishSettings.pcDprofit && isMe) sendDprofit(mc, responder) }
            "corpse", "corpses" -> { if (FishSettings.pcCorpse && respond(cmd, typer, isLocal)) sendCorpse(mc, ign, responder) }
            "bank" -> { if (FishSettings.pcBank && respond(cmd, typer, isLocal)) sendBank(mc, ign, responder) }
            "powder" -> { if (FishSettings.pcPowder && respond(cmd, typer, isLocal)) sendPowder(mc, ign, responder) }
            "nw", "networth" -> { if (FishSettings.pcNw && respond(cmd, typer, isLocal)) sendNetworth(mc, ign, responder) }
            "level", "sblvl" -> { if (FishSettings.pcLevel && respond(cmd, typer, isLocal)) sendSkyblockLevel(mc, ign, responder) }
            "farming" -> { if (FishSettings.pcFarming && respond(cmd, typer, isLocal)) sendFarming(mc, ign, responder) }
            "nuc", "nucleus" -> { if (FishSettings.pcNuc && respond(cmd, typer, isLocal)) sendNucleus(mc, ign, responder) }
            "worm", "scatha" -> { if (FishSettings.pcWorm && respond(cmd, typer, isLocal)) sendWorm(mc, ign, responder) }
            "fps" -> { if (FishSettings.pcFps && isMe) sendFps(mc, responder) }
            "tps" -> { if (FishSettings.pcTps && isMe) sendTps(mc, responder) }
            "ping" -> { if (FishSettings.pcPing && isMe) sendPing(mc, responder) }
            "ai", "allinv" -> { if (FishSettings.pcAllinvite && isMe) sendRawCommand(mc, "p settings allinvite") }
            "d" -> { if (FishSettings.pcDisband && isMe) sendRawCommand(mc, "p disband") }
            // Party actions: only honor from party chat or local /command (never from DM/guild/officer/all chat,
            // where someone saying ".warp" would otherwise make our client try `/p warp` and error out).
            "kick" -> { if (FishSettings.pcActionKick && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendRawCommand(mc, "p kick $rawArg1") }
            "warp", "w" -> { if (FishSettings.pcActionWarp && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe)) sendRawCommand(mc, "p warp") }
            "transfer", "pt", "ptme" -> { if (FishSettings.pcActionTransfer && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe)) sendRawCommand(mc, "p transfer $ign") }
            "promote" -> { if (FishSettings.pcActionPromote && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendRawCommand(mc, "p promote $rawArg1") }
            "demote" -> { if (FishSettings.pcActionDemote && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendRawCommand(mc, "p demote $rawArg1") }
            else -> {
                if ((cmd.matches(Regex("[fm][1-7]")) || cmd == "e") && FishSettings.pcJoinFloor && allowPartyAction(typer, isMe)) handleJoinInstance(cmd, mc, responder)
                else if (cmd.matches(Regex("t[1-5]")) && FishSettings.pcJoinFloor && allowPartyAction(typer, isMe)) handleKuudra(cmd, mc, responder)
            }
        }
    }

    // Per-command dedup so multiple FishMod-running party members don't all spam the same response.
    // Each (cmd|typer) pair can only fire once per 5 seconds across the whole party.
    private val RECENT_RESPONSES = ConcurrentHashMap<String, Long>()
    private const val RESPONSE_DEDUP_MS = 5000L

    /** Local /command lookups always respond; party/guild echoes go through the dedup. */
    private fun respond(cmd: String, typer: String, isLocal: Boolean): Boolean {
        return isLocal || shouldRespond(cmd, typer)
    }

    private fun shouldRespond(cmd: String, typer: String): Boolean {
        val now = System.currentTimeMillis()
        val key = cmd + "|" + typer.lowercase()
        val last = RECENT_RESPONSES[key]
        if (last != null && now - last < RESPONSE_DEDUP_MS) return false
        RECENT_RESPONSES[key] = now
        // Light GC: drop entries older than 30s
        RECENT_RESPONSES.entries.removeIf { now - it.value > 30_000 }
        return true
    }

    /** Party actions (kick/warp/transfer/promote/demote) only run from party chat or local /command. */
    private fun partyActionAllowed(responder: String?, isLocal: Boolean): Boolean {
        return isLocal || (responder != null && responder.startsWith("pc "))
    }

    /**
     * Who besides yourself may trigger a party action (kick/warp/promote/demote/transfer) or a
     * floor/Kuudra join (.e/.f1-7/.m1-7/.t1-5), per FishSettings.pcPartyActionsMode:
     * "off"/"self" (nobody else), "whitelist" (listed names only), "blacklist" (anyone not listed),
     * "everyone" (any party member). You can always trigger your own actions.
     */
    private fun allowPartyAction(typer: String, isMe: Boolean): Boolean {
        if (isMe) return true
        return when (FishSettings.pcPartyActionsMode) {
            "everyone" -> true
            "blacklist" -> !fishmod.utils.NameList.contains(FishSettings.pcPartyActionsBlacklist, typer)
            "whitelist" -> fishmod.utils.NameList.contains(FishSettings.pcPartyActionsWhitelist, typer) &&
                    !fishmod.utils.NameList.contains(FishSettings.pcPartyActionsBlacklist, typer)
            else -> false // "off"/"self"/unrecognised
        }
    }

    /** Builds a list of currently-enabled dot-commands for .help / .?. */
    private fun buildHelp(): String {
        val cmds = ArrayList<String>()
        if (FishSettings.pcPb) cmds.add("pb")
        if (FishSettings.pcCata) cmds.add("cata")
        if (FishSettings.pcRtca) cmds.add("rtca")
        if (FishSettings.pcRtc) cmds.add("rtc")
        if (FishSettings.pcCrtc) cmds.add("crtc")
        if (FishSettings.pcSecrets) cmds.add("secrets/sa")
        if (FishSettings.pcRuns) cmds.add("runs/totalruns")
        if (FishSettings.pcCollection) cmds.add("collection")
        if (FishSettings.pcMp) cmds.add("mp")
        if (FishSettings.pcNw) cmds.add("nw")
        if (FishSettings.pcLevel) cmds.add("level")
        if (FishSettings.pcFarming) cmds.add("farming")
        if (FishSettings.pcNuc) cmds.add("nuc")
        if (FishSettings.pcWorm) cmds.add("worm") // .scatha is a hidden alias
        if (FishSettings.pcBank) cmds.add("bank")
        if (FishSettings.pcPowder) cmds.add("powder")
        if (FishSettings.pcCorpse) cmds.add("corpses")
        if (FishSettings.pcDprofit) cmds.add("dprofit")
        if (FishSettings.pcFps) cmds.add("fps")
        if (FishSettings.pcTps) cmds.add("tps")
        if (FishSettings.pcPing) cmds.add("ping")
        if (FishSettings.pcAllinvite) cmds.add("ai")
        if (FishSettings.pcJoinFloor) cmds.add("e/f1-7/m1-7/t1-5")
        if (FishSettings.pcActionKick) cmds.add("kick")
        if (FishSettings.pcActionWarp) cmds.add("warp/w")
        if (FishSettings.pcActionTransfer) cmds.add("transfer/pt/ptme")
        if (FishSettings.pcActionPromote) cmds.add("promote")
        if (FishSettings.pcActionDemote) cmds.add("demote")
        if (FishSettings.pcDisband) cmds.add("d")
        return "FishMod cmds: ." + cmds.joinToString(" .")
    }

    /**
     * Sends a reply to a lookup. Local /command dispatch (responder == LOCAL) just prints the text
     * to your own chat; party/guild/officer/all/DM dispatch relays "<responder><text>" to the server
     * as a real chat command so the rest of the channel sees it.
     */
    private fun sendCmd(mc: MinecraftClient, responder: String?, text: String) {
        if (LOCAL == responder) {
            mc.execute { fishmod.utils.FishMsg.send("§f$text") }
            return
        }
        sendRawCommand(mc, responder + text)
    }

    /** Sends a command to the server unconditionally (party actions, joininstance, etc.), after a short delay to avoid rate-limiting. */
    private fun sendRawCommand(mc: MinecraftClient, command: String) {
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
            .execute {
                mc.execute {
                    if (mc.networkHandler != null) {
                        mc.networkHandler!!.sendChatCommand(command)
                        // Refresh the suppression window so Hypixel's error replies stay hidden.
                        ChatCommandState.lastPartyCommandAt = System.currentTimeMillis()
                    }
                }
            }
    }

    // ─── command dispatcher ───────────────────────────────────────────────────

    @JvmStatic
    fun handleCommand(fullCmd: String): Boolean {
        val mc = MinecraftClient.getInstance()
        if (mc.networkHandler == null) return false
        val responder = "pc "

        // Split "rtca PlayerName" → cmd="rtca", arg="PlayerName" (or null)
        val parts = fullCmd.split(Regex("\\s+"), 2)
        val cmd = parts[0]
        val arg = if (parts.size > 1) parts[1] else null
        // If no arg, default to local player name
        val localName = mc.player?.name?.string
        val target = arg ?: localName

        when (cmd) {
            "help", "?" -> {
                if (!FishSettings.pcHelp) return false
                sendCmd(mc, responder, buildHelp())
                return true
            }
            "ai", "allinv" -> {
                if (!FishSettings.pcAllinvite || target == null) return false
                sendRawCommand(mc, "p settings allinvite")
                return true
            }
            "pb" -> {
                if (!FishSettings.pcPb) return false
                val pbParts = fullCmd.split(Regex("\\s+"), 3)
                val pbArg1 = if (pbParts.size > 1) pbParts[1] else null
                val pbArg2 = if (pbParts.size > 2) pbParts[2] else null
                val pbIgn: String?
                val pbFloor: String?
                if (isFloor(pbArg1)) { pbIgn = localName; pbFloor = pbArg1 }
                else { pbIgn = pbArg1 ?: localName; pbFloor = pbArg2 }
                if (pbIgn == null) return false
                runPbForPlayer(mc, pbIgn, pbFloor, responder)
                return true
            }
            "mp" -> {
                if (!FishSettings.pcMp || target == null) return false
                runMpForPlayer(mc, target, responder)
                return true
            }
            "collection" -> {
                if (!FishSettings.pcCollection || localName == null) return false
                val cp = fullCmd.split(Regex("\\s+"), 3)
                val colArg1 = if (cp.size > 1) cp[1] else null
                val colArg2 = if (cp.size > 2) cp[2] else null
                val colIgn: String
                val colFloor: String?
                if (isFloor(colArg1)) { colIgn = localName; colFloor = colArg1 }
                else { colIgn = colArg1 ?: localName; colFloor = colArg2 }
                runCollectionForPlayer(mc, colIgn, colFloor, responder)
                return true
            }
            "secrets", "sa" -> {
                if (!FishSettings.pcSecrets || target == null) return false
                runStatsForPlayer(mc, target, cmd, null, responder)
                return true
            }
            "runs" -> {
                // Support: runs [ign] [floor]  e.g. "runs SomePlayer m7" or "runs m7" or "runs"
                val rp = fullCmd.split(Regex("\\s+"), 3)
                val runTarget = if (rp.size > 1) rp[1] else localName
                val floorArg = if (rp.size > 2) rp[2] else null
                if (!FishSettings.pcRuns || runTarget == null) return false
                runStatsForPlayer(mc, runTarget, cmd, floorArg, responder)
                return true
            }
            "totalruns" -> {
                if (!FishSettings.pcRuns || target == null) return false
                runTotalRunsForPlayer(mc, target, responder)
                return true
            }
            "cata" -> {
                if (!FishSettings.pcCata || target == null) return false
                runCataForPlayer(mc, target, responder)
                return true
            }
            "rtca" -> {
                if (!FishSettings.pcRtca || target == null) return false
                runRtcaForPlayer(mc, target, responder)
                return true
            }
            "fps" -> {
                if (!FishSettings.pcFps || target == null) return false
                sendFps(mc, responder)
                return true
            }
            "tps" -> {
                if (!FishSettings.pcTps || target == null) return false
                sendTps(mc, responder)
                return true
            }
            "ping" -> {
                if (!FishSettings.pcPing || target == null) return false
                sendPing(mc, responder)
                return true
            }
            "d" -> {
                if (!FishSettings.pcDisband || target == null) return false
                sendRawCommand(mc, "p disband")
                return true
            }
            "bank" -> {
                if (!FishSettings.pcBank || target == null) return false
                sendBank(mc, target, responder)
                return true
            }
            "powder" -> {
                if (!FishSettings.pcPowder || target == null) return false
                sendPowder(mc, target, responder)
                return true
            }
            "corpse", "corpses" -> {
                if (!FishSettings.pcCorpse || target == null) return false
                sendCorpse(mc, target, responder)
                return true
            }
            "nw", "networth" -> {
                if (!FishSettings.pcNw || target == null) return false
                sendNetworth(mc, target, responder)
                return true
            }
            "worm", "scatha" -> {
                if (!FishSettings.pcWorm || target == null) return false
                sendWorm(mc, target, responder)
                return true
            }
        }

        if (cmd == "e" || cmd.matches(Regex("[fm][1-7]"))) {
            if (!FishSettings.pcJoinFloor) return false
            handleJoinInstance(cmd, mc, responder)
            return true
        }
        if (cmd.matches(Regex("t[1-5]"))) {
            if (!FishSettings.pcJoinFloor) return false
            handleKuudra(cmd, mc, responder)
            return true
        }
        return false
    }

    // ─── command implementations ──────────────────────────────────────────────

    // ─── party-triggered lookups (by IGN) ────────────────────────────────────

    private fun runRtcaForPlayer(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getByName(mc, ign) { data -> buildAndSendRtca(mc, data, ign, responder) }
    }

    private fun runCataForPlayer(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getByName(mc, ign) { data ->
            val level = HypixelApi.formatLevel(data.cataXp)
            val toNext = HypixelApi.xpToNextLevel(data.cataXp)
            sendCmd(mc, responder, "$ign's Cata: $level | $toNext XP to next")
        }
    }

    /**
     * Handles .secrets, .sa, .runs [floor] commands.
     * For .runs, floor defaults to "m7" if not provided.
     * Floor format: "m1"-"m7" (master), "f1"-"f7" (normal), "e" (entrance).
     */
    private fun runStatsForPlayer(mc: MinecraftClient, ign: String, cmd: String, floorArg: String?, responder: String?) {
        HypixelApi.getByName(mc, ign) { data ->
            val sb = StringBuilder("$ign's ")
            when (cmd) {
                "secrets" -> {
                    sb.append("Secrets: ").append(String.format("%,d", data.totalSecrets))
                    if (data.secretAverage != null) sb.append(" | SA: ").append(data.secretAverage)
                }
                "sa" -> {
                    sb.append("SA: ").append(data.secretAverage ?: "N/A")
                }
                "runs" -> {
                    val floor = floorArg?.lowercase() ?: "m7"
                    val count: Long
                    val label: String
                    if (floor == "e") {
                        count = data.cataTimes[0]
                        label = "E"
                    } else if (floor.matches(Regex("[fm][1-7]"))) {
                        val type = floor[0]
                        val num = floor[1] - '0'
                        if (type == 'm') {
                            count = data.masterTimes[num]
                            label = "M$num"
                        } else {
                            count = data.cataTimes[num]
                            label = "F$num"
                        }
                    } else {
                        // Unrecognised floor — fall back to total
                        count = data.totalRuns
                        label = "Total"
                    }
                    sb.append(label).append(" Runs: ").append(String.format("%,d", count))
                }
            }
            sendCmd(mc, responder, sb.toString())
        }
    }

    private fun runTotalRunsForPlayer(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getByName(mc, ign) { data ->
            sendCmd(mc, responder, "$ign's Total Runs: " + String.format("%,d", data.totalRuns))
        }
    }

    private fun runPbForPlayer(mc: MinecraftClient, ign: String, floor: String?, responder: String?) {
        HypixelApi.getByName(mc, ign) { data ->
            val isMaster = floor == null || floor.lowercase().startsWith("m")
            var floorNum = 7
            if (floor != null) {
                try { floorNum = floor.substring(1).toInt() } catch (ignored: Exception) {}
            }
            val pbs = if (isMaster) data.masterPbs else data.cataPbs
            val pb = if (floorNum >= 0 && floorNum < pbs.size) pbs[floorNum] else null
            val label = (if (isMaster) "M" else "F") + floorNum + " PB"
            sendCmd(mc, responder, "$ign's $label: " + (pb ?: "N/A"))
        }
    }

    private fun runMpForPlayer(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getByName(mc, ign) { data ->
            val v = if (data.magicalPower >= 0) data.magicalPower.toString() else "N/A"
            sendCmd(mc, responder, "$ign's MP: $v")
        }
    }

    // Hypixel catacombs collection milestones (per floor): tiers unlock at these points.
    private val COLLECTION_MILESTONES = longArrayOf(1, 5, 10, 25, 50, 100, 250, 500, 1000)
    private const val COLLECTION_MAX = 1000L

    /** Returns "X/MAX (max)" if maxed, otherwise "X/NEXT (next: NEXT)". For per-floor only. */
    private fun formatCollectionProgress(col: Long): String {
        if (col >= COLLECTION_MAX) {
            return String.format("%,d/%,d (max)", col, COLLECTION_MAX)
        }
        var next = COLLECTION_MAX
        for (m in COLLECTION_MILESTONES) if (col < m) { next = m; break }
        return String.format("%,d/%,d", col, next)
    }

    private fun runCollectionForPlayer(mc: MinecraftClient, ign: String, floor: String?, responder: String?) {
        HypixelApi.getByName(mc, ign) { data ->
            val label: String
            val value: String
            if (floor != null) {
                val isMaster = floor.lowercase().startsWith("m")
                var floorNum = 7
                try { floorNum = floor.substring(1).toInt() } catch (ignored: Exception) {}
                val cataRuns = if (floorNum < data.cataTimes.size) data.cataTimes[floorNum] else 0L
                val masterRuns = if (floorNum < data.masterTimes.size) data.masterTimes[floorNum] else 0L
                val col = cataRuns + masterRuns * 2
                label = (if (isMaster) "M" else "F") + floorNum + " Collection"
                value = formatCollectionProgress(col)
            } else {
                var col = 0L
                for (t in data.cataTimes) col += t
                for (t in data.masterTimes) col += t * 2
                label = "Collection"
                value = String.format("%,d", col)
            }
            sendCmd(mc, responder, "$ign's $label: $value")
        }
    }

    private fun runRtcForPlayer(mc: MinecraftClient, ign: String, levelArg: String?, responder: String?) {
        var target = 50
        if (levelArg != null) {
            try { target = maxOf(1, minOf(999, levelArg.toInt())) } catch (ignored: NumberFormatException) {}
        }
        val targetLevel = target
        HypixelApi.getByName(mc, ign) { data ->
            val xpNeeded: Long
            if (targetLevel < HypixelApi.CATA_XP_TABLE.size) {
                xpNeeded = HypixelApi.CATA_XP_TABLE[targetLevel] - data.cataXp
            } else {
                val over = (targetLevel - 50).toLong() * HypixelApi.CATA_OVERFLOW_XP_PER_LEVEL
                xpNeeded = HypixelApi.CATA_XP_TABLE[50] + over - data.cataXp
            }
            val xpPerRun = maxOf(1L, FishSettings.rtcCataXpPerRun.toLong())
            val result: String
            if (xpNeeded <= 0) {
                result = "Done ✔ :java:"
            } else {
                val runs: Long
                if (FishSettings.rtcaIncludeDailyBonus) {
                    val bonusXp = (5 * xpPerRun * 1.4).toLong() // 5 daily-bonus runs at +50%
                    runs = if (xpNeeded <= bonusXp) {
                        Math.ceil(xpNeeded / (xpPerRun * 1.4)).toLong()
                    } else {
                        5 + (xpNeeded - bonusXp + xpPerRun - 1) / xpPerRun
                    }
                } else {
                    runs = (xpNeeded + xpPerRun - 1) / xpPerRun
                }
                result = if (runs >= 1_000) String.format("%.1fk", runs / 1_000.0) else runs.toString()
            }
            sendCmd(mc, responder, "$ign's runs to Cata $targetLevel: $result")
        }
    }

    /** Maps a class name/alias to the Hypixel class key, or null if unrecognised. */
    private fun resolveClass(s: String?): String? {
        if (s == null) return null
        return when (s.lowercase()) {
            "healer", "heal", "h" -> "healer"
            "mage", "m" -> "mage"
            "berserk", "berserker", "bers", "ber", "b" -> "berserk"
            "archer", "arch", "a" -> "archer"
            "tank", "t" -> "tank"
            else -> null
        }
    }

    /**
     * .crtc — XP needed for a single class to reach a target level (default 50, or above if specified).
     * Class XP uses the same curve as catacombs (CATA_XP_TABLE); levels above 50 cost 200M XP each.
     */
    private fun runCrtcForPlayer(mc: MinecraftClient, ign: String, classArg: String?, levelArg: String?, responder: String?) {
        val classKey = resolveClass(classArg)
        if (classKey == null) {
            sendCmd(mc, responder, "Usage: .crtc [name] <healer|mage|berserk|archer|tank> [level]")
            return
        }
        var target = 50
        if (levelArg != null) {
            try { target = maxOf(1, minOf(999, levelArg.toInt())) } catch (ignored: NumberFormatException) {}
        }
        val targetLevel = target
        HypixelApi.getByName(mc, ign) { data ->
            val curXp = data.classXp.getOrDefault(classKey, 0L)
            val goalXp: Long
            if (targetLevel < HypixelApi.CATA_XP_TABLE.size) {
                goalXp = HypixelApi.CATA_XP_TABLE[targetLevel]
            } else {
                val over = (targetLevel - 50).toLong() * HypixelApi.CATA_OVERFLOW_XP_PER_LEVEL
                goalXp = HypixelApi.CATA_XP_TABLE[50] + over
            }
            val xpNeeded = goalXp - curXp
            val disp = classKey.substring(0, 1).uppercase() + classKey.substring(1)
            val result: String
            if (xpNeeded <= 0) {
                result = "Done ✔"
            } else {
                val xpPerRun = maxOf(1L, FishSettings.rtcaClassXpPerRun.toLong())
                val runs: Long
                if (FishSettings.rtcaIncludeDailyBonus) {
                    val bonusXp = (5 * xpPerRun * 1.4).toLong() // 5 daily-bonus runs at +40%
                    runs = if (xpNeeded <= bonusXp) Math.ceil(xpNeeded / (xpPerRun * 1.4)).toLong()
                    else 5 + (xpNeeded - bonusXp + xpPerRun - 1) / xpPerRun
                } else {
                    runs = (xpNeeded + xpPerRun - 1) / xpPerRun
                }
                val runsStr = if (runs >= 1_000) String.format("%.1fk", runs / 1_000.0) else runs.toString()
                result = fmtCoins(xpNeeded.toDouble()) + " XP | " + runsStr + " runs"
            }
            sendCmd(mc, responder, "$ign's $disp to $targetLevel: $result")
        }
    }

    private fun sendDprofit(mc: MinecraftClient, responder: String?) {
        val total = fishmod.features.croesus.LootTrackerOverlay.totalValueForChat()
        val runs = fishmod.features.croesus.LootTrackerOverlay.runsForChat()
        val avg = total / maxOf(1, runs)
        val pr = fishmod.features.croesus.LootTrackerOverlay.fmtCoinsPublic(avg)
        sendCmd(mc, responder, "Profit Per Run: $pr ($runs runs)")
    }

    private fun buildAndSendRtca(mc: MinecraftClient, data: HypixelApi.DungeonData, ign: String, responder: String?) {
        val xpPerRun = maxOf(1L, FishSettings.rtcaClassXpPerRun.toLong())
        val passiveXp = maxOf(0L, FishSettings.rtcaClassPassiveXpPerRun.toLong())

        val classes = arrayOf("healer", "mage", "berserk", "archer", "tank")
        val shortNames = arrayOf("H", "M", "B", "A", "T")

        val xpLeft = LongArray(5)
        for (i in 0 until 5) {
            xpLeft[i] = maxOf(0L, HypixelApi.XP_FOR_50 - data.classXp.getOrDefault(classes[i], 0L))
        }

        val runsPerClass = LongArray(5)
        var bonusRunsLeft = if (FishSettings.rtcaIncludeDailyBonus) 5 else 0
        for (guard in 0 until 2_000_000) {
            var pick = 0
            for (i in 1 until 5) if (xpLeft[i] > xpLeft[pick]) pick = i
            if (xpLeft[pick] <= 0) break
            runsPerClass[pick]++
            val mult = if (bonusRunsLeft > 0) 1.4 else 1.0
            if (bonusRunsLeft > 0) bonusRunsLeft--
            val activeXp = (xpPerRun * mult).toLong()
            val passiveXpThisRun = (passiveXp * mult).toLong()
            for (i in 0 until 5)
                xpLeft[i] = maxOf(0L, xpLeft[i] - (if (i == pick) activeXp else passiveXpThisRun))
        }

        var total = 0L
        for (i in 0 until 5) total += runsPerClass[i]
        val totalStr = if (total >= 1_000) String.format("%.1fk", total / 1_000.0) else total.toString()

        val sb = StringBuilder("$ign's RTCA ($totalStr): ")
        for (i in 0 until 5) {
            sb.append(shortNames[i]).append(": ")
            if (runsPerClass[i] == 0L) sb.append("✔")
            else if (runsPerClass[i] >= 1_000) sb.append(String.format("%.1fk", runsPerClass[i] / 1_000.0))
            else sb.append(runsPerClass[i])
            if (i < 4) sb.append(" | ")
        }
        val out = sb.toString()
        sendCmd(mc, responder, out)
    }

    // ─── local command implementations ───────────────────────────────────────

    private fun handleJoinInstance(cmd: String, mc: MinecraftClient, responder: String?) {
        val elapsed = System.currentTimeMillis() - dungeonEnteredAt
        if (elapsed < 26_000L) {
            val rem = (26_000L - elapsed) / 1_000L + 1L
            sendCmd(mc, responder, "Wait ${rem}s before joining.")
            return
        }
        val floor: String
        if (cmd == "e") {
            floor = "catacombs_entrance"
        } else {
            val type = cmd[0]
            val num = cmd[1] - '0'
            floor = (if (type == 'm') "master_" else "") + "catacombs_floor_" + NUM_WORDS[num - 1]
        }
        val joinCmd = "joininstance $floor"
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /$joinCmd"))
        sendRawCommand(mc, joinCmd)
    }

    private fun handleKuudra(cmd: String, mc: MinecraftClient, responder: String?) {
        val elapsed = System.currentTimeMillis() - dungeonEnteredAt
        if (elapsed < 30_000L) {
            val rem = (30_000L - elapsed) / 1_000L + 1L
            sendCmd(mc, responder, "Wait ${rem}s before joining Kuudra.")
            return
        }
        val tier = cmd[1] - '1' // t1=0 … t5=4
        val joinCmd = "joininstance kuudra_" + KUUDRA_TIERS[tier]
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /$joinCmd"))
        sendRawCommand(mc, joinCmd)
    }

    private fun sendCorpse(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getEconomyByName(mc, ign) { bank, purse, corpses ->
            sendCmd(mc, responder, "$ign's Corpses: " + (corpses ?: "N/A"))
        }
    }

    private fun sendBank(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getEconomyByName(mc, ign) { bank, purse, corpses ->
            val b = if (bank >= 0) fmtCoins(bank.toDouble()) else "N/A"
            val p = if (purse >= 0) fmtCoins(purse.toDouble()) else "N/A"
            sendCmd(mc, responder, "$ign's Bank: $b | Purse: $p")
        }
    }

    private fun sendPowder(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getPowderByName(mc, ign) { data ->
            if (!data.hasData()) {
                sendCmd(mc, responder, "$ign's Powder: N/A")
                return@getPowderByName
            }
            val m = if (data.mithril >= 0) String.format("%,d", data.mithril) else "N/A"
            val g = if (data.gemstone >= 0) String.format("%,d", data.gemstone) else "N/A"
            val l = if (data.glacite >= 0) String.format("%,d", data.glacite) else "N/A"
            sendCmd(mc, responder, "$ign's Powder: Mithril: $m | Gemstone: $g | Glacite: $l")
        }
    }

    private fun sendNetworth(mc: MinecraftClient, ign: String, responder: String?) {
        fishmod.features.croesus.CroesusPrices.refreshIfStale()
        Misc.addChatMessage(Text.literal("§7[FM] Looking up $ign's networth..."))
        HypixelApi.getNetworth(mc, ign) { nw, prof ->
            if (nw < 0) { sendCmd(mc, responder, "$ign's Networth: N/A"); return@getNetworth }
            sendCmd(mc, responder, "$ign's Networth: " + fmtCoins(nw.toDouble()) + (if (prof != null) " ($prof)" else ""))
        }
    }

    private fun sendSkyblockLevel(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getProfileStats(mc, ign) { sb, farm ->
            sendCmd(mc, responder, "$ign's SB Level: " + (if (sb >= 0) String.format("%.2f", sb) else "N/A"))
        }
    }

    private fun sendFarming(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getProfileStats(mc, ign) { sb, farm ->
            sendCmd(mc, responder, "$ign's Farming: " + (if (farm >= 0) String.format("%.2f", farm) else "N/A"))
        }
    }

    private fun sendNucleus(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getNucleusRuns(mc, ign) { runs ->
            sendCmd(mc, responder, "$ign's Nucleus Runs: " + (if (runs >= 0) String.format("%,d", runs) else "N/A"))
        }
    }

    private fun sendWorm(mc: MinecraftClient, ign: String, responder: String?) {
        HypixelApi.getWormStats(mc, ign) { s ->
            if (!s.found) { sendCmd(mc, responder, "$ign's Bestiary: N/A"); return@getWormStats }
            val tier = "Tier ${s.tier}/${s.maxTier}" +
                    (if (s.nextTierKills != null) " (" + String.format("%,d", s.total) + "/" + String.format("%,d", s.nextTierKills) + ")" else " (MAX)")
            sendCmd(mc, responder, "$ign's Bestiary: Worm " + String.format("%,d", s.worm) +
                    " | Scatha " + String.format("%,d", s.scatha) + " | " + tier)
        }
    }

    private fun fmtCoins(v: Double): String {
        if (v >= 1_000_000_000.0) return String.format("%.2fB", v / 1_000_000_000.0)
        if (v >= 1_000_000.0) return String.format("%.2fM", v / 1_000_000.0)
        if (v >= 1_000.0) return String.format("%.1fk", v / 1_000.0)
        return String.format("%,d", v.toLong())
    }

    private fun sendFps(mc: MinecraftClient, responder: String?) {
        val fps = mc.currentFps
        sendCmd(mc, responder, "FPS: $fps")
    }

    /** Current measured server TPS (0..20), or -1 if not enough samples yet. */
    @JvmStatic
    fun currentTps(): Double {
        val filled = minOf(tickIdx, TICK_TIMES.size)
        if (filled == 0) return -1.0
        var sum = 0L
        for (i in 0 until filled) sum += TICK_TIMES[i]
        val avgMs = sum.toDouble() / filled
        return minOf(20.0, 1000.0 / avgMs)
    }

    private fun sendTps(mc: MinecraftClient, responder: String?) {
        val filled = minOf(tickIdx, TICK_TIMES.size)
        if (filled == 0) {
            sendCmd(mc, responder, "TPS: N/A")
            return
        }
        var sum = 0L
        for (i in 0 until filled) sum += TICK_TIMES[i]
        val avgMs = sum.toDouble() / filled
        val tps = minOf(20.0, 1000.0 / avgMs)
        val formatted = String.format("%.1f", tps)
        sendCmd(mc, responder, "TPS: $formatted")
    }

    private fun sendPing(mc: MinecraftClient, responder: String?) {
        if (mc.player == null || mc.networkHandler == null) return
        // The vanilla ping/pong round trip is the most accurate, freshest end-to-end source (the same
        // one Odin uses). Server-measured tab latency and the server-list join ping are fallbacks only
        // for the brief window before a live measurement is available.
        var ping = fishmod.utils.PingTracker.latest()
        if (ping < 0) {
            val entry = mc.networkHandler!!.getPlayerListEntry(mc.player!!.uuid)
            if (entry != null && entry.latency > 0) ping = entry.latency
        }
        if (ping < 0) {
            try { val si = mc.currentServerEntry; if (si != null && si.ping > 0) ping = si.ping.toInt() }
            catch (ignored: Exception) {}
        }
        sendCmd(mc, responder, "Ping: " + (if (ping >= 0) "${ping}ms" else "N/A"))
    }

    /** Responder sentinel for /command lookups — result is shown in your own chat, not sent anywhere. */
    const val LOCAL = ""
}
