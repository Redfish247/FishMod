package fishmod.features.fishing

import com.google.gson.Gson
import com.mojang.blaze3d.platform.InputConstants
import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputIntSetting
import fishmod.features.FishModScreen.KeybindSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.utils.FishMsg
import fishmod.utils.IoExecutor
import fishmod.utils.SafeFiles
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.utils.events.interfaces.WorldHook
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import java.io.File
import fishmod.features.fishing.FishingTrackerSettings as S

// Phase 3: sea creature, profit, efficiency, barn, festival and per-area trackers. All OFF by default.
// Every chat string here is adapted from Feesh and UNVERIFIED in game.
object FishingTrackers {
    private val FILE = File("config/fishmod/fishing_trackers.json")
    private const val SAVE_EVERY_TICKS = 200

    @JvmStatic lateinit var pauseKey: KeyMapping; private set
    private var ticks = 0
    private var castTicks = 0
    private const val CAST_POLL_TICKS = 5

    @JvmStatic
    fun init() {
        load()
        FishingTrackerHud.init()
        pauseKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("Pause Fishing Trackers", fishmod.compat.GLFW.KEYSYM, InputConstants.UNKNOWN.value, fishmod.utils.FishKeys.category()),
        )

        FishingEvents.seaCreature.register(SeaCreatureHook { e ->
            if (on()) FishingTrackerStore.onCatch(e.creature, e.count, e.cocoon, e.location, e.atMs)
        })
        FishingEvents.rareDrop.register(RareDropHook { d ->
            if (on()) FishingTrackerStore.onDrop(d.itemName, d.price)
        })
        FishEvents.chat.register { c ->
            // setting first, then cheap contains tests inside the parsers, regex last
            if (on()) FishDiag.guard("FishingTrackers.1", "fishing tracker chat parse failed") { onChat(c.chatPlain) }
            false
        }
        FishEvents.worldChange.register(WorldHook { if (on()) FishingTrackerStore.onWorldChange(); false })

        ClientTickEvents.END_CLIENT_TICK.register {
            while (pauseKey.consumeClick()) {
                if (!on()) continue
                val p = !FishingTrackerStore.paused
                FishingTrackerStore.setPaused(p)
                FishMsg.send(if (p) "§b[Fishing] §fTrackers paused" else "§b[Fishing] §fTrackers resumed")
            }
            if (++castTicks >= CAST_POLL_TICKS) {
                castTicks = 0
                // passive: only reads whether our own hook exists
                if (on() && net.minecraft.client.Minecraft.getInstance().player?.fishing != null) FishingTrackerStore.noteCast(System.currentTimeMillis())
            }
            if (++ticks >= SAVE_EVERY_TICKS) { ticks = 0; if (FishingTrackerStore.dirty) save() }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { if (FishingTrackerStore.dirty) save(sync = true) }
    }

    private fun on() = S.fishingTrackers && Fishing.enabled()

    private fun onChat(p: String) {
        if (p.isEmpty()) return
        val now = System.currentTimeMillis()
        if (p == FishingTrackerMath.FESTIVAL_ENDED) { FishingTrackerStore.onFestivalEnd(); return }
        if (p == FishingTrackerMath.BARN_FULL) { FishingTrackerStore.onBarnFull(); return }
        for (a in FishingTrackerAreas.ALL) {
            val parse = a.line ?: continue
            if (!a.inArea(Fishing.location())) continue
            val line = parse(p) ?: continue
            FishingTrackerStore.onLine(a.id, line, now)
            return
        }
    }

    // ── persistence ──

    private fun load() {
        if (!FILE.exists()) return
        try {
            FILE.reader().use { r -> Gson().fromJson(r, FishingTotals::class.java)?.let { FishingTrackerStore.totals = it } }
        } catch (e: Exception) {
            FishDiag.fail("FishingTrackers.2", "fishing tracker file unreadable", e)
            SafeFiles.quarantine(FILE, e)
        }
    }

