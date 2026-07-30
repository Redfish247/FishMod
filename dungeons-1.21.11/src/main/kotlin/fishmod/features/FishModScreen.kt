package fishmod.features

import fishmod.utils.config.Config
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.Buttons
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Split
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.font.TextRenderer
import fishmod.utils.Easing
import fishmod.utils.rendering.NvgRecorder

import java.util.ArrayList
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Multi-column config screen (matches the FishMod design mockup).
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  FishMod                                       [ search ]  │  title bar
 *  ├──────┬──────┬──────┬──────┬──────┬───────────────────────┤
 *  │ Genl │ Dngn │ Cosm │Party │ Vis. │  Floor7  │ each column  │
 *  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │   [ ]    │ scrolls on   │
 *  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │   [ ]    │ its own      │
 *  ├──────┴──────┴──────┴──────┴──────┴───────────────────────┤
 *  │  Edit HUD                       Reset      Save & Close    │  footer
 *  └──────────────────────────────────────────────────────────┘
 *
 * All columns render simultaneously; each scrolls independently. Left-click a feature
 * toggle = master on/off. Left-click a feature row body (when it has sub-settings) =
 * expand an inline panel beneath it with the rich controls (sliders, dropdowns, colour
 * pickers, text inputs), animated open/closed with a cubic ease-in-out (see
 * [Easing]). Multiple features (in the same or different columns) can be expanded
 * at once — expanding one never collapses another.
 */
class FishModScreen : Screen(Text.literal("FishMod")) {

    // ----- state -----
    private val columns: MutableList<Column> = ArrayList()
    private var searchText = ""
    private var searchFocused = false
    private var activeSlider: Setting? = null
    private var activeSliderX = 0
    private var activeInput: Setting? = null
    private var capturingKeybind: KeybindSetting? = null
    private var searchField: TextFieldWidget? = null
    private var resetArmed = false
    private var resetArmedAt = 0L
    private var hoverDesc: String? = null
    private var hoverDescX = 0
    private var hoverDescY = 0

    init {
        buildCategories()
    }

