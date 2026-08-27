package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Easing
import fishmod.utils.config.Config
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.Buttons
import fishmod.utils.config.values.Dungeons
import fishmod.cosmetic.NickState
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.dungeon.Split
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import fishmod.utils.rendering.NvgRecorder
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.reflect.KMutableProperty0

/** Multi-column config screen; each column scrolls independently and rows expand inline sub-panels. */
class FishModScreen : Screen(Component.literal("FishMod")), HasNvgOverlay {

    private val columns: MutableList<Column> = ArrayList()
    private var searchText = ""
    private var searchFocused = false
    private var activeSlider: Setting? = null
    private var activeSliderX = 0
    private var activeSliderW = SLIDER_W
    private var activeInput: Setting? = null
    private var capturingKeybind: KeybindSetting? = null
    private var searchField: EditBox? = null
    private var resetArmed = false
    private var resetArmedAt = 0L
    private var hoverDesc: String? = null
    private var hoverDescX = 0
    private var hoverDescY = 0
    private var hScroll = 0
    private var hScrollAnim = 0.0
    private var dragColumn: Column? = null
    private var dragGrabDX = 0
    private var dragMouseX = 0
    /** True when the current [dragColumn] drag was started with right-click: on release it merges
     *  onto whatever header it's dropped over instead of just reordering top-level slots. */
    private var dragColumnMerge = false

    // Stack-segment drag state (nested columns-under-a-column, rendered as a vertical stack).
    // Left-drag reorders a segment among its siblings live, mirroring dragColumn's snap;
    // right-drag restructures on release instead — dropped onto another column it merges there,
    // dropped onto empty space it pops back out to top level, dropped back onto its own parent
    // it's left alone.
    private var dragTabParent: Column? = null
    private var dragTabChild: Column? = null
    private var dragTabGrabDY = 0
    private var dragTabMouseX = 0
    private var dragTabMouseY = 0
    private var dragTabRightClick = false

