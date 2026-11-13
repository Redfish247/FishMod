package fishmod.features.fishing

import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputIntSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.SoundSearchSetting
import fishmod.features.FishModScreen.SubcategoryHeader
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.fishing.FishingAlertSettings as S
import fishmod.utils.FishMsg
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.utils.sound.SoundManager
import net.minecraft.network.chat.Component

object FishingAlerts {
    @JvmStatic fun init() {
        FishingAlertsWorld.init()
        FishingTitles.init()
        FishingEvents.seaCreature.register { e -> onCatch(e); false }
        FishingEvents.rareDrop.register { d -> onDrop(d); false }
        FishEvents.chat.register { c ->
            // setting first, then the cheap prefix tests inside, regex last
            if (S.fishingAlerts && (S.alertWormholeGone || S.alertNewTrophy) && Fishing.active()) onChat(c.chatPlain)
            false
        }
    }

    private fun onCatch(e: SeaCreatureCaught) {
        if (!Fishing.active()) return
        if (S.fishingAlerts && S.alertSeaCreature && (e.creature.rare || !S.alertScRareOnly)) {
            val (m, sub) = creatureTitle(e.creature, e.count)
            title(FishingTitles.CREATURE, m, sub)
            sound(S.alertScSound, S.alertScVolume)
        }
        FishingShare.onCatch(e)
    }

    private fun onDrop(d: RareDrop) {
        if (!Fishing.active()) return
        if (S.fishingAlerts && S.alertRareDrop && d.price >= S.alertDropMinPrice) {
            val (m, sub) = dropTitle(d.itemName, d.price)
            title(FishingTitles.DROP, m, sub)
            sound(S.alertSound, S.alertSoundVolume)
        }
        FishingShare.onDrop(d)
    }

    private fun onChat(plain: String) {
        if (S.alertWormholeGone && FishingAlertText.isWormholeGone(plain)) {
            if (Fishing.inArea(fishmod.utils.SkyblockLocation.LOTUS_ATOLL) || Fishing.inArea(fishmod.utils.SkyblockLocation.CRIMSON_ISLE))
                notify(FishingTitles.WORMHOLE, wormholeTitle(true), null)
            WormholeFinder.onGone()
            return
        }
        if (S.alertNewTrophy) {
            val d = FishingAlertText.newDiscovery(plain, Fishing.location() == fishmod.utils.SkyblockLocation.CRIMSON_ISLE) ?: return
            notify(FishingTitles.TROPHY, trophyTitle(d), null)
        }
    }

    // Title texts (also used by the HUD editor examples)
    fun creatureTitle(sc: SeaCreature, count: Int) = "§${sc.rarity.code}§l${sc.name.uppercase()}!" to (if (count > 1) "§7x$count" else "")

    fun dropTitle(item: String, price: Double) =
        "§6§lRARE DROP!" to "§f$item" + (if (price > 0) " §7(§6${FishingAlertText.fmtCoins(price)}§7)" else "")

    fun hotspotTitle(perk: String?) = if (perk != null) "§6§lHotspot found" to "§f$perk" else "§c§lHotspot is gone" to ""

    fun wormholeTitle(gone: Boolean) = (if (gone) "§c§lWormhole is gone" else "§c§lWormhole closed!") to ""

    fun trophyTitle(discovery: String) = "§b§lNEW DISCOVERY" to "§f$discovery"

    fun deployableTitle(name: String) = "§e§l$name expires soon" to ""

    fun baitTitle(name: String, left: Int?) = if (left == null) "§b§lBait changed" to "§f$name" else "§c§lOut of bait soon" to "§f$left $name"

    fun notify(slot: FishingTitles.Slot, t: Pair<String, String>, chat: String?) = notify(slot, t.first, t.second, chat)

    fun notify(slot: FishingTitles.Slot, main: String, sub: String, chat: String?) {
        title(slot, main, sub)
        sound(S.alertSound, S.alertSoundVolume)
        if (chat != null) FishMsg.send("§7$chat")
    }

    private fun title(slot: FishingTitles.Slot, main: String, sub: String) =
        FishingTitles.show(slot, main, sub, S.alertTitleMs.coerceIn(500, 10_000))

    private fun sound(name: String, volPct: Int) {
        var vol = volPct.coerceIn(0, 500) / 100f
        val snd = SoundManager.preset(name)
        while (vol > 0.01f) { SoundManager.play(snd, minOf(vol, 1f)); vol -= 1f }
    }

    @JvmStatic fun compact(plain: String): Component? {
        if (!S.compactDoubleHook) return null
        if (!Fishing.active()) return null
        FishingAlertText.compactDoubleHook(plain)?.let { return Component.literal(it) }
        if (S.compactSpawn && plain.length > 12) {
            val sc = SeaCreatures.match(plain, Fishing.location(), FishingSettings.fishingAnyArea) ?: return null
            if (sc.rare || sc.boss) return Component.literal(FishingAlertText.compactSpawn(sc))
        }
        return null
    }


