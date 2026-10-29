# #360 — Network Protocol Error on Hypixel join (26.2, 1.6.382) — Bug

[needs-info] Player Paulvier is kicked with "Network Protocol Error" joining Hypixel; removing FishMod fixes it, removing Odin/Noamm does not.

## Done (commit 19cda198, after 1.6.382)
- `PacketErrorGuardMixin`: a packet-handler exception with a FishMod frame no longer disconnects; it is logged once per packet type as `[FishMod] FishMod threw while handling <Packet>, kept the connection` with the full stack trace.
- Location/InstallHeartbeat handlers catch Throwable; SessionStats/RemoteSync join resets guarded.
- Ruled out: Hypixel Mod API jar (only Fabric build is `1.0.2+build.1+mc26.1`, listed for 26.2; same jar Odin/Noamm bundle), duplicate payload/channel ids (we register none), 26.2 overrides (none touch networking).

## Ask Paulvier for (if it still happens on the next build)
1. `.minecraft/logs/latest.log` from the failed join, whole file. Lines that matter:
   - `Failed to handle packet` / `Error handling packet` and the stack trace under it
   - any `[FishMod]` line, especially `kept the connection`
   - `Encountered exception` / `Exception caught in connection` / `Packet ... too large` / `DecoderException` (decode side, not covered by the guard)
2. The file the disconnect screen links: `.minecraft/debug/disconnect-*.txt` (26.x writes one per protocol error; it names the packet and the throwing class).
3. Mod list (`mods` folder names) and whether the Hypixel Mod API mod is also installed separately, plus the version.
4. If the guard line shows up but he can now join: that stack is the real bug; send it here.

## Still unknown
- Which packet throws. If the disconnect report shows a decode error (netty thread) or no FishMod frame, the cause is outside what the guard catches.
