package fishmod.features.fishing

import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.SliderDoubleSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.fishing.FishingWorldSettings as W
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

// World + items. init() runs from Fishing.init(), addMenu()/describe() from FishingMenu.
object FishingWorld {
    // Cached once per tick so the entity render mixin does one static boolean read per entity.
    @JvmField @Volatile var anyOn = false
    @JvmField @Volatile var anyHookTimer = false
    @JvmField @Volatile var anyRare = false

    @JvmStatic
    fun init() {
        HookTimer.init()
        WormholeFinder.init()
        RareMobHighlight.init()
        FishingItemOverlays.init()
        ClientTickEvents.END_CLIENT_TICK.register { refresh() }
    }

    private fun refresh() {
        val on = FishingSettings.fishing && W.fishingWorld
        anyHookTimer = on && W.hookTimer
        anyRare = on && (W.rareHighlight || W.rareHp || W.rareImmunity)
        anyOn = on && (W.hookTimer || W.rareHighlight || W.hideOtherHooks || W.hidePlayersNearBobber)
    }

    fun addMenu(col: Column) {
        val world = Feature("Fishing World", W::class.java, "fishingWorld")
        world.sub.add(ToggleSetting("Hook Timer", "Show the bite countdown and bite indicator at your hook", W::class.java, "hookTimer"))
        world.sub.add(ToggleSetting("Hide Hook Timer Stand", "Hide Hypixel's own timer text", W::class.java, "hookTimerHideStand").gatedBy { W.hookTimer })
        world.sub.add(SliderDoubleSetting("Hook Timer Scale", "", W::class.java, "hookTimerScale", 0.5, 4.0).gatedBy { W.hookTimer })
        world.sub.add(InputSetting("Bite Text", "Shown when a fish bites", W::class.java, "hookBiteText").gatedBy { W.hookTimer })
        world.sub.add(ToggleSetting("Highlight Rare Creatures", "Glow rare sea creatures", W::class.java, "rareHighlight"))
        world.sub.add(ColorPickerSetting("Highlight Color", "", W::class.java, "rareHighlightColor").gatedBy { W.rareHighlight })
        world.sub.add(ToggleSetting("Rare Creature HP", "Show HP above rare sea creatures", W::class.java, "rareHp"))
        world.sub.add(ToggleSetting("Spawn Immunity Timer", "Show the 5 s spawn immunity countdown", W::class.java, "rareImmunity"))
        world.sub.add(ToggleSetting("Hide Other Hooks", "Do not render other players' fishing hooks", W::class.java, "hideOtherHooks"))
        world.sub.add(ToggleSetting("Hide Players Near Bobber", "Do not render players close to your hook", W::class.java, "hidePlayersNearBobber"))
        world.sub.add(SliderDoubleSetting("Hide Radius", "Blocks around your hook", W::class.java, "hidePlayersNearBobberRadius", 2.0, 30.0).gatedBy { W.hidePlayersNearBobber })
        col.features.add(world)

        val wh = Feature("Wormhole Finder", W::class.java, "wormholeFinder")
        wh.sub.add(ToggleSetting("Departure Alert", "Title when the wormhole closes (uses the Wormhole alert position in the HUD editor)", W::class.java, "wormholeDeparture"))
        wh.sub.add(ColorPickerSetting("Waypoint Color", "", W::class.java, "wormholeColor"))
        wh.sub.add(ToggleSetting("Debug Raw Arrows", "Print each nearby text display (text, position, heading) to chat", W::class.java, "wormholeDebug"))
        col.features.add(wh)

        val items = Feature("Fishing Items", W::class.java, "fishingItems")
        items.sub.add(ToggleSetting("Trash Book Highlight", "Highlight enchanted books from the list below", W::class.java, "trashBooks"))
        items.sub.add(InputSetting("Trash Books", "Comma separated book names, e.g. Corruption I,Corruption 1", W::class.java, "trashBooksList").gatedBy { W.trashBooks })
        items.sub.add(ColorPickerSetting("Trash Book Color", "", W::class.java, "trashBooksColor").gatedBy { W.trashBooks })
        items.sub.add(ToggleSetting("Rod Part Abbreviations", "Show hook/line/sinker initials on fishing rods", W::class.java, "rodParts"))
        items.sub.add(ToggleSetting("Thunder Bottle Charge", "Show charge % on Thunder, Storm and Hurricane bottles", W::class.java, "thunderBottle"))
        col.features.add(items)
    }

    fun describe(name: String): String? = when (name) {
        "Fishing World" -> "Hook timer, rare creature highlight/HP and hiding hooks (all off by default)"
        "Wormhole Finder" -> "Wear Froggles on Lotus Atoll or Crimson Isle: marks the wormhole the arrows point at (off by default, untested)"
        "Fishing Items" -> "Item overlays: trash books, rod parts, bottle charge (all off by default)"
        else -> null
    }
}