    private fun save(sync: Boolean = false) {
        FishingTrackerStore.dirty = false
        val json = Gson().toJson(FishingTrackerStore.totals)
        val job = Runnable { FishDiag.guard("FishingTrackers.3", "fishing tracker file write failed") { SafeFiles.writeAtomic(FILE, json) } }
        if (sync) job.run() else IoExecutor.execute(job)
    }

    // ── menu ──

    fun addMenu(col: Column) {
        val f = Feature("Fishing Trackers", S::class.java, "fishingTrackers")
        f.sub.add(ButtonSetting("Show", "Session or Total numbers on every tracker HUD", { S.fishingTrackerMode }, Runnable {
            S.fishingTrackerMode = if (S.fishingTrackerMode == "Total") "Session" else "Total"
            FishingTrackerStore.version++
        }))
        f.sub.add(ButtonSetting("Show When", "Both = on a fishing island and rod cast in the last 60 s; Either = one of them", { S.fishingShowWhen }, Runnable {
            S.fishingShowWhen = if (S.fishingShowWhen == "Either") "Both" else "Either"
        }))
        f.sub.add(SliderIntSetting("Max Rows", "Rows per tracker list", S::class.java, "fishingTrackerLines", 1, 30).gatedBy { S.fishingScHud || S.fishingProfitHud || S.fishingAreaHud })
        f.sub.add(ToggleSetting("Sea Creature Tracker", "Counts per creature with percent, double hooks and cocoons", S::class.java, "fishingScHud"))
        f.sub.add(ToggleSetting("Profit Tracker", "Rare drop value, costs, net and coins per hour", S::class.java, "fishingProfitHud"))
        f.sub.add(InputIntSetting("Cost Per Catch", "Coins spent per sea creature (bait), subtracted from profit", S::class.java, "fishingCostPerCatch").gatedBy { S.fishingProfitHud })
        f.sub.add(ToggleSetting("Efficiency Tracker", "Sea creatures and catches per hour", S::class.java, "fishingEffHud"))
        f.sub.add(ToggleSetting("Barn Fishing Timer", "Time and count since you started filling the barn", S::class.java, "fishingBarnHud"))
        f.sub.add(SliderIntSetting("Barn Warning (s)", "Barn timer turns red after this long", S::class.java, "fishingBarnSeconds", 30, 900, 30).gatedBy { S.fishingBarnHud })
        f.sub.add(ToggleSetting("Fishing Festival Tracker", "Sharks caught during the festival", S::class.java, "fishingFestivalHud"))
        f.sub.add(ToggleSetting("Area Trackers", "Rare creatures and drops of the world you are in (Crimson Isle, Bayou, Lotus Atoll, ...)", S::class.java, "fishingAreaHud"))
        f.sub.add(ToggleSetting("Treasure Tracker", "Treasure catches in the area HUD (any world)", S::class.java, "fishingAreaTreasure").gatedBy { S.fishingAreaHud })
        f.sub.add(ToggleSetting("Archfiend Dice Tracker", "Dice rolls and net coins in the area HUD, after a roll", S::class.java, "fishingAreaDice").gatedBy { S.fishingAreaHud })
        f.sub.add(KeybindSetting("Pause Key", "Pause or resume every fishing tracker", { pauseKey }))
        f.sub.add(ButtonSetting("Reset Session", "Clear session numbers", "Reset", Runnable { FishingTrackerStore.resetSession() }))
        f.sub.add(ButtonSetting("Reset Total", "Clear saved total numbers", "Reset", Runnable { FishingTrackerStore.resetTotal(); save() }))
        col.features.add(f)
    }

    fun describe(name: String): String? = when (name) {
        "Fishing Trackers" -> "Sea creature, profit, efficiency, barn, festival and per-area trackers (all HUDs off by default)"
        else -> null
    }
}
