package fishmod.features.dungeon;

import fishmod.utils.HypixelApi;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.Misc;
import fishmod.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
public class PartyCommandHandler {

    private static final String[] NUM_WORDS =
            {"one", "two", "three", "four", "five", "six", "seven"};

    private static final String[] KUUDRA_TIERS =
            {"normal", "hot", "burning", "fiery", "infernal"};

    private static long dungeonEnteredAt = 0;

    // TPS tracking — rolling average of last 20 client tick intervals
    private static final long[] TICK_TIMES = new long[20];
    private static int tickIdx = 0;
    private static long lastTickMs = -1;

    public static void init() {
        // When the local player sends a chat message starting with a command prefix,
        // pre-arm the suppression window so Hypixel's "Unknown party command." reply
        // (which can race ahead of the party echo) is hidden.
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String t = message.trim();
            if (t.startsWith(".") || t.startsWith("!")) {
                ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
            }
            return true;
        });

        // Track when the player enters a dungeon or Kuudra (for 30s joininstance guard)
        Events.ON_LOCATION_CHANGE.register(loc -> {
            if (loc == Location.DUNGEON || loc == Location.KUUDRA) dungeonEnteredAt = System.currentTimeMillis();
            return false;
        });

        // Server tick timing for real TPS (CommonPingS2CPacket fires once per server tick)
        Events.ON_SERVER_TICK.register(() -> {
            long now = System.currentTimeMillis();
            if (lastTickMs > 0) {
                TICK_TIMES[tickIdx % TICK_TIMES.length] = now - lastTickMs;
                tickIdx++;
            }
            lastTickMs = now;
            return false;
        });

    }

    /** Returns true if s looks like a floor specifier: m1-m7, f1-f7, or e. */
    private static boolean isFloor(String s) {
        if (s == null) return false;
        String l = s.toLowerCase();
        return l.equals("e") || l.matches("[fm][1-7]");
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
    public static void onPartyCommand(String typer, String cmd, String rawArg1, String rawArg2) {
        onPartyCommand(typer, cmd, rawArg1, rawArg2, null, "pc ");
    }

    /**
     * responder = chat-command prefix used to reply, e.g. "pc ", "gc ", "oc ", "ac ",
     * or "msg PlayerName " for a private-message reply.
     */
    /** Responder sentinel for /command lookups — result is shown in your own chat, not sent anywhere. */
    public static final String LOCAL = "";

    /** Back-compat overload for callers that only have two args (rawArg3 = null). */
    public static void onPartyCommand(String typer, String cmd, String rawArg1, String rawArg2, String responder) {
        onPartyCommand(typer, cmd, rawArg1, rawArg2, null, responder);
    }

    public static void onPartyCommand(String typer, String cmd, String rawArg1, String rawArg2, String rawArg3, String responder) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        // Use the real account name (GameProfile), NOT getName() — a cosmetic /nick overrides
        // getName() and would break the isMe check for self-only commands (.ping/.fps/.corpse...).
        String selfName = (mc.player != null) ? mc.player.getGameProfile().name() : null;
        boolean isMe = selfName != null && typer.equalsIgnoreCase(selfName);
        // Local /command lookups bypass the party-dedup so they always respond immediately.
        boolean isLocal = LOCAL.equals(responder);

        // Default target for non-runs commands: explicit arg or fall back to typer
        String ign = rawArg1 != null ? rawArg1 : typer;

        switch (cmd) {
            case "help", "?" -> { if (FishSettings.pcHelp && respond(cmd, typer, isLocal)) sendCmd(mc, responder + buildHelp()); }
            // Stats lookups: respond to ANY party member's command (default target = typer if no arg)
            case "rtca"      -> { if (FishSettings.pcRtca && respond(cmd, typer, isLocal))    runRtcaForPlayer(mc, ign, responder);             }
            case "rtc"       -> { if (FishSettings.pcRtc  && respond(cmd, typer, isLocal)) {
                String rtcIgn; String levelArg;
                if (rawArg1 != null && rawArg1.matches("\\d+")) { rtcIgn = typer; levelArg = rawArg1; }
                else { rtcIgn = rawArg1 != null ? rawArg1 : typer; levelArg = rawArg2; }
                runRtcForPlayer(mc, rtcIgn, levelArg, responder);
            } }
            case "crtc"      -> { if (FishSettings.pcCrtc && respond(cmd, typer, isLocal)) {
                // .crtc [name] <class> [level] — smart-parse: if arg1 is a class, name defaults to typer.
                String cIgn, cClass, cLevel;
                if (resolveClass(rawArg1) != null) { cIgn = typer; cClass = rawArg1; cLevel = rawArg2; }
                else { cIgn = rawArg1 != null ? rawArg1 : typer; cClass = rawArg2; cLevel = rawArg3; }
                runCrtcForPlayer(mc, cIgn, cClass, cLevel, responder);
            } }
            case "cata"      -> { if (FishSettings.pcCata && respond(cmd, typer, isLocal))    runCataForPlayer(mc, ign, responder);             }
            case "pb" -> {
                if (!FishSettings.pcPb || !respond(cmd, typer, isLocal)) break;
                String pbIgn, pbFloor;
                if (isFloor(rawArg1)) {
                    pbIgn   = typer;
                    pbFloor = rawArg1;
                } else {
                    pbIgn   = rawArg1 != null ? rawArg1 : typer;
                    pbFloor = rawArg2;
                }
                runPbForPlayer(mc, pbIgn, pbFloor, responder);
            }
            case "mp" -> { if (FishSettings.pcMp && respond(cmd, typer, isLocal)) runMpForPlayer(mc, rawArg1 != null ? rawArg1 : typer, responder); }
            case "collection" -> {
                if (!FishSettings.pcCollection || !respond(cmd, typer, isLocal)) break;
                String colIgn, colFloor;
                if (isFloor(rawArg1)) { colIgn = typer;                             colFloor = rawArg1; }
                else                  { colIgn = rawArg1 != null ? rawArg1 : typer; colFloor = rawArg2; }
                runCollectionForPlayer(mc, colIgn, colFloor, responder);
            }
            case "secrets", "sa" -> { if (FishSettings.pcSecrets && respond(cmd, typer, isLocal)) runStatsForPlayer(mc, ign, cmd, null, responder); }
            case "runs" -> {
                if (!FishSettings.pcRuns || !respond(cmd, typer, isLocal)) break;
                String runsIgn, floor;
                if (isFloor(rawArg1)) {
                    runsIgn = typer;
                    floor   = rawArg1;
                } else {
                    runsIgn = rawArg1 != null ? rawArg1 : typer;
                    floor   = rawArg2;
                }
                runStatsForPlayer(mc, runsIgn, cmd, floor, responder);
            }
            case "totalruns" -> { if (FishSettings.pcRuns && respond(cmd, typer, isLocal))    runTotalRunsForPlayer(mc, ign, responder);        }
            // Self-only metrics: only the typer's own mod responds (data is local to each player)
            case "dprofit"   -> { if (FishSettings.pcDprofit && isMe) sendDprofit(mc, responder);              }
            case "corpse", "corpses" -> { if (FishSettings.pcCorpse && respond(cmd, typer, isLocal)) sendCorpse(mc, ign, responder);  }
            case "bank" -> { if (FishSettings.pcBank && respond(cmd, typer, isLocal)) sendBank(mc, ign, responder); }
            case "powder" -> { if (FishSettings.pcPowder && respond(cmd, typer, isLocal)) sendPowder(mc, ign, responder); }
            case "nw", "networth" -> { if (FishSettings.pcNw && respond(cmd, typer, isLocal)) sendNetworth(mc, ign, responder); }
            case "level", "sblvl" -> { if (FishSettings.pcLevel && respond(cmd, typer, isLocal)) sendSkyblockLevel(mc, ign, responder); }
            case "farming" -> { if (FishSettings.pcFarming && respond(cmd, typer, isLocal)) sendFarming(mc, ign, responder); }
            case "nuc", "nucleus" -> { if (FishSettings.pcNuc && respond(cmd, typer, isLocal)) sendNucleus(mc, ign, responder); }
            case "worm", "scatha" -> { if (FishSettings.pcWorm && respond(cmd, typer, isLocal)) sendWorm(mc, ign, responder); }
            case "fps"    -> { if (FishSettings.pcFps    && isMe) sendFps(mc, responder);  }
            case "tps"    -> { if (FishSettings.pcTps    && isMe) sendTps(mc, responder);  }
            case "ping"   -> { if (FishSettings.pcPing   && isMe) sendPing(mc, responder); }
            case "ai", "allinv" -> { if (FishSettings.pcAllinvite && isMe) sendCmd(mc, "p settings allinvite"); }
            case "d"            -> { if (FishSettings.pcDisband   && isMe) sendCmd(mc, "p disband");             }
            // Party actions: only honor from party chat or local /command (never from DM/guild/officer/all chat,
            // where someone saying ".warp" would otherwise make our client try `/p warp` and error out).
            case "kick"     -> { if (partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendCmd(mc, "p kick " + rawArg1);     }
            case "warp"     -> { if (partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe))                    sendCmd(mc, "p warp");                }
            case "transfer" -> { if (partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendCmd(mc, "p transfer " + rawArg1); }
            case "promote"  -> { if (partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendCmd(mc, "p promote " + rawArg1);  }
            default -> {
                if ((cmd.matches("[fm][1-7]") || cmd.equals("e")) && FishSettings.pcJoinFloor) handleJoinInstance(cmd, mc, responder);
                else if (cmd.matches("t[1-5]") && FishSettings.pcJoinFloor) handleKuudra(cmd, mc, responder);
            }
        }
    }

    // Per-command dedup so multiple FishMod-running party members don't all spam the same response.
    // Each (cmd|typer) pair can only fire once per 5 seconds across the whole party.
    private static final java.util.Map<String, Long> RECENT_RESPONSES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long RESPONSE_DEDUP_MS = 5000;
    /** Local /command lookups always respond; party/guild echoes go through the dedup. */
    private static boolean respond(String cmd, String typer, boolean isLocal) {
        return isLocal || shouldRespond(cmd, typer);
    }

    private static boolean shouldRespond(String cmd, String typer) {
        long now = System.currentTimeMillis();
        String key = cmd + "|" + typer.toLowerCase();
        Long last = RECENT_RESPONSES.get(key);
        if (last != null && now - last < RESPONSE_DEDUP_MS) return false;
        RECENT_RESPONSES.put(key, now);
        // Light GC: drop entries older than 30s
        RECENT_RESPONSES.entrySet().removeIf(e -> now - e.getValue() > 30_000);
        return true;
    }

    /** Party actions (kick/warp/transfer/promote) only run from party chat or local /command. */
    private static boolean partyActionAllowed(String responder, boolean isLocal) {
        return isLocal || (responder != null && responder.startsWith("pc "));
    }

    private static boolean allowPartyAction(String typer, boolean isMe) {
        return FishSettings.pcPartyActions;
    }

    /** Builds a list of currently-enabled dot-commands for .help / .?. */
    private static String buildHelp() {
        java.util.List<String> cmds = new java.util.ArrayList<>();
        if (FishSettings.pcPb)         cmds.add("pb");
        if (FishSettings.pcCata)       cmds.add("cata");
        if (FishSettings.pcRtca)       cmds.add("rtca");
        if (FishSettings.pcRtc)        cmds.add("rtc");
        if (FishSettings.pcCrtc)       cmds.add("crtc");
        if (FishSettings.pcSecrets)    cmds.add("secrets/sa");
        if (FishSettings.pcRuns)       cmds.add("runs/totalruns");
        if (FishSettings.pcCollection) cmds.add("collection");
        if (FishSettings.pcMp)         cmds.add("mp");
        if (FishSettings.pcNw)         cmds.add("nw");
        if (FishSettings.pcLevel)      cmds.add("level");
        if (FishSettings.pcFarming)    cmds.add("farming");
        if (FishSettings.pcNuc)        cmds.add("nuc");
        if (FishSettings.pcWorm)       cmds.add("worm"); // .scatha is a hidden alias
        if (FishSettings.pcBank)       cmds.add("bank");
        if (FishSettings.pcPowder)     cmds.add("powder");
        if (FishSettings.pcCorpse)     cmds.add("corpses");
        if (FishSettings.pcDprofit)    cmds.add("dprofit");
        if (FishSettings.pcFps)        cmds.add("fps");
        if (FishSettings.pcTps)        cmds.add("tps");
        if (FishSettings.pcPing)       cmds.add("ping");
        if (FishSettings.pcAllinvite)  cmds.add("ai");
        if (FishSettings.pcJoinFloor)  cmds.add("e/f1-7/m1-7/t1-5");
        if (FishSettings.pcPartyActions) cmds.add("kick/warp/transfer/promote");
        if (FishSettings.pcDisband)    cmds.add("d");
        return "FishMod cmds: ." + String.join(" .", cmds);
    }

    /** Sends a command to the server after a short delay to avoid rate-limiting. */
    private static void sendCmd(MinecraftClient mc, String command) {
        // Local /command lookups: show the result in your own chat instead of sending it anywhere.
        if (command.startsWith(LOCAL)) {
            String msg = command.substring(LOCAL.length());
            mc.execute(() -> fishmod.utils.FishMsg.send("§f" + msg));
            return;
        }
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
            .execute(() -> mc.execute(() -> {
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendChatCommand(command);
                    // Refresh the suppression window so Hypixel's error replies stay hidden.
                    ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
                }
            }));
    }

    // ─── command dispatcher ───────────────────────────────────────────────────

    public static boolean handleCommand(String fullCmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return false;
        final String responder = "pc ";

        // Split "rtca PlayerName" → cmd="rtca", arg="PlayerName" (or null)
        String[] parts = fullCmd.split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;
        // If no arg, default to local player name
        String localName = mc.player != null ? mc.player.getName().getString() : null;
        String target = arg != null ? arg : localName;

        switch (cmd) {
            case "help": case "?":
                if (!FishSettings.pcHelp) return false;
                sendCmd(mc, responder + buildHelp());
                return true;
            case "ai": case "allinv":
                if (!FishSettings.pcAllinvite || target == null) return false;
                sendCmd(mc, "p settings allinvite");
                return true;
            case "pb": {
                if (!FishSettings.pcPb) return false;
                String[] pbParts = fullCmd.split("\\s+", 3);
                String pbArg1 = pbParts.length > 1 ? pbParts[1] : null;
                String pbArg2 = pbParts.length > 2 ? pbParts[2] : null;
                String pbIgn, pbFloor;
                if (isFloor(pbArg1)) { pbIgn = localName; pbFloor = pbArg1; }
                else { pbIgn = pbArg1 != null ? pbArg1 : localName; pbFloor = pbArg2; }
                if (pbIgn == null) return false;
                runPbForPlayer(mc, pbIgn, pbFloor, responder);
                return true;
            }
            case "mp":
                if (!FishSettings.pcMp || target == null) return false;
                runMpForPlayer(mc, target, responder);
                return true;
            case "collection": {
                if (!FishSettings.pcCollection || localName == null) return false;
                String[] cp = fullCmd.split("\\s+", 3);
                String colArg1 = cp.length > 1 ? cp[1] : null;
                String colArg2 = cp.length > 2 ? cp[2] : null;
                String colIgn, colFloor;
                if (isFloor(colArg1)) { colIgn = localName; colFloor = colArg1; }
                else { colIgn = colArg1 != null ? colArg1 : localName; colFloor = colArg2; }
                runCollectionForPlayer(mc, colIgn, colFloor, responder);
                return true;
            }
            case "secrets": case "sa":
                if (!FishSettings.pcSecrets || target == null) return false;
                runStatsForPlayer(mc, target, cmd, null, responder);
                return true;
            case "runs": {
                // Support: runs [ign] [floor]  e.g. "runs SomePlayer m7" or "runs m7" or "runs"
                String[] rp = fullCmd.split("\\s+", 3);
                String runTarget = rp.length > 1 ? rp[1] : localName;
                String floorArg  = rp.length > 2 ? rp[2] : null;
                if (!FishSettings.pcRuns || runTarget == null) return false;
                runStatsForPlayer(mc, runTarget, cmd, floorArg, responder);
                return true;
            }
            case "totalruns":
                if (!FishSettings.pcRuns || target == null) return false;
                runTotalRunsForPlayer(mc, target, responder);
                return true;
            case "cata":
                if (!FishSettings.pcCata || target == null) return false;
                runCataForPlayer(mc, target, responder);
                return true;
            case "rtca":
                if (!FishSettings.pcRtca || target == null) return false;
                runRtcaForPlayer(mc, target, responder);
                return true;
            case "fps":
                if (!FishSettings.pcFps || target == null) return false;
                sendFps(mc, responder);
                return true;
            case "tps":
                if (!FishSettings.pcTps || target == null) return false;
                sendTps(mc, responder);
                return true;
            case "ping":
                if (!FishSettings.pcPing || target == null) return false;
                sendPing(mc, responder);
                return true;
            case "d":
                if (!FishSettings.pcDisband || target == null) return false;
                sendCmd(mc, "p disband");
                return true;
            case "bank":
                if (!FishSettings.pcBank || target == null) return false;
                sendBank(mc, target, responder);
                return true;
            case "powder":
                if (!FishSettings.pcPowder || target == null) return false;
                sendPowder(mc, target, responder);
                return true;
            case "corpse": case "corpses":
                if (!FishSettings.pcCorpse || target == null) return false;
                sendCorpse(mc, target, responder);
                return true;
            case "nw": case "networth":
                if (!FishSettings.pcNw || target == null) return false;
                sendNetworth(mc, target, responder);
                return true;
            case "worm": case "scatha":
                if (!FishSettings.pcWorm || target == null) return false;
                sendWorm(mc, target, responder);
                return true;
        }

        if (cmd.equals("e") || cmd.matches("[fm][1-7]")) {
            if (!FishSettings.pcJoinFloor) return false;
            handleJoinInstance(cmd, mc, responder);
            return true;
        }
        if (cmd.matches("t[1-5]")) {
            if (!FishSettings.pcJoinFloor) return false;
            handleKuudra(cmd, mc, responder);
            return true;
        }
        return false;
    }

    // ─── command implementations ──────────────────────────────────────────────

    // ─── party-triggered lookups (by IGN) ────────────────────────────────────

    private static void runRtcaForPlayer(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> buildAndSendRtca(mc, data, ign, responder));
    }

    private static void runCataForPlayer(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            String level  = HypixelApi.formatLevel(data.cataXp);
            String toNext = HypixelApi.xpToNextLevel(data.cataXp);
            sendCmd(mc, responder + ign + "'s Cata: " + level + " | " + toNext + " XP to next");
        });
    }

    /**
     * Handles .secrets, .sa, .runs [floor] commands.
     * For .runs, floor defaults to "m7" if not provided.
     * Floor format: "m1"-"m7" (master), "f1"-"f7" (normal), "e" (entrance).
     */
    private static void runStatsForPlayer(MinecraftClient mc, String ign, String cmd, String floorArg, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            StringBuilder sb = new StringBuilder(ign + "'s ");
            switch (cmd) {
                case "secrets" -> {
                    sb.append("Secrets: ").append(String.format("%,d", data.totalSecrets));
                    if (data.secretAverage != null) sb.append(" | SA: ").append(data.secretAverage);
                }
                case "sa" -> {
                    sb.append("SA: ").append(data.secretAverage != null ? data.secretAverage : "N/A");
                }
                case "runs" -> {
                    String floor = floorArg != null ? floorArg.toLowerCase() : "m7";
                    long count;
                    String label;
                    if (floor.equals("e")) {
                        count = data.cataTimes[0];
                        label = "E";
                    } else if (floor.matches("[fm][1-7]")) {
                        char type = floor.charAt(0);
                        int num   = floor.charAt(1) - '0';
                        if (type == 'm') {
                            count = data.masterTimes[num];
                            label = "M" + num;
                        } else {
                            count = data.cataTimes[num];
                            label = "F" + num;
                        }
                    } else {
                        // Unrecognised floor — fall back to total
                        count = data.totalRuns;
                        label = "Total";
                    }
                    sb.append(label).append(" Runs: ").append(String.format("%,d", count));
                }
            }
            sendCmd(mc, responder + sb);
        });
    }

    private static void runTotalRunsForPlayer(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            sendCmd(mc, responder + ign + "'s Total Runs: " + String.format("%,d", data.totalRuns));
        });
    }

    private static void runPbForPlayer(MinecraftClient mc, String ign, String floor, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            boolean isMaster = floor == null || floor.toLowerCase().startsWith("m");
            int floorNum = 7;
            if (floor != null) {
                try { floorNum = Integer.parseInt(floor.substring(1)); } catch (Exception ignored) {}
            }
            String[] pbs = isMaster ? data.masterPbs : data.cataPbs;
            String pb = (floorNum >= 0 && floorNum < pbs.length) ? pbs[floorNum] : null;
            String label = (isMaster ? "M" : "F") + floorNum + " PB";
            sendCmd(mc, responder + ign + "'s " + label + ": " + (pb != null ? pb : "N/A"));
        });
    }

    private static void runMpForPlayer(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            String val = data.magicalPower >= 0 ? String.valueOf(data.magicalPower) : "N/A";
            sendCmd(mc, responder + ign + "'s MP: " + val);
        });
    }

    // Hypixel catacombs collection milestones (per floor): tiers unlock at these points.
    private static final long[] COLLECTION_MILESTONES = {1, 5, 10, 25, 50, 100, 250, 500, 1000};
    private static final long COLLECTION_MAX = 1000;

    /** Returns "X/MAX (max)" if maxed, otherwise "X/NEXT (next: NEXT)". For per-floor only. */
    private static String formatCollectionProgress(long col) {
        if (col >= COLLECTION_MAX) {
            return String.format("%,d/%,d (max)", col, COLLECTION_MAX);
        }
        long next = COLLECTION_MAX;
        for (long m : COLLECTION_MILESTONES) if (col < m) { next = m; break; }
        return String.format("%,d/%,d", col, next);
    }

    private static void runCollectionForPlayer(MinecraftClient mc, String ign, String floor, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            String label;
            String value;
            if (floor != null) {
                boolean isMaster = floor.toLowerCase().startsWith("m");
                int floorNum = 7;
                try { floorNum = Integer.parseInt(floor.substring(1)); } catch (Exception ignored) {}
                long cataRuns   = floorNum < data.cataTimes.length   ? data.cataTimes[floorNum]   : 0;
                long masterRuns = floorNum < data.masterTimes.length ? data.masterTimes[floorNum] : 0;
                long col = cataRuns + masterRuns * 2;
                label = (isMaster ? "M" : "F") + floorNum + " Collection";
                value = formatCollectionProgress(col);
            } else {
                long col = 0;
                for (long t : data.cataTimes)   col += t;
                for (long t : data.masterTimes) col += t * 2;
                label = "Collection";
                value = String.format("%,d", col);
            }
            sendCmd(mc, responder + ign + "'s " + label + ": " + value);
        });
    }

    private static void runRtcForPlayer(MinecraftClient mc, String ign, String levelArg, String responder) {
        int target = 50;
        if (levelArg != null) {
            try { target = Math.max(1, Math.min(99, Integer.parseInt(levelArg))); } catch (NumberFormatException ignored) {}
        }
        final int targetLevel = target;
        HypixelApi.getByName(mc, ign, data -> {
            long xpNeeded;
            if (targetLevel < HypixelApi.CATA_XP_TABLE.length) {
                xpNeeded = HypixelApi.CATA_XP_TABLE[targetLevel] - data.cataXp;
            } else {
                long over = (long)(targetLevel - 50) * 200_000_000L;
                xpNeeded = HypixelApi.CATA_XP_TABLE[50] + over - data.cataXp;
            }
            long xpPerRun = Math.max(1, FishSettings.rtcCataXpPerRun);
            String result;
            if (xpNeeded <= 0) {
                result = "Done ✔ :java:";
            } else {
                long runs;
                if (FishSettings.rtcaIncludeDailyBonus) {
                    long bonusXp = (long)(5 * xpPerRun * 1.4); // 5 daily-bonus runs at +50%
                    if (xpNeeded <= bonusXp) {
                        runs = (long) Math.ceil(xpNeeded / (xpPerRun * 1.4));
                    } else {
                        runs = 5 + (xpNeeded - bonusXp + xpPerRun - 1) / xpPerRun;
                    }
                } else {
                    runs = (xpNeeded + xpPerRun - 1) / xpPerRun;
                }
                result = runs >= 1_000 ? String.format("%.1fk", runs / 1_000.0) : Long.toString(runs);
            }
            sendCmd(mc, responder + ign + "'s runs to Cata " + targetLevel + ": " + result);
        });
    }

    /** Maps a class name/alias to the Hypixel class key, or null if unrecognised. */
    private static String resolveClass(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "healer", "heal", "h"                  -> "healer";
            case "mage", "m"                            -> "mage";
            case "berserk", "berserker", "bers", "ber", "b" -> "berserk";
            case "archer", "arch", "a"                  -> "archer";
            case "tank", "t"                            -> "tank";
            default                                     -> null;
        };
    }

    /**
     * .crtc — XP needed for a single class to reach a target level (default 50, or above if specified).
     * Class XP uses the same curve as catacombs (CATA_XP_TABLE); levels above 50 cost 200M XP each.
     */
    private static void runCrtcForPlayer(MinecraftClient mc, String ign, String classArg, String levelArg, String responder) {
        String classKey = resolveClass(classArg);
        if (classKey == null) {
            sendCmd(mc, responder + "Usage: .crtc [name] <healer|mage|berserk|archer|tank> [level]");
            return;
        }
        int target = 50;
        if (levelArg != null) {
            try { target = Math.max(1, Math.min(99, Integer.parseInt(levelArg))); } catch (NumberFormatException ignored) {}
        }
        final int targetLevel = target;
        HypixelApi.getByName(mc, ign, data -> {
            long curXp = data.classXp.getOrDefault(classKey, 0L);
            long goalXp;
            if (targetLevel < HypixelApi.CATA_XP_TABLE.length) {
                goalXp = HypixelApi.CATA_XP_TABLE[targetLevel];
            } else {
                long over = (long)(targetLevel - 50) * 200_000_000L;
                goalXp = HypixelApi.CATA_XP_TABLE[50] + over;
            }
            long xpNeeded = goalXp - curXp;
            String disp = Character.toUpperCase(classKey.charAt(0)) + classKey.substring(1);
            String result;
            if (xpNeeded <= 0) {
                result = "Done ✔";
            } else {
                long xpPerRun = Math.max(1, FishSettings.rtcaClassXpPerRun);
                long runs;
                if (FishSettings.rtcaIncludeDailyBonus) {
                    long bonusXp = (long)(5 * xpPerRun * 1.4); // 5 daily-bonus runs at +40%
                    if (xpNeeded <= bonusXp) runs = (long) Math.ceil(xpNeeded / (xpPerRun * 1.4));
                    else                     runs = 5 + (xpNeeded - bonusXp + xpPerRun - 1) / xpPerRun;
                } else {
                    runs = (xpNeeded + xpPerRun - 1) / xpPerRun;
                }
                String runsStr = runs >= 1_000 ? String.format("%.1fk", runs / 1_000.0) : Long.toString(runs);
                result = fmtCoins(xpNeeded) + " XP | " + runsStr + " runs";
            }
            sendCmd(mc, responder + ign + "'s " + disp + " to " + targetLevel + ": " + result);
        });
    }

    private static void sendDprofit(MinecraftClient mc, String responder) {
        java.util.List<fishmod.features.croesus.CroesusStore.Entry> all = fishmod.features.croesus.CroesusStore.all();
        String last = all.isEmpty()
                ? "—"
                : fishmod.features.croesus.CroesusOverlay.bestDrop(all.get(all.size() - 1));
        String pr = fishmod.features.croesus.CroesusOverlay.fmtCoins(
                fishmod.features.croesus.CroesusOverlay.avgProfit());
        sendCmd(mc, responder + "Last RNG: " + last + " | Profit Per Run: " + pr);
    }

    private static void buildAndSendRtca(MinecraftClient mc, HypixelApi.DungeonData data, String ign, String responder) {
        long xpPerRun = Math.max(1, FishSettings.rtcaClassXpPerRun);
        long passiveXp = Math.max(0, FishSettings.rtcaClassPassiveXpPerRun);

        String[] classes    = {"healer", "mage", "berserk", "archer", "tank"};
        String[] shortNames = {"H",      "M",    "B",       "A",      "T"   };

        long[] xpLeft = new long[5];
        for (int i = 0; i < 5; i++) {
            xpLeft[i] = Math.max(0L, HypixelApi.XP_FOR_50 - data.classXp.getOrDefault(classes[i], 0L));
        }

        long[] runsPerClass = new long[5];
        int bonusRunsLeft = FishSettings.rtcaIncludeDailyBonus ? 5 : 0;
        for (int guard = 0; guard < 2_000_000; guard++) {
            int pick = 0;
            for (int i = 1; i < 5; i++) if (xpLeft[i] > xpLeft[pick]) pick = i;
            if (xpLeft[pick] <= 0) break;
            runsPerClass[pick]++;
            double mult = bonusRunsLeft > 0 ? 1.4 : 1.0;
            if (bonusRunsLeft > 0) bonusRunsLeft--;
            long activeXp  = (long)(xpPerRun  * mult);
            long passiveXpThisRun = (long)(passiveXp * mult);
            for (int i = 0; i < 5; i++)
                xpLeft[i] = Math.max(0L, xpLeft[i] - (i == pick ? activeXp : passiveXpThisRun));
        }

        long total = 0;
        for (int i = 0; i < 5; i++) total += runsPerClass[i];
        String totalStr = total >= 1_000 ? String.format("%.1fk", total / 1_000.0) : Long.toString(total);

        StringBuilder sb = new StringBuilder(ign + "'s RTCA (" + totalStr + "): ");
        for (int i = 0; i < 5; i++) {
            sb.append(shortNames[i]).append(": ");
            if (runsPerClass[i] == 0)           sb.append("✔");
            else if (runsPerClass[i] >= 1_000)  sb.append(String.format("%.1fk", runsPerClass[i] / 1_000.0));
            else                                sb.append(runsPerClass[i]);
            if (i < 4) sb.append(" | ");
        }
        String out = sb.toString();
        sendCmd(mc, responder + out);
    }

    // ─── local command implementations ───────────────────────────────────────

    private static void handleJoinInstance(String cmd, MinecraftClient mc, String responder) {
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 26_000L) {
            long rem = (26_000L - elapsed) / 1_000L + 1L;
            sendCmd(mc, responder + "Wait " + rem + "s before joining.");
            return;
        }
        String floor;
        if (cmd.equals("e")) {
            floor = "catacombs_entrance";
        } else {
            char type = cmd.charAt(0);
            int  num  = cmd.charAt(1) - '0';
            floor = (type == 'm' ? "master_" : "") + "catacombs_floor_" + NUM_WORDS[num - 1];
        }
        String joinCmd = "joininstance " + floor;
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /" + joinCmd));
        sendCmd(mc, joinCmd);
    }

    private static void handleKuudra(String cmd, MinecraftClient mc, String responder) {
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 30_000L) {
            long rem = (30_000L - elapsed) / 1_000L + 1L;
            sendCmd(mc, responder + "Wait " + rem + "s before joining Kuudra.");
            return;
        }
        int tier = cmd.charAt(1) - '1'; // t1=0 … t5=4
        String joinCmd = "joininstance kuudra_" + KUUDRA_TIERS[tier];
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /" + joinCmd));
        sendCmd(mc, joinCmd);
    }

    private static void sendCorpse(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getEconomyByName(mc, ign, (bank, purse, corpses) ->
            sendCmd(mc, responder + ign + "'s Corpses: " + (corpses != null ? corpses : "N/A")));
    }

    private static void sendBank(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getEconomyByName(mc, ign, (bank, purse, corpses) -> {
            String b = bank >= 0 ? fmtCoins(bank) : "N/A";
            String p = purse >= 0 ? fmtCoins(purse) : "N/A";
            sendCmd(mc, responder + ign + "'s Bank: " + b + " | Purse: " + p);
        });
    }

    private static void sendPowder(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getPowderByName(mc, ign, data -> {
            if (!data.hasData()) {
                sendCmd(mc, responder + ign + "'s Powder: N/A");
                return;
            }
            String m = data.mithril  >= 0 ? String.format("%,d", data.mithril)  : "N/A";
            String g = data.gemstone >= 0 ? String.format("%,d", data.gemstone) : "N/A";
            String l = data.glacite  >= 0 ? String.format("%,d", data.glacite)  : "N/A";
            sendCmd(mc, responder + ign + "'s Powder: Mithril: " + m + " | Gemstone: " + g + " | Glacite: " + l);
        });
    }

    private static void sendNetworth(MinecraftClient mc, String ign, String responder) {
        fishmod.features.croesus.CroesusPrices.refreshIfStale();
        Misc.addChatMessage(Text.literal("§7[FM] Looking up " + ign + "'s networth..."));
        HypixelApi.getNetworth(mc, ign, (nw, prof) -> {
            if (nw < 0) { sendCmd(mc, responder + ign + "'s Networth: N/A"); return; }
            sendCmd(mc, responder + ign + "'s Networth: " + fmtCoins(nw) + (prof != null ? " (" + prof + ")" : ""));
        });
    }

    private static void sendSkyblockLevel(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getProfileStats(mc, ign, (sb, farm) ->
            sendCmd(mc, responder + ign + "'s SB Level: " + (sb >= 0 ? String.format("%.2f", sb) : "N/A")));
    }

    private static void sendFarming(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getProfileStats(mc, ign, (sb, farm) ->
            sendCmd(mc, responder + ign + "'s Farming: " + (farm >= 0 ? String.format("%.2f", farm) : "N/A")));
    }

    private static void sendNucleus(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getNucleusRuns(mc, ign, runs ->
            sendCmd(mc, responder + ign + "'s Nucleus Runs: " + (runs >= 0 ? String.format("%,d", runs) : "N/A")));
    }

    private static void sendWorm(MinecraftClient mc, String ign, String responder) {
        HypixelApi.getWormStats(mc, ign, s -> {
            if (!s.found) { sendCmd(mc, responder + ign + "'s Bestiary: N/A"); return; }
            String tier = "Tier " + s.tier + "/" + s.maxTier
                    + (s.nextTierKills != null ? " (" + String.format("%,d", s.total) + "/" + String.format("%,d", s.nextTierKills) + ")" : " (MAX)");
            sendCmd(mc, responder + ign + "'s Bestiary: Worm " + String.format("%,d", s.worm)
                    + " | Scatha " + String.format("%,d", s.scatha) + " | " + tier);
        });
    }

    private static String fmtCoins(double v) {
        if (v >= 1_000_000_000d) return String.format("%.2fB", v / 1_000_000_000d);
        if (v >= 1_000_000d)     return String.format("%.2fM", v / 1_000_000d);
        if (v >= 1_000d)         return String.format("%.1fk", v / 1_000d);
        return String.format("%,d", (long) v);
    }

    private static void sendFps(MinecraftClient mc, String responder) {
        int fps = mc.getCurrentFps();
        sendCmd(mc, responder + "FPS: " + fps);
    }

    /** Current measured server TPS (0..20), or -1 if not enough samples yet. */
    public static double currentTps() {
        int filled = Math.min(tickIdx, TICK_TIMES.length);
        if (filled == 0) return -1;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_TIMES[i];
        double avgMs = (double) sum / filled;
        return Math.min(20.0, 1000.0 / avgMs);
    }

    private static void sendTps(MinecraftClient mc, String responder) {
        int filled = Math.min(tickIdx, TICK_TIMES.length);
        if (filled == 0) {
            sendCmd(mc, responder + "TPS: N/A");
            return;
        }
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_TIMES[i];
        double avgMs = (double) sum / filled;
        double tps = Math.min(20.0, 1000.0 / avgMs);
        String formatted = String.format("%.1f", tps);
        sendCmd(mc, responder + "TPS: " + formatted);
    }

    private static void sendPing(MinecraftClient mc, String responder) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        // The vanilla ping/pong round trip is the most accurate, freshest end-to-end source (the same
        // one Odin uses). Server-measured tab latency and the server-list join ping are fallbacks only
        // for the brief window before a live measurement is available.
        int ping = fishmod.utils.PingTracker.latest();
        if (ping < 0) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null && entry.getLatency() > 0) ping = entry.getLatency();
        }
        if (ping < 0) {
            try { var si = mc.getCurrentServerEntry(); if (si != null && si.ping > 0) ping = (int) si.ping; }
            catch (Exception ignored) {}
        }
        sendCmd(mc, responder + "Ping: " + (ping >= 0 ? ping + "ms" : "N/A"));
    }


}