    // -----------------------------------------------------------------------------------
    // Category / feature graph
    // -----------------------------------------------------------------------------------
    private fun buildCategories() {
        val general = Column("General", "gear")
        val dungeon = Column("Dungeon", "arch")
        val party = Column("Party", "people")
        val visuals = Column("Visuals", "eye")
        val floor7 = Column("Floor 7", "arch")

        // ===== General =====
        run {
            val f = Feature("Mod Prefix",
                Supplier { FishSettings.modPrefixEnabled }, Consumer { v -> FishSettings.modPrefixEnabled = v })
            f.sub.add(InputSetting("Prefix", "",
                Supplier { FishSettings.modPrefix },
                Consumer { v -> FishSettings.modPrefix = if (v != null && v.length > 10) v.substring(0, 10) else v }))
            general.features.add(f)
        }
        run {
            val f = Feature("Inventory Buttons",
                Supplier { Buttons.enableInventoryButtons },
                Consumer { v -> Buttons.enableInventoryButtons = v })
            f.sub.add(makeButtonInput("Button 1", Supplier { Buttons.command1 }, Consumer { v -> Buttons.command1 = v }))
            f.sub.add(makeButtonInput("Button 2", Supplier { Buttons.command2 }, Consumer { v -> Buttons.command2 = v }))
            f.sub.add(makeButtonInput("Button 3", Supplier { Buttons.command3 }, Consumer { v -> Buttons.command3 = v }))
            f.sub.add(makeButtonInput("Button 4", Supplier { Buttons.command4 }, Consumer { v -> Buttons.command4 = v }))
            f.sub.add(makeButtonInput("Button 5", Supplier { Buttons.command5 }, Consumer { v -> Buttons.command5 = v }))
            f.sub.add(makeButtonInput("Button 6", Supplier { Buttons.command6 }, Consumer { v -> Buttons.command6 = v }))
            f.sub.add(makeButtonInput("Button 7", Supplier { Buttons.command7 }, Consumer { v -> Buttons.command7 = v }))
            general.features.add(f)
        }
        run {
            val f = Feature("Wardrobe Hotkeys",
                Supplier { FishSettings.wardrobeHotkeysEnabled }, Consumer { v -> FishSettings.wardrobeHotkeysEnabled = v })
            f.sub.add(ToggleSetting("Auto-Close GUI", "",
                Supplier { FishSettings.wardrobeHotkeysAutoClose }, Consumer { v -> FishSettings.wardrobeHotkeysAutoClose = v }))
            f.sub.add(SubcategoryHeader("Click a slot, then press a key/mouse button (Esc unbinds)"))
            val slots = fishmod.utils.Keybinds.wardrobeSlots
            if (slots != null) {
                for (i in slots.indices) {
                    val idx = i
                    f.sub.add(KeybindSetting("Slot " + (idx + 1), "",
                        Supplier { fishmod.utils.Keybinds.wardrobeSlots!![idx] }))
                }
            }
            general.features.add(f)
        }
        general.features.add(Feature("Smart Copy Chat",
            Supplier { FishSettings.smartCopyChat }, Consumer { v -> FishSettings.smartCopyChat = v }))
        general.features.add(Feature("Compact Chat",
            Supplier { FishSettings.chatCompact }, Consumer { v -> FishSettings.chatCompact = v }))
        run {
            val f = Feature("Compact Tab",
                Supplier { FishSettings.compactTabEnabled }, Consumer { v -> FishSettings.compactTabEnabled = v })
            f.sub.add(SliderIntSetting("Opacity %", "",
                Supplier { FishSettings.compactTabOpacity }, Consumer { v -> FishSettings.compactTabOpacity = v }, 0, 100))
            general.features.add(f)
        }
        run {
            val f = Feature("Chat Filter",
                Supplier { FishSettings.chatFilterEnabled }, Consumer { v -> FishSettings.chatFilterEnabled = v })
            f.sub.add(ToggleSetting("Kill Combo", "",
                Supplier { FishSettings.cfKillCombo }, Consumer { v -> FishSettings.cfKillCombo = v }))
            f.sub.add(ToggleSetting("Boss Messages", "",
                Supplier { FishSettings.cfBossMessages }, Consumer { v -> FishSettings.cfBossMessages = v }))
            f.sub.add(ToggleSetting("Friend Join/Leave", "",
                Supplier { FishSettings.cfFriendJoinLeave }, Consumer { v -> FishSettings.cfFriendJoinLeave = v }))
            f.sub.add(ToggleSetting("Bazaar", "",
                Supplier { FishSettings.cfBazaar }, Consumer { v -> FishSettings.cfBazaar = v }))
            f.sub.add(ToggleSetting("Warping", "",
                Supplier { FishSettings.cfWarping }, Consumer { v -> FishSettings.cfWarping = v }))
            general.features.add(f)
        }

        // ===== Dungeon =====
        run {
            val f = Feature("Dungeon Score",
                Supplier { FishSettings.dungeonScoreEnabled }, Consumer { v -> FishSettings.dungeonScoreEnabled = v })
            f.sub.add(ToggleSetting("Score Missing Msg (1min)", "",
                Supplier { FishSettings.dungeonScoreMissingMsg }, Consumer { v -> FishSettings.dungeonScoreMissingMsg = v }))
            f.sub.add(ToggleSetting("Score Left (not total secrets)", "",
                Supplier { FishSettings.dungeonScoreShowLeft }, Consumer { v -> FishSettings.dungeonScoreShowLeft = v }))
            f.sub.add(ToggleSetting("270 Title", "",
                Supplier { FishSettings.score270TitleEnabled }, Consumer { v -> FishSettings.score270TitleEnabled = v }))
            f.sub.add(ToggleSetting("270 Chat Msg", "",
                Supplier { FishSettings.score270ChatEnabled }, Consumer { v -> FishSettings.score270ChatEnabled = v }))
            val t270 = InputSetting("270 Text", "",
                Supplier { FishSettings.score270Text }, Consumer { v -> FishSettings.score270Text = v })
            t270.hint = "& color codes ok"
            f.sub.add(t270)
            f.sub.add(ToggleSetting("300 Title", "",
                Supplier { FishSettings.score300TitleEnabled }, Consumer { v -> FishSettings.score300TitleEnabled = v }))
            f.sub.add(ToggleSetting("300 Chat Msg", "",
                Supplier { FishSettings.score300ChatEnabled }, Consumer { v -> FishSettings.score300ChatEnabled = v }))
            val t300 = InputSetting("300 Text", "",
                Supplier { FishSettings.score300Text }, Consumer { v -> FishSettings.score300Text = v })
            t300.hint = "& color codes ok"
            f.sub.add(t300)
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("PB Pace",
            Supplier { FishSettings.pbPaceEnabled }, Consumer { v -> FishSettings.pbPaceEnabled = v }))
        dungeon.features.add(Feature("Puzzle Overlay",
            Supplier { FishSettings.showPuzzles }, Consumer { v -> FishSettings.showPuzzles = v }))
        run {
            val f = Feature("Death Message",
                Supplier { FishSettings.deathMessageEnabled }, Consumer { v -> FishSettings.deathMessageEnabled = v })
            val tmpl = InputSetting("Template", "",
                Supplier { FishSettings.deathMessageTemplate }, Consumer { v -> FishSettings.deathMessageTemplate = v })
            tmpl.hint = "{name} = player who died"
            f.sub.add(tmpl)
            f.sub.add(ToggleSetting("To Party", "",
                Supplier { FishSettings.deathMessageToParty }, Consumer { v -> FishSettings.deathMessageToParty = v }))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Send Lag to Party",
            Supplier { FishSettings.sendLagToParty }, Consumer { v -> FishSettings.sendLagToParty = v }))
        run {
            val f = Feature("Splits",
                Supplier { Phase.enableSplits }, Consumer { v -> Phase.enableSplits = v })
            f.sub.add(ToggleSetting("Total Time", "",
                Supplier { Phase.includeTotalTime }, Consumer { v -> Phase.includeTotalTime = v }))
            f.sub.add(ToggleSetting("Send in Chat", "",
                Supplier { Phase.sendSplitInChat }, Consumer { v -> Phase.sendSplitInChat = v }))
            f.sub.add(DropdownSetting("Tick Timer", "",
                Split.TimerType.values(), Supplier { Split.timerType }, Consumer { v -> Split.timerType = v }))
            f.sub.add(ToggleSetting("Activated Only", "",
                Supplier { Phase.onlyShowActivatedSplits }, Consumer { v -> Phase.onlyShowActivatedSplits = v }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Session Stats",
                Supplier { FishSettings.sessionStatsEnabled }, Consumer { v -> FishSettings.sessionStatsEnabled = v })
            f.sub.add(ToggleSetting("In Dungeon", "",
                Supplier { FishSettings.sessionStatsInDungeon }, Consumer { v -> FishSettings.sessionStatsInDungeon = v }))
            f.sub.add(ToggleSetting("In D Hub", "",
                Supplier { FishSettings.sessionStatsInDungeonHub }, Consumer { v -> FishSettings.sessionStatsInDungeonHub = v }))
            f.sub.add(ToggleSetting("Reset Relog", "",
                Supplier { FishSettings.sessionStatsResetOnRelog }, Consumer { v -> FishSettings.sessionStatsResetOnRelog = v }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Loot Tracker",
                Supplier { FishSettings.lootTrackerEnabled }, Consumer { v -> FishSettings.lootTrackerEnabled = v })
            f.sub.add(DropdownSetting("Price", "",
                FishSettings.PriceMode.values(),
                Supplier { FishSettings.trackerPriceModeEnum },
                Consumer { v -> FishSettings.trackerPriceModeEnum = v; fishmod.features.croesus.CroesusPrices.applyPriceMode() }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Simon Says",
                Supplier { FishSettings.simonSaysEnabled }, Consumer { v -> FishSettings.simonSaysEnabled = v })
            f.sub.add(ToggleSetting("Show HUD", "",
                Supplier { FishSettings.simonSaysHudEnabled }, Consumer { v -> FishSettings.simonSaysHudEnabled = v }))
            f.sub.add(ToggleSetting("To Party", "",
                Supplier { FishSettings.simonSaysPartyChat }, Consumer { v -> FishSettings.simonSaysPartyChat = v }))
            f.sub.add(ToggleSetting("Fail Msg", "",
                Supplier { FishSettings.simonSaysFailEnabled }, Consumer { v -> FishSettings.simonSaysFailEnabled = v }))
            f.sub.add(InputSetting("Fail Text", "",
                Supplier { FishSettings.simonSaysFailMessage }, Consumer { v -> FishSettings.simonSaysFailMessage = v }))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Class Colored Boots",
            Supplier { FishSettings.classColoredBootsEnabled }, Consumer { v -> FishSettings.classColoredBootsEnabled = v }))
        run {
            val f = Feature("M7 Lever Waypoints",
                Supplier { FishSettings.enableM7LeverWaypoints }, Consumer { v -> FishSettings.enableM7LeverWaypoints = v })
            f.sub.add(ColorPickerSetting("Box Color", "",
                Supplier { FishSettings.m7LeverWaypointColor }, Consumer { v -> FishSettings.m7LeverWaypointColor = v }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Starred Mob Highlight",
                Supplier { FishSettings.enableStarredMobHighlight }, Consumer { v -> FishSettings.enableStarredMobHighlight = v })
            f.sub.add(ColorPickerSetting("Outline Color", "",
                Supplier { FishSettings.starredMobHighlightColor }, Consumer { v -> FishSettings.starredMobHighlightColor = v }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dupe Class Detector",
                Supplier { Dungeons.detectDuplicateClass },
                Consumer { v -> Dungeons.detectDuplicateClass = v })
            f.sub.add(ToggleSetting("Ignore Mage", "",
                Supplier { Dungeons.ignoreDupeMage },
                Consumer { v -> Dungeons.ignoreDupeMage = v }))
            f.sub.add(ToggleSetting("To Party", "",
                Supplier { Dungeons.dupeClassPartyChat },
                Consumer { v -> Dungeons.dupeClassPartyChat = v }))
            dungeon.features.add(f)
        }
        // ===== Party =====
        run {
            val f = Feature("Party Commands", null, null)
            f.sub.add(ToggleSetting(".ai", "", Supplier { FishSettings.pcAllinvite }, Consumer { v -> FishSettings.pcAllinvite = v }))
            f.sub.add(ToggleSetting(".pb", "", Supplier { FishSettings.pcPb }, Consumer { v -> FishSettings.pcPb = v }))
            f.sub.add(ToggleSetting(".cata", "", Supplier { FishSettings.pcCata }, Consumer { v -> FishSettings.pcCata = v }))
            f.sub.add(ToggleSetting(".rtca", "", Supplier { FishSettings.pcRtca }, Consumer { v -> FishSettings.pcRtca = v }))
            f.sub.add(ToggleSetting(".rtc", "", Supplier { FishSettings.pcRtc }, Consumer { v -> FishSettings.pcRtc = v }))
            f.sub.add(ToggleSetting(".crtc", "", Supplier { FishSettings.pcCrtc }, Consumer { v -> FishSettings.pcCrtc = v }))
            f.sub.add(ToggleSetting(".dprofit", "", Supplier { FishSettings.pcDprofit }, Consumer { v -> FishSettings.pcDprofit = v }))
            f.sub.add(ToggleSetting(".corpse", "", Supplier { FishSettings.pcCorpse }, Consumer { v -> FishSettings.pcCorpse = v }))
            f.sub.add(ToggleSetting(".f# / .m#", "", Supplier { FishSettings.pcJoinFloor }, Consumer { v -> FishSettings.pcJoinFloor = v }))
            f.sub.add(ToggleSetting(".fps", "", Supplier { FishSettings.pcFps }, Consumer { v -> FishSettings.pcFps = v }))
            f.sub.add(ToggleSetting(".tps", "", Supplier { FishSettings.pcTps }, Consumer { v -> FishSettings.pcTps = v }))
            f.sub.add(ToggleSetting(".ping", "", Supplier { FishSettings.pcPing }, Consumer { v -> FishSettings.pcPing = v }))
            f.sub.add(ToggleSetting(".secrets", "", Supplier { FishSettings.pcSecrets }, Consumer { v -> FishSettings.pcSecrets = v }))
            f.sub.add(ToggleSetting(".runs", "", Supplier { FishSettings.pcRuns }, Consumer { v -> FishSettings.pcRuns = v }))
            f.sub.add(ToggleSetting(".d", "", Supplier { FishSettings.pcDisband }, Consumer { v -> FishSettings.pcDisband = v }))
            f.sub.add(ToggleSetting(".mp", "", Supplier { FishSettings.pcMp }, Consumer { v -> FishSettings.pcMp = v }))
            f.sub.add(ToggleSetting(".collection", "", Supplier { FishSettings.pcCollection }, Consumer { v -> FishSettings.pcCollection = v }))
            f.sub.add(ToggleSetting(".nw", "", Supplier { FishSettings.pcNw }, Consumer { v -> FishSettings.pcNw = v }))
            f.sub.add(ToggleSetting(".bank", "", Supplier { FishSettings.pcBank }, Consumer { v -> FishSettings.pcBank = v }))
            f.sub.add(ToggleSetting(".powder", "", Supplier { FishSettings.pcPowder }, Consumer { v -> FishSettings.pcPowder = v }))
            f.sub.add(ToggleSetting(".level", "", Supplier { FishSettings.pcLevel }, Consumer { v -> FishSettings.pcLevel = v }))
            f.sub.add(ToggleSetting(".farming", "", Supplier { FishSettings.pcFarming }, Consumer { v -> FishSettings.pcFarming = v }))
            f.sub.add(ToggleSetting(".nuc", "", Supplier { FishSettings.pcNuc }, Consumer { v -> FishSettings.pcNuc = v }))
            f.sub.add(ToggleSetting(".worm / .scatha", "", Supplier { FishSettings.pcWorm }, Consumer { v -> FishSettings.pcWorm = v }))
            f.sub.add(ToggleSetting(".help / .?", "", Supplier { FishSettings.pcHelp }, Consumer { v -> FishSettings.pcHelp = v }))
            f.sub.add(SubcategoryHeader("Party Actions"))
            f.sub.add(ToggleSetting(".kick", "", Supplier { FishSettings.pcActionKick }, Consumer { v -> FishSettings.pcActionKick = v }))
            f.sub.add(ToggleSetting(".warp / .w", "", Supplier { FishSettings.pcActionWarp }, Consumer { v -> FishSettings.pcActionWarp = v }))
            f.sub.add(ToggleSetting(".transfer / .pt / .ptme", "", Supplier { FishSettings.pcActionTransfer }, Consumer { v -> FishSettings.pcActionTransfer = v }))
            f.sub.add(ToggleSetting(".promote", "", Supplier { FishSettings.pcActionPromote }, Consumer { v -> FishSettings.pcActionPromote = v }))
            f.sub.add(ToggleSetting(".demote", "", Supplier { FishSettings.pcActionDemote }, Consumer { v -> FishSettings.pcActionDemote = v }))
            f.sub.add(DropdownSetting("Who Can Trigger", "", arrayOf("off", "self", "whitelist", "blacklist", "everyone"),
                Supplier { FishSettings.pcPartyActionsMode }, Consumer { v -> FishSettings.pcPartyActionsMode = v }))
            val paWhitelist = InputSetting("Whitelist", "",
                Supplier { FishSettings.pcPartyActionsWhitelist }, Consumer { v -> FishSettings.pcPartyActionsWhitelist = v })
            paWhitelist.hint = "or /fmcmd whitelist add|remove|list"
            f.sub.add(paWhitelist)
            val paBlacklist = InputSetting("Blacklist", "",
                Supplier { FishSettings.pcPartyActionsBlacklist }, Consumer { v -> FishSettings.pcPartyActionsBlacklist = v })
            paBlacklist.hint = "or /fmcmd blacklist add|remove|list"
            f.sub.add(paBlacklist)
            party.features.add(f)
        }
        run {
            val f = Feature("Chat Channels", null, null)
            f.sub.add(ToggleSetting("Personal Messages", "", Supplier { FishSettings.chatPrivate }, Consumer { v -> FishSettings.chatPrivate = v }))
            f.sub.add(ToggleSetting("Party", "", Supplier { FishSettings.chatParty }, Consumer { v -> FishSettings.chatParty = v }))
            f.sub.add(ToggleSetting("Guild", "", Supplier { FishSettings.chatGuild }, Consumer { v -> FishSettings.chatGuild = v }))
            f.sub.add(ToggleSetting("All", "", Supplier { FishSettings.chatAll }, Consumer { v -> FishSettings.chatAll = v }))
            party.features.add(f)
        }
        party.features.add(Feature("Party Finder Join Stats",
            Supplier { FishSettings.pfStatsEnabled }, Consumer { v -> FishSettings.pfStatsEnabled = v }))

        // ===== Visuals =====
        run {
            val f = Feature("Cooldown Overlay",
                Supplier { FishSettings.cooldownOverlayEnabled }, Consumer { v -> FishSettings.cooldownOverlayEnabled = v })
            f.sub.add(ToggleSetting("Show Number", "",
                Supplier { FishSettings.cooldownShowText }, Consumer { v -> FishSettings.cooldownShowText = v }))
            f.sub.add(ToggleSetting("Under 3s Only", "",
                Supplier { FishSettings.cooldownOnlyUnder3s }, Consumer { v -> FishSettings.cooldownOnlyUnder3s = v }))
            f.sub.add(ToggleSetting("In Inventory", "",
                Supplier { FishSettings.cooldownInInventory }, Consumer { v -> FishSettings.cooldownInInventory = v }))
            visuals.features.add(f)
        }
        visuals.features.add(Feature("Catacombs Overflow Levels",
            Supplier { FishSettings.catacombsOverflowEnabled }, Consumer { v -> FishSettings.catacombsOverflowEnabled = v }))
        run {
            val f = Feature("Pet HUD",
                Supplier { FishSettings.petHudEnabled }, Consumer { v -> FishSettings.petHudEnabled = v })
            f.sub.add(ToggleSetting("Show Level", "",
                Supplier { FishSettings.petHudShowLevel }, Consumer { v -> FishSettings.petHudShowLevel = v }))
            f.sub.add(ToggleSetting("Fade Idle", "",
                Supplier { FishSettings.petHudFadeIdle }, Consumer { v -> FishSettings.petHudFadeIdle = v }))
            f.sub.add(SliderIntSetting("Fade ms", "",
                Supplier { FishSettings.petHudFadeMs }, Consumer { v -> FishSettings.petHudFadeMs = v }, 1000, 30000))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Soulflow HUD",
                Supplier { FishSettings.soulflowHudEnabled }, Consumer { v -> FishSettings.soulflowHudEnabled = v })
            f.sub.add(InputIntSetting("Warning", "",
                Supplier { FishSettings.soulflowWarningThreshold }, Consumer { v -> FishSettings.soulflowWarningThreshold = v }))
            f.sub.add(ToggleSetting("Missing Warn", "",
                Supplier { FishSettings.soulflowMissingNotifier }, Consumer { v -> FishSettings.soulflowMissingNotifier = v }))
            visuals.features.add(f)
        }
        visuals.features.add(Feature("Fire Freeze Timer",
            Supplier { FishSettings.fireFreezeTimerEnabled }, Consumer { v -> FishSettings.fireFreezeTimerEnabled = v }))
        run {
            val f = Feature("Explosive Shot",
                Supplier { FishSettings.explosiveShotEnabled }, Consumer { v -> FishSettings.explosiveShotEnabled = v })
            f.sub.add(ToggleSetting("Announce to Party (Archer)", "",
                Supplier { FishSettings.explosiveShotAnnounceParty }, Consumer { v -> FishSettings.explosiveShotAnnounceParty = v }))
            visuals.features.add(f)
        }
        // ===== Floor 7 (ported from blade-addons) =====
        run {
            val f = Feature("Maxor Tick Timer",
                Supplier { Floor7.enableMaxorTickTimer }, Consumer { v -> Floor7.enableMaxorTickTimer = v })
            floor7.features.add(f)
        }
        run {
            val f = Feature("Crystal Spawn",
                Supplier { Floor7.enableCrystalSpawnTime }, Consumer { v -> Floor7.enableCrystalSpawnTime = v })
            f.sub.add(ToggleSetting("Place Reminder", "",
                Supplier { Floor7.crystalPlaceReminder }, Consumer { v -> Floor7.crystalPlaceReminder = v }))
            f.sub.add(ToggleSetting("Instant Reminder", "",
                Supplier { Floor7.instantlyDisplayCrystalReminder }, Consumer { v -> Floor7.instantlyDisplayCrystalReminder = v }))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Storm Tick Timer",
                Supplier { Floor7.enableStormTickTimer }, Consumer { v -> Floor7.enableStormTickTimer = v })
            f.sub.add(ToggleSetting("Tick Down From 5", "",
                Supplier { Floor7.tickDownStormTickTimer }, Consumer { v -> Floor7.tickDownStormTickTimer = v }))
            f.sub.add(ColorPickerSetting("Timer Color", "",
                Supplier { Floor7.stormTickTimerColor }, Consumer { v -> Floor7.stormTickTimerColor = v }))
            floor7.features.add(f)
        }
        floor7.features.add(Feature("Storm Death Time",
            Supplier { Floor7.enableStormDeathTime }, Consumer { v -> Floor7.enableStormDeathTime = v }))
        run {
            val f = Feature("LB Release Timer",
                Supplier { Floor7.enableLbReleaseTimer }, Consumer { v -> Floor7.enableLbReleaseTimer = v })
            f.sub.add(ColorPickerSetting("Timer Color", "",
                Supplier { Floor7.lbReleaseTimerColor }, Consumer { v -> Floor7.lbReleaseTimerColor = v }))
            floor7.features.add(f)
        }
        floor7.features.add(Feature("Storm Crushed Noti",
            Supplier { Floor7.notifyStormCrush }, Consumer { v -> Floor7.notifyStormCrush = v }))
        run {
            val f = Feature("Goldor Tick Timer",
                Supplier { Floor7.enableGoldorTickTimer }, Consumer { v -> Floor7.enableGoldorTickTimer = v })
            f.sub.add(ToggleSetting("In 3s Increments", "",
                Supplier { Floor7.inDeathTicks }, Consumer { v -> Floor7.inDeathTicks = v }))
            f.sub.add(ToggleSetting("Tick Up", "",
                Supplier { Floor7.makeGoldorTickUp }, Consumer { v -> Floor7.makeGoldorTickUp = v }))
            floor7.features.add(f)
        }
        floor7.features.add(Feature("Term Start Timer",
            Supplier { Floor7.enableTermStartTimer }, Consumer { v -> Floor7.enableTermStartTimer = v }))
        floor7.features.add(Feature("Goldor Leap Timer",
            Supplier { Floor7.leapNotifications }, Consumer { v -> Floor7.leapNotifications = v }))
        run {
            val f = Feature("Section Progress",
                Supplier { Floor7.showSectionProgress }, Consumer { v -> Floor7.showSectionProgress = v })
            f.sub.add(ToggleSetting("Color w/ Progress", "",
                Supplier { Floor7.sectionColorProgress }, Consumer { v -> Floor7.sectionColorProgress = v }))
            f.sub.add(ToggleSetting("Prev Objective", "",
                Supplier { Floor7.sectionPrevObjective }, Consumer { v -> Floor7.sectionPrevObjective = v }))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Goldor Splits",
                Supplier { fishmod.utils.dungeon.Section.enableTerminalSplits }, Consumer { v -> fishmod.utils.dungeon.Section.enableTerminalSplits = v })
            f.sub.add(ToggleSetting("Total Time", "",
                Supplier { fishmod.utils.dungeon.Section.includeTotalTime }, Consumer { v -> fishmod.utils.dungeon.Section.includeTotalTime = v }))
            f.sub.add(DropdownSetting("Show During", "",
                fishmod.utils.dungeon.Section.DisplayTerminalSplitsWhen.values(),
                Supplier { fishmod.utils.dungeon.Section.displayTerminalSplitsWhen },
                Consumer { v -> fishmod.utils.dungeon.Section.displayTerminalSplitsWhen = v }))
            floor7.features.add(f)
        }

        for (et in FishModAddonApi.dungeonToggles) {
            dungeon.features.add(Feature(et.name(), et.get(), et.set()))
        }

        columns.add(general)
        columns.add(dungeon)
        columns.add(party)
        columns.add(visuals)
        columns.add(floor7)
    }

