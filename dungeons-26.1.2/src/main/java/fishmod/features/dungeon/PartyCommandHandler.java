package fishmod.features.dungeon;

import fishmod.utils.HypixelApi;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.Misc;
import fishmod.utils.events.Events;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public class PartyCommandHandler {

    private static final String[] NUM_WORDS =
            {"one", "two", "three", "four", "five", "six", "seven"};

    private static final String[] KUUDRA_TIERS =
            {"normal", "hot", "burning", "fiery", "infernal"};

    private static long dungeonEnteredAt = 0;

    private static final long[] TICK_TIMES = new long[20];
    private static int tickIdx = 0;
    private static long lastTickMs = -1;

    public static void init() {
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String t = message.trim();
            if (t.startsWith(".") || t.startsWith("!")) {
                ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
            }
            return true;
        });

        Events.ON_LOCATION_CHANGE.register(loc -> {
            if (loc == Location.DUNGEON || loc == Location.KUUDRA) dungeonEnteredAt = System.currentTimeMillis();
            return false;
        });

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

    private static boolean isFloor(String s) {
        if (s == null) return false;
        String l = s.toLowerCase();
        return l.equals("e") || l.matches("[fm][1-7]");
    }

    public static void onPartyCommand(String typer, String cmd, String rawArg1, String rawArg2) {
        onPartyCommand(typer, cmd, rawArg1, rawArg2, null, "pc ");
    }

    public static final String LOCAL = "";

    public static void onPartyCommand(String typer, String cmd, String rawArg1, String rawArg2, String responder) {
        onPartyCommand(typer, cmd, rawArg1, rawArg2, null, responder);
    }

    public static void onPartyCommand(String typer, String cmd, String rawArg1, String rawArg2, String rawArg3, String responder) {
        if (!FishSettings.partyCommandsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        String selfName = (mc.player != null) ? mc.player.getGameProfile().name() : null;
        boolean isMe = selfName != null && typer.equalsIgnoreCase(selfName);
        boolean isLocal = LOCAL.equals(responder);

        String ign = rawArg1 != null ? rawArg1 : typer;

        switch (cmd) {
            case "help", "?" -> { if (FishSettings.pcHelp && respond(cmd, typer, isLocal)) sendCmd(mc, responder, buildHelp()); }
            case "rtca"      -> { if (FishSettings.pcRtca && respond(cmd, typer, isLocal))    runRtcaForPlayer(mc, ign, responder);             }
            case "rtc"       -> { if (FishSettings.pcRtc  && respond(cmd, typer, isLocal)) {
                String rtcIgn; String levelArg;
                if (rawArg1 != null && rawArg1.matches("\\d+")) { rtcIgn = typer; levelArg = rawArg1; }
                else { rtcIgn = rawArg1 != null ? rawArg1 : typer; levelArg = rawArg2; }
                runRtcForPlayer(mc, rtcIgn, levelArg, responder);
            } }
            case "crtc"      -> { if (FishSettings.pcCrtc && respond(cmd, typer, isLocal)) {
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
            case "dprofit"   -> { if (FishSettings.pcDprofit && isMe) sendDprofit(mc, responder);              }
            case "crit"      -> { if (FishSettings.pcCrit    && isMe) sendCmd(mc, responder, fishmod.features.CritTracker.buildMessage()); }
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
            case "ai", "allinv" -> { if (FishSettings.pcAllinvite && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && respond(cmd, "*", isLocal)) sendRawCommand(mc, "p settings allinvite"); }
            case "d"            -> { if (FishSettings.pcDisband   && isMe) sendRawCommand(mc, "p disband");             }
            case "kick", "k"              -> { if (FishSettings.pcActionKick     && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendRawCommand(mc, "p kick " + resolvePartyTarget(mc, rawArg1));    }
            case "warp", "w"              -> { if (FishSettings.pcActionWarp     && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe))                    sendRawCommand(mc, "p warp");                }
            case "transfer", "pt", "ptme" -> { if (FishSettings.pcActionTransfer && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe))                    sendRawCommand(mc, "p transfer " + resolvePartyTarget(mc, ign));     }
            case "promote", "pro"         -> { if (FishSettings.pcActionPromote  && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendRawCommand(mc, "p promote " + resolvePartyTarget(mc, rawArg1));  }
            case "demote", "dem"          -> { if (FishSettings.pcActionDemote   && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe) && rawArg1 != null) sendRawCommand(mc, "p demote " + resolvePartyTarget(mc, rawArg1));   }
            default -> {
                if ((cmd.matches("[fm][1-7]") || cmd.equals("e")) && FishSettings.pcJoinFloor && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe)) handleJoinInstance(cmd, mc, responder);
                else if (cmd.matches("t[1-5]") && FishSettings.pcJoinFloor && partyActionAllowed(responder, isLocal) && allowPartyAction(typer, isMe)) handleKuudra(cmd, mc, responder);
            }
        }
    }

    public static boolean localEnabled(String cmd) {
        if (!FishSettings.partyCommandsEnabled) return false;
        return switch (cmd) {
            case "help", "?" -> FishSettings.pcHelp;
            case "rtca" -> FishSettings.pcRtca;
            case "rtc" -> FishSettings.pcRtc;
            case "crtc" -> FishSettings.pcCrtc;
            case "cata" -> FishSettings.pcCata;
            case "pb" -> FishSettings.pcPb;
            case "mp" -> FishSettings.pcMp;
            case "collection" -> FishSettings.pcCollection;
            case "secrets", "sa" -> FishSettings.pcSecrets;
            case "runs", "totalruns" -> FishSettings.pcRuns;
            case "dprofit" -> FishSettings.pcDprofit;
            case "crit" -> FishSettings.pcCrit;
            case "corpse", "corpses" -> FishSettings.pcCorpse;
            case "bank" -> FishSettings.pcBank;
            case "powder" -> FishSettings.pcPowder;
            case "nw", "networth" -> FishSettings.pcNw;
            case "level", "sblvl" -> FishSettings.pcLevel;
            case "farming" -> FishSettings.pcFarming;
            case "nuc", "nucleus" -> FishSettings.pcNuc;
            case "worm", "scatha" -> FishSettings.pcWorm;
            case "fps" -> FishSettings.pcFps;
            case "tps" -> FishSettings.pcTps;
            case "ping" -> FishSettings.pcPing;
            case "ai", "allinv" -> FishSettings.pcAllinvite;
            case "d" -> FishSettings.pcDisband;
            case "kick", "k" -> FishSettings.pcActionKick;
            case "warp", "w" -> FishSettings.pcActionWarp;
            case "transfer", "pt", "ptme" -> FishSettings.pcActionTransfer;
            case "promote", "pro" -> FishSettings.pcActionPromote;
            case "demote", "dem" -> FishSettings.pcActionDemote;
            default -> isFloor(cmd) || cmd.matches("t[1-5]") ? FishSettings.pcJoinFloor : false;
        };
    }

    private static final java.util.Map<String, Long> RECENT_RESPONSES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long RESPONSE_DEDUP_MS = 5000;
    private static boolean respond(String cmd, String typer, boolean isLocal) {
        return isLocal || shouldRespond(cmd, typer);
    }

    private static boolean shouldRespond(String cmd, String typer) {
        long now = System.currentTimeMillis();
        String key = cmd + "|" + typer.toLowerCase();
        Long last = RECENT_RESPONSES.get(key);
        if (last != null && now - last < RESPONSE_DEDUP_MS) return false;
        RECENT_RESPONSES.put(key, now);
        if (RECENT_RESPONSES.size() > 64) RECENT_RESPONSES.entrySet().removeIf(e -> now - e.getValue() > 30_000);
        return true;
    }

    private static boolean partyActionAllowed(String responder, boolean isLocal) {
        return isLocal || (responder != null && responder.startsWith("pc "));
    }

    private static boolean allowPartyAction(String typer, boolean isMe) {
        if (isMe) return true;
        if (fishmod.utils.NameList.contains(FishSettings.pcPartyActionsBlacklist, typer)) return false;
        return switch (FishSettings.pcPartyActionsMode) {
            case "everyone", "blacklist" -> true;
            case "whitelist" -> fishmod.utils.NameList.contains(FishSettings.pcPartyActionsWhitelist, typer);
            default -> false;
        };
    }

    private static String resolvePartyTarget(Minecraft mc, String fragment) {
        if (fragment == null || fragment.isBlank()) return fragment;
        String tracked = fishmod.features.dungeon.PartyMemberTracker.resolve(fragment);
        if (tracked != null) return tracked;
        if (mc.getConnection() == null) return fragment;
        String frag = fragment.toLowerCase();
        String bestPrefix = null;
        String bestFuzzy = null;
        int bestFuzzyDist = Integer.MAX_VALUE;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null) continue;
            String lname = name.toLowerCase();
            if (lname.equals(frag)) return name;
            if (lname.startsWith(frag)) {
                if (bestPrefix == null || lname.length() < bestPrefix.length()) bestPrefix = name;
            } else {
                int d = levenshtein(lname, frag);
                if (d < bestFuzzyDist) { bestFuzzyDist = d; bestFuzzy = name; }
            }
        }
        if (bestPrefix != null) return bestPrefix;
        if (bestFuzzy != null && bestFuzzyDist <= Math.max(1, frag.length() / 2)) return bestFuzzy;
        return fragment;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

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
        if (FishSettings.pcWorm)       cmds.add("worm");
        if (FishSettings.pcBank)       cmds.add("bank");
        if (FishSettings.pcPowder)     cmds.add("powder");
        if (FishSettings.pcCorpse)     cmds.add("corpses");
        if (FishSettings.pcDprofit)    cmds.add("dprofit");
        if (FishSettings.pcCrit)       cmds.add("crit");
        if (FishSettings.pcFps)        cmds.add("fps");
        if (FishSettings.pcTps)        cmds.add("tps");
        if (FishSettings.pcPing)       cmds.add("ping");
        if (FishSettings.pcAllinvite)  cmds.add("ai");
        if (FishSettings.pcJoinFloor)  cmds.add("e/f1-7/m1-7/t1-5");
        if (FishSettings.pcActionKick)     cmds.add("kick/k");
        if (FishSettings.pcActionWarp)     cmds.add("warp/w");
        if (FishSettings.pcActionTransfer) cmds.add("transfer/pt/ptme");
        if (FishSettings.pcActionPromote)  cmds.add("promote/pro");
        if (FishSettings.pcActionDemote)   cmds.add("demote/dem");
        if (FishSettings.pcDisband)    cmds.add("d");
        return "FishMod cmds: ." + String.join(" .", cmds);
    }

    private static void sendCmd(Minecraft mc, String responder, String text) {
        if (LOCAL.equals(responder)) {
            mc.execute(() -> fishmod.utils.FishMsg.send("§f" + text));
            return;
        }
        sendRawCommand(mc, responder + text);
    }

    private static void sendRawCommand(Minecraft mc, String command) {
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
            .execute(() -> mc.execute(() -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand(command);
                    ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
                }
            }));
    }

    public static boolean handleCommand(String fullCmd) {
        if (!FishSettings.partyCommandsEnabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        final String responder = "pc ";

        String[] parts = fullCmd.split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;
        String localName = mc.player != null ? mc.player.getName().getString() : null;
        String target = arg != null ? arg : localName;

        switch (cmd) {
            case "help": case "?":
                if (!FishSettings.pcHelp) return false;
                sendCmd(mc, responder, buildHelp());
                return true;
            case "ai": case "allinv":
                if (!FishSettings.pcAllinvite || target == null) return false;
                sendRawCommand(mc, "p settings allinvite");
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
                sendRawCommand(mc, "p disband");
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

    private static void runRtcaForPlayer(Minecraft mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> buildAndSendRtca(mc, data, ign, responder));
    }

    private static void runCataForPlayer(Minecraft mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            String level  = HypixelApi.formatLevel(data.cataXp);
            String toNext = HypixelApi.xpToNextLevel(data.cataXp);
            sendCmd(mc, responder, ign + "'s Cata: " + level + " | " + toNext + " XP to next");
        });
    }

    private static void runStatsForPlayer(Minecraft mc, String ign, String cmd, String floorArg, String responder) {
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
                        long[] times = (type == 'm') ? data.masterTimes : data.cataTimes;
                        count = num < times.length ? times[num] : 0;
                        label = (type == 'm' ? "M" : "F") + num;
                    } else {
                        count = data.totalRuns;
                        label = "Total";
                    }
                    sb.append(label).append(" Runs: ").append(String.format("%,d", count));
                }
            }
            sendCmd(mc, responder, sb.toString());
        });
    }

    private static void runTotalRunsForPlayer(Minecraft mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            sendCmd(mc, responder, ign + "'s Total Runs: " + String.format("%,d", data.totalRuns));
        });
    }

    private static void runPbForPlayer(Minecraft mc, String ign, String floor, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            boolean isMaster = floor == null || floor.toLowerCase().startsWith("m");
            int floorNum = 7;
            if (floor != null && floor.equalsIgnoreCase("e")) {
                floorNum = 0;
                isMaster = false;
            } else if (floor != null) {
                try { floorNum = Integer.parseInt(floor.substring(1)); } catch (Exception ignored) {}
            }
            String[] pbs = isMaster ? data.masterPbs : data.cataPbs;
            String pb = (floorNum >= 0 && floorNum < pbs.length) ? pbs[floorNum] : null;
            String label = (isMaster ? "M" : "F") + floorNum + " PB";
            sendCmd(mc, responder, ign + "'s " + label + ": " + (pb != null ? pb : "N/A"));
        });
    }

    private static void runMpForPlayer(Minecraft mc, String ign, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            String val = data.magicalPower >= 0 ? String.valueOf(data.magicalPower) : "N/A";
            sendCmd(mc, responder, ign + "'s MP: " + val);
        });
    }

    private static final long[] COLLECTION_MILESTONES = {1, 5, 10, 25, 50, 100, 250, 500, 1000};
    private static final long COLLECTION_MAX = 1000;

    private static String formatCollectionProgress(long col) {
        if (col >= COLLECTION_MAX) {
            return String.format("%,d/%,d (max)", col, COLLECTION_MAX);
        }
        long next = COLLECTION_MAX;
        for (long m : COLLECTION_MILESTONES) if (col < m) { next = m; break; }
        return String.format("%,d/%,d", col, next);
    }

    private static void runCollectionForPlayer(Minecraft mc, String ign, String floor, String responder) {
        HypixelApi.getByName(mc, ign, data -> {
            String label;
            String value;
            if (floor != null) {
                boolean isMaster = floor.toLowerCase().startsWith("m");
                int floorNum = 7;
                if (floor.equalsIgnoreCase("e")) {
                    floorNum = 0;
                    isMaster = false;
                } else {
                    try { floorNum = Integer.parseInt(floor.substring(1)); } catch (Exception ignored) {}
                    if (floorNum < 0) floorNum = 7;
                }
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
            sendCmd(mc, responder, ign + "'s " + label + ": " + value);
        });
    }

    private static void runRtcForPlayer(Minecraft mc, String ign, String levelArg, String responder) {
        int target = 50;
        if (levelArg != null) {
            try { target = Math.max(1, Math.min(999, Integer.parseInt(levelArg))); } catch (NumberFormatException ignored) {}
        }
        final int targetLevel = target;
        HypixelApi.getByName(mc, ign, data -> {
            long xpNeeded;
            if (targetLevel < HypixelApi.CATA_XP_TABLE.length) {
                xpNeeded = HypixelApi.CATA_XP_TABLE[targetLevel] - data.cataXp;
            } else {
                long over = (long)(targetLevel - 50) * HypixelApi.CATA_OVERFLOW_XP_PER_LEVEL;
                xpNeeded = HypixelApi.CATA_XP_TABLE[50] + over - data.cataXp;
            }
            long xpPerRun = Math.max(1, FishSettings.rtcCataXpPerRun);
            String result;
            if (xpNeeded <= 0) {
                result = "Done ✔";
            } else {
                long runs;
                if (FishSettings.rtcaIncludeDailyBonus) {
                    long bonusXp = (long)(5 * xpPerRun * 1.4);
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
            sendCmd(mc, responder, ign + "'s runs to Cata " + targetLevel + ": " + result);
        });
    }

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

    private static void runCrtcForPlayer(Minecraft mc, String ign, String classArg, String levelArg, String responder) {
        String classKey = resolveClass(classArg);
        if (classKey == null) {
            sendCmd(mc, responder, "Usage: .crtc [name] <healer|mage|berserk|archer|tank> [level]");
            return;
        }
        int target = 50;
        if (levelArg != null) {
            try { target = Math.max(1, Math.min(999, Integer.parseInt(levelArg))); } catch (NumberFormatException ignored) {}
        }
        final int targetLevel = target;
        HypixelApi.getByName(mc, ign, data -> {
            long curXp = data.classXp.getOrDefault(classKey, 0L);
            long goalXp;
            if (targetLevel < HypixelApi.CATA_XP_TABLE.length) {
                goalXp = HypixelApi.CATA_XP_TABLE[targetLevel];
            } else {
                long over = (long)(targetLevel - 50) * HypixelApi.CATA_OVERFLOW_XP_PER_LEVEL;
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
                    long bonusXp = (long)(5 * xpPerRun * 1.4);
                    if (xpNeeded <= bonusXp) runs = (long) Math.ceil(xpNeeded / (xpPerRun * 1.4));
                    else                     runs = 5 + (xpNeeded - bonusXp + xpPerRun - 1) / xpPerRun;
                } else {
                    runs = (xpNeeded + xpPerRun - 1) / xpPerRun;
                }
                String runsStr = runs >= 1_000 ? String.format("%.1fk", runs / 1_000.0) : Long.toString(runs);
                result = fmtCoins(xpNeeded) + " XP | " + runsStr + " runs";
            }
            sendCmd(mc, responder, ign + "'s " + disp + " to " + targetLevel + ": " + result);
        });
    }

    private static void sendDprofit(Minecraft mc, String responder) {
        double total = fishmod.features.PartyLootScreen.totalValueForChat();
        int runs = fishmod.features.PartyLootScreen.runsForChat();
        double avg = total / Math.max(1, runs);
        String pr = fishmod.features.PartyLootScreen.fmtCoinsPublic(avg);
        sendCmd(mc, responder, "Profit Per Run: " + pr + " (" + runs + " runs)");
    }

    private static void buildAndSendRtca(Minecraft mc, HypixelApi.DungeonData data, String ign, String responder) {
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
        sendCmd(mc, responder, out);
    }

    private static void handleJoinInstance(String cmd, Minecraft mc, String responder) {
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 26_000L) {
            long rem = (26_000L - elapsed) / 1_000L + 1L;
            sendCmd(mc, responder, "Wait " + rem + "s before joining.");
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
        Misc.addChatMessage(Component.literal("§7[FM] Sending: /" + joinCmd));
        sendRawCommand(mc, joinCmd);
    }

    private static void handleKuudra(String cmd, Minecraft mc, String responder) {
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 30_000L) {
            long rem = (30_000L - elapsed) / 1_000L + 1L;
            sendCmd(mc, responder, "Wait " + rem + "s before joining Kuudra.");
            return;
        }
        int tier = cmd.charAt(1) - '1';
        String joinCmd = "joininstance kuudra_" + KUUDRA_TIERS[tier];
        Misc.addChatMessage(Component.literal("§7[FM] Sending: /" + joinCmd));
        sendRawCommand(mc, joinCmd);
    }

    private static void sendCorpse(Minecraft mc, String ign, String responder) {
        HypixelApi.getEconomyByName(mc, ign, (bank, purse, corpses) ->
            sendCmd(mc, responder, ign + "'s Corpses: " + (corpses != null ? corpses : "N/A")));
    }

    private static void sendBank(Minecraft mc, String ign, String responder) {
        HypixelApi.getEconomyByName(mc, ign, (bank, purse, corpses) -> {
            String b = bank >= 0 ? fmtCoins(bank) : "N/A";
            String p = purse >= 0 ? fmtCoins(purse) : "N/A";
            sendCmd(mc, responder, ign + "'s Bank: " + b + " | Purse: " + p);
        });
    }

    private static void sendPowder(Minecraft mc, String ign, String responder) {
        HypixelApi.getPowderByName(mc, ign, data -> {
            if (!data.hasData()) {
                sendCmd(mc, responder, ign + "'s Powder: N/A");
                return;
            }
            String m = data.mithril  >= 0 ? String.format("%,d", data.mithril)  : "N/A";
            String g = data.gemstone >= 0 ? String.format("%,d", data.gemstone) : "N/A";
            String l = data.glacite  >= 0 ? String.format("%,d", data.glacite)  : "N/A";
            sendCmd(mc, responder, ign + "'s Powder: Mithril: " + m + " | Gemstone: " + g + " | Glacite: " + l);
        });
    }

    private static void sendNetworth(Minecraft mc, String ign, String responder) {
        fishmod.features.croesus.CroesusPrices.refreshIfStale();
        Misc.addChatMessage(Component.literal("§7[FM] Looking up " + ign + "'s networth..."));
        HypixelApi.getNetworth(mc, ign, (nw, prof) -> {
            if (nw == HypixelApi.NETWORTH_BLOCKED) { sendCmd(mc, responder, "FishMod API access is blocked for you by an admin."); return; }
            if (nw < 0) { sendCmd(mc, responder, ign + "'s Networth: N/A"); return; }
            sendCmd(mc, responder, ign + "'s Networth: " + fmtCoins(nw) + (prof != null ? " (" + prof + ")" : ""));
        });
    }

    private static void sendSkyblockLevel(Minecraft mc, String ign, String responder) {
        HypixelApi.getProfileStats(mc, ign, (sb, farm) ->
            sendCmd(mc, responder, ign + "'s SB Level: " + (sb >= 0 ? String.format("%.2f", sb) : "N/A")));
    }

    private static void sendFarming(Minecraft mc, String ign, String responder) {
        HypixelApi.getProfileStats(mc, ign, (sb, farm) ->
            sendCmd(mc, responder, ign + "'s Farming: " + (farm >= 0 ? String.format("%.2f", farm) : "N/A")));
    }

    private static void sendNucleus(Minecraft mc, String ign, String responder) {
        HypixelApi.getNucleusRuns(mc, ign, runs ->
            sendCmd(mc, responder, ign + "'s Nucleus Runs: " + (runs >= 0 ? String.format("%,d", runs) : "N/A")));
    }

    private static void sendWorm(Minecraft mc, String ign, String responder) {
        HypixelApi.getWormStats(mc, ign, s -> {
            if (!s.found) { sendCmd(mc, responder, ign + "'s Bestiary: N/A"); return; }
            String tier = "Tier " + s.tier + "/" + s.maxTier
                    + (s.nextTierKills != null ? " (" + String.format("%,d", s.total) + "/" + String.format("%,d", s.nextTierKills) + ")" : " (MAX)");
            sendCmd(mc, responder, ign + "'s Bestiary: Worm " + String.format("%,d", s.worm)
                    + " | Scatha " + String.format("%,d", s.scatha) + " | " + tier);
        });
    }

    private static String fmtCoins(double v) {
        if (v >= 1_000_000_000d) return String.format("%.2fB", v / 1_000_000_000d);
        if (v >= 1_000_000d)     return String.format("%.2fM", v / 1_000_000d);
        if (v >= 1_000d)         return String.format("%.1fk", v / 1_000d);
        return String.format("%,d", (long) v);
    }

    private static void sendFps(Minecraft mc, String responder) {
        int fps = mc.getFps();
        sendCmd(mc, responder, "FPS: " + fps);
    }

    public static double currentTps() {
        int filled = Math.min(tickIdx, TICK_TIMES.length);
        if (filled == 0) return -1;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_TIMES[i];
        double avgMs = (double) sum / filled;
        return Math.min(20.0, 1000.0 / avgMs);
    }

    private static void sendTps(Minecraft mc, String responder) {
        int filled = Math.min(tickIdx, TICK_TIMES.length);
        if (filled == 0) {
            sendCmd(mc, responder, "TPS: N/A");
            return;
        }
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_TIMES[i];
        double avgMs = (double) sum / filled;
        double tps = Math.min(20.0, 1000.0 / avgMs);
        String formatted = String.format("%.1f", tps);
        sendCmd(mc, responder, "TPS: " + formatted);
    }

    private static void sendPing(Minecraft mc, String responder) {
        if (mc.player == null || mc.getConnection() == null) return;
        int ping = fishmod.utils.PingTracker.latest();
        if (ping < 0) {
            var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null && entry.getLatency() > 0) ping = entry.getLatency();
        }
        if (ping < 0) {
            try { var si = mc.getCurrentServer(); if (si != null && si.ping > 0) ping = (int) si.ping; }
            catch (Exception ignored) {}
        }
        sendCmd(mc, responder, "Ping: " + (ping >= 0 ? ping + "ms" : "N/A"));
    }

}