    init {
        buildCategories()
        applySavedColumnOrder()
        // Watchdog: paintNvgOverlay() is only ever invoked via GameRendererNvgMixin, so if that
        // injection never fires for some reason (e.g. another rendering mod reshaping the render
        // pipeline this mixin targets), the screen would otherwise sit blank forever with zero log
        // output. Surface it to the player instead of failing silently.
        fishmod.utils.Scheduler.scheduleTask({
            if (paintCount == 0 && Minecraft.getInstance().screen === this) {
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] paintNvgOverlay was never invoked - the GameRendererNvgMixin hook didn't fire (likely a rendering-mod conflict)")
                fishmod.utils.Misc.addChatMessage(Component.literal(
                    "§c[FishMod] The /fm screen failed to render (a rendering mod may be conflicting). Please report this to the mod author."
                ))
            }
        }, 40)
    }

    private fun buildCategories() {
        val general = Column("General", "gear")
        val dungeon = Column("Dungeon", "arch")
        val cosmetics = Column("Cosmetics", "hanger")
        val party = Column("Party", "people")
        val visuals = Column("Visuals", "eye")
        val floor7 = Column("Floor 7", "arch")
        val dungeonMap = Column("Dungeon Map", "map")

        // ===== General =====
        run {
            val f = Feature("Mod Prefix", FishSettings::modPrefixEnabled)
            f.sub.add(InputSetting("Prefix", "",
                { FishSettings.modPrefix },
                { v -> FishSettings.modPrefix = if (v != null && v.length > 10) v.substring(0, 10) else v }))
            general.features.add(f)
        }
        run {
            val f = Feature("Inventory Buttons", Buttons::enableInventoryButtons)
            f.sub.add(makeButtonInput("Button 1", Buttons::command1))
            f.sub.add(makeButtonInput("Button 2", Buttons::command2))
            f.sub.add(makeButtonInput("Button 3", Buttons::command3))
            f.sub.add(makeButtonInput("Button 4", Buttons::command4))
            f.sub.add(makeButtonInput("Button 5", Buttons::command5))
            f.sub.add(makeButtonInput("Button 6", Buttons::command6))
            f.sub.add(makeButtonInput("Button 7", Buttons::command7))
            general.features.add(f)
        }
        run {
            val f = Feature("Wardrobe Hotkeys", FishSettings::wardrobeHotkeysEnabled)
            f.sub.add(ToggleSetting("Auto-Close GUI", "", FishSettings::wardrobeHotkeysAutoClose))
            f.sub.add(SubcategoryHeader("Click a slot, then press a key/mouse button (Esc unbinds)"))
            val slots = fishmod.utils.Keybinds.wardrobeSlots
            if (slots != null) {
                for (i in slots.indices) {
                    val idx = i
                    f.sub.add(KeybindSetting("Slot " + (idx + 1), "",
                        { fishmod.utils.Keybinds.wardrobeSlots!![idx] }))
                }
            }
            general.features.add(f)
        }
        general.features.add(Feature("Smart Copy Chat", FishSettings::smartCopyChat))
        general.features.add(Feature("Compact Chat", FishSettings::chatCompact))
        general.features.add(Feature("Auto Sprint", FishSettings::autoSprintEnabled))
        run {
            val f = Feature("Sound Manager", FishSettings::soundMasterEnabled)
            f.sub.add(SliderIntSetting("Master Volume %", "Applied to every FishMod feature cue", FishSettings::soundMasterVolume, 0, 100))
            general.features.add(f)
        }
        run {
            val f = Feature("Compact Tab", FishSettings::compactTabEnabled)
            f.sub.add(SliderIntSetting("Opacity %", "", FishSettings::compactTabOpacity, 0, 100))
            f.sub.add(ToggleSetting("Stat Bar", "SERVER/TPS/FPS/PING strip", FishSettings::compactTabStatBarEnabled))
            f.sub.add(DropdownSetting("Stat Bar Position", "", arrayOf("TOP", "BOTTOM", "LEFT", "RIGHT"),
                { FishSettings.compactTabStatBarPosition },
                { v -> FishSettings.compactTabStatBarPosition = v }))
            general.features.add(f)
        }
        run {
            val f = Feature("Custom Scoreboard", FishSettings::customScoreboardEnabled)
            f.sub.add(SliderIntSetting("Opacity %", "", FishSettings::customScoreboardOpacity, 0, 100))
            f.sub.add(SliderIntSetting("Y Offset", "", FishSettings::customScoreboardHudY, 0, 200))
            f.sub.add(ToggleSetting("Compact Numbers", "1,234,567 -> 1.2M", FishSettings::customScoreboardCompactNumbers))
            f.sub.add(ToggleSetting("Vanilla In Dungeons", "Show vanilla's sidebar while in a dungeon", FishSettings::customScoreboardHideInDungeon))
            f.sub.add(SubcategoryHeader("Location & Time"))
            f.sub.add(ToggleSetting("Date", "", FishSettings::sbSectionDate))
            f.sub.add(ToggleSetting("Time of Day", "", FishSettings::sbSectionTime))
            f.sub.add(ToggleSetting("Location", "", FishSettings::sbSectionLocation))
            f.sub.add(ToggleSetting("Players", "", FishSettings::sbSectionPlayers))
            f.sub.add(ToggleSetting("Game Mode", "", FishSettings::sbSectionGameMode))
            f.sub.add(SubcategoryHeader("Currencies"))
            f.sub.add(ToggleSetting("Purse", "", FishSettings::sbSectionPurse))
            f.sub.add(ToggleSetting("Bank", "", FishSettings::sbSectionBank))
            f.sub.add(ToggleSetting("Motes", "", FishSettings::sbSectionMotes))
            f.sub.add(ToggleSetting("Bits", "", FishSettings::sbSectionBits))
            f.sub.add(ToggleSetting("Copper", "", FishSettings::sbSectionCopper))
            f.sub.add(ToggleSetting("Sowdust", "", FishSettings::sbSectionSowdust))
            f.sub.add(ToggleSetting("Gems", "", FishSettings::sbSectionGems))
            f.sub.add(ToggleSetting("North Stars", "", FishSettings::sbSectionNorthStars))
            f.sub.add(ToggleSetting("Soulflow", "", FishSettings::sbSectionSoulflow))
            f.sub.add(SubcategoryHeader("Activities"))
            f.sub.add(ToggleSetting("Heat", "", FishSettings::sbSectionHeat))
            f.sub.add(ToggleSetting("Cold", "", FishSettings::sbSectionCold))
            f.sub.add(ToggleSetting("Guild", "", FishSettings::sbSectionGuild))
            f.sub.add(ToggleSetting("Cookie Buff", "", FishSettings::sbSectionCookie))
            f.sub.add(ToggleSetting("Skill Average", "", FishSettings::sbSectionSkillAverage))
            f.sub.add(ToggleSetting("Objective", "", FishSettings::sbSectionObjective))
            f.sub.add(ToggleSetting("Slayer", "", FishSettings::sbSectionSlayer))
            f.sub.add(ToggleSetting("Powder (HotM)", "", FishSettings::sbSectionPowder))
            f.sub.add(ToggleSetting("Diana", "", FishSettings::sbSectionDiana))
            f.sub.add(ToggleSetting("Party", "", FishSettings::sbSectionParty))
            f.sub.add(ToggleSetting("Power/Tuning", "", FishSettings::sbSectionEquipment))
            f.sub.add(ToggleSetting("Dungeon Stats", "", FishSettings::sbSectionDungeon))
            f.sub.add(ToggleSetting("Pet", "", FishSettings::sbSectionPet))
            f.sub.add(ToggleSetting("Other Lines", "", FishSettings::sbSectionOther))
            f.sub.add(SubcategoryHeader("Extras"))
            f.sub.add(ToggleSetting("TPS", "", FishSettings::sbSectionTps))
            f.sub.add(ToggleSetting("Ping", "", FishSettings::sbSectionPing))
            f.sub.add(ToggleSetting("FPS", "", FishSettings::sbSectionFps))
            f.sub.add(ToggleSetting("Pet", "Same pet PetHud already tracks", FishSettings::sbSectionPetExtra))
            f.sub.add(ToggleSetting("Skills", "Hypixel API, refreshes every 60s", FishSettings::sbSectionSkills))
            f.sub.add(ToggleSetting("Bestiary %", "Hypixel API, refreshes every 60s", FishSettings::sbSectionBestiary))
            f.sub.add(ToggleSetting("Collections", "Hypixel API, refreshes every 60s", FishSettings::sbSectionCollections))
            f.sub.add(ToggleSetting("Election (Mayor)", "Public Hypixel API, refreshes hourly", FishSettings::sbSectionElection))
            f.sub.add(ToggleSetting("Fire Sales", "Public Hypixel API, refreshes every 5min", FishSettings::sbSectionFireSales))
            general.features.add(f)
        }
        run {
            val f = Feature("Chat Filter", FishSettings::chatFilterEnabled)
            f.sub.add(ToggleSetting("Kill Combo", "", FishSettings::cfKillCombo))
            f.sub.add(ToggleSetting("Boss Messages", "", FishSettings::cfBossMessages))
            f.sub.add(ToggleSetting("Friend Join/Leave", "", FishSettings::cfFriendJoinLeave))
            f.sub.add(ToggleSetting("Bazaar", "", FishSettings::cfBazaar))
            f.sub.add(ToggleSetting("Warping", "", FishSettings::cfWarping))
            general.features.add(f)
        }

        // ===== Dungeon =====
        // Dungeon Score lives entirely under the Dungeon Map column now (Info HUD readout +
        // Score Messages alerts) — see below, folded together instead of duplicating a second tracker here.
        dungeon.features.add(Feature("PB Pace", FishSettings::pbPaceEnabled))
        dungeon.features.add(Feature("Puzzle Overlay", FishSettings::showPuzzles))
        dungeon.features.add(Feature("Leap Messages", Dungeons::enableLeapMessages))
        dungeon.features.add(Feature("Key Notifier", Dungeons::enableKeyNotifier))
        run {
            val f = Feature("Death Message", FishSettings::deathMessageEnabled)
            val tmpl = InputSetting("Template", "", FishSettings::deathMessageTemplate)
            tmpl.hint = "{name} = player who died"
            f.sub.add(tmpl)
            f.sub.add(ToggleSetting("To Party", "", FishSettings::deathMessageToParty))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Send Lag to Party", FishSettings::sendLagToParty))
        run {
            val f = Feature("Splits", Phase::enableSplits)
            f.sub.add(ToggleSetting("Total Time", "", Phase::includeTotalTime))
            f.sub.add(ToggleSetting("Send in Chat", "", Phase::sendSplitInChat))
            f.sub.add(DropdownSetting("Tick Timer", "",
                Split.TimerType.values(), { Split.timerType }, { v -> Split.timerType = v }))
            f.sub.add(ToggleSetting("Activated Only", "", Phase::onlyShowActivatedSplits))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Session Stats", FishSettings::sessionStatsEnabled)
            f.sub.add(ToggleSetting("In Dungeon", "", FishSettings::sessionStatsInDungeon))
            f.sub.add(ToggleSetting("In D Hub", "", FishSettings::sessionStatsInDungeonHub))
            f.sub.add(ToggleSetting("Reset Relog", "", FishSettings::sessionStatsResetOnRelog))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Loot Tracker", FishSettings::lootTrackerEnabled)
            f.sub.add(DropdownSetting("Price", "",
                FishSettings.PriceMode.values(),
                { FishSettings.trackerPriceModeEnum },
                { v -> FishSettings.trackerPriceModeEnum = v; fishmod.features.croesus.CroesusPrices.applyPriceMode() }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Simon Says", FishSettings::simonSaysEnabled)
            f.sub.add(ToggleSetting("Show HUD", "", FishSettings::simonSaysHudEnabled))
            f.sub.add(ToggleSetting("To Party", "", FishSettings::simonSaysPartyChat))
            f.sub.add(ToggleSetting("Fail Msg", "", FishSettings::simonSaysFailEnabled))
            f.sub.add(InputSetting("Fail Text", "", FishSettings::simonSaysFailMessage))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Class Colored Boots", FishSettings::classColoredBootsEnabled))
        run {
            val f = Feature("M7 Lever Waypoints", FishSettings::enableM7LeverWaypoints)
            f.sub.add(ColorPickerSetting("Box Color", "", FishSettings::m7LeverWaypointColor))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Starred Mob Highlight", FishSettings::enableStarredMobHighlight)
            f.sub.add(ColorPickerSetting("Outline Color", "", FishSettings::starredMobHighlightColor))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dupe Class Detector", Dungeons::detectDuplicateClass)
            f.sub.add(ToggleSetting("Ignore Mage", "", Dungeons::ignoreDupeMage))
            f.sub.add(ToggleSetting("To Party", "", Dungeons::dupeClassPartyChat))
            dungeon.features.add(f)
        }
        // ===== Cosmetics =====
        run {
            val f = Feature("Name Color",
                { NickState.isActive() },
                { v -> if (!v) NickState.reset() else NickState.applyFromSettings() })
            val name = LimitedInputSetting("Custom Name", "", 18,
                { FishSettings.nickCustomName },
                { v -> FishSettings.nickCustomName = v ?: ""; if (NickState.isActive()) NickState.applyFromSettings() })
            name.hint = "blank = your IGN; &l/&o/&m/&n/&k/&r formats, &* star"
            f.sub.add(name)
            f.sub.add(DropdownSetting("Color Mode", "", arrayOf("SOLID", "GRADIENT", "GRADIENT3", "RAINBOW"),
                { FishSettings.nickColorMode },
                { v -> FishSettings.nickColorMode = v; if (NickState.isActive()) NickState.applyFromSettings() }))
            f.sub.add(ConditionalColorPickerSetting("Start Color", "",
                { !"RAINBOW".equals(FishSettings.nickColorMode, ignoreCase = true) },
                { FishSettings.nickColorStart },
                { v -> FishSettings.nickColorStart = v; if (NickState.isActive()) NickState.applyFromSettings() }))
            f.sub.add(ConditionalColorPickerSetting("Mid Color", "",
                { "GRADIENT3".equals(FishSettings.nickColorMode, ignoreCase = true) },
                { FishSettings.nickColorMid },
                { v -> FishSettings.nickColorMid = v; if (NickState.isActive()) NickState.applyFromSettings() }))
            f.sub.add(ConditionalColorPickerSetting("End Color", "",
                { "GRADIENT".equals(FishSettings.nickColorMode, ignoreCase = true) || "GRADIENT3".equals(FishSettings.nickColorMode, ignoreCase = true) },
                { FishSettings.nickColorEnd },
                { v -> FishSettings.nickColorEnd = v; if (NickState.isActive()) NickState.applyFromSettings() }))
            f.sub.add(SubcategoryHeader("Modes"))
            f.sub.add(SubcategoryHeader("SOLID: 1 color"))
            f.sub.add(SubcategoryHeader("GRADIENT: start → end"))
            f.sub.add(SubcategoryHeader("GRADIENT3: start → mid → end"))
            f.sub.add(SubcategoryHeader("RAINBOW: fixed 6 colors"))
            f.sub.add(SubcategoryHeader("Codes"))
            f.sub.add(SubcategoryHeader("&l bold   &o italic"))
            f.sub.add(SubcategoryHeader("&m strike   &n underline"))
            f.sub.add(SubcategoryHeader("&k magic   &r reset"))
            f.sub.add(SubcategoryHeader("&* star   &#rrggbb hex"))
            f.sub.add(ToggleSetting("See Others", "", FishSettings::remoteNicksEnabled))
            f.sub.add(ButtonSetting("Refresh Now", "", Runnable { fishmod.cosmetic.RemoteNicks.forceRefresh() }))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Nametag", FishSettings::nickPreviewEnabled)
            f.sub.add(SliderDoubleSetting("Height", "", FishSettings::nickPreviewYOffset, -1.5, 1.0))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Player Size",
                { FishSettings.playerSizeEnabled },
                { v -> FishSettings.playerSizeEnabled = v; fishmod.cosmetic.PlayerSize.uploadOwn() })
            f.sub.add(SliderDoubleSetting("Width (X)", "",
                { FishSettings.playerSizeScaleX },
                { v -> FishSettings.playerSizeScaleX = v; fishmod.cosmetic.PlayerSize.uploadOwn() },
                fishmod.cosmetic.PlayerSize.MIN.toDouble(), fishmod.cosmetic.PlayerSize.MAX.toDouble()))
            f.sub.add(SliderDoubleSetting("Height (Y)", "",
                { FishSettings.playerSizeScaleY },
                { v -> FishSettings.playerSizeScaleY = v; fishmod.cosmetic.PlayerSize.uploadOwn() },
                fishmod.cosmetic.PlayerSize.MIN.toDouble(), fishmod.cosmetic.PlayerSize.MAX.toDouble()))
            f.sub.add(SliderDoubleSetting("Depth (Z)", "",
                { FishSettings.playerSizeScaleZ },
                { v -> FishSettings.playerSizeScaleZ = v; fishmod.cosmetic.PlayerSize.uploadOwn() },
                fishmod.cosmetic.PlayerSize.MIN.toDouble(), fishmod.cosmetic.PlayerSize.MAX.toDouble()))
            f.sub.add(ToggleSetting("Share w/ All", "",
                { FishSettings.playerSizeShared },
                { v ->
                    FishSettings.playerSizeShared = v
                    if (v) { fishmod.cosmetic.PlayerSize.uploadOwn(); fishmod.cosmetic.RemoteSync.forceSync() }
                    else { fishmod.cosmetic.PlayerSize.clearOwnShare(); fishmod.cosmetic.RemoteScales.clearAll() }
                }))
            cosmetics.features.add(f)
        }
        // ===== Party =====
        run {
            val f = Feature("Party Commands", FishSettings::partyCommandsEnabled)
            f.sub.add(ToggleSetting(".ai", "", FishSettings::pcAllinvite))
            f.sub.add(ToggleSetting(".pb", "", FishSettings::pcPb))
            f.sub.add(ToggleSetting(".cata", "", FishSettings::pcCata))
            f.sub.add(ToggleSetting(".rtca", "", FishSettings::pcRtca))
            f.sub.add(ToggleSetting(".rtc", "", FishSettings::pcRtc))
            f.sub.add(ToggleSetting(".crtc", "", FishSettings::pcCrtc))
            f.sub.add(ToggleSetting(".dprofit", "", FishSettings::pcDprofit))
            f.sub.add(ToggleSetting(".crit", "", FishSettings::pcCrit))
            f.sub.add(ToggleSetting(".corpse", "", FishSettings::pcCorpse))
            f.sub.add(ToggleSetting(".f# / .m#", "", FishSettings::pcJoinFloor))
            f.sub.add(ToggleSetting(".fps", "", FishSettings::pcFps))
            f.sub.add(ToggleSetting(".tps", "", FishSettings::pcTps))
            f.sub.add(ToggleSetting(".ping", "", FishSettings::pcPing))
            f.sub.add(ToggleSetting(".secrets", "", FishSettings::pcSecrets))
            f.sub.add(ToggleSetting(".runs", "", FishSettings::pcRuns))
            f.sub.add(ToggleSetting(".d", "", FishSettings::pcDisband))
            f.sub.add(ToggleSetting(".mp", "", FishSettings::pcMp))
            f.sub.add(ToggleSetting(".collection", "", FishSettings::pcCollection))
            f.sub.add(ToggleSetting(".nw", "", FishSettings::pcNw))
            f.sub.add(ToggleSetting(".bank", "", FishSettings::pcBank))
            f.sub.add(ToggleSetting(".powder", "", FishSettings::pcPowder))
            f.sub.add(ToggleSetting(".level", "", FishSettings::pcLevel))
            f.sub.add(ToggleSetting(".farming", "", FishSettings::pcFarming))
            f.sub.add(ToggleSetting(".nuc", "", FishSettings::pcNuc))
            f.sub.add(ToggleSetting(".worm / .scatha", "", FishSettings::pcWorm))
            f.sub.add(ToggleSetting(".help / .?", "", FishSettings::pcHelp))
            f.sub.add(SubcategoryHeader("Party Actions"))
            f.sub.add(ToggleSetting(".kick", "", FishSettings::pcActionKick))
            f.sub.add(ToggleSetting(".warp / .w", "", FishSettings::pcActionWarp))
            f.sub.add(ToggleSetting(".transfer / .pt / .ptme", "", FishSettings::pcActionTransfer))
            f.sub.add(ToggleSetting(".promote", "", FishSettings::pcActionPromote))
            f.sub.add(ToggleSetting(".demote", "", FishSettings::pcActionDemote))
            f.sub.add(DropdownSetting("Who Can Trigger", "", arrayOf("off", "self", "whitelist", "blacklist", "everyone"),
                { FishSettings.pcPartyActionsMode }, { v -> FishSettings.pcPartyActionsMode = v }))
            val paWhitelist = InputSetting("Whitelist", "", FishSettings::pcPartyActionsWhitelist)
            paWhitelist.hint = "or /fmcmd whitelist add|remove|list"
            f.sub.add(paWhitelist)
            val paBlacklist = InputSetting("Blacklist", "", FishSettings::pcPartyActionsBlacklist)
            paBlacklist.hint = "or /fmcmd blacklist add|remove|list"
            f.sub.add(paBlacklist)
            party.features.add(f)
        }
        run {
            val f = Feature("Chat Channels", null, null)
            f.sub.add(ToggleSetting("Personal Messages", "", FishSettings::chatPrivate))
            f.sub.add(ToggleSetting("Party", "", FishSettings::chatParty))
            f.sub.add(ToggleSetting("Guild", "", FishSettings::chatGuild))
            f.sub.add(ToggleSetting("All", "", FishSettings::chatAll))
            party.features.add(f)
        }
        party.features.add(Feature("Party Finder Join Stats", FishSettings::pfStatsEnabled))

        // ===== Visuals =====
        run {
            val f = Feature("Cooldown Overlay", FishSettings::cooldownOverlayEnabled)
            f.sub.add(ToggleSetting("Show Number", "", FishSettings::cooldownShowText))
            f.sub.add(ToggleSetting("Under 3s Only", "", FishSettings::cooldownOnlyUnder3s))
            f.sub.add(ToggleSetting("In Inventory", "", FishSettings::cooldownInInventory))
            visuals.features.add(f)
        }
        visuals.features.add(Feature("Catacombs Overflow Levels", FishSettings::catacombsOverflowEnabled))
        run {
            val f = Feature("Pet HUD", FishSettings::petHudEnabled)
            f.sub.add(ToggleSetting("Show Level", "", FishSettings::petHudShowLevel))
            f.sub.add(ToggleSetting("Show Rarity", "Colour the pet name by its rarity", FishSettings::petHudShowRarity))
            f.sub.add(ToggleSetting("Fade Idle", "", FishSettings::petHudFadeIdle))
            f.sub.add(SliderIntSetting("Fade ms", "", FishSettings::petHudFadeMs, 1000, 30000))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Soulflow HUD", FishSettings::soulflowHudEnabled)
            f.sub.add(InputIntSetting("Warning", "", FishSettings::soulflowWarningThreshold))
            f.sub.add(ToggleSetting("Missing Warn", "", FishSettings::soulflowMissingNotifier))
            visuals.features.add(f)
        }
        visuals.features.add(Feature("Fire Freeze Timer", FishSettings::fireFreezeTimerEnabled))
        visuals.features.add(Feature("Loadout Title", FishSettings::loadoutTitleEnabled))
        run {
            val f = Feature("Explosive Shot", FishSettings::explosiveShotEnabled)
            f.sub.add(ToggleSetting("Announce to Party (Archer)", "", FishSettings::explosiveShotAnnounceParty))
            visuals.features.add(f)
        }

        // ===== Floor 7 (ported from blade-addons) =====
        run {
            val f = Feature("Tick Timers", Floor7::enableTickTimers)
            f.sub.add(SubcategoryHeader("Maxor"))
            f.sub.add(ToggleSetting("Maxor", "", Floor7::enableMaxorTickTimer))
            f.sub.add(SubcategoryHeader("Storm"))
            f.sub.add(ToggleSetting("Storm", "", Floor7::enableStormTickTimer))
            f.sub.add(ToggleSetting("Tick Down From 5", "", Floor7::tickDownStormTickTimer))
            f.sub.add(ColorPickerSetting("Storm Timer Color", "", Floor7::stormTickTimerColor))
            f.sub.add(ToggleSetting("Storm Death Time", "", Floor7::enableStormDeathTime))
            f.sub.add(ToggleSetting("LB Release Timer", "", Floor7::enableLbReleaseTimer))
            f.sub.add(ColorPickerSetting("LB Release Timer Color", "", Floor7::lbReleaseTimerColor))
            f.sub.add(SliderIntSetting("LB Release Ping (ms)", "Fires the release cue this much earlier to offset latency", Floor7::lbReleaseTimerPingMs, 0, 500))
            f.sub.add(ToggleSetting("Storm Crushed Noti", "", Floor7::notifyStormCrush))
            f.sub.add(SubcategoryHeader("Goldor"))
            f.sub.add(ToggleSetting("Goldor", "", Floor7::enableGoldorTickTimer))
            f.sub.add(ToggleSetting("In 3s Increments", "", Floor7::inDeathTicks))
            f.sub.add(ToggleSetting("Tick Up", "", Floor7::makeGoldorTickUp))
            f.sub.add(ToggleSetting("Term Start Timer", "", Floor7::enableTermStartTimer))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Crystal Spawn", Floor7::enableCrystalSpawnTime)
            f.sub.add(ToggleSetting("Place Reminder", "", Floor7::crystalPlaceReminder))
            f.sub.add(ToggleSetting("Instant Reminder", "", Floor7::instantlyDisplayCrystalReminder))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Section Progress", Floor7::showSectionProgress)
            f.sub.add(ToggleSetting("Color w/ Progress", "", Floor7::sectionColorProgress))
            f.sub.add(ToggleSetting("Prev Objective", "", Floor7::sectionPrevObjective))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Goldor Splits", Section::enableTerminalSplits)
            f.sub.add(ToggleSetting("Total Time", "", Section::includeTotalTime))
            f.sub.add(DropdownSetting("Show During", "",
                Section.DisplayTerminalSplitsWhen.values(),
                { Section.displayTerminalSplitsWhen },
                { v -> Section.displayTerminalSplitsWhen = v }))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Gate Display", Floor7::gateDisplayEnabled)
            f.sub.add(SliderDoubleSetting("Text Scale", "",
                { Floor7.gateDisplayScale.toDouble() },
                { v -> Floor7.gateDisplayScale = v.toFloat() },
                1.0, 12.0))
            floor7.features.add(f)
        }
        run {
            val f = Feature("S4 Term/Leap Tracker", Floor7::s4TrackerEnabled)
            f.sub.add(ToggleSetting("Debug HUD", "", Floor7::s4DebugHudEnabled))
            f.sub.add(ToggleSetting("Alerts", "", Floor7::s4AlertsEnabled))
            f.sub.add(ToggleSetting("Alert Sound", "", Floor7::s4AlertSoundEnabled))
            f.sub.add(ToggleSetting("Early Leap Alert", "", Floor7::s4EarlyLeapAlert))
            f.sub.add(ToggleSetting("Late Leap Alert", "", Floor7::s4LateLeapAlert))
            f.sub.add(ToggleSetting("Missed Term Alert", "", Floor7::s4MissedTermAlert))
            f.sub.add(ToggleSetting("Death Alert", "", Floor7::s4DeathAlert))
            f.sub.add(SliderIntSetting("Late Leap Threshold (ticks)", "",
                { Floor7.s4LateLeapThresholdTicks }, { v -> Floor7.s4LateLeapThresholdTicks = v }, 20, 400))
            f.sub.add(SliderIntSetting("Alert Duration (ticks)", "",
                { Floor7.s4AlertDurationTicks }, { v -> Floor7.s4AlertDurationTicks = v }, 20, 200))
            f.sub.add(SliderIntSetting("Alert Cooldown (ticks)", "",
                { Floor7.s4AlertCooldownTicks }, { v -> Floor7.s4AlertCooldownTicks = v }, 10, 200))
            floor7.features.add(f)
        }

        for (et in FishModAddonApi.dungeonToggles) {
            dungeon.features.add(Feature(et.name(), { et.get().get() }, { v -> et.set().accept(v) }))
        }

        // ===== Dungeon Map (ported from System22) =====
        run {
            // Legit Mode / Insight Legit toggles live in FishModAddons only (via
            // FishModAddonApi.registerDungeonToggle) — this core mod never exposes a way to turn
            // legit mode off, and forces it back on every join regardless (see FishModInit).
            val f = Feature("Enable Map", fishmod.utils.config.values.DungeonMapSettings::mapEnabled)
            f.sub.add(ColorPickerSetting("Background Color", "", fishmod.utils.config.values.DungeonMapSettings::mapBackgroundColor))
            f.sub.add(SliderIntSetting("Background Opacity %", "",
                { ((fishmod.utils.config.values.DungeonMapSettings.mapBackgroundColor ushr 24) and 0xFF) * 100 / 255 },
                { v ->
                    val alpha = (v * 255 / 100).coerceIn(0, 255)
                    val rgb = fishmod.utils.config.values.DungeonMapSettings.mapBackgroundColor and 0xFFFFFF
                    fishmod.utils.config.values.DungeonMapSettings.mapBackgroundColor = (alpha shl 24) or rgb
                },
                0, 100))
            f.sub.add(SliderIntSetting("Background Size", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapBackgroundSize.toInt() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapBackgroundSize = v.toFloat() },
                0, 100))
            f.sub.add(SliderIntSetting("Text Scale %", "",
                { (fishmod.utils.config.values.DungeonMapSettings.mapTextScaling * 100).toInt() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapTextScaling = v / 100.0f },
                10, 200))
            f.sub.add(ToggleSetting("Center Text", "", fishmod.utils.config.values.DungeonMapSettings::mapTextCenter))
            f.sub.add(ToggleSetting("Ugly Question Marks", "", fishmod.utils.config.values.DungeonMapSettings::mapUglyQuestionMarks))
            f.sub.add(ToggleSetting("Show Room Secrets", "", fishmod.utils.config.values.DungeonMapSettings::mapShowRoomSecrets))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Background Image", { fishmod.utils.config.values.DungeonMapSettings.mapImageSelection.isNotEmpty() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapImageSelection = if (v) fishmod.utils.config.values.DungeonMapSettings.mapImageSelection else "" })
            f.sub.add(ButtonSetting("Open Images Folder", "") {
                try {
                    fishmod.features.dungeon.map.MapImageLoader.init()
                    net.minecraft.util.Util.getPlatform().openUri(fishmod.features.dungeon.map.MapImageLoader.getImagesPath().toUri())
                } catch (ignored: Exception) {}
            })
            val imageNames: Array<String> = run {
                val names = fishmod.features.dungeon.map.MapImageLoader.getImageNames()
                (if (names.isEmpty()) listOf("No image") else names).toTypedArray()
            }
            f.sub.add(DropdownSetting("Image", "", imageNames,
                { if (fishmod.utils.config.values.DungeonMapSettings.mapImageSelection in imageNames) fishmod.utils.config.values.DungeonMapSettings.mapImageSelection else imageNames[0] },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapImageSelection = v }))
            f.sub.add(SliderIntSetting("Image Alpha", "", fishmod.utils.config.values.DungeonMapSettings::mapImageAlpha, 0, 255))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Info HUD", { fishmod.utils.config.values.DungeonMapSettings.mapInfoEnabled == true },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapInfoEnabled = v })
            f.sub.add(ToggleSetting("No Words", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoNoWords))
            f.sub.add(ToggleSetting("Tied to Map", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoMapTied))
            f.sub.add(ToggleSetting("Show Secrets", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowSecrets))
            f.sub.add(ToggleSetting("Secrets Tail = Left (not total)", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowLeft))
            f.sub.add(ToggleSetting("Show Score", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowScore))
            f.sub.add(ToggleSetting("Show Deaths", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowDeaths))
            f.sub.add(ToggleSetting("Show Mimic", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowMimic))
            f.sub.add(ToggleSetting("Show Prince", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowPrince))
            f.sub.add(ToggleSetting("Show Crypts", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowCrypts))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Player Heads", fishmod.utils.config.values.DungeonMapSettings::mapPlayerHeadDrawOwnLast)
            f.sub.add(ColorPickerSetting("Head Background", "", fishmod.utils.config.values.DungeonMapSettings::mapPlayerHeadBackground))
            f.sub.add(ColorPickerSetting("Own Head Background", "", fishmod.utils.config.values.DungeonMapSettings::mapPlayerHeadOwnBackground))
            f.sub.add(SliderIntSetting("Outline Size", "", fishmod.utils.config.values.DungeonMapSettings::mapPlayerHeadBackgroundSize, 0, 5))
            f.sub.add(ToggleSetting("Ugly Pointer (Own)", "", fishmod.utils.config.values.DungeonMapSettings::mapPlayerUglyPointer))
            f.sub.add(SliderIntSetting("Player Name Scale %", "",
                { (fishmod.utils.config.values.DungeonMapSettings.mapPlayerNamesScaling * 100).toInt() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapPlayerNamesScaling = v / 100.0f },
                0, 150))
            f.sub.add(ColorPickerSetting("Name Color", "", fishmod.utils.config.values.DungeonMapSettings::mapPlayerNameColor))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Room Additions", fishmod.utils.config.values.DungeonMapSettings::mapRoomAdditionsPrince)
            f.sub.add(ToggleSetting("Mimic Reveal", "", fishmod.utils.config.values.DungeonMapSettings::mapRoomAdditionsMimic))
            f.sub.add(ToggleSetting("Mimic on Insight", "", fishmod.utils.config.values.DungeonMapSettings::mapMimicOnInsight))
            f.sub.add(ColorPickerSetting("Mimic Room Color", "", fishmod.utils.config.values.DungeonMapSettings::mapMimicRoomColor))
            f.sub.add(SliderIntSetting("Darken Multiplier %", "",
                { (fishmod.utils.config.values.DungeonMapSettings.mapDarkenMultiplier * 100).toInt() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapDarkenMultiplier = v / 100.0f },
                0, 100))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Door Colors", fishmod.utils.config.values.DungeonMapSettings::mapDoorGay)
            f.sub.add(SliderDoubleSetting("Door Thickness", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapDoorThickness.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapDoorThickness = v.toFloat() },
                0.0, 10.0))
            f.sub.add(ColorPickerSetting("Unopened", "", fishmod.utils.config.values.DungeonMapSettings::mapUnopenedDoorColor))
            f.sub.add(ColorPickerSetting("Blood", "", fishmod.utils.config.values.DungeonMapSettings::mapBloodDoorColor))
            f.sub.add(ColorPickerSetting("Wither", "", fishmod.utils.config.values.DungeonMapSettings::mapWitherDoorColor))
            f.sub.add(ColorPickerSetting("Normal", "", fishmod.utils.config.values.DungeonMapSettings::mapNormalDoorColor))
            f.sub.add(ColorPickerSetting("Puzzle", "", fishmod.utils.config.values.DungeonMapSettings::mapPuzzleDoorColor))
            f.sub.add(ColorPickerSetting("Champion", "", fishmod.utils.config.values.DungeonMapSettings::mapChampionDoorColor))
            f.sub.add(ColorPickerSetting("Trap", "", fishmod.utils.config.values.DungeonMapSettings::mapTrapDoorColor))
            f.sub.add(ColorPickerSetting("Entrance", "", fishmod.utils.config.values.DungeonMapSettings::mapEntranceDoorColor))
            f.sub.add(ColorPickerSetting("Fairy", "", fishmod.utils.config.values.DungeonMapSettings::mapFairyDoorColor))
            f.sub.add(ColorPickerSetting("Rare", "", fishmod.utils.config.values.DungeonMapSettings::mapRareDoorColor))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Door Highlight", fishmod.utils.config.values.DungeonMapSettings::mapDoorHighlightEnabled)
            f.sub.add(SliderDoubleSetting("Width", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapDoorHighlightWidth.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapDoorHighlightWidth = v.toFloat() },
                1.0, 10.0))
            f.sub.add(ToggleSetting("Through Walls", "Every highlighted door, not just Wither",
                fishmod.utils.config.values.DungeonMapSettings::mapDoorHighlightThroughWall))
            f.sub.add(ToggleSetting("Full Box", "Highlight the whole frame, not just the near face",
                fishmod.utils.config.values.DungeonMapSettings::mapDoorHighlightFullBox))
            f.sub.add(ColorPickerSetting("Wither: No Key", "Wither-door box until the Wither Key is picked up (turns green once held)",
                fishmod.utils.config.values.DungeonMapSettings::mapWitherHighlightMissingColor))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Room Colors", fishmod.utils.config.values.DungeonMapSettings::mapTextCenter)
            f.sub.add(ColorPickerSetting("Unopened", "", fishmod.utils.config.values.DungeonMapSettings::mapUnopenedRoomColor))
            f.sub.add(ColorPickerSetting("Blood", "", fishmod.utils.config.values.DungeonMapSettings::mapBloodRoomColor))
            f.sub.add(ColorPickerSetting("Normal", "", fishmod.utils.config.values.DungeonMapSettings::mapNormalRoomColor))
            f.sub.add(ColorPickerSetting("Puzzle", "", fishmod.utils.config.values.DungeonMapSettings::mapPuzzleRoomColor))
            f.sub.add(ColorPickerSetting("Champion", "", fishmod.utils.config.values.DungeonMapSettings::mapChampionRoomColor))
            f.sub.add(ColorPickerSetting("Trap", "", fishmod.utils.config.values.DungeonMapSettings::mapTrapRoomColor))
            f.sub.add(ColorPickerSetting("Entrance", "", fishmod.utils.config.values.DungeonMapSettings::mapEntranceRoomColor))
            f.sub.add(ColorPickerSetting("Fairy", "", fishmod.utils.config.values.DungeonMapSettings::mapFairyRoomColor))
            f.sub.add(ColorPickerSetting("Rare", "", fishmod.utils.config.values.DungeonMapSettings::mapRareRoomColor))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Score Messages", fishmod.utils.config.values.DungeonMapSettings::mapScoreMessages)
            f.sub.add(ToggleSetting("Score Missing Msg (1min, to party)", "", fishmod.utils.config.values.DungeonMapSettings::mapScoreMissingMsg))
            f.sub.add(SubcategoryHeader("270 Score"))
            f.sub.add(ToggleSetting("Title", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270Title))
            val t270t = InputSetting("Title Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270TitleText)
            t270t.hint = "& color codes, <time> ok"
            f.sub.add(t270t)
            f.sub.add(ToggleSetting("Party Chat", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270MessageEnabled))
            val t270c = InputSetting("Party Chat Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270Message)
            t270c.hint = "<time> ok"
            f.sub.add(t270c)
            f.sub.add(ToggleSetting("Client-only Msg", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270ClientEnabled))
            val t270cl = InputSetting("Client-only Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270ClientMessage)
            t270cl.hint = "& color codes, <time> ok"
            f.sub.add(t270cl)
            f.sub.add(SubcategoryHeader("300 Score"))
            f.sub.add(ToggleSetting("Title", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300Title))
            val t300t = InputSetting("Title Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300TitleText)
            t300t.hint = "& color codes, <time> ok"
            f.sub.add(t300t)
            f.sub.add(ToggleSetting("Party Chat", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300MessageEnabled))
            val t300c = InputSetting("Party Chat Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300Message)
            t300c.hint = "<time> ok"
            f.sub.add(t300c)
            f.sub.add(ToggleSetting("Client-only Msg", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300ClientEnabled))
            val t300cl = InputSetting("Client-only Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300ClientMessage)
            t300cl.hint = "& color codes, <time> ok"
            f.sub.add(t300cl)
            f.sub.add(SubcategoryHeader("Title Display"))
            f.sub.add(SliderDoubleSetting("Title Scale", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale = v.toFloat() },
                0.5, 4.0))
            f.sub.add(ToggleSetting("Title Sound", "", fishmod.utils.config.values.DungeonMapSettings::mapScoreTitleSound))
            f.sub.add(DropdownSetting("Sound", "", fishmod.features.dungeon.map.ScoreMessages.SOUND_OPTIONS,
                { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSoundId },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSoundId = v }))
            dungeonMap.features.add(f)
        }

        columns.add(general)
        columns.add(dungeon)
        columns.add(cosmetics)
        columns.add(party)
        columns.add(visuals)
        columns.add(floor7)
        columns.add(dungeonMap)
    }

    /** Restores column order AND tab groupings saved from a previous drag. Each slot is either a
     *  bare name (standalone) or "activeIdx:NameA+NameB+..." (a tab group, first name becomes the
     *  self-including host — see [Column.children]). Falls back to the default layout if anything
     *  doesn't resolve cleanly (unknown name, name used twice, etc). */
    private fun applySavedColumnOrder() {
        val saved = FishSettings.fmColumnOrder
        if (saved.isBlank()) return
        val byName = columns.associateBy { it.name }
        val used = HashSet<String>()
        // Validate every name resolves and appears exactly once before mutating anything, so a
        // malformed/stale save can't leave some columns half-grouped.
        data class Slot(val names: List<String>, val activeIdx: Int)
        val slots = ArrayList<Slot>()
        for (slot in saved.split(",")) {
            if (slot.isBlank()) continue
            val colon = slot.indexOf(':')
            val (names, activeIdx) = if (colon > 0 && slot.substring(0, colon).all { it.isDigit() })
                slot.substring(colon + 1).split("+") to (slot.substring(0, colon).toIntOrNull() ?: 0)
            else
                listOf(slot) to 0
            for (n in names) {
                if (byName[n] == null || !used.add(n)) return
            }
            slots.add(Slot(names, activeIdx))
        }
        if (used.size != columns.size) return

        val reordered = ArrayList<Column>(columns.size)
        for (slot in slots) {
            val members = slot.names.map { byName.getValue(it) }
            val host = members[0]
            host.children.clear()
            if (members.size > 1) {
                host.children.addAll(members)
                host.activeChild = slot.activeIdx.coerceIn(0, members.size - 1)
            }
            reordered.add(host)
        }
        columns.clear()
        columns.addAll(reordered)
    }

    private fun saveColumnOrder() {
        FishSettings.fmColumnOrder = columns.joinToString(",") { top ->
            if (top.isGroup()) top.activeChild.toString() + ":" + top.children.joinToString("+") { it.name }
            else top.name
        }
    }

    private fun left(): Int = 0
    private fun top(): Int = 0
    /** Virtual (pre-shrink) screen bounds: the whole layout below is computed in this space, then
     *  [paintNvgOverlay] scales the recorded drawing down by [fishmod.utils.rendering.UiScale.factor]
     *  so it occupies the same fraction of the real screen regardless of Minecraft's GUI scale. */
    private fun right(): Int = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
    private fun bottom(): Int = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()

    /** Converts a real mouse coordinate (as delivered by vanilla input callbacks) into the same
     *  virtual space [right]/[bottom] use, so hit-testing lines up with the shrunk visuals. */
    private fun vx(real: Number): Int = (real.toDouble() / fishmod.utils.rendering.UiScale.factor()).toInt()

    private fun cx0(): Int = left() + MARGIN
    private fun cx1(): Int = right() - MARGIN
    private fun cyTop(): Int = top() + TOP_BAR_H + MARGIN + HEADER_H
    private fun cyBot(): Int = bottom() - BOTTOM_RESERVE

    private fun visibleFeatures(c: Column): List<Feature> {
        val f = searchText.lowercase()
        val out = ArrayList<Feature>()
        for (ft in c.features) {
            if (f.isEmpty() || ft.name.lowercase().contains(f)) out.add(ft)
        }
        return out
    }

    private fun visibleColumns(): List<Column> {
        val out = ArrayList<Column>()
        for (c in columns) {
            val matches = if (c.isGroup()) c.children.any { visibleFeatures(it).isNotEmpty() } else visibleFeatures(c).isNotEmpty()
            if (matches || searchText.isEmpty()) out.add(c)
        }
        return out
    }

    private fun columnWidth(): Int {
        val n = visibleColumns().size
        if (n == 0) return 0
        val avail = (cx1() - cx0()) - (n - 1) * COLUMN_GUTTER
        return Math.max(MIN_COLUMN_W, avail / n)
    }

    private fun columnX0(visibleIndex: Int): Int {
        return cx0() + visibleIndex * (columnWidth() + COLUMN_GUTTER) - Math.round(hScrollAnim).toInt()
    }

    /** Total width needed to lay out every visible column side by side, ignoring the viewport. */
    private fun totalColumnsWidth(): Int {
        val n = visibleColumns().size
        if (n == 0) return 0
        return n * columnWidth() + (n - 1) * COLUMN_GUTTER
    }

    private fun maxHScroll(): Int = Math.max(0, totalColumnsWidth() - (cx1() - cx0()))

    private fun clampHScroll() {
        hScroll = Mth.clamp(hScroll, 0, maxHScroll())
        hScrollAnim += (hScroll - hScrollAnim) * 0.35
        if (Math.abs(hScroll - hScrollAnim) < 0.5) hScrollAnim = hScroll.toDouble()
    }

    /** One source of truth for a row's geometry, used by both render and hit-testing. */
    private class RowLayout(
        val feature: Feature,
        val rowTop: Int, val rowBottom: Int,
        val subTop: Int, val subBottom: Int
    )

    private fun layoutColumn(c: Column, scrollOffset: Int, topY: Int = cyTop()): List<RowLayout> {
        val out = ArrayList<RowLayout>()
        var y = topY - scrollOffset
        for (f in visibleFeatures(c)) {
            val rowTop = y
            val rowBottom = y + ROW_H
            y = rowBottom
            val subH = f.animatedSubHeight()
            val subTop = y
            val subBottom = y + subH
            if (subH > 0) y = subBottom
            y += ROW_GAP
            out.add(RowLayout(f, rowTop, rowBottom, subTop, subBottom))
        }
        return out
    }

    private fun columnContentHeight(c: Column): Int {
        val rows = layoutColumn(c, 0)
        if (rows.isEmpty()) return 6
        val last = rows[rows.size - 1]
        return Math.max(last.rowBottom, last.subBottom) - cyTop() + ROW_GAP + 6
    }
    private fun maxScrollFor(c: Column, viewportH: Int): Int = Math.max(0, columnContentHeight(c) - viewportH)
    private fun clampScroll(c: Column, viewportH: Int) { c.scroll = Mth.clamp(c.scroll, 0, maxScrollFor(c, viewportH)) }

    /** Clamps scroll for every visible column, sizing each stacked child's viewport to its actual
     *  rendered band (see [stackSegments]) instead of an even split. */
    private fun clampAllScrolls() {
        val full = cyBot() - cyTop()
        for (c in visibleColumns()) {
            if (c.isGroup()) {
                for (seg in stackSegments(c, cyTop() - HEADER_H, cyBot())) clampScroll(seg.col, seg.segBot - seg.bodyTop)
            } else {
                clampScroll(c, full)
            }
        }
    }

    /** One child's band within [c]'s vertical stack: [segTop] is where its own header starts,
     *  [bodyTop] (segTop + HEADER_H) is where its rows/scrolling begin, [segBot] is the band's
     *  bottom — each child renders as a fully normal, independent column card within its band. */
    private class StackSegment(val col: Column, val segTop: Int, val segBot: Int) {
        val bodyTop: Int get() = segTop + HEADER_H
    }

    /** Packs each child's card directly beneath the previous one (separated by the same
     *  [COLUMN_GUTTER] gap used between side-by-side columns), sized to its own content — no
     *  leftover blank band like an even split would leave for a short column. Only the last child
     *  stretches to fill whatever height remains, so it can still scroll if it's long. */
    private fun stackSegments(c: Column, top: Int, bot: Int): List<StackSegment> {
        val n = c.children.size
        if (n == 0) return emptyList()
        val out = ArrayList<StackSegment>(n)
        var y = top
        for (i in 0 until n) {
            val child = c.children[i]
            val segTop = y
            val segBot = if (i == n - 1) bot else Math.min(bot, segTop + HEADER_H + columnContentHeight(child))
            out.add(StackSegment(child, segTop, segBot))
            y = segBot + COLUMN_GUTTER
        }
        return out
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) { }
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) { }

    private var widgetRenderFailureLogged = false
    private var recorderSizeLogged = false

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (resetArmed && System.currentTimeMillis() - resetArmedAt > 3000) resetArmed = false
        clampAllScrolls()
        clampHScroll()

        // draw commands replayed later in paintNvgOverlay() after the vanilla GUI flush
        NvgRecorder.clear()

        extractBlurredBackground(ctx)
        ctx.fillGradient(0, 0, this.width, this.height, DIM_TOP, DIM_BOT)

        // Hit-testing and layout throughout this class work in the virtual (pre-shrink) coordinate
        // space right()/bottom() use; convert the real mouse position once here at the entry point.
        val vmx = vx(mouseX)
        val vmy = vx(mouseY)

        hoverDesc = null
        try {
            renderTopBar(ctx, vmx, vmy)
            renderContent(ctx, vmx, vmy)
            renderSearchBar(ctx, vmx, vmy)
            renderHoverTooltip(ctx)
        } catch (t: Throwable) {
            // Blur/dim above are already appended to the render state by this point; don't let a widget-layer
            // exception strand the screen as blur-only with no diagnostic. Log once per screen instance.
            if (!widgetRenderFailureLogged) {
                widgetRenderFailureLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[FishModScreen] widget rendering failed - screen will show blur only", t)
            }
        }

        if (!recorderSizeLogged) {
            recorderSizeLogged = true
            fishmod.utils.debug.Debug.LOGGER.info("[NanoVG] extractRenderState queued {} draw commands", NvgRecorder.size())
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    /** One source of truth for the 4 top-right pill buttons' geometry, for render and hit-testing. */
    private fun topBarButtonRects(): Array<IntArray> {
        val labels = arrayOf("Edit HUD", "Credits", if (resetArmed) "Confirm?" else "Reset", "Save & Close")
        val bh = 20
        val gap = 8
        val y = MARGIN - 2
        val rects = Array(4) { IntArray(0) }
        var x = right() - MARGIN
        for (i in 3 downTo 0) {
            val w = sw(this.font, labels[i], 0.85f) + 20
            x -= w
            rects[i] = intArrayOf(x, y, w, bh)
            x -= gap
        }
        return rects
    }

    private fun renderTopBar(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val ws = 1.3f
        sst(ctx, this.font, "Fish", MARGIN, MARGIN, TEXT_COLOR, ws)
        val fw = sw(this.font, "Fish", ws)
        sst(ctx, this.font, "Mod", MARGIN + fw, MARGIN, ACCENT, ws)

        val labels = arrayOf("Edit HUD", "Credits", if (resetArmed) "Confirm?" else "Reset", "Save & Close")
        val filled = booleanArrayOf(false, false, false, true)
        val accents = intArrayOf(ACCENT, ACCENT, if (resetArmed) 0xFFE05A5A.toInt() else ACCENT, ACCENT)
        val rects = topBarButtonRects()
        for (i in 0 until 4) {
            val r = rects[i]
            val hover = mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3]
            drawPillButton(ctx, r[0], r[1], r[2], r[3], labels[i], filled[i], accents[i], hover)
        }
    }

    private fun drawPillButton(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, label: String, filled: Boolean, accent: Int, hover: Boolean) {
        val textW = sw(this.font, label, 0.85f)
        if (filled) {
            roundedRect(ctx, x, y, w, h, h / 2, if (hover) ACCENT_HOVER else accent)
            sst(ctx, this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, 0xFF06302F.toInt(), 0.85f)
        } else {
            roundedRectRing(ctx, x, y, w, h, h / 2 - 1, 1, if (hover) 0xFF20272E.toInt() else 0xFF171C21.toInt(), if (hover) ACCENT_HOVER else accent)
            sst(ctx, this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, if (hover) ACCENT_HOVER else TEXT_COLOR, 0.85f)
        }
    }

    private fun renderSearchBar(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val bw = 190
        val bh = 24
        val bx = (right() - bw) / 2
        val by = bottom() - BOTTOM_RESERVE + (BOTTOM_RESERVE - bh) / 2 - 8
        roundedRectRing(ctx, bx, by, bw, bh, bh / 2, 1, 0xFF14181D.toInt(), if (searchFocused) ACCENT else 0xFF3A3F48.toInt())

        val gx = bx + 16
        val gy = by + bh / 2 - 1
        disc(ctx, gx, gy, 3, SUBTEXT_COLOR)
        NvgRecorder.fillRect((gx + 2).toFloat(), (gy + 2).toFloat(), 4f, 1f, SUBTEXT_COLOR)

        var field = searchField
        if (field == null) {
            field = EditBox(this.font, bx + 30, by + 6, bw - 40, bh - 12, Component.empty())
            field.setMaxLength(48)
            field.setBordered(false)
            field.setResponder { s -> searchText = s; for (c in columns) c.scroll = 0 }
            searchField = field
        } else {
            field.setX(bx + 30); field.setY(by + 6); field.setWidth(bw - 40)
        }
        if (searchText.isEmpty() && !searchFocused) {
            sst(ctx, this.font, "Search…", bx + 30, by + (bh - 8) / 2, SUBTEXT_COLOR, 0.9f)
        } else {
            nvgTextFieldContent(field, bx + 30, by + 6, bw - 40, bh - 12)
        }
    }

    private fun renderColumnCard(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, headerTop: Int, cardBottom: Int, mouseX: Int, mouseY: Int, showPopOut: Boolean = false) {
        val hy = headerTop
        val w = x1 - x0
        NvgRecorder.dropShadow(x0.toFloat(), hy.toFloat(), w.toFloat(), (cardBottom - hy).toFloat(), CARD_RADIUS.toFloat(), 10f, 0x60000000)
        roundedRect(ctx, x0, hy, w, cardBottom - hy, CARD_RADIUS, CARD_BG)
        NvgRecorder.fillRect((x0 + CARD_RADIUS).toFloat(), hy.toFloat(), (w - 2 * CARD_RADIUS).toFloat(), HEADER_STRIP_H.toFloat(), ACCENT)
        sst(ctx, this.font, ellipsize(c.name, w - (if (showPopOut) 40 else 20)), x0 + 10, hy + HEADER_STRIP_H + 6, TEXT_COLOR, 1f)
        if (showPopOut) {
            val r = popOutIconRect(x1, hy)
            val hov = mouseX in r[0]..r[2] && mouseY in r[1]..r[3]
            NvgRecorder.popOutIcon(r[0].toFloat(), r[1].toFloat(), (r[2] - r[0]).toFloat(), if (hov) ACCENT else SUBTEXT_COLOR)
        }
    }

    /** Bounding box of a stacked column's "pop back out to top level" button, top-right of its header. */
    private fun popOutIconRect(x1: Int, headerTop: Int): IntArray {
        val s = 10
        val px = x1 - s - 8
        val py = headerTop + HEADER_STRIP_H + (HEADER_H - HEADER_STRIP_H - s) / 2
        return intArrayOf(px, py, px + s, py + s)
    }

    private fun renderColumnScrollbar(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, top: Int, bot: Int) {
        val vp = bot - top
        val ms = maxScrollFor(c, vp)
        if (ms <= 0) return
        val trackX = x1 - 3
        val barH = Math.max(20, (vp.toLong() * vp / columnContentHeight(c)).toInt())
        val barY = top + ((vp - barH).toLong() * c.scroll / ms).toInt()
        NvgRecorder.fillRect(trackX.toFloat(), top.toFloat(), 2f, (bot - top).toFloat(), 0xFF141A20.toInt())
        NvgRecorder.fillRect(trackX.toFloat(), barY.toFloat(), 2f, barH.toFloat(), ACCENT)
    }

    private fun renderContent(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val cols = visibleColumns()
        val top = cyTop()
        val bot = cyBot()
        val colW = columnWidth()
        val dc = dragColumn

        for (i in cols.indices) {
            val c = cols[i]
            if (c === dc) continue
            renderOneColumn(ctx, c, columnX0(i), colW, top, bot, mouseX, mouseY)
        }

        // Dragged column renders last (on top of its neighbors) and follows the mouse instead of its slot.
        if (dc != null) {
            renderOneColumn(ctx, dc, dragMouseX - dragGrabDX, colW, top, bot, mouseX, mouseY)
        }
    }

    private fun renderOneColumn(ctx: GuiGraphicsExtractor, c: Column, x0: Int, colW: Int, top: Int, bot: Int, mouseX: Int, mouseY: Int) {
        val x1 = x0 + colW

        if (c.isGroup()) {
            val dragged = if (dragTabParent === c) dragTabChild else null
            for (seg in stackSegments(c, top - HEADER_H, bot)) {
                if (seg.col === dragged) continue
                renderColumnBlock(ctx, seg.col, x0, x1, seg.segTop, seg.segBot, mouseX, mouseY, showPopOut = true)
            }
            // The child being dragged out of/around the stack floats at the cursor, rendered as a
            // full card (not a bare label) so it reads exactly like any other column mid-drag.
            if (dragged != null) {
                renderColumnBlock(ctx, dragged, x0, x1, dragTabMouseY - dragTabGrabDY, bot, mouseX, mouseY)
            }
            return
        }

        renderColumnBlock(ctx, c, x0, x1, top - HEADER_H, bot, mouseX, mouseY)
    }

    /** Renders one column as a normal, fully independent card — header, rows, scrollbar — sized to
     *  its own content and capped to [bandBot]. Used both for standalone top-level columns and for
     *  each member of a vertical stack, so a stacked column looks exactly like a plain one, just
     *  placed directly beneath its neighbor instead of beside it. */
    private fun renderColumnBlock(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, headerTop: Int, bandBot: Int, mouseX: Int, mouseY: Int, showPopOut: Boolean = false) {
        val bodyTop = headerTop + HEADER_H
        val colBottom = Math.min(bodyTop + columnContentHeight(c), bandBot)

        renderColumnCard(ctx, c, x0, x1, headerTop, colBottom, mouseX, mouseY, showPopOut)

        NvgRecorder.pushScissor(x0.toFloat(), bodyTop.toFloat(), (x1 - x0).toFloat(), (colBottom - bodyTop).toFloat())
        for (rl in layoutColumn(c, c.scroll, bodyTop)) {
            if (rl.rowBottom > bodyTop && rl.rowTop < colBottom) renderRow(ctx, rl.feature, x0, x1, rl.rowTop, mouseX, mouseY)
            val animH = rl.subBottom - rl.subTop
            if (animH > 0 && rl.subBottom > bodyTop && rl.subTop < colBottom) {
                renderSubPanel(ctx, rl.feature, x0, x1, rl.subTop, animH, mouseX, mouseY)
            }
        }
        NvgRecorder.popScissor()

        renderColumnScrollbar(ctx, c, x0, x1, bodyTop, colBottom)
    }

    private fun ellipsize(text: String, maxW: Int, scale: Float = 1f): String {
        if (sw(this.font, text, scale) <= maxW) return text
        var label = text
        while (label.length > 1 && sw(this.font, "$label…", scale) > maxW) label = label.substring(0, label.length - 1)
        return "$label…"
    }

    private fun renderRow(ctx: GuiGraphicsExtractor, f: Feature, x0: Int, x1: Int, top: Int, mouseX: Int, mouseY: Int) {
        val on = f.hasMaster() && f.get!!()
        val inView = mouseY >= cyTop() && mouseY <= cyBot()
        val hover = inView && mouseX >= x0 && mouseX <= x1 && mouseY >= top && mouseY <= top + ROW_H

        if (on) NvgRecorder.fillRect((x0 + 2).toFloat(), top.toFloat(), (x1 - x0 - 4).toFloat(), ROW_H.toFloat(), ROW_ENABLED)
        if (hover) NvgRecorder.fillRect((x0 + 2).toFloat(), top.toFloat(), (x1 - x0 - 4).toFloat(), ROW_H.toFloat(), ROW_HOVER)
        if (on) NvgRecorder.fillRect((x0 + 2).toFloat(), (top + 3).toFloat(), 2f, (ROW_H - 6).toFloat(), ACCENT)

        var label = f.name
        val maxTextW = x1 - x0 - 20
        if (stw(this.font, label) > maxTextW) {
            while (label.length > 1 && stw(this.font, "$label…") > maxTextW) label = label.substring(0, label.length - 1)
            label = "$label…"
        }
        NvgRecorder.text(label, (x0 + 10).toFloat(), (top + (ROW_H - 8) / 2).toFloat(), NVG_BASE_TEXT_SIZE, if (on) TEXT_COLOR else SUBTEXT_COLOR)
        if (hover) {
            val d = descFor(f.name)
            if (d.isNotEmpty()) { hoverDesc = d; hoverDescX = x1 + 8; hoverDescY = top }
        }

        if (f.sub.isNotEmpty()) {
            drawChevron(ctx, x1 - 14, top + ROW_H / 2 - 2, f.expanded(), if (f.expanded()) ACCENT else CHEVRON_COLOR)
        }
    }

    /** Small floating tooltip drawn last, on top of everything, for the row the mouse is hovering. */
    private fun renderHoverTooltip(ctx: GuiGraphicsExtractor) {
        val desc = hoverDesc ?: return
        val tw = stw(this.font, desc)
        val bw = tw + 16
        val bh = 18
        val bx = Math.min(hoverDescX, right() - bw - 4)
        val by = hoverDescY
        roundedRectRing(ctx, bx, by, bw, bh, 5, 1, 0xFF14181D.toInt(), ACCENT)
        st(ctx, this.font, desc, bx + 8, by + 5, TEXT_COLOR)
    }

    private fun renderSubPanel(ctx: GuiGraphicsExtractor, f: Feature, x0: Int, x1: Int, top: Int, animatedH: Int, mouseX: Int, mouseY: Int) {
        val animating = f.expandAnim.isAnimating()
        if (animating) NvgRecorder.pushScissor(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), animatedH.toFloat())

        val subH = f.naturalSubHeight()
        NvgRecorder.fillRect(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), subH.toFloat(), SUBROW_BG)
        NvgRecorder.fillRect(x0.toFloat(), top.toFloat(), 2f, subH.toFloat(), ACCENT)
        val leftX = x0 + 14
        val rightX = x1 - 12
        var sy = top + 6
        for (s in f.sub) {
            val sh = s.getHeight()
            if (s !is SubcategoryHeader && s !is InputSetting && s !is SliderIntSetting && s !is SliderDoubleSetting &&
                s !is InputIntSetting && s !is InputDoubleSetting && s !is ColorPickerSetting) {
                val labelH = if (s is DropdownSetting<*>) ITEM_HEIGHT else sh
                st(ctx, this.font, s.name, leftX + 2, sy + (labelH - 8) / 2, TEXT_COLOR)
            }
            s.render(ctx, leftX, rightX, sy, mouseX, mouseY, this.font)
            sy += sh
        }

        if (animating) NvgRecorder.popScissor()
    }

    private fun hovBtn(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = vx(click.x())
        val my = vx(click.y())
        val btn = click.button()

        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(InputConstants.Type.MOUSE.getOrCreate(btn))
            capturingKeybind = null
            return true
        }

        val prevInput = activeInput
        if (prevInput is InputSetting && prevInput.textField != null) prevInput.textField!!.setFocused(false)
        activeInput = null

        val swW = 190
        val swH = 24
        val sx = (right() - swW) / 2
        val sy0 = bottom() - BOTTOM_RESERVE + (BOTTOM_RESERVE - swH) / 2 - 8
        searchFocused = mx >= sx && mx <= sx + swW && my >= sy0 && my <= sy0 + swH
        searchField?.setFocused(searchFocused)
        if (searchFocused) return true

        val rects = topBarButtonRects()
        if (hovBtn(mx, my, rects[0][0], rects[0][1], rects[0][2], rects[0][3])) { Minecraft.getInstance().setScreen(FishHudEditor(this)); return true }
        if (hovBtn(mx, my, rects[1][0], rects[1][1], rects[1][2], rects[1][3])) { Minecraft.getInstance().setScreen(CreditsScreen(this)); return true }
        if (hovBtn(mx, my, rects[2][0], rects[2][1], rects[2][2], rects[2][3])) {
            if (resetArmed) { resetAllColumns(); resetArmed = false }
            else { resetArmed = true; resetArmedAt = System.currentTimeMillis() }
            return true
        }
        if (hovBtn(mx, my, rects[3][0], rects[3][1], rects[3][2], rects[3][3])) { onClose(); return true }

        // Grabbing a column's header card starts a drag; only meaningful when the visible column
        // order matches the master list 1:1, i.e. no active search filter. Left-drag reorders,
        // right-drag restructures (stack/pop a column) — see mouseReleased.
        val headerTop = cyTop() - HEADER_H
        if (searchText.isEmpty() && my >= headerTop && my < cyTop()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val x0 = columnX0(ci)
                val x1 = x0 + colW
                if (mx < x0 || mx > x1) continue
                dragColumn = cols[ci]
                dragColumnMerge = btn == 1
                dragGrabDX = mx - x0
                dragMouseX = mx
                return true
            }
        }

        // A stacked column's mini segment headers live inside the normal content band; grabbing
        // one starts the same kind of drag as above, scoped to reordering/popping within the stack.
        if (searchText.isEmpty() && my >= cyTop() && my <= cyBot()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val slot = cols[ci]
                if (!slot.isGroup()) continue
                val x0 = columnX0(ci)
                val x1 = x0 + colW
                if (mx < x0 || mx > x1) continue
                for (seg in stackSegments(slot, cyTop() - HEADER_H, cyBot())) {
                    if (my < seg.segTop || my >= seg.bodyTop) continue
                    val pr = popOutIconRect(x1, seg.segTop)
                    if (mx in pr[0]..pr[2] && my in pr[1]..pr[3]) {
                        popOutChild(slot, seg.col)
                        return true
                    }
                    dragTabParent = slot
                    dragTabChild = seg.col
                    dragTabGrabDY = my - seg.segTop
                    dragTabMouseX = mx
                    dragTabMouseY = my
                    dragTabRightClick = btn == 1
                    return true
                }
                break
            }
        }

        if (my >= cyTop() && my <= cyBot()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val slot = cols[ci]
                val x0 = columnX0(ci)
                val x1 = x0 + colW
                if (mx < x0 || mx > x1) continue

                if (slot.isGroup()) {
                    for (seg in stackSegments(slot, cyTop() - HEADER_H, cyBot())) {
                        if (my < seg.bodyTop || my > seg.segBot) continue
                        return handleRowClick(seg.col, x0, x1, mx, my, btn, seg.bodyTop)
                    }
                    return true
                }
                return handleRowClick(slot, x0, x1, mx, my, btn, cyTop())
            }
            return true
        }
        return super.mouseClicked(click, bl)
    }

    /** Removes [child] from [parent]'s stack and reinserts it as a standalone top-level column
     *  right next to where the stack sits — the "move it to the side" undo for stacking. */
    private fun popOutChild(parent: Column, child: Column) {
        val idx = columns.indexOf(parent)
        parent.children.remove(child)
        collapseIfNeeded(parent)
        columns.add(if (idx >= 0) idx + 1 else columns.size, child)
        saveColumnOrder()
    }

    /** Row/sub-panel hit-testing for one column's body, shared by standalone columns and each
     *  segment of a vertical stack — only the content's origin y ([topY]) differs between them. */
    private fun handleRowClick(col: Column, x0: Int, x1: Int, mx: Int, my: Int, btn: Int, topY: Int): Boolean {
        for (rl in layoutColumn(col, col.scroll, topY)) {
            val f = rl.feature
            // left-click toggles on/off, right-click expands (either click expands if no master toggle)
            if (my >= rl.rowTop && my <= rl.rowBottom) {
                if (f.hasMaster()) {
                    if (btn == 1 && f.sub.isNotEmpty()) f.toggleExpanded()
                    else f.set!!(!f.get!!())
                } else if (f.sub.isNotEmpty()) {
                    f.toggleExpanded()
                }
                return true
            }
            val subH = rl.subBottom - rl.subTop
            if (subH > 0 && my >= rl.subTop && my <= rl.subBottom) {
                val leftX = x0 + 14
                val rightX = x1 - 12
                var ssy = rl.subTop + 6
                for (s in f.sub) {
                    val sh = s.getHeight()
                    if (my >= ssy && my <= ssy + sh) {
                        if (s is InputSetting || s is InputIntSetting || s is InputDoubleSetting) {
                            activeInput = s
                        }
                    }
                    if (s.onClick(mx, my, leftX, rightX, ssy, btn)) {
                        if (s is KeybindSetting && s.capturing) capturingKeybind = s
                        return true
                    }
                    if (s is SliderIntSetting || s is SliderDoubleSetting) {
                        val slx = leftX + 2
                        val slw = rightX - leftX - 4
                        val sly = ssy + TWO_LINE_CTRL_Y
                        if (mx >= slx && mx <= slx + slw && my >= sly - 4 && my <= sly + SLIDER_H + 4) {
                            activeSlider = s; activeSliderX = slx; activeSliderW = slw; s.onDrag(mx, slx, slw); return true
                        }
                    }
                    ssy += sh
                }
                return true // swallow clicks inside the body
            }
        }
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val slider = activeSlider
        if (slider != null) { slider.onDrag(vx(click.x()), activeSliderX, activeSliderW); return true }
        val dc = dragColumn
        if (dc != null) {
            dragMouseX = vx(click.x())
            // Merge-mode (right-drag) leaves slot order alone while dragging so the target header
            // stays put under the cursor instead of hopping away; only a plain left-drag live-snaps.
            if (!dragColumnMerge) updateDragReorder(dc)
            return true
        }
        val tc = dragTabChild
        if (tc != null) {
            dragTabMouseX = vx(click.x())
            dragTabMouseY = vx(click.y())
            if (!dragTabRightClick) {
                val tp = dragTabParent
                if (tp != null) updateTabDragReorder(tp, tc)
            }
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        activeSlider = null

        val dc = dragColumn
        if (dc != null) {
            if (dragColumnMerge) {
                val over = headerColumnAt(vx(click.x()), vx(click.y()))
                if (over != null && over !== dc) {
                    columns.remove(dc)
                    mergeInto(over, dc)
                }
            }
            dragColumn = null
            dragColumnMerge = false
            saveColumnOrder()
        }

        val tp = dragTabParent
        val tc = dragTabChild
        if (tc != null) {
            if (dragTabRightClick) {
                val mx = vx(click.x())
                val my = vx(click.y())
                val over = headerColumnAt(mx, my)
                if (over != null && over !== tp) {
                    tp?.children?.remove(tc)
                    mergeInto(over, tc)
                    if (tp != null) collapseIfNeeded(tp)
                } else if (over == null) {
                    tp?.children?.remove(tc)
                    if (tp != null) collapseIfNeeded(tp)
                    insertAtNearestSlot(tc, mx)
                }
                // over === tp: dropped back onto its own parent's header — leave it alone.
            }
            dragTabParent = null
            dragTabChild = null
            dragTabRightClick = false
            saveColumnOrder()
        }

        return super.mouseReleased(click)
    }

    /** Live swap-based reorder: whichever slot the dragged column's floating center is nearest
     *  becomes its new position in the master list, so the other tabs snap out of the way as you drag. */
    private fun updateDragReorder(dc: Column) {
        val cols = visibleColumns()
        val colW = columnWidth()
        val floatCenter = (dragMouseX - dragGrabDX) + colW / 2
        val curIdx = columns.indexOf(dc)
        if (curIdx < 0) return
        var targetIdx = curIdx
        var bestDist = Int.MAX_VALUE
        for (ci in cols.indices) {
            val center = columnX0(ci) + colW / 2
            val dist = Math.abs(center - floatCenter)
            if (dist < bestDist) { bestDist = dist; targetIdx = columns.indexOf(cols[ci]) }
        }
        if (targetIdx != curIdx && targetIdx >= 0) {
            columns.removeAt(curIdx)
            columns.add(targetIdx, dc)
        }
    }

    /** Same swap-based snap as [updateDragReorder], scoped to one group's vertical stack. */
    private fun updateTabDragReorder(parent: Column, child: Column) {
        val segs = stackSegments(parent, cyTop() - HEADER_H, cyBot())
        if (segs.isEmpty()) return
        // Bands are no longer uniform height, so use the dragged child's own natural height (not
        // some other sibling's) to convert its grabbed point back into a comparable center.
        val draggedH = HEADER_H + columnContentHeight(child)
        val floatCenter = (dragTabMouseY - dragTabGrabDY) + draggedH / 2
        val curIdx = parent.children.indexOf(child)
        if (curIdx < 0) return
        var targetIdx = curIdx
        var bestDist = Int.MAX_VALUE
        for (i in segs.indices) {
            val center = (segs[i].segTop + segs[i].segBot) / 2
            val dist = Math.abs(center - floatCenter)
            if (dist < bestDist) { bestDist = dist; targetIdx = i }
        }
        if (targetIdx != curIdx) {
            parent.children.removeAt(curIdx)
            parent.children.add(targetIdx, child)
        }
    }

    /** Top-level column (if any) whose card — header or stacked body — the given point sits over. */
    private fun headerColumnAt(mx: Int, my: Int): Column? {
        if (my < cyTop() - HEADER_H || my > cyBot()) return null
        val cols = visibleColumns()
        val colW = columnWidth()
        for (ci in cols.indices) {
            val x0 = columnX0(ci)
            val x1 = x0 + colW
            if (mx in x0..x1) return cols[ci]
        }
        return null
    }

    /** Folds [incoming] into [target]'s vertical stack, turning a standalone target into a fresh
     *  self-including group first if needed (see [Column.children]). */
    private fun mergeInto(target: Column, incoming: Column) {
        if (!target.isGroup()) target.children.add(target)
        // Flatten rather than nest: if incoming is itself a stack (a whole group dragged by its
        // outer header), fold its members in directly so stacks never nest inside one another.
        val toAdd = if (incoming.isGroup()) ArrayList(incoming.children) else listOf(incoming)
        incoming.children.clear()
        for (m in toAdd) if (m !in target.children) target.children.add(m)
        target.activeChild = 0
    }

    /** When a group is down to one member, that survivor takes over the slot directly instead of
     *  staying wrapped in a now-pointless single-tab group. */
    private fun collapseIfNeeded(parent: Column) {
        if (parent.children.size == 1) {
            val survivor = parent.children[0]
            parent.children.clear()
            val idx = columns.indexOf(parent)
            if (idx >= 0) columns[idx] = survivor
        } else if (parent.activeChild >= parent.children.size) {
            parent.activeChild = 0
        }
    }

    /** Inserts a popped-out tab as a new top-level slot, snapping to whichever slot position is
     *  closest to the drop's x — the same "nearest slot" rule [updateDragReorder] uses. */
    private fun insertAtNearestSlot(newCol: Column, mx: Int) {
        val cols = visibleColumns()
        if (cols.isEmpty()) { columns.add(newCol); return }
        val colW = columnWidth()
        var bestIdx = columns.size
        var bestDist = Int.MAX_VALUE
        for (ci in cols.indices) {
            val center = columnX0(ci) + colW / 2
            val dist = Math.abs(center - mx)
            if (dist < bestDist) { bestDist = dist; bestIdx = columns.indexOf(cols[ci]) }
        }
        columns.add(bestIdx, newCol)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        // Shift+wheel, a trackpad's horizontal swipe, or plain scrolling over the column headers
        // (above the row content, where there's nothing to scroll vertically anyway) pans sideways.
        val shiftDown = InputConstants.isKeyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
            InputConstants.isKeyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_RIGHT_SHIFT)

        val mouseX = vx(mouseX).toDouble()
        val mouseY = vx(mouseY).toDouble()
        val cols = visibleColumns()
        val colW = columnWidth()
        if (horizontalAmount == 0.0 && !shiftDown && mouseY >= cyTop()) {
            for (i in cols.indices) {
                val x0 = columnX0(i)
                val x1 = x0 + colW
                if (mouseX < x0 || mouseX > x1) continue
                val slot = cols[i]
                if (slot.isGroup()) {
                    for (seg in stackSegments(slot, cyTop() - HEADER_H, cyBot())) {
                        if (mouseY < seg.bodyTop || mouseY > seg.segBot) continue
                        val vp = seg.segBot - seg.bodyTop
                        seg.col.scroll = Mth.clamp((seg.col.scroll - verticalAmount * 18).toInt(), 0, maxScrollFor(seg.col, vp))
                        return true
                    }
                    return true
                }
                val colBottom = Math.min(cyTop() + columnContentHeight(slot), cyBot())
                if (mouseY <= colBottom) {
                    slot.scroll = Mth.clamp((slot.scroll - verticalAmount * 18).toInt(), 0, maxScrollFor(slot, colBottom - cyTop()))
                    return true
                }
            }
        }

        // Anywhere else in the panel (headers, blank gutters, below a short column's content)
        // pans sideways instead of doing nothing.
        val amount = if (horizontalAmount != 0.0) horizontalAmount else verticalAmount
        hScroll = Mth.clamp((hScroll - amount * 24).toInt(), 0, maxHScroll())
        return true
    }

    private fun resetAllColumns() {
        for (c in columns) {
            val targets = if (c.isGroup()) c.children else listOf(c)
            for (t in targets) for (f in t.features) {
                if (f.hasMaster() && f.get!!()) f.set!!(false)
            }
        }
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(
                if (input.key() == GLFW.GLFW_KEY_ESCAPE) InputConstants.UNKNOWN
                else InputConstants.getKey(input)
            )
            capturingKeybind = null
            return true
        }
        val ai = activeInput
        if (ai is InputSetting && ai.textField != null) { ai.textField!!.keyPressed(input); return true }
        if (ai is InputIntSetting && ai.textField != null) { ai.textField!!.keyPressed(input); return true }
        if (ai is InputDoubleSetting && ai.textField != null) { ai.textField!!.keyPressed(input); return true }
        if (searchFocused && searchField != null) { searchField!!.keyPressed(input); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        val ai = activeInput
        if (ai is InputSetting && ai.textField != null) {
            ai.textField!!.charTyped(input); ai.setter(ai.textField!!.value); return true
        }
        if (ai is InputIntSetting && ai.textField != null) { ai.textField!!.charTyped(input); return true }
        if (ai is InputDoubleSetting && ai.textField != null) { ai.textField!!.charTyped(input); return true }
        if (searchFocused && searchField != null) {
            searchField!!.charTyped(input); searchText = searchField!!.value; for (c in columns) c.scroll = 0; return true
        }
        return super.charTyped(input)
    }

    private val nvgGlState = fishmod.utils.rendering.NvgGlStateGuard()
    private var nvgFailureLogged = false
    private var paintCount = 0
    private var replaySizeLogged = false

    /** Called by GameRendererNvgMixin right after the vanilla GUI flush each frame, for correct z-ordering. */
    override fun paintNvgOverlay() {
        paintCount++
        nvgGlState.capture()
        try {
            val ctx = fishmod.utils.rendering.NvgContext.get()

            // Must use the real GUI scale factor, not 1.0, or NanoVG's baked font glyphs blur when stretched.
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            if (!replaySizeLogged) {
                replaySizeLogged = true
                fishmod.utils.debug.Debug.LOGGER.info("[NanoVG] paintNvgOverlay replaying {} draw commands", fishmod.utils.rendering.NvgRecorder.size())
            }
            org.lwjgl.nanovg.NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            fishmod.utils.rendering.NvgRecorder.replay(fishmod.utils.rendering.UiScale.factor())
            org.lwjgl.nanovg.NanoVG.nvgEndFrame(ctx)

            fishmod_glCheck("after paintNvgOverlay")
        } catch (t: Throwable) {
            // Fail safe instead of crash-looping the render thread; likely a bundled NanoVG native failing to load.
            if (!nvgFailureLogged) {
                nvgFailureLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] paintNvgOverlay failed - settings screen will render without its NanoVG layer from now on", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }

    private fun fishmod_glCheck(where: String) {
        var err: Int
        while (org.lwjgl.opengl.GL11.glGetError().also { err = it } != org.lwjgl.opengl.GL11.GL_NO_ERROR) {
            fishmod.utils.debug.Debug.LOGGER.warn("[NanoVG] GL error 0x{} at {}", Integer.toHexString(err), where)
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        Config.manager.save()
        FishConfig.manager.save()
        super.onClose()
    }

    class Column(val name: String, val icon: String) {
        val features: MutableList<Feature> = ArrayList()
        var scroll = 0

        /** Non-empty when this slot is a vertical stack: [children] all render at once, sharing
         *  the card and splitting its height evenly, each independently scrollable — saves
         *  horizontal space by letting several columns share one slot instead of sitting side by
         *  side. Right-click-drag a whole column onto another to stack them; drag a child's mini
         *  header to reorder it within the stack or right-drag it out to merge elsewhere/pop back
         *  to top level. */
        val children: MutableList<Column> = ArrayList()
        var activeChild: Int = 0

        fun isGroup(): Boolean = children.isNotEmpty()
        fun content(): Column = if (isGroup()) children[activeChild.coerceIn(0, children.size - 1)] else this
    }

    class Feature(val name: String, val get: (() -> Boolean)?, val set: ((Boolean) -> Unit)?) {
        constructor(name: String, prop: KMutableProperty0<Boolean>) : this(name, { prop.get() }, { prop.set(it) })

        val sub: MutableList<Setting> = ArrayList()
        val expandAnim = Easing.Anim(250)
        fun hasMaster(): Boolean = get != null && set != null

        fun expanded(): Boolean = expandAnim.target()
        fun toggleExpanded() { expandAnim.setTarget(!expandAnim.target()) }
        fun naturalSubHeight(): Int {
            if (sub.isEmpty()) return 0
            var total = 0
            for (s in sub) total += s.getHeight()
            return total + 10
        }
        fun animatedSubHeight(): Int {
            val natural = naturalSubHeight()
            return if (natural == 0) 0 else Math.round(natural * expandAnim.progress())
        }
    }

    abstract class Setting(var name: String, var description: String) {
        abstract fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, settingY: Int, mouseX: Int, mouseY: Int, tr: Font)
        open fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, settingY: Int, button: Int): Boolean = false
        open fun onDrag(mx: Int, sx: Int, sliderW: Int) {}
        open fun getHeight(): Int = ITEM_HEIGHT
    }

    class SubcategoryHeader(name: String) : Setting(name, "") {
        override fun getHeight(): Int = SUBCAT_HEIGHT
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            roundRect(ctx, leftX, sy, rightX, sy + SUBCAT_HEIGHT, 2, 0xFF11131A.toInt())
            NvgRecorder.fillRect((leftX + 1).toFloat(), (sy + 2).toFloat(), 2f, (SUBCAT_HEIGHT - 4).toFloat(), ACCENT)
            st(ctx, tr, name, leftX + 6, sy + (SUBCAT_HEIGHT - 8) / 2, ACCENT)
        }
    }

    class ToggleSetting(name: String, desc: String, val getter: () -> Boolean, val setter: (Boolean) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Boolean>) : this(name, desc, { prop.get() }, { prop.set(it) })

        private val knobAnim = Easing.Anim(150)
        private var lastValue: Boolean? = null
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val on = getter()
            if (lastValue == null) { lastValue = on; knobAnim.setTarget(on) }
            else if (lastValue != on) { lastValue = on; knobAnim.setTarget(on) }
            val tx = rightX - W - 2
            val ty = sy + (ITEM_HEIGHT - H) / 2
            val hov = mx >= tx && mx <= tx + W && my >= ty && my <= ty + H
            drawTogglePill(ctx, tx, ty, W, H, on, knobAnim.progress(), hov)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val tx = rightX - W - 2
            val ty = sy + (ITEM_HEIGHT - H) / 2
            if (mx >= tx && mx <= tx + W && my >= ty && my <= ty + H) {
                setter(!getter()); return true
            }
            return false
        }
        // Own compact size (not the shared PILL_H used by dropdown/color/keybind pills) so the
        // toggle reads as a small modern switch rather than a big pill dominating the row.
        companion object { const val W = 26; const val H = 14 }
    }

    /** Slider/text-input settings render on two lines: name on line 1 (full-width, left-aligned,
     *  no competing control), the actual control on line 2 below it — so a long label never
     *  visually overlaps a right-aligned control on the same row. See TWO_LINE_H. */
    class SliderIntSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit, val min: Int, val max: Int) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Int>, min: Int, max: Int) : this(name, desc, { prop.get() }, { prop.set(it) }, min, max)

        override fun getHeight(): Int = TWO_LINE_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            val slx = leftX + 2
            val slw = rightX - leftX - 4
            val sly = sy + TWO_LINE_CTRL_Y
            val pct = (getter() - min).toFloat() / (max - min)
            pill(ctx, slx, sly, slx + slw, sly + SLIDER_H, SLIDER_BG)
            val fillW = (slw * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            val v = getter().toString()
            st(ctx, tr, v, slx + slw - stw(tr, v), sly - 9, SUBTEXT_COLOR)
        }
        override fun onDrag(mx: Int, sx: Int, sliderW: Int) {
            val pct = Mth.clamp((mx - sx).toFloat() / sliderW, 0f, 1f)
            setter(min + (pct * (max - min)).toInt())
        }
    }

    class SliderDoubleSetting(name: String, desc: String, val getter: () -> Double, val setter: (Double) -> Unit, val min: Double, val max: Double) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Double>, min: Double, max: Double) : this(name, desc, { prop.get() }, { prop.set(it) }, min, max)

        override fun getHeight(): Int = TWO_LINE_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            val slx = leftX + 2
            val slw = rightX - leftX - 4
            val sly = sy + TWO_LINE_CTRL_Y
            val pct = ((getter() - min) / (max - min)).toFloat()
            pill(ctx, slx, sly, slx + slw, sly + SLIDER_H, SLIDER_BG)
            val fillW = (slw * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            val v = String.format("%.1f", getter())
            st(ctx, tr, v, slx + slw - stw(tr, v), sly - 9, SUBTEXT_COLOR)
        }
        override fun onDrag(mx: Int, sx: Int, sliderW: Int) {
            val pct = Mth.clamp((mx - sx).toFloat() / sliderW, 0f, 1f)
            setter(min + pct * (max - min))
        }
    }

    /** Click expands an inline option list; right-click quick-cycles to the next value without expanding. */
    class DropdownSetting<T>(name: String, desc: String, val values: Array<T>, val getter: () -> T, val setter: (T) -> Unit) : Setting(name, desc) {
        private val expandAnim = Easing.Anim(200)
        private var expanded = false
        private var pillX = 0
        private var pillW = 0

        private fun indexOfCurrent(): Int {
            val cur = getter()
            for (i in values.indices) if (values[i] === cur || values[i] == cur) return i
            return 0
        }

        override fun getHeight(): Int {
            return ITEM_HEIGHT + Math.round(values.size * OPTION_H * expandAnim.progress())
        }

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val current = getter().toString()
            val textW = stw(tr, current)
            pillW = textW + 22
            pillX = rightX - pillW - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, PILL_H / 2, 2, TRACK_OFF, if (hov) ACCENT_HOVER else ACCENT)
            st(ctx, tr, current, pillX + 10, by + (PILL_H - 8) / 2 - 1, TEXT_COLOR)

            val animating = expandAnim.isAnimating()
            if (expanded || animating) {
                val animH = Math.round(values.size * OPTION_H * expandAnim.progress())
                val oy = sy + ITEM_HEIGHT
                if (animating) NvgRecorder.pushScissor(leftX.toFloat(), oy.toFloat(), (rightX - leftX).toFloat(), animH.toFloat())
                roundedRect(ctx, leftX + 2, oy, rightX - leftX - 4, values.size * OPTION_H, 5, SUBROW_BG)
                val curIdx = indexOfCurrent()
                for (i in values.indices) {
                    val rowY = oy + i * OPTION_H
                    val selected = i == curIdx
                    val rowHov = mx >= leftX + 2 && mx <= rightX - 2 && my >= rowY && my <= rowY + OPTION_H
                    if (rowHov) roundedRect(ctx, leftX + 4, rowY + 1, rightX - leftX - 8, OPTION_H - 2, 4, ROW_HOVER)
                    st(ctx, tr, values[i].toString(), leftX + 10, rowY + (OPTION_H - 8) / 2,
                        if (selected) ACCENT_HOVER else (if (rowHov) TEXT_COLOR else SUBTEXT_COLOR))
                    if (selected) NvgRecorder.fillRect((leftX + 2).toFloat(), (rowY + 3).toFloat(), 2f, (OPTION_H - 6).toFloat(), ACCENT)
                }
                if (animating) NvgRecorder.popScissor()
            }
        }

        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H) {
                if (btn == 1) {
                    setter(values[(indexOfCurrent() + 1) % values.size])
                } else {
                    expanded = !expanded
                    expandAnim.setTarget(expanded)
                }
                return true
            }
            if (expanded) {
                val oy = sy + ITEM_HEIGHT
                for (i in values.indices) {
                    val rowY = oy + i * OPTION_H
                    if (mx >= leftX && mx <= rightX && my >= rowY && my <= rowY + OPTION_H) {
                        setter(values[i])
                        expanded = false
                        expandAnim.setTarget(false)
                        return true
                    }
                }
            }
            return false
        }
    }

    open class InputSetting(name: String, desc: String, val getter: () -> String, val setter: (String) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<String>) : this(name, desc, { prop.get() }, { prop.set(it) })

        var textField: EditBox? = null
        var hint: String? = null
        open fun initField(tr: Font) {
            if (textField == null) {
                val tf = EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty())
                tf.setMaxLength(256)
                tf.value = getter()
                tf.setResponder { s -> setter(s) }
                textField = tf
            }
        }
        override fun getHeight(): Int = if (hint != null) 35 else 26
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            initField(tr)
            st(ctx, tr, name, leftX + 2, sy + 1, TEXT_COLOR)
            val ix = leftX + 2
            val iy = sy + 11
            val fieldW = rightX - leftX - 4
            val tf = textField!!
            if (!tf.isFocused) { tf.cursorPosition = 0; tf.setHighlightPos(0) }
            nvgTextField(tf, ix, iy, fieldW, INPUT_H)
            hint?.let { st(ctx, tr, it, leftX + 2, sy + 27, SUBTEXT_COLOR) }
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = leftX + 2
            val iy = sy + 11
            val fieldW = rightX - leftX - 4
            if (mx >= ix && mx <= ix + fieldW && my >= iy && my <= iy + INPUT_H) {
                val tf = textField
                if (tf != null) {
                    tf.setFocused(true)
                    val len = tf.value.length
                    tf.cursorPosition = len; tf.setHighlightPos(len)
                }
                return true
            }
            return false
        }
    }

    class LimitedInputSetting(name: String, desc: String, val maxVisible: Int, getter: () -> String, setter: (String) -> Unit) :
        InputSetting("", desc, getter, capWrapper(setter, maxVisible)) {
        val displayLabel: String = name
        override fun getHeight(): Int = ITEM_HEIGHT + 9
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, displayLabel, leftX + 2, sy + 1, TEXT_COLOR)
            initField(tr)
            val ix = rightX - INPUT_W - 2
            val iy = sy + 2
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
            val len = visibleLen(getter())
            val counter = "$len/$maxVisible"
            val color = if (len >= maxVisible) 0xFFFF5555.toInt() else SUBTEXT_COLOR
            st(ctx, tr, counter, leftX + 2, sy + getHeight() - 9, color)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = rightX - INPUT_W - 2
            val iy = sy + 2
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                textField?.setFocused(true)
                return true
            }
            return false
        }
        companion object {
            fun visibleLen(s: String?): Int {
                if (s == null) return 0
                return s.replace(Regex("&#[0-9a-fA-F]{6}"), "").replace(Regex("[&§][0-9a-fk-orxA-FK-ORX]"), "").length
            }
            private fun capWrapper(inner: (String) -> Unit, max: Int): (String) -> Unit {
                return { v ->
                    var s = v
                    while (s.isNotEmpty() && visibleLen(s) > max) s = s.substring(0, s.length - 1)
                    inner(s)
                }
            }
        }
    }

    open class ColorPickerSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Int>) : this(name, desc, { prop.get() }, { prop.set(it) })

        private val expandAnim = Easing.Anim(200)
        private var expanded = false
        private var pillX = 0
        private var pillW = 0

        private fun indexOfCurrent(): Int {
            val cur = getter() or 0xFF000000.toInt()
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in PRESET_ARGB.indices) {
                val dr = ((PRESET_ARGB[i] shr 16) and 0xFF) - ((cur shr 16) and 0xFF)
                val dg = ((PRESET_ARGB[i] shr 8) and 0xFF) - ((cur shr 8) and 0xFF)
                val db = (PRESET_ARGB[i] and 0xFF) - (cur and 0xFF)
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDist) { bestDist = dist; best = i }
            }
            return best
        }

        override fun getHeight(): Int {
            return ITEM_HEIGHT + Math.round(PRESET_ARGB.size * OPTION_H * expandAnim.progress())
        }

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX, sy + (ITEM_HEIGHT - 8) / 2, TEXT_COLOR)
            val idx = indexOfCurrent()
            val label = PRESET_NAMES[idx]
            val textW = stw(tr, label)
            val swatchD = 8
            pillW = textW + swatchD + 26
            pillX = rightX - pillW - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, PILL_H / 2, 2, TRACK_OFF, if (hov) ACCENT_HOVER else ACCENT)
            disc(ctx, pillX + 12, by + PILL_H / 2, swatchD / 2, getter() or 0xFF000000.toInt())
            st(ctx, tr, label, pillX + 22, by + (PILL_H - 8) / 2 - 1, TEXT_COLOR)

            val animating = expandAnim.isAnimating()
            if (expanded || animating) {
                val oy = sy + ITEM_HEIGHT
                roundedRect(ctx, leftX + 2, oy, rightX - leftX - 4, PRESET_ARGB.size * OPTION_H, 5, SUBROW_BG)
                for (i in PRESET_ARGB.indices) {
                    val rowY = oy + i * OPTION_H
                    val selected = i == idx
                    val rowHov = mx >= leftX + 2 && mx <= rightX - 2 && my >= rowY && my <= rowY + OPTION_H
                    if (rowHov) roundedRect(ctx, leftX + 4, rowY + 1, rightX - leftX - 8, OPTION_H - 2, 4, ROW_HOVER)
                    disc(ctx, leftX + 12, rowY + OPTION_H / 2, 4, PRESET_ARGB[i])
                    st(ctx, tr, PRESET_NAMES[i], leftX + 22, rowY + (OPTION_H - 8) / 2,
                        if (selected) ACCENT_HOVER else (if (rowHov) TEXT_COLOR else SUBTEXT_COLOR))
                    if (selected) NvgRecorder.fillRect((leftX + 2).toFloat(), (rowY + 3).toFloat(), 2f, (OPTION_H - 6).toFloat(), ACCENT)
                }
            }
        }

        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H) {
                if (btn == 1) {
                    setter(PRESET_ARGB[(indexOfCurrent() + 1) % PRESET_ARGB.size])
                } else {
                    expanded = !expanded
                    expandAnim.setTarget(expanded)
                }
                return true
            }
            if (expanded) {
                val oy = sy + ITEM_HEIGHT
                for (i in PRESET_ARGB.indices) {
                    val rowY = oy + i * OPTION_H
                    if (mx >= leftX && mx <= rightX && my >= rowY && my <= rowY + OPTION_H) {
                        setter(PRESET_ARGB[i])
                        expanded = false
                        expandAnim.setTarget(false)
                        return true
                    }
                }
            }
            return false
        }

        companion object {
            val PRESET_ARGB: IntArray = intArrayOf(
                0xFFFFFFFF.toInt(), 0xFFFF5555.toInt(), 0xFFAA0000.toInt(), 0xFFFFAA00.toInt(), 0xFFFFFF55.toInt(),
                0xFF55FF55.toInt(), 0xFF00AA00.toInt(), 0xFF55FFFF.toInt(), 0xFF00AAAA.toInt(), 0xFF5555FF.toInt(),
                0xFF0000AA.toInt(), 0xFFAA00AA.toInt(), 0xFFFF55FF.toInt(), 0xFF663311.toInt(), 0xFFAAAAAA.toInt(),
                0xFF555555.toInt(), 0xFF000000.toInt()
            )
            val PRESET_NAMES: Array<String> = arrayOf(
                "White", "Red", "Dark Red", "Orange", "Yellow",
                "Green", "Dark Green", "Aqua", "Dark Aqua", "Blue",
                "Dark Blue", "Purple", "Pink", "Brown", "Gray",
                "Dark Gray", "Black"
            )
        }
    }

    class ConditionalColorPickerSetting(
        name: String, desc: String, val visible: () -> Boolean,
        getter: () -> Int, setter: (Int) -> Unit
    ) : ColorPickerSetting(name, desc, getter, setter) {
        val shownName: String = name
        override fun getHeight(): Int {
            if (!visible()) { this.name = ""; return 0 }
            this.name = shownName
            return super.getHeight()
        }
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            if (!visible()) return
            super.render(ctx, leftX, rightX, sy, mx, my, tr)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            if (!visible()) return false
            return super.onClick(mx, my, leftX, rightX, sy, btn)
        }
    }

    class ButtonSetting(name: String, desc: String, val action: Runnable) : Setting(name, desc) {
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val bw = 60
            val bx = rightX - bw - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + PILL_H
            roundedRect(ctx, bx, by, bw, PILL_H, PILL_H / 2, if (hov) ACCENT_HOVER else ACCENT)
            st(ctx, tr, "Open", bx + (bw - stw(tr, "Open")) / 2, by + (PILL_H - 8) / 2 - 1, 0xFF06302F.toInt())
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val bw = 60
            val bx = rightX - bw - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + PILL_H) {
                action.run(); return true
            }
            return false
        }
    }

    /** Click then press a key/mouse button to bind (Esc unbinds); edits the vanilla KeyMapping directly. */
    class KeybindSetting(name: String, desc: String, val getter: () -> KeyMapping?) : Setting(name, desc) {
        var capturing = false
        private var pillX = 0
        private var pillW = 0
        private fun label(): String {
            if (capturing) return "..."
            val kb = getter() ?: return "-"
            return if (kb.isUnbound) "Not Bound" else kb.translatedKeyMessage.string
        }
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val t = label()
            val textW = stw(tr, t)
            pillW = textW + 20
            pillX = rightX - pillW - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H
            val ring = if (capturing) ACCENT_HOVER else (if (hov) ACCENT else 0xFF464C56.toInt())
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, PILL_H / 2, 2, TRACK_OFF, ring)
            st(ctx, tr, t, pillX + (pillW - textW) / 2, by + (PILL_H - 8) / 2 - 1, if (capturing) ACCENT_HOVER else TEXT_COLOR)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H) {
                capturing = true; return true
            }
            return false
        }
        fun applyKey(key: InputConstants.Key) {
            val kb = getter() ?: return
            kb.setKey(key)
            KeyMapping.resetMapping()
            Minecraft.getInstance().options.save()
            capturing = false
        }
    }

    class InputIntSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Int>) : this(name, desc, { prop.get() }, { prop.set(it) })

        var textField: EditBox? = null
        fun initField(tr: Font) {
            if (textField == null) {
                val tf = EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty())
                tf.setMaxLength(10)
                tf.value = getter().toString()
                tf.setResponder { s ->
                    try { setter(Integer.parseInt(s.trim())) }
                    catch (ignored: NumberFormatException) {}
                }
                textField = tf
            }
        }
        override fun getHeight(): Int = TWO_LINE_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            initField(tr)
            val ix = leftX + 2
            val iy = sy + TWO_LINE_CTRL_Y
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = leftX + 2
            val iy = sy + TWO_LINE_CTRL_Y
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                textField?.setFocused(true)
                return true
            }
            return false
        }
    }

    class InputDoubleSetting(name: String, desc: String, val getter: () -> Double, val setter: (Double) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Double>) : this(name, desc, { prop.get() }, { prop.set(it) })

        var textField: EditBox? = null
        fun initField(tr: Font) {
            if (textField == null) {
                val tf = EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty())
                tf.setMaxLength(12)
                tf.value = getter().toString()
                tf.setResponder { s ->
                    try { setter(java.lang.Double.parseDouble(s.trim())) }
                    catch (ignored: NumberFormatException) {}
                }
                textField = tf
            }
        }
        override fun getHeight(): Int = TWO_LINE_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            initField(tr)
            val ix = leftX + 2
            val iy = sy + TWO_LINE_CTRL_Y
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = leftX + 2
            val iy = sy + TWO_LINE_CTRL_Y
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                textField?.setFocused(true)
                return true
            }
            return false
        }
    }

    class LabelSetting(name: String, desc: String) : Setting(name, desc) {
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {}
    }

    companion object {
        private val ACCENT = ScreenTheme.ACCENT
        private val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        private const val DIM_TOP = 0x2E000000
        private const val DIM_BOT = 0x50000000
        private val CARD_BG = ScreenTheme.CARD_BG
        private const val ROW_HOVER = 0x1EFFFFFF
        private const val ROW_ENABLED = 0x2624B6B0
        private val SUBROW_BG = 0xFF0F1317.toInt()
        private val TRACK_OFF = 0xFF3A3F48.toInt()
        private val TEXT_COLOR = ScreenTheme.TEXT_COLOR
        private val SUBTEXT_COLOR = ScreenTheme.SUBTEXT_COLOR
        private val CHEVRON_COLOR = 0xFF6C7885.toInt()

        private const val TEXT_SCALE = ScreenTheme.TEXT_SCALE

        private const val MARGIN = 16
        private const val TOP_BAR_H = 26
        private const val BOTTOM_RESERVE = 46

        private const val COLUMN_GUTTER = 6
        private const val CARD_RADIUS = 7
        private const val HEADER_H = 24
        private const val HEADER_STRIP_H = 3
        private const val MIN_COLUMN_W = 172 // floor so controls don't clip; widened so column tabs read as spacious, not cramped

        private const val ROW_H = 22
        private const val ROW_GAP = 3
        private const val ITEM_HEIGHT = 22
        private const val PILL_H = 18
        private const val OPTION_H = 16
        private val SLIDER_BG = 0xFF2C3138.toInt()
        private val SLIDER_FILL = ACCENT
        private const val SLIDER_W = 56
        private const val SLIDER_H = 5
        private const val INPUT_W = 62
        private const val INPUT_H = 14
        private const val SUBCAT_HEIGHT = 13
        // Sliders/text-inputs render name + control on two separate lines (see class docs above
        // SliderIntSetting) instead of squeezing a right-aligned control onto the name's row.
        private const val TWO_LINE_H = 36
        private const val TWO_LINE_CTRL_Y = 20

        /** Roughly matches Minecraft's default font weight; TEXT_SCALE multiplies this. */
        private const val NVG_BASE_TEXT_SIZE = 9.5f
        private const val INPUT_TEXT_SIZE = 7f

        // Shape/text helpers push into NvgRecorder (not `ctx`) so they paint after vanilla draws each
        // frame, in the deferred paintNvgOverlay() pass; `ctx` is kept only to avoid rippling through call
        // sites (and because vanilla widgets like EditBox still need it for their own state, not drawing).
        fun roundedRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
            NvgRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), color)
        }

        fun roundRect(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, color: Int) {
            roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, color)
        }

        fun roundedRectRing(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, strokeW: Int, fillColor: Int, ringColor: Int) {
            NvgRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), strokeW.toFloat(), fillColor, ringColor)
        }

        fun pill(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
            val h = y2 - y1
            roundedRect(ctx, x1, y1, x2 - x1, h, h / 2, color)
        }

        fun panel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, fill: Int, border: Int) {
            roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, border)
            roundedRect(ctx, x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, Math.max(0, r - 1), fill)
        }

        fun disc(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
            NvgRecorder.disc(cx.toFloat(), cy.toFloat(), r.toFloat(), color)
        }

        /** Deferred equivalent of `ctx.fill(x1, y1, x2, y2, color)` — glyph icons must go through
         *  NvgRecorder like everything else on this screen so [UiScale]'s shrink applies to them too. */
        private fun nf(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
            NvgRecorder.fillRect(x1.toFloat(), y1.toFloat(), (x2 - x1).toFloat(), (y2 - y1).toFloat(), color)
        }

        fun st(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int) {
            NvgRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * TEXT_SCALE, color)
        }
        fun stw(tr: Font, s: String): Int = Math.ceil(NvgRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()

        fun sst(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int, scale: Float) {
            NvgRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * scale, color)
        }
        fun sw(tr: Font, s: String, scale: Float): Int = Math.ceil(NvgRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * scale).toDouble()).toInt()

        fun drawChevron(ctx: GuiGraphicsExtractor, gx: Int, cy: Int, open: Boolean, color: Int) {
            NvgRecorder.chevron(gx.toFloat(), cy.toFloat(), open, color)
        }

        /** A vanilla EditBox.extractRenderState() call would flush before the NanoVG column
         *  background and be invisible, so this redraws the field entirely via NanoVG instead;
         *  the EditBox itself is kept only for cursor/selection/IME state, never for drawing. */
        fun nvgTextField(tf: EditBox, x: Int, y: Int, w: Int, h: Int) {
            val focused = tf.isFocused
            NvgRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 3f, 1f, SUBROW_BG, if (focused) ACCENT else TRACK_OFF)
            nvgTextFieldContent(tf, x, y, w, h)
        }

        /** Text + caret only, no box — for fields whose box (e.g. the search bar's pill ring) is drawn separately. */
        fun nvgTextFieldContent(tf: EditBox, x: Int, y: Int, w: Int, h: Int) {
            val text = tf.value
            val cursor = Math.min(tf.cursorPosition, text.length)
            val cursorX = NvgRecorder.textWidth(text.substring(0, cursor), INPUT_TEXT_SIZE)
            val pad = 3f
            val visibleW = w - pad * 2f
            val scroll = Math.max(0f, cursorX - visibleW)
            NvgRecorder.pushScissor((x + 1).toFloat(), (y + 1).toFloat(), (w - 2).toFloat(), (h - 2).toFloat())
            NvgRecorder.text(text, x + pad - scroll, y + (h - INPUT_TEXT_SIZE) / 2f, INPUT_TEXT_SIZE, TEXT_COLOR)
            if (tf.isFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                NvgRecorder.fillRect(x + pad + cursorX - scroll, (y + 2).toFloat(), 1f, (h - 4).toFloat(), TEXT_COLOR)
            }
            NvgRecorder.popScissor()
        }

        private fun drawGlyph(ctx: GuiGraphicsExtractor, t: String, cx: Int, cy: Int, c: Int, bg: Int) {
            when (t) {
                "gear" -> {
                    disc(ctx, cx, cy, 5, c)
                    nf(cx - 1, cy - 7, cx + 1, cy + 7, c); nf(cx - 7, cy - 1, cx + 7, cy + 1, c)
                    nf(cx - 5, cy - 5, cx - 3, cy - 3, c); nf(cx + 3, cy - 5, cx + 5, cy - 3, c)
                    nf(cx - 5, cy + 3, cx - 3, cy + 5, c); nf(cx + 3, cy + 3, cx + 5, cy + 5, c)
                    disc(ctx, cx, cy, 2, bg)
                }
                "arch" -> {
                    nf(cx - 6, cy - 6, cx - 3, cy + 7, c); nf(cx + 3, cy - 6, cx + 6, cy + 7, c)
                    nf(cx - 6, cy - 6, cx + 6, cy - 3, c)
                }
                "hanger" -> {
                    nf(cx - 7, cy + 2, cx + 7, cy + 4, c)
                    nf(cx - 1, cy - 5, cx + 1, cy + 3, c)
                    nf(cx - 1, cy - 6, cx + 3, cy - 4, c)
                }
                "people" -> {
                    disc(ctx, cx - 4, cy - 3, 3, c); disc(ctx, cx + 4, cy - 3, 3, c)
                    nf(cx - 7, cy + 2, cx + 7, cy + 6, c)
                }
                "eye" -> {
                    nf(cx - 7, cy - 1, cx + 7, cy + 1, c); nf(cx - 5, cy - 3, cx + 5, cy + 3, c)
                    disc(ctx, cx, cy, 2, bg); disc(ctx, cx, cy, 1, c)
                }
                "text" -> {
                    nf(cx - 5, cy - 5, cx + 5, cy - 3, c); nf(cx - 1, cy - 5, cx + 1, cy + 6, c)
                }
                "chat" -> {
                    nf(cx - 7, cy - 5, cx + 7, cy + 2, c); nf(cx - 5, cy + 2, cx - 1, cy + 6, c)
                    nf(cx - 4, cy - 2, cx + 4, cy - 1, bg); nf(cx - 4, cy, cx + 2, cy + 1, bg)
                }
                "star" -> {
                    nf(cx - 1, cy - 7, cx + 1, cy + 7, c); nf(cx - 7, cy - 1, cx + 7, cy + 1, c)
                    nf(cx - 4, cy - 4, cx - 2, cy - 2, c); nf(cx + 2, cy - 4, cx + 4, cy - 2, c)
                    nf(cx - 4, cy + 2, cx - 2, cy + 4, c); nf(cx + 2, cy + 2, cx + 4, cy + 4, c)
                }
                "cube" -> {
                    nf(cx - 6, cy - 6, cx + 6, cy - 4, c); nf(cx - 6, cy + 4, cx + 6, cy + 6, c)
                    nf(cx - 6, cy - 6, cx - 4, cy + 6, c); nf(cx + 4, cy - 6, cx + 6, cy + 6, c)
                }
                "clock" -> {
                    disc(ctx, cx, cy, 6, c); disc(ctx, cx, cy, 4, bg)
                    nf(cx - 1, cy - 4, cx + 1, cy + 1, c); nf(cx - 1, cy - 1, cx + 4, cy + 1, c)
                }
                "coin" -> {
                    disc(ctx, cx, cy, 6, c); disc(ctx, cx, cy, 3, bg); disc(ctx, cx, cy, 1, c)
                }
                "palette" -> {
                    disc(ctx, cx, cy, 6, c)
                    nf(cx - 3, cy - 3, cx - 1, cy - 1, bg); nf(cx + 1, cy - 3, cx + 3, cy - 1, bg)
                    nf(cx - 1, cy + 1, cx + 1, cy + 3, bg)
                }
                "tag" -> {
                    nf(cx - 6, cy - 4, cx + 2, cy + 4, c); nf(cx + 2, cy - 3, cx + 4, cy + 3, c)
                    nf(cx + 4, cy - 1, cx + 6, cy + 1, c); disc(ctx, cx - 3, cy, 1, bg)
                }
                "slider" -> {
                    nf(cx - 7, cy - 1, cx + 7, cy + 1, c); nf(cx, cy - 4, cx + 4, cy + 4, c)
                }
                "bell" -> {
                    nf(cx - 4, cy - 3, cx + 4, cy + 3, c); nf(cx - 5, cy + 3, cx + 5, cy + 4, c)
                    nf(cx - 1, cy - 6, cx + 1, cy - 4, c); nf(cx - 1, cy + 4, cx + 1, cy + 6, c)
                }
                "map" -> {
                    nf(cx - 6, cy - 5, cx + 6, cy + 5, c); nf(cx - 1, cy - 5, cx + 1, cy + 5, bg)
                    nf(cx - 6, cy - 1, cx + 6, cy + 1, bg)
                }
                else -> {
                    nf(cx - 5, cy - 5, cx + 5, cy - 3, c); nf(cx - 5, cy + 3, cx + 5, cy + 5, c)
                    nf(cx - 5, cy - 5, cx - 3, cy + 5, c); nf(cx + 3, cy - 5, cx + 5, cy + 5, c)
                }
            }
        }

        private fun descFor(name: String): String {
            return when (name) {
                "Mod Prefix" -> "Tag FishMod's chat output with a prefix"
                "Inventory Buttons" -> "Clickable command buttons in your inventory"
                "Smart Copy Chat" -> "Right-click a chat line to copy it"
                "Compact Tab" -> "Cleaner custom tab player list"
                "Chat Filter" -> "Hide selected chat spam"
                "Explosive Shot" -> "Title with per-enemy damage"
                "Dungeon Score" -> "Live S+ score tracker overlay"
                "Puzzle Overlay" -> "Show solved puzzle names"
                "Death Message" -> "Announce deaths with a template"
                "Send Lag to Party" -> "Warn the party when your game lags"
                "Splits" -> "Phase split timers for runs"
                "Session Stats" -> "Per-session run statistics HUD"
                "Loot Tracker" -> "Manual drop & profit tracker (D Hub inv)"
                "Simon Says" -> "F7 Goldor device solver"
                "Class Colored Boots" -> "Dye boots by your dungeon class"
                "M7 Lever Waypoints" -> "See F7/M7 levers through walls"
                "Starred Mob Highlight" -> "Outline dungeon mobs that need to be killed to clear the floor"
                "Tick Timers" -> "Maxor/Storm/Goldor tick timers + related P2/terminal notifications"
                "Crystal Spawn" -> "Crystal spawn countdown + reminder"
                "Section Progress" -> "Terminal section completed/total"
                "Goldor Splits" -> "S1-S4 terminal split timers + total time"
                "S4 Term/Leap Tracker" -> "Flags early/late Core leaps and terminals that look unfinished during S4"
                "Name Color" -> "Recolor your username gradient"
                "Nametag" -> "Show your own above-head nametag"
                "Player Size" -> "Resize your model (render only)"
                "Party Commands" -> "Dot-commands usable in party chat"
                "Chat Channels" -> "Where dot-commands are allowed"
                "Rarity Background" -> "Rarity-colored backing on all slots"
                "Cooldown Overlay" -> "Ability cooldowns on item slots"
                "Pet HUD" -> "Show your active pet & level"
                "Soulflow HUD" -> "Track your soulflow count"
                "Fire Freeze Timer" -> "Fire Freeze staff cooldown timer"
                "Warp Map" -> "Mini warp map HUD"
                "Slayer XP Tracker" -> "Slayer XP per hour overlay"
                "Skill XP Tracker" -> "Skill XP per hour overlay"
                "Powder Tracker" -> "Powder & gemstone gains"
                "Farming Tracker" -> "Farming coins per hour"
                "Harvest Feast Tracker" -> "Harvest Feast event tracker"
                "Mining Tracker" -> "Mining coins per hour"
                "Trophy Frogs" -> "Trophy frog catch tracker"
                "Bobber Reminder" -> "Reel-in countdown, alert & missed HUD"
                "Sea Creatures" -> "Sea creature counts & rare-catch alert"
                "Trophy Fish" -> "Trophy fish catch tracker (Crimson)"
                "Slayer Alerts" -> "Title + ping on slayer boss events"
                "Slayer Drops" -> "Session rare-drop counter"
                "Party Finder Join Stats" -> "Whispers print sender's MP/PB/Cata/Gear to your chat"
                else -> descForExternal(name)
            }
        }

        private fun descForExternal(name: String): String {
            for (et in FishModAddonApi.dungeonToggles) {
                if (et.name() == name) return et.description()
            }
            return ""
        }

        private fun makeButtonInput(name: String, prop: KMutableProperty0<String>): InputSetting {
            val s = InputSetting(name, "", prop)
            s.hint = "command without /"
            return s
        }

        /** Static so the nested Setting subclasses, which have no outer-instance reference, can call it. */
        fun drawTogglePill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, on: Boolean, knobProgress: Float, hover: Boolean) {
            val track = if (on) (if (hover) ACCENT_HOVER else ACCENT) else TRACK_OFF
            val ring = if (on) (if (hover) ACCENT_HOVER else ACCENT) else (if (hover) 0xFF565C68.toInt() else 0xFF464C56.toInt())
            roundedRectRing(ctx, x, y, w, h, h / 2, 2, track, ring)
            val knobR = h / 2 - 3
            val knobX = x + knobR + 3 + Math.round(knobProgress * (w - 2 * (knobR + 3)))
            disc(ctx, knobX, y + h / 2, knobR, 0xFFFFFFFF.toInt())
        }
    }
}