    fun addMenu(col: Column) {
        val a = Feature("Fishing Alerts", S::class.java, "fishingAlerts")
        a.sub.add(ToggleSetting("Sea Creature", "Title and sound when you catch one", S::class.java, "alertSeaCreature"))
        a.sub.add(ToggleSetting("Rare Creatures Only", "Skip common catches", S::class.java, "alertScRareOnly").gatedBy { S.alertSeaCreature })
        a.sub.add(SoundSearchSetting("Creature Sound", "Type to search every game sound", { S.alertScSound }, { v -> S.alertScSound = v }, { S.alertScVolume }).gatedBy { S.alertSeaCreature })
        a.sub.add(SliderIntSetting("Creature Volume %", "", S::class.java, "alertScVolume", 0, 500, 5).gatedBy { S.alertSeaCreature })
        a.sub.add(ToggleSetting("Rare Drop", "Title with the item and its price", S::class.java, "alertRareDrop"))
        a.sub.add(InputIntSetting("Min Drop Price", "Only alert for drops worth at least this many coins (0 = all)", S::class.java, "alertDropMinPrice").gatedBy { S.alertRareDrop })
        a.sub.add(ToggleSetting("Hotspot Found", "Title when you get close to a hotspot", S::class.java, "alertHotspotFound"))
        a.sub.add(ToggleSetting("Hotspot Gone", "Title when the hotspot you fished in despawns", S::class.java, "alertHotspotGone"))
        a.sub.add(ToggleSetting("Wormhole Gone", "Lotus Atoll and Crimson Isle", S::class.java, "alertWormholeGone"))
        a.sub.add(ToggleSetting("New Trophy Fish", "Title on a NEW DISCOVERY line", S::class.java, "alertNewTrophy"))
        a.sub.add(ToggleSetting("Deployable Expiring", "Totem, Black Hole, Umberella, lanterns and flux orbs", S::class.java, "alertDeployable"))
        a.sub.add(SliderIntSetting("Expiry Seconds", "Alert when this many seconds remain", S::class.java, "alertDeployableSecs", 5, 300, 5).gatedBy { S.alertDeployable })
        a.sub.add(ToggleSetting("Bait", "Alert when bait changes or runs low", S::class.java, "alertBait"))
        a.sub.add(SliderIntSetting("Low Bait Count", "", S::class.java, "alertBaitLow", 1, 64).gatedBy { S.alertBait })
        a.sub.add(SoundSearchSetting("Alert Sound", "Sound for every alert except creatures", { S.alertSound }, { v -> S.alertSound = v }, { S.alertSoundVolume }).gatedBy { (S.alertRareDrop || S.alertHotspotFound || S.alertHotspotGone || S.alertWormholeGone || S.alertNewTrophy || S.alertDeployable || S.alertBait) })
        a.sub.add(SliderIntSetting("Alert Volume %", "", S::class.java, "alertSoundVolume", 0, 500, 5).gatedBy { (S.alertRareDrop || S.alertHotspotFound || S.alertHotspotGone || S.alertWormholeGone || S.alertNewTrophy || S.alertDeployable || S.alertBait) })
        a.sub.add(SliderIntSetting("Title Time (ms)", "", S::class.java, "alertTitleMs", 500, 10000, 100).gatedBy { (S.alertSeaCreature || (S.alertRareDrop || S.alertHotspotFound || S.alertHotspotGone || S.alertWormholeGone || S.alertNewTrophy || S.alertDeployable || S.alertBait)) })
        col.features.add(a)

        val share = Feature("Fishing Share", S::class.java, "shareEnabled")
        share.sub.add(ToggleSetting("Party Chat", "Send ticked creatures and drops to party chat", S::class.java, "shareParty"))
        share.sub.add(ToggleSetting("World Boss To All Chat", "Also send world bosses with coords to all chat", S::class.java, "shareWorldBossAll"))
        share.sub.add(ToggleSetting("Rare Drops", "Share drops worth at least the price below", S::class.java, "shareDrops"))
        share.sub.add(InputIntSetting("Drop Price", "Coins", S::class.java, "shareDropMinPrice").gatedBy { S.shareDrops })
        share.sub.add(ToggleSetting("Share Hotspot", "Adds clickable [PARTY] and [ALL] buttons to the hotspot message; each click sends one chat line, never automatic", S::class.java, "shareHotspot"))
        share.sub.add(SubcategoryHeader("Share These Creatures"))
        for (sc in FishingShare.bigMobs) {
            share.sub.add(ToggleSetting(sc.name, "", { FishingShare.isChecked(sc.name) }, { v -> FishingShare.setChecked(sc.name, v) }).gatedBy { S.shareParty || S.shareWorldBossAll })
        }
        col.features.add(share)

        val compact = Feature("Compact Catch Messages", S::class.java, "compactDoubleHook")
        compact.sub.add(ToggleSetting("Spawn Lines", "Shorten rare creature spawn messages", S::class.java, "compactSpawn").gatedBy { S.compactDoubleHook })
        col.features.add(compact)
    }

    fun describe(name: String): String? = when (name) {
        "Fishing Alerts" -> "Titles and sounds for catches, drops, hotspots, deployables and bait (all off by default)"
        "Fishing Share" -> "Opt-in sharing of big catches to party chat; nothing is sent unless ticked"
        "Compact Catch Messages" -> "Shortens the Double Hook line; spawn lines have their own toggle"
        else -> null
    }
}