    // -----------------------------------------------------------------------------------
    // Region geometry — floating over the full screen, no bordered modal box
    // -----------------------------------------------------------------------------------
    private fun left(): Int = 0
    private fun top(): Int = 0
    private fun right(): Int = this.width
    private fun bottom(): Int = this.height

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
            if (visibleFeatures(c).isNotEmpty() || searchText.isEmpty()) out.add(c)
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
        return cx0() + visibleIndex * (columnWidth() + COLUMN_GUTTER)
    }

    /** A single computed row rect within a column; the one source of truth both render and hit-testing consume. */
    private class RowLayout(
        val feature: Feature,
        val rowTop: Int, val rowBottom: Int,
        val subTop: Int, val subBottom: Int
    )

    private fun layoutColumn(c: Column, scrollOffset: Int): List<RowLayout> {
        val out = ArrayList<RowLayout>()
        var y = cyTop() - scrollOffset
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
    private fun maxScrollFor(c: Column): Int = Math.max(0, columnContentHeight(c) - (cyBot() - cyTop()))
    private fun clampScroll(c: Column) { c.scroll = MathHelper.clamp(c.scroll, 0, maxScrollFor(c)) }

    // -----------------------------------------------------------------------------------
    // Background: solid dark (matches the mockup), no vanilla blur/dirt
    // -----------------------------------------------------------------------------------
    override fun renderBackground(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) { }
    override fun renderInGameBackground(ctx: DrawContext) { }

    // -----------------------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------------------
    override fun render(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (resetArmed && System.currentTimeMillis() - resetArmedAt > 3000) resetArmed = false
        for (c in visibleColumns()) clampScroll(c)

        // fresh batch of NanoVG draw commands this frame — replayed for real later, in
        // paintNvgOverlay(), once GameRendererNvgMixin fires after the vanilla GUI flush
        NvgRecorder.clear()

        // blur the live game behind the columns instead of just darkening it, plus a light scrim for text contrast
        applyBlur(ctx)
        ctx.fillGradient(0, 0, this.width, this.height, DIM_TOP, DIM_BOT)

        hoverDesc = null
        renderTopBar(ctx, mouseX, mouseY)
        renderContent(ctx, mouseX, mouseY)
        renderSearchBar(ctx, mouseX, mouseY)
        renderHoverTooltip(ctx)

        super.render(ctx, mouseX, mouseY, delta)
    }

    /** Geometry for the 4 top-right pill buttons — the one source of truth for both render and hit-testing. */
    private fun topBarButtonRects(): Array<IntArray> {
        val labels = arrayOf("Edit HUD", "Credits", if (resetArmed) "Confirm?" else "Reset", "Save & Close")
        val bh = 20
        val gap = 8
        val y = MARGIN - 2
        val rects = Array(4) { IntArray(0) }
        var x = right() - MARGIN
        for (i in 3 downTo 0) {
            val w = sw(this.textRenderer, labels[i], 0.85f) + 20
            x -= w
            rects[i] = intArrayOf(x, y, w, bh)
            x -= gap
        }
        return rects
    }

    private fun renderTopBar(ctx: DrawContext, mouseX: Int, mouseY: Int) {
        // wordmark "FishMod" (top-left, no bar/border)
        val ws = 1.3f
        sst(ctx, this.textRenderer, "Fish", MARGIN, MARGIN, TEXT_COLOR, ws)
        val fw = sw(this.textRenderer, "Fish", ws)
        sst(ctx, this.textRenderer, "Mod", MARGIN + fw, MARGIN, ACCENT, ws)

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

    private fun drawPillButton(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, label: String, filled: Boolean, accent: Int, hover: Boolean) {
        val textW = sw(this.textRenderer, label, 0.85f)
        if (filled) {
            roundedRect(ctx, x, y, w, h, h / 2, if (hover) ACCENT_HOVER else accent)
            sst(ctx, this.textRenderer, label, x + (w - textW) / 2, y + (h - 8) / 2, 0xFF06302F.toInt(), 0.85f)
        } else {
            roundedRectRing(ctx, x, y, w, h, h / 2 - 1, 1, if (hover) 0xFF20272E.toInt() else 0xFF171C21.toInt(), if (hover) ACCENT_HOVER else accent)
            sst(ctx, this.textRenderer, label, x + (w - textW) / 2, y + (h - 8) / 2, if (hover) ACCENT_HOVER else TEXT_COLOR, 0.85f)
        }
    }

    /** The pill box, magnifying-glass icon, and placeholder are drawn via NanoVG like everything
     *  else; the live typed text + caret use [nvgTextFieldContent] (text only, no box —
     *  the pill ring above already is the box) since the real [TextFieldWidget] still owns
     *  cursor/selection/IME state, just not its own rendering. */
    private fun renderSearchBar(ctx: DrawContext, mouseX: Int, mouseY: Int) {
        val bw = 190
        val bh = 24
        val bx = (this.width - bw) / 2
        val by = this.height - BOTTOM_RESERVE + (BOTTOM_RESERVE - bh) / 2 - 8
        roundedRectRing(ctx, bx, by, bw, bh, bh / 2, 1, 0xFF14181D.toInt(), if (searchFocused) ACCENT else 0xFF3A3F48.toInt())

        val gx = bx + 16
        val gy = by + bh / 2 - 1
        disc(ctx, gx, gy, 3, SUBTEXT_COLOR)
        NvgRecorder.fillRect((gx + 2).toFloat(), (gy + 2).toFloat(), 4f, 1f, SUBTEXT_COLOR)

        var field = searchField
        if (field == null) {
            field = TextFieldWidget(this.textRenderer, bx + 30, by + 6, bw - 40, bh - 12, Text.empty())
            field.setMaxLength(48)
            field.setDrawsBackground(false)
            field.setChangedListener { s -> searchText = s; for (c in columns) c.scroll = 0 }
            searchField = field
        } else {
            field.setX(bx + 30); field.setY(by + 6); field.setWidth(bw - 40)
        }
        if (searchText.isEmpty() && !searchFocused) {
            sst(ctx, this.textRenderer, "Search…", bx + 30, by + (bh - 8) / 2, SUBTEXT_COLOR, 0.9f)
        } else {
            nvgTextFieldContent(field, bx + 30, by + 6, bw - 40, bh - 12)
        }
    }

    private fun renderColumnCard(ctx: DrawContext, c: Column, x0: Int, x1: Int, cardBottom: Int, mouseX: Int, mouseY: Int) {
        val hy = cyTop() - HEADER_H
        val w = x1 - x0
        NvgRecorder.dropShadow(x0.toFloat(), hy.toFloat(), w.toFloat(), (cardBottom - hy).toFloat(), CARD_RADIUS.toFloat(), 10f, 0x60000000)
        roundedRect(ctx, x0, hy, w, cardBottom - hy, CARD_RADIUS, CARD_BG)
        NvgRecorder.fillRect((x0 + CARD_RADIUS).toFloat(), hy.toFloat(), (w - 2 * CARD_RADIUS).toFloat(), HEADER_STRIP_H.toFloat(), ACCENT)
        sst(ctx, this.textRenderer, c.name, x0 + 10, hy + HEADER_STRIP_H + 6, TEXT_COLOR, 1f)
    }

    private fun renderColumnScrollbar(ctx: DrawContext, c: Column, x0: Int, x1: Int, top: Int, bot: Int) {
        val ms = maxScrollFor(c)
        if (ms <= 0) return
        val trackX = x1 - 3
        val vp = bot - top
        val barH = Math.max(20, (vp.toLong() * vp / columnContentHeight(c)).toInt())
        val barY = top + ((vp - barH).toLong() * c.scroll / ms).toInt()
        NvgRecorder.fillRect(trackX.toFloat(), top.toFloat(), 2f, (bot - top).toFloat(), 0xFF141A20.toInt())
        NvgRecorder.fillRect(trackX.toFloat(), barY.toFloat(), 2f, barH.toFloat(), ACCENT)
    }

    private fun renderContent(ctx: DrawContext, mouseX: Int, mouseY: Int) {
        val cols = visibleColumns()
        val top = cyTop()
        val bot = cyBot()
        val colW = columnWidth()

        for (i in cols.indices) {
            val c = cols[i]
            val x0 = columnX0(i)
            val x1 = x0 + colW
            val colBottom = Math.min(top + columnContentHeight(c), bot)

            renderColumnCard(ctx, c, x0, x1, colBottom, mouseX, mouseY)

            NvgRecorder.pushScissor(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), (colBottom - top).toFloat())
            for (rl in layoutColumn(c, c.scroll)) {
                if (rl.rowBottom > top && rl.rowTop < colBottom) renderRow(ctx, rl.feature, x0, x1, rl.rowTop, mouseX, mouseY)
                val animH = rl.subBottom - rl.subTop
                if (animH > 0 && rl.subBottom > top && rl.subTop < colBottom) {
                    renderSubPanel(ctx, rl.feature, x0, x1, rl.subTop, animH, mouseX, mouseY)
                }
            }
            NvgRecorder.popScissor()

            renderColumnScrollbar(ctx, c, x0, x1, top, colBottom)
        }
    }

    private fun renderRow(ctx: DrawContext, f: Feature, x0: Int, x1: Int, top: Int, mouseX: Int, mouseY: Int) {
        val on = f.hasMaster() && f.get!!.get()
        val inView = mouseY >= cyTop() && mouseY <= cyBot()
        val hover = inView && mouseX >= x0 && mouseX <= x1 && mouseY >= top && mouseY <= top + ROW_H

        if (on) NvgRecorder.fillRect((x0 + 2).toFloat(), top.toFloat(), (x1 - x0 - 4).toFloat(), ROW_H.toFloat(), ROW_ENABLED)
        if (hover) NvgRecorder.fillRect((x0 + 2).toFloat(), top.toFloat(), (x1 - x0 - 4).toFloat(), ROW_H.toFloat(), ROW_HOVER)
        if (on) NvgRecorder.fillRect((x0 + 2).toFloat(), (top + 3).toFloat(), 2f, (ROW_H - 6).toFloat(), ACCENT)

        var label = f.name
        val maxTextW = x1 - x0 - 20
        if (stw(this.textRenderer, label) > maxTextW) {
            while (label.length > 1 && stw(this.textRenderer, "$label…") > maxTextW) label = label.substring(0, label.length - 1)
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
    private fun renderHoverTooltip(ctx: DrawContext) {
        val desc = hoverDesc ?: return
        val tw = stw(this.textRenderer, desc)
        val bw = tw + 16
        val bh = 18
        val bx = Math.min(hoverDescX, this.width - bw - 4)
        val by = hoverDescY
        roundedRectRing(ctx, bx, by, bw, bh, 5, 1, 0xFF14181D.toInt(), ACCENT)
        st(ctx, this.textRenderer, desc, bx + 8, by + 5, TEXT_COLOR)
    }

    /** While animating, pushes its own narrower scissor to hide the not-yet-revealed portion of
     *  the panel, then pops it — NanoVG's nvgSave/nvgRestore stack (see NvgRecorder.pushScissor/
     *  popScissor) makes this a real nested scope, so the caller's own wider clip (already pushed
     *  in renderContent) is preserved automatically once this pops back out. */
    private fun renderSubPanel(ctx: DrawContext, f: Feature, x0: Int, x1: Int, top: Int, animatedH: Int, mouseX: Int, mouseY: Int) {
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
            if (s !is SubcategoryHeader && s !is InputSetting && s !is ColorPickerSetting) {
                // Center within the fixed top strip (ITEM_HEIGHT), not the full row height `sh` —
                // for expandable settings (DropdownSetting) `sh` grows as the option list opens,
                // which would otherwise drag this label downward mid-animation even though the
                // label itself always lives in that fixed top strip.
                st(ctx, this.textRenderer, s.name, leftX + 2, sy + (ITEM_HEIGHT - 8) / 2, TEXT_COLOR)
            }
            s.render(ctx, leftX, rightX, sy, mouseX, mouseY, this.textRenderer)
            sy += sh
        }

        if (animating) NvgRecorder.popScissor()
    }

    private fun hovBtn(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    // -----------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------
    override fun mouseClicked(click: Click, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        val btn = click.button()

        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(btn))
            capturingKeybind = null
            return true
        }

        val prevInput = activeInput
        if (prevInput is InputSetting && prevInput.textField != null) prevInput.textField!!.setFocused(false)
        activeInput = null

        // ----- search (floating pill, bottom-center) -----
        val swW = 190
        val swH = 24
        val sx = (this.width - swW) / 2
        val sy0 = this.height - BOTTOM_RESERVE + (BOTTOM_RESERVE - swH) / 2 - 8
        searchFocused = mx >= sx && mx <= sx + swW && my >= sy0 && my <= sy0 + swH
        searchField?.setFocused(searchFocused)
        if (searchFocused) return true

        // ----- top-right pill buttons -----
        val rects = topBarButtonRects()
        if (hovBtn(mx, my, rects[0][0], rects[0][1], rects[0][2], rects[0][3])) { MinecraftClient.getInstance().setScreen(FishHudEditor(this)); return true }
        if (hovBtn(mx, my, rects[1][0], rects[1][1], rects[1][2], rects[1][3])) { MinecraftClient.getInstance().setScreen(CreditsScreen(this)); return true }
        if (hovBtn(mx, my, rects[2][0], rects[2][1], rects[2][2], rects[2][3])) {
            if (resetArmed) { resetAllColumns(); resetArmed = false }
            else { resetArmed = true; resetArmedAt = System.currentTimeMillis() }
            return true
        }
        if (hovBtn(mx, my, rects[3][0], rects[3][1], rects[3][2], rects[3][3])) { close(); return true }

        // ----- content columns / rows / sub-panels -----
        if (my >= cyTop() && my <= cyBot()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val col = cols[ci]
                val x0 = columnX0(ci)
                val x1 = x0 + colW
                if (mx < x0 || mx > x1) continue

                for (rl in layoutColumn(col, col.scroll)) {
                    val f = rl.feature
                    // row hit — left-click toggles master on/off, right-click toggles the expand panel
                    // (features with no master toggle expand on either click)
                    if (my >= rl.rowTop && my <= rl.rowBottom) {
                        if (f.hasMaster()) {
                            if (btn == 1 && f.sub.isNotEmpty()) f.toggleExpanded()
                            else f.set!!.accept(!f.get!!.get())
                        } else if (f.sub.isNotEmpty()) {
                            f.toggleExpanded()
                        }
                        return true
                    }
                    // sub-panel hit
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
                                val slx = rightX - SLIDER_W - 2
                                val sly = ssy + (ITEM_HEIGHT - SLIDER_H) / 2
                                if (mx >= slx && mx <= slx + SLIDER_W && my >= sly - 4 && my <= sly + SLIDER_H + 4) {
                                    activeSlider = s; activeSliderX = slx; s.onDrag(mx, slx, SLIDER_W); return true
                                }
                            }
                            ssy += sh
                        }
                        return true // swallow clicks inside the body
                    }
                }
                return true // swallow clicks in the column's empty space
            }
            return true
        }
        return super.mouseClicked(click, bl)
    }

    override fun mouseDragged(click: Click, deltaX: Double, deltaY: Double): Boolean {
        val slider = activeSlider
        if (slider != null) { slider.onDrag(click.x().toInt(), activeSliderX, SLIDER_W); return true }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: Click): Boolean {
        activeSlider = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val cols = visibleColumns()
        val colW = columnWidth()
        for (i in cols.indices) {
            val x0 = columnX0(i)
            val x1 = x0 + colW
            if (mouseX >= x0 && mouseX <= x1) {
                val c = cols[i]
                c.scroll = MathHelper.clamp((c.scroll - verticalAmount * 18).toInt(), 0, maxScrollFor(c))
                return true
            }
        }
        return true
    }

    private fun resetAllColumns() {
        for (c in columns) for (f in c.features) {
            if (f.hasMaster() && f.get!!.get()) f.set!!.accept(false)
        }
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(
                if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
                    net.minecraft.client.util.InputUtil.UNKNOWN_KEY
                else
                    net.minecraft.client.util.InputUtil.fromKeyCode(input)
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

    override fun charTyped(input: CharInput): Boolean {
        val ai = activeInput
        if (ai is InputSetting && ai.textField != null) {
            ai.textField!!.charTyped(input); ai.setter.accept(ai.textField!!.text); return true
        }
        if (ai is InputIntSetting && ai.textField != null) { ai.textField!!.charTyped(input); return true }
        if (ai is InputDoubleSetting && ai.textField != null) { ai.textField!!.charTyped(input); return true }
        if (searchFocused && searchField != null) {
            searchField!!.charTyped(input); searchText = searchField!!.text; for (c in columns) c.scroll = 0; return true
        }
        return super.charTyped(input)
    }

    private val nvgGlState = fishmod.utils.rendering.NvgGlStateGuard()

    /** Called by GameRendererNvgMixin right after the vanilla GUI flush each frame this screen is
     *  open — the one point per frame where NanoVG's immediate GL draws land after (not before)
     *  all of this frame's vanilla content, giving correct z-ordering for free. */
    fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = fishmod.utils.rendering.NvgContext.get()

            // Device pixel ratio must be real GUI-scale-derived, not a fixed 1.0 — NanoVG bakes font
            // glyphs into its atlas at a resolution scaled by this ratio so they stay crisp once
            // stretched across the (larger) real framebuffer viewport.
            val pixelRatio = MinecraftClient.getInstance().window.scaleFactor.toFloat()
            org.lwjgl.nanovg.NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay()
            org.lwjgl.nanovg.NanoVG.nvgEndFrame(ctx)

            fishmod_glCheck("after paintNvgOverlay")
        } catch (t: Throwable) {
            // Fail safe instead of crash-looping the render thread: the screen still opens with
            // its vanilla dim/blur background, just missing the NanoVG-drawn buttons/text/cards.
            // Logged once (this runs every frame the screen is open) - usually means the bundled
            // LWJGL NanoVG native failed to load: wrong OS/arch natives, or the LWJGL version was
            // overridden by a custom launcher instance and no longer matches what we bundled.
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

    override fun shouldPause(): Boolean = false

    override fun close() {
        Config.manager.save()
        FishConfig.manager.save()
        super.close()
    }

    // -----------------------------------------------------------------------------------
    // Model
    // -----------------------------------------------------------------------------------
    class Column(val name: String, val icon: String) {
        val features: MutableList<Feature> = ArrayList()
        var scroll = 0
    }

    class Feature(val name: String, val get: Supplier<Boolean>?, val set: Consumer<Boolean>?) {
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

    // -----------------------------------------------------------------------------------
    // Setting widgets
    // -----------------------------------------------------------------------------------
    abstract class Setting(var name: String, var description: String) {
        abstract fun render(ctx: DrawContext, leftX: Int, rightX: Int, settingY: Int, mouseX: Int, mouseY: Int, tr: TextRenderer)
        open fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, settingY: Int, button: Int): Boolean = false
        open fun onDrag(mx: Int, sx: Int, sliderW: Int) {}
        open fun getHeight(): Int = ITEM_HEIGHT
    }

    class SubcategoryHeader(name: String) : Setting(name, "") {
        override fun getHeight(): Int = SUBCAT_HEIGHT
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            roundRect(ctx, leftX, sy, rightX, sy + SUBCAT_HEIGHT, 2, 0xFF11131A.toInt())
            ctx.fill(leftX + 1, sy + 2, leftX + 3, sy + SUBCAT_HEIGHT - 2, ACCENT)
            st(ctx, tr, name, leftX + 6, sy + (SUBCAT_HEIGHT - 8) / 2, ACCENT)
        }
    }

    /** Odin-style rounded pill toggle with a hollow accent ring and an animated sliding knob. */
    class ToggleSetting(name: String, desc: String, val getter: Supplier<Boolean>, val setter: Consumer<Boolean>) : Setting(name, desc) {
        private val knobAnim = Easing.Anim(150)
        private var lastValue: Boolean? = null
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            val on = getter.get()
            if (lastValue == null) { lastValue = on; knobAnim.setTarget(on) }
            else if (lastValue != on) { lastValue = on; knobAnim.setTarget(on) }
            val tx = rightX - W - 2
            val ty = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= tx && mx <= tx + W && my >= ty && my <= ty + PILL_H
            drawTogglePill(ctx, tx, ty, W, PILL_H, on, knobAnim.progress(), hov)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val tx = rightX - W - 2
            val ty = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= tx && mx <= tx + W && my >= ty && my <= ty + PILL_H) {
                setter.accept(!getter.get()); return true
            }
            return false
        }
        companion object { const val W = 34 }
    }

    class SliderIntSetting(name: String, desc: String, val getter: Supplier<Int>, val setter: Consumer<Int>, val min: Int, val max: Int) : Setting(name, desc) {
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            val slx = rightX - SLIDER_W - 2
            val sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2
            val pct = (getter.get() - min).toFloat() / (max - min)
            pill(ctx, slx, sly, slx + SLIDER_W, sly + SLIDER_H, SLIDER_BG)
            val fillW = (SLIDER_W * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            val v = getter.get().toString()
            st(ctx, tr, v, slx + SLIDER_W - stw(tr, v), sly - 9, SUBTEXT_COLOR)
        }
        override fun onDrag(mx: Int, sx: Int, sliderW: Int) {
            val pct = MathHelper.clamp((mx - sx).toFloat() / sliderW, 0f, 1f)
            setter.accept(min + (pct * (max - min)).toInt())
        }
    }

    class SliderDoubleSetting(name: String, desc: String, val getter: Supplier<Double>, val setter: Consumer<Double>, val min: Double, val max: Double) : Setting(name, desc) {
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            val slx = rightX - SLIDER_W - 2
            val sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2
            val pct = ((getter.get() - min) / (max - min)).toFloat()
            pill(ctx, slx, sly, slx + SLIDER_W, sly + SLIDER_H, SLIDER_BG)
            val fillW = (SLIDER_W * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            val v = String.format("%.1f", getter.get())
            st(ctx, tr, v, slx + SLIDER_W - stw(tr, v), sly - 9, SUBTEXT_COLOR)
        }
        override fun onDrag(mx: Int, sx: Int, sliderW: Int) {
            val pct = MathHelper.clamp((mx - sx).toFloat() / sliderW, 0f, 1f)
            setter.accept(min + pct * (max - min))
        }
    }

    // Click to advance to the next value; right-click goes back one.
    /** Odin-style selector: a rounded pill showing the current value; click expands an animated
     *  inline list of every option beneath it (right-click quick-cycles without expanding). */
    class DropdownSetting<T>(name: String, desc: String, val values: Array<T>, val getter: Supplier<T>, val setter: Consumer<T>) : Setting(name, desc) {
        private val expandAnim = Easing.Anim(200)
        private var expanded = false
        private var pillX = 0
        private var pillW = 0

        private fun indexOfCurrent(): Int {
            val cur = getter.get()
            for (i in values.indices) if (values[i] === cur || values[i] == cur) return i
            return 0
        }

        override fun getHeight(): Int {
            return ITEM_HEIGHT + Math.round(values.size * OPTION_H * expandAnim.progress())
        }

        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            val current = getter.get().toString()
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
                if (animating) ctx.enableScissor(leftX, oy, rightX, oy + animH)
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
                if (animating) ctx.disableScissor()
            }
        }

        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H) {
                if (btn == 1) {
                    setter.accept(values[(indexOfCurrent() + 1) % values.size])
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
                        setter.accept(values[i])
                        expanded = false
                        expandAnim.setTarget(false)
                        return true
                    }
                }
            }
            return false
        }
    }

    open class InputSetting(name: String, desc: String, val getter: Supplier<String>, val setter: Consumer<String>) : Setting(name, desc) {
        var textField: TextFieldWidget? = null
        var hint: String? = null
        open fun initField(tr: TextRenderer) {
            if (textField == null) {
                val tf = TextFieldWidget(tr, 0, 0, INPUT_W, INPUT_H, Text.empty())
                tf.setMaxLength(256)
                tf.text = getter.get()
                tf.setChangedListener(setter)
                textField = tf
            }
        }
        override fun getHeight(): Int = if (hint != null) 35 else 26
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            initField(tr)
            st(ctx, tr, name, leftX + 2, sy + 1, TEXT_COLOR)
            val ix = leftX + 2
            val iy = sy + 11
            val fieldW = rightX - leftX - 4
            val tf = textField!!
            if (!tf.isFocused) { tf.setSelectionStart(0); tf.setSelectionEnd(0) }
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
                    val len = tf.text.length
                    tf.setSelectionStart(len); tf.setSelectionEnd(len)
                }
                return true
            }
            return false
        }
    }

    class LimitedInputSetting(name: String, desc: String, val maxVisible: Int, getter: Supplier<String>, setter: Consumer<String>) :
        InputSetting("", desc, getter, capWrapper(setter, maxVisible)) {
        val displayLabel: String = name
        override fun getHeight(): Int = ITEM_HEIGHT + 9
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            st(ctx, tr, displayLabel, leftX + 2, sy + 1, TEXT_COLOR)
            initField(tr)
            val ix = rightX - INPUT_W - 2
            val iy = sy + 2
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
            val len = visibleLen(getter.get())
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
            private fun capWrapper(inner: Consumer<String>, max: Int): Consumer<String> {
                return Consumer { v ->
                    var s = v ?: ""
                    while (s.isNotEmpty() && visibleLen(s) > max) s = s.substring(0, s.length - 1)
                    inner.accept(s)
                }
            }
        }
    }

    /** Odin-style dropdown of preset named colors — same expand/collapse mechanic as
     *  [DropdownSetting], picked over a free-form HSB square + hue bar because it needs no
     *  live vanilla widget and no drag-square geometry, just the same fixed-option-list pattern
     *  that's already known to render correctly. */
    open class ColorPickerSetting(name: String, desc: String, val getter: Supplier<Int>, val setter: Consumer<Int>) : Setting(name, desc) {
        private val expandAnim = Easing.Anim(200)
        private var expanded = false
        private var pillX = 0
        private var pillW = 0

        private fun indexOfCurrent(): Int {
            val cur = getter.get() or 0xFF000000.toInt()
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

        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
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
            disc(ctx, pillX + 12, by + PILL_H / 2, swatchD / 2, getter.get() or 0xFF000000.toInt())
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
                    setter.accept(PRESET_ARGB[(indexOfCurrent() + 1) % PRESET_ARGB.size])
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
                        setter.accept(PRESET_ARGB[i])
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
                0xFFFFFFFF.toInt(), 0xFFFF5555.toInt(), 0xFFFFAA00.toInt(), 0xFFFFFF55.toInt(), 0xFF55FF55.toInt(), 0xFF55FFFF.toInt(),
                0xFF5555FF.toInt(), 0xFFAA00AA.toInt(), 0xFFFF55FF.toInt(), 0xFFAAAAAA.toInt(), 0xFF555555.toInt(), 0xFF000000.toInt()
            )
            val PRESET_NAMES: Array<String> = arrayOf(
                "White", "Red", "Orange", "Yellow", "Green", "Aqua",
                "Blue", "Purple", "Pink", "Gray", "Dark Gray", "Black"
            )
        }
    }

    class ConditionalColorPickerSetting(
        name: String, desc: String, val visible: Supplier<Boolean>,
        getter: Supplier<Int>, setter: Consumer<Int>
    ) : ColorPickerSetting(name, desc, getter, setter) {
        val shownName: String = name
        override fun getHeight(): Int {
            if (!visible.get()) { this.name = ""; return 0 }
            this.name = shownName
            return super.getHeight()
        }
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            if (!visible.get()) return
            super.render(ctx, leftX, rightX, sy, mx, my, tr)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            if (!visible.get()) return false
            return super.onClick(mx, my, leftX, rightX, sy, btn)
        }
    }

    class ButtonSetting(name: String, desc: String, val action: Runnable) : Setting(name, desc) {
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
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

    /** In-GUI rebind box for a vanilla [net.minecraft.client.option.KeyBinding] — click, then
     *  press a key or mouse button to bind it (Esc unbinds). Stays in sync with Options > Controls
     *  since it edits the same KeyBinding object. */
    /** Odin-style rounded pill rebind box — click, then press a key/mouse button (Esc unbinds). */
    class KeybindSetting(name: String, desc: String, val getter: Supplier<net.minecraft.client.option.KeyBinding?>) : Setting(name, desc) {
        var capturing = false
        private var pillX = 0
        private var pillW = 0
        private fun label(): String {
            if (capturing) return "..."
            val kb = getter.get() ?: return "-"
            return if (kb.isUnbound) "Not Bound" else kb.boundKeyLocalizedText.string
        }
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
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
        fun applyKey(key: net.minecraft.client.util.InputUtil.Key) {
            val kb = getter.get() ?: return
            kb.setBoundKey(key)
            net.minecraft.client.option.KeyBinding.updateKeysByCode()
            MinecraftClient.getInstance().options.write()
            capturing = false
        }
    }

    class InputIntSetting(name: String, desc: String, val getter: Supplier<Int>, val setter: Consumer<Int>) : Setting(name, desc) {
        var textField: TextFieldWidget? = null
        fun initField(tr: TextRenderer) {
            if (textField == null) {
                val tf = TextFieldWidget(tr, 0, 0, INPUT_W, INPUT_H, Text.empty())
                tf.setMaxLength(10)
                tf.text = getter.get().toString()
                tf.setChangedListener { s ->
                    try { setter.accept(Integer.parseInt(s.trim())) }
                    catch (ignored: NumberFormatException) {}
                }
                textField = tf
            }
        }
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            initField(tr)
            val ix = rightX - INPUT_W - 2
            val iy = sy + (ITEM_HEIGHT - INPUT_H) / 2
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = rightX - INPUT_W - 2
            val iy = sy + (ITEM_HEIGHT - INPUT_H) / 2
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                textField?.setFocused(true)
                return true
            }
            return false
        }
    }

    class InputDoubleSetting(name: String, desc: String, val getter: Supplier<Double>, val setter: Consumer<Double>) : Setting(name, desc) {
        var textField: TextFieldWidget? = null
        fun initField(tr: TextRenderer) {
            if (textField == null) {
                val tf = TextFieldWidget(tr, 0, 0, INPUT_W, INPUT_H, Text.empty())
                tf.setMaxLength(12)
                tf.text = getter.get().toString()
                tf.setChangedListener { s ->
                    try { setter.accept(java.lang.Double.parseDouble(s.trim())) }
                    catch (ignored: NumberFormatException) {}
                }
                textField = tf
            }
        }
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {
            initField(tr)
            val ix = rightX - INPUT_W - 2
            val iy = sy + (ITEM_HEIGHT - INPUT_H) / 2
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = rightX - INPUT_W - 2
            val iy = sy + (ITEM_HEIGHT - INPUT_H) / 2
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                textField?.setFocused(true)
                return true
            }
            return false
        }
    }

    class LabelSetting(name: String, desc: String) : Setting(name, desc) {
        override fun render(ctx: DrawContext, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: TextRenderer) {}
    }

    companion object {
        // ----- palette (recolored teal — matches the mod's existing accent, not a reference-repo copy) -----
        private val ACCENT = 0xFF24B6B0.toInt()
        private val ACCENT_HOVER = 0xFF3AD8D1.toInt()
        private const val DIM_TOP = 0x2E000000 // light scrim over the blurred game, just enough for text contrast
        private const val DIM_BOT = 0x50000000
        private val CARD_BG = 0xFF14181D.toInt() // floating card body
        private const val ROW_HOVER = 0x1EFFFFFF // translucent hover wash over a row
        private const val ROW_ENABLED = 0x2624B6B0 // translucent accent tint over an enabled row
        private val SUBROW_BG = 0xFF0F1317.toInt()
        private val TRACK_OFF = 0xFF3A3F48.toInt() // toggle/keybind/dropdown pill track when inactive
        private val TEXT_COLOR = 0xFFEDF1F5.toInt()
        private val SUBTEXT_COLOR = 0xFF8A96A3.toInt()
        private val CHEVRON_COLOR = 0xFF6C7885.toInt()

        private const val TEXT_SCALE = 0.75f

        // ----- screen chrome (floating elements, no bordered modal box) -----
        private const val MARGIN = 16
        private const val TOP_BAR_H = 26 // reserved space for wordmark + top-right pill buttons
        private const val BOTTOM_RESERVE = 46 // reserved space for the floating search pill

        // ----- multi-column layout -----
        private const val COLUMN_GUTTER = 12 // px between column cards
        private const val CARD_RADIUS = 7
        private const val HEADER_H = 24 // header bar height (icon + name)
        private const val HEADER_STRIP_H = 3 // accent strip thickness at header top
        private const val MIN_COLUMN_W = 136 // floor so controls don't clip

        // ----- row / setting-widget geometry -----
        private const val ROW_H = 22
        private const val ROW_GAP = 3
        private const val ITEM_HEIGHT = 22
        private const val PILL_H = 18 // sub-setting toggle/dropdown/keybind pill height
        private const val OPTION_H = 16 // dropdown option-list row height
        private val SLIDER_BG = 0xFF2C3138.toInt()
        private val SLIDER_FILL = ACCENT
        private const val SLIDER_W = 56
        private const val SLIDER_H = 5
        private const val INPUT_W = 62
        private const val INPUT_H = 14
        private const val SUBCAT_HEIGHT = 13

        private var nvgFailureLogged = false

        /** Base NanoVG font size (px) that reads at roughly the same visual weight as Minecraft's
         *  default font at its normal size; TEXT_SCALE/arbitrary scale factors multiply this. */
        private const val NVG_BASE_TEXT_SIZE = 9.5f

        /** All shape/text helpers below now push into [NvgRecorder] instead of drawing via
         *  `ctx` directly — NanoVG paints strictly after every vanilla draw this frame (see
         *  GameRendererNvgMixin), so leaving any of these on vanilla would always render underneath
         *  the converted ones regardless of call order. `ctx` is kept in each signature only to
         *  avoid rippling through every existing call site. */

        /** True filled rounded rectangle. */
        fun roundedRect(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
            NvgRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), color)
        }

        /** Corner-coordinate overload matching `ctx.fill`'s (x1,y1,x2,y2) convention. */
        fun roundRect(ctx: DrawContext, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, color: Int) {
            roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, color)
        }

        /** A rounded rect with a hollow accent-colored ring of `strokeW` around it. */
        fun roundedRectRing(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, r: Int, strokeW: Int, fillColor: Int, ringColor: Int) {
            NvgRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), strokeW.toFloat(), fillColor, ringColor)
        }

        /** True pill (fully rounded rectangle whose radius is half its height). Takes corner coordinates, like `ctx.fill`. */
        fun pill(ctx: DrawContext, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
            val h = y2 - y1
            roundedRect(ctx, x1, y1, x2 - x1, h, h / 2, color)
        }

        /** 1px border frame around a fill (square corners — used for tiny non-decorative frames). */
        fun panel(ctx: DrawContext, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, fill: Int, border: Int) {
            roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, border)
            roundedRect(ctx, x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, Math.max(0, r - 1), fill)
        }

        /** True filled circle — used for glyphs and toggle knobs. */
        fun disc(ctx: DrawContext, cx: Int, cy: Int, r: Int, color: Int) {
            NvgRecorder.disc(cx.toFloat(), cy.toFloat(), r.toFloat(), color)
        }

        /** Sub-panel menu text at TEXT_SCALE. */
        fun st(ctx: DrawContext, tr: TextRenderer, s: String, x: Int, y: Int, color: Int) {
            NvgRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * TEXT_SCALE, color)
        }
        fun stw(tr: TextRenderer, s: String): Int = Math.ceil(NvgRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()

        /** Text at an arbitrary scale. */
        fun sst(ctx: DrawContext, tr: TextRenderer, s: String, x: Int, y: Int, color: Int, scale: Float) {
            NvgRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * scale, color)
        }
        fun sw(tr: TextRenderer, s: String, scale: Float): Int = Math.ceil(NvgRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * scale).toDouble()).toInt()

        private const val INPUT_TEXT_SIZE = 7f

        /** Draws a [TextFieldWidget]'s box + text + blinking caret entirely via NanoVG. A live
         *  `textField.render(...)` call is a vanilla DrawContext draw, which flushes *before*
         *  this frame's NanoVG column-card background — so it would render underneath that background
         *  and be invisible, exactly like the search bar would be if it weren't carved out into its
         *  own non-overlapping strip. The widget still owns cursor/selection/edit state (keyPressed/
         *  charTyped delegate to it elsewhere); only the drawing is redone here, measured with
         *  NanoVG's own font metrics since vanilla's getCharacterX() uses Minecraft's font instead. */
        fun nvgTextField(tf: TextFieldWidget, x: Int, y: Int, w: Int, h: Int) {
            val focused = tf.isFocused
            NvgRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 3f, 1f, SUBROW_BG, if (focused) ACCENT else TRACK_OFF)
            nvgTextFieldContent(tf, x, y, w, h)
        }

        /** Just the text + blinking caret, no box — for fields whose box is drawn separately (the
         *  search bar's own pill ring already serves as its box, so calling [nvgTextField]
         *  there would nest a second box inside it). */
        fun nvgTextFieldContent(tf: TextFieldWidget, x: Int, y: Int, w: Int, h: Int) {
            val text = tf.text
            val cursor = Math.min(tf.cursor, text.length)
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

        /** Small triangle: pointing down when `open`, right when closed; `cy` is the vertical centre. */
        fun drawChevron(ctx: DrawContext, gx: Int, cy: Int, open: Boolean, color: Int) {
            NvgRecorder.chevron(gx.toFloat(), cy.toFloat(), open, color)
        }

        /** Tiny vector emblem (~14px) centred at (cx,cy). `bg` is the tile fill, for knockouts. */
        private fun drawGlyph(ctx: DrawContext, t: String, cx: Int, cy: Int, c: Int, bg: Int) {
            when (t) {
                "gear" -> {
                    disc(ctx, cx, cy, 5, c)
                    ctx.fill(cx - 1, cy - 7, cx + 1, cy + 7, c); ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c)
                    ctx.fill(cx - 5, cy - 5, cx - 3, cy - 3, c); ctx.fill(cx + 3, cy - 5, cx + 5, cy - 3, c)
                    ctx.fill(cx - 5, cy + 3, cx - 3, cy + 5, c); ctx.fill(cx + 3, cy + 3, cx + 5, cy + 5, c)
                    disc(ctx, cx, cy, 2, bg)
                }
                "arch" -> {
                    ctx.fill(cx - 6, cy - 6, cx - 3, cy + 7, c); ctx.fill(cx + 3, cy - 6, cx + 6, cy + 7, c)
                    ctx.fill(cx - 6, cy - 6, cx + 6, cy - 3, c)
                }
                "hanger" -> {
                    ctx.fill(cx - 7, cy + 2, cx + 7, cy + 4, c)
                    ctx.fill(cx - 1, cy - 5, cx + 1, cy + 3, c)
                    ctx.fill(cx - 1, cy - 6, cx + 3, cy - 4, c)
                }
                "people" -> {
                    disc(ctx, cx - 4, cy - 3, 3, c); disc(ctx, cx + 4, cy - 3, 3, c)
                    ctx.fill(cx - 7, cy + 2, cx + 7, cy + 6, c)
                }
                "eye" -> {
                    ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c); ctx.fill(cx - 5, cy - 3, cx + 5, cy + 3, c)
                    disc(ctx, cx, cy, 2, bg); disc(ctx, cx, cy, 1, c)
                }
                "text" -> {
                    ctx.fill(cx - 5, cy - 5, cx + 5, cy - 3, c); ctx.fill(cx - 1, cy - 5, cx + 1, cy + 6, c)
                }
                "chat" -> {
                    ctx.fill(cx - 7, cy - 5, cx + 7, cy + 2, c); ctx.fill(cx - 5, cy + 2, cx - 1, cy + 6, c)
                    ctx.fill(cx - 4, cy - 2, cx + 4, cy - 1, bg); ctx.fill(cx - 4, cy, cx + 2, cy + 1, bg)
                }
                "star" -> {
                    ctx.fill(cx - 1, cy - 7, cx + 1, cy + 7, c); ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c)
                    ctx.fill(cx - 4, cy - 4, cx - 2, cy - 2, c); ctx.fill(cx + 2, cy - 4, cx + 4, cy - 2, c)
                    ctx.fill(cx - 4, cy + 2, cx - 2, cy + 4, c); ctx.fill(cx + 2, cy + 2, cx + 4, cy + 4, c)
                }
                "cube" -> {
                    ctx.fill(cx - 6, cy - 6, cx + 6, cy - 4, c); ctx.fill(cx - 6, cy + 4, cx + 6, cy + 6, c)
                    ctx.fill(cx - 6, cy - 6, cx - 4, cy + 6, c); ctx.fill(cx + 4, cy - 6, cx + 6, cy + 6, c)
                }
                "clock" -> {
                    disc(ctx, cx, cy, 6, c); disc(ctx, cx, cy, 4, bg)
                    ctx.fill(cx - 1, cy - 4, cx + 1, cy + 1, c); ctx.fill(cx - 1, cy - 1, cx + 4, cy + 1, c)
                }
                "coin" -> {
                    disc(ctx, cx, cy, 6, c); disc(ctx, cx, cy, 3, bg); disc(ctx, cx, cy, 1, c)
                }
                "palette" -> {
                    disc(ctx, cx, cy, 6, c)
                    ctx.fill(cx - 3, cy - 3, cx - 1, cy - 1, bg); ctx.fill(cx + 1, cy - 3, cx + 3, cy - 1, bg)
                    ctx.fill(cx - 1, cy + 1, cx + 1, cy + 3, bg)
                }
                "tag" -> {
                    ctx.fill(cx - 6, cy - 4, cx + 2, cy + 4, c); ctx.fill(cx + 2, cy - 3, cx + 4, cy + 3, c)
                    ctx.fill(cx + 4, cy - 1, cx + 6, cy + 1, c); disc(ctx, cx - 3, cy, 1, bg)
                }
                "slider" -> {
                    ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c); ctx.fill(cx, cy - 4, cx + 4, cy + 4, c)
                }
                "bell" -> {
                    ctx.fill(cx - 4, cy - 3, cx + 4, cy + 3, c); ctx.fill(cx - 5, cy + 3, cx + 5, cy + 4, c)
                    ctx.fill(cx - 1, cy - 6, cx + 1, cy - 4, c); ctx.fill(cx - 1, cy + 4, cx + 1, cy + 6, c)
                }
                "map" -> {
                    ctx.fill(cx - 6, cy - 5, cx + 6, cy + 5, c); ctx.fill(cx - 1, cy - 5, cx + 1, cy + 5, bg)
                    ctx.fill(cx - 6, cy - 1, cx + 6, cy + 1, bg)
                }
                else -> { // box
                    ctx.fill(cx - 5, cy - 5, cx + 5, cy - 3, c); ctx.fill(cx - 5, cy + 3, cx + 5, cy + 5, c)
                    ctx.fill(cx - 5, cy - 5, cx - 3, cy + 5, c); ctx.fill(cx + 3, cy - 5, cx + 5, cy + 5, c)
                }
            }
        }

        /** Short one-line description shown under each row label. */
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
                "Maxor Tick Timer" -> "Tick timer during Maxor (P1)"
                "Crystal Spawn" -> "Crystal spawn countdown + reminder"
                "Storm Tick Timer" -> "Tick timer during Storm (P2)"
                "Storm Death Time" -> "Show when Storm died"
                "LB Release Timer" -> "Countdown to the Last Breath shot: Archer 34.35s, Healer 34.05s"
                "Storm Crushed Noti" -> "Alert when Storm is crushed"
                "Goldor Tick Timer" -> "Terminal-phase tick timer"
                "Goldor Leap Timer" -> "Countdown from Goldor's death to when to leap"
                "Term Start Timer" -> "Countdown to terminals start"
                "Section Progress" -> "Terminal section completed/total"
                "Goldor Splits" -> "S1-S4 terminal split timers + total time"
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

        /** Description for a toggle registered via [FishModAddonApi] (e.g. from an addon mod). */
        private fun descForExternal(name: String): String {
            for (et in FishModAddonApi.dungeonToggles) {
                if (et.name() == name) return et.description()
            }
            return ""
        }

        /** Builds a command-input row for an inventory button (the hint reminds it's a command, no slash). */
        private fun makeButtonInput(name: String, getter: Supplier<String>, setter: Consumer<String>): InputSetting {
            val s = InputSetting(name, "", getter, setter)
            s.hint = "command without /"
            return s
        }

        /** Odin-style rounded pill with a hollow accent ring and a sliding circular knob. Static so the
         *  static nested Setting subclasses (which have no outer-instance reference) can call it too. */
        fun drawTogglePill(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, on: Boolean, knobProgress: Float, hover: Boolean) {
            val track = if (on) (if (hover) ACCENT_HOVER else ACCENT) else TRACK_OFF
            val ring = if (on) (if (hover) ACCENT_HOVER else ACCENT) else (if (hover) 0xFF565C68.toInt() else 0xFF464C56.toInt())
            roundedRectRing(ctx, x, y, w, h, h / 2, 2, track, ring)
            val knobR = h / 2 - 3
            val knobX = x + knobR + 3 + Math.round(knobProgress * (w - 2 * (knobR + 3)))
            disc(ctx, knobX, y + h / 2, knobR, 0xFFFFFFFF.toInt())
        }
    }
}
