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
import fishmod.utils.config.values.Visual
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

    // stack-segment drag state: left-drag reorders siblings live, right-drag restructures on release (merge onto a column / pop to top level / no-op onto own parent)
    private var dragTabParent: Column? = null
    private var dragTabChild: Column? = null
    private var dragTabGrabDY = 0
    private var dragTabMouseX = 0
    private var dragTabMouseY = 0
    private var dragTabRightClick = false

    // Cascading curtain open/close animation. Timestamp-driven (not frame-counted) since this is a
    // NanoVG immediate-mode renderer and duration/stagger are user-configurable at runtime.
    private val screenOpenTime = System.currentTimeMillis()
    private var closing = false
    private var closeStartTime = 0L
    private var closeFinalized = false

    init {
        buildCategories()
        applySavedColumnOrder()
        // watchdog: paintNvgOverlay() only runs via GameRendererNvgMixin; if that injection never fires the screen sits blank with no log, so surface it to the player
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
        val invStorage = Column("Inventory & Storage", "cube")
        val party = Column("Party & Social", "people")
        val dungeon = Column("Dungeons", "arch")
        val dungeonTrackers = Column("Dungeon Trackers", "coin")
        val dungeonMap = Column("Dungeon Map", "map")
        val solvers = Column("Dungeon Solvers", "slider")
        val floor7 = Column("Floor 7", "clock")
        val hud = Column("HUD & Overlays", "bell")
        val visuals = Column("Visuals & Rendering", "eye")
        val cosmetics = Column("Cosmetics", "hanger")

        run {
            val f = Feature("UI Customization", null, null)
            f.sub.add(SubcategoryHeader("Background"))
            f.sub.add(DropdownSetting("Background Preset", "", arrayOf("Dark Glass", "Deep Blue", "Crimson", "Violet", "Custom"),
                { FishSettings.fmBgPreset },
                { v -> FishSettings.fmBgPreset = v }))
            f.sub.add(ColorPickerSetting("Custom Color", "", FishSettings::fmBgCustomColor).gatedBy { FishSettings.fmBgPreset == "Custom" })
            f.sub.add(SliderIntSetting("Background Alpha %", "0% invisible - 100% solid", FishSettings::fmBgAlpha, 0, 100))
            f.sub.add(SubcategoryHeader("Accent"))
            f.sub.add(ColorPickerSetting("Accent Color", "", FishSettings::fmButtonColor))
            f.sub.add(SliderIntSetting("Accent Opacity %", "0% invisible - 100% solid", FishSettings::fmButtonAlpha, 0, 100))
            f.sub.add(SubcategoryHeader("Buttons"))
            f.sub.add(ColorPickerSetting("Button Color", "Tint behind an enabled row, e.g. Door Colors", FishSettings::fmRowColor))
            f.sub.add(SliderIntSetting("Button Opacity %", "0% invisible - 100% solid", FishSettings::fmRowAlpha, 0, 100))
            f.sub.add(SubcategoryHeader("Cascade Animation"))
            f.sub.add(ToggleSetting("Menu Animations", "Off = the menu just appears; no cascade / expand / toggle slides", FishSettings::fmAnimations))
            f.sub.add(SliderIntSetting("Drop Duration (ms)", "200 snappy - 1200 dramatic", FishSettings::fmDropDurationMs, 200, 1200, 25).gatedBy { FishSettings.fmAnimations })
            f.sub.add(SliderIntSetting("Stagger Delay (ms)", "Extra delay per column, 0 = all at once", FishSettings::fmStaggerDelayMs, 0, 120, 5).gatedBy { FishSettings.fmAnimations })
            f.sub.add(DropdownSetting("Exit Style", "How columns animate on close", arrayOf("Floor Fall", "Reverse Curtain"),
                { FishSettings.fmExitStyle },
                { v -> FishSettings.fmExitStyle = v }).gatedBy { FishSettings.fmAnimations })
            general.features.add(f)
        }
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
        run {
            val f = Feature("Slot Binds", FishSettings::slotBindsEnabled)
            f.sub.add(SubcategoryHeader("Hold the bind key + click a hotbar slot then an inv slot to link; shift-left-click to swap"))
            f.sub.add(KeybindSetting("Bind Key (hold)", "Default R", { fishmod.utils.Keybinds.slotBind }))
            f.sub.add(SubcategoryHeader("Profiles — separate bind sets; type an existing name to switch, a new name to start one"))
            f.sub.add(InputSetting("Profile", "", FishSettings::slotBindsProfile))
            f.sub.add(ButtonSetting("New Profile", "Fresh empty set", Runnable { fishmod.features.SlotBinds.newProfile() }))
            f.sub.add(ButtonSetting("Delete Profile", "Remove the current set (Default is only cleared)", Runnable { fishmod.features.SlotBinds.deleteActiveProfile() }))
            f.sub.add(KeybindSetting("Cycle Profile Key", "", { fishmod.utils.Keybinds.slotBindCycleProfile }))
            f.sub.add(ToggleSetting("Show Bound Slots", "", FishSettings::slotBindsShow))
            f.sub.add(ToggleSetting("Connecting Line", "Draw a line between a bound pair", FishSettings::slotBindsLine).gatedBy { FishSettings.slotBindsShow })
            f.sub.add(ToggleSetting("Slot Border", "Outline each bound slot", FishSettings::slotBindsBorder).gatedBy { FishSettings.slotBindsShow })
            f.sub.add(ToggleSetting("Hover Only", "Only show a link when hovering one of its slots", FishSettings::slotBindsHoverOnly).gatedBy { FishSettings.slotBindsShow })
            f.sub.add(ColorPickerSetting("Colour", "", FishSettings::slotBindsColor).gatedBy { FishSettings.slotBindsShow })
            general.features.add(f)
        }
        run {
            // Every generic chat QoL toggle in one card — was 5 separate cards (Smart Copy Chat,
            // Compact Chat, Infinite Chat History, Chat Search, Chat Filter).
            val f = Feature("Chat", FishSettings::chatFeatureEnabled)
            f.sub.add(ToggleSetting("Smart Copy Chat", "", FishSettings::smartCopyChat))
            f.sub.add(ToggleSetting("Compact Chat", "Collapse identical messages within the last minute into one \"(N)\" line", FishSettings::chatCompact))
            f.sub.add(SubcategoryHeader("Infinite Chat History"))
            f.sub.add(ToggleSetting("Infinite Chat History", "", FishSettings::infiniteChatHistory))
            f.sub.add(SliderIntSetting("Max Lines", "Scrollback + sent-message history kept (vanilla is 100)",
                FishSettings::infiniteChatHistoryLimit, 500, 20000, 500).gatedBy { FishSettings.infiniteChatHistory })
            f.sub.add(SubcategoryHeader("Chat Search"))
            f.sub.add(ToggleSetting("Chat Search", "", FishSettings::chatSearch))
            f.sub.add(SubcategoryHeader("Bind \"FishMod: Toggle Chat Search\" in Options → Controls; press it while chat is open to show the search field")
                .gatedBy { FishSettings.chatSearch })
            f.sub.add(SubcategoryHeader("Chat Peek"))
            f.sub.add(ToggleSetting("Chat Peek", "Hold the bound key to pull up chat fully opaque and scroll through it, without opening the chat box", FishSettings::chatPeek))
            f.sub.add(KeybindSetting("Peek Key (hold)", "Unbound by default", { fishmod.utils.Keybinds.chatPeek }).gatedBy { FishSettings.chatPeek })
            f.sub.add(SubcategoryHeader("Chat Filter"))
            f.sub.add(ToggleSetting("Chat Filter", "", FishSettings::chatFilterEnabled))
            f.sub.add(ToggleSetting("Kill Combo", "", FishSettings::cfKillCombo).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Boss Messages", "", FishSettings::cfBossMessages).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Friend Join/Leave", "", FishSettings::cfFriendJoinLeave).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Bazaar", "", FishSettings::cfBazaar).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Warping", "", FishSettings::cfWarping).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Skyblock/Dungeon Spam", "NoammAddons' full useless-message list", FishSettings::cfNoammSpam).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Collapse Blank Lines", "Drop repeated empty chat lines", FishSettings::cfCollapseBlank).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(ToggleSetting("Custom Regex", "Apply the list below", FishSettings::cfCustom).gatedBy { FishSettings.chatFilterEnabled })
            f.sub.add(InputSetting("Patterns", "One regex per line (or ;-separated)",
                { FishSettings.cfCustomPatterns }, { v -> FishSettings.cfCustomPatterns = v ?: "" })
                .gatedBy { FishSettings.chatFilterEnabled && FishSettings.cfCustom })
            general.features.add(f)
        }
        run {
            val f = Feature("No Cursor Reset", FishSettings::noCursorReset)
            f.sub.add(SliderIntSetting("Unhook Timeout (ms)", "Window after a GUI opens where the cursor is kept in place",
                FishSettings::noCursorResetMs, 0, 1000, 10))
            general.features.add(f)
        }
        general.features.add(Feature("Arrow Fix (shortbow pullback)", FishSettings::arrowFixEnabled))
        general.features.add(Feature("Mono Audio", FishSettings::monoAudioEnabled))
        general.features.add(Feature("Sword Blocking", FishSettings::swordBlockingEnabled))
        run {
            val f = Feature("Ragnarock", FishSettings::ragnarockEnabled)
            f.sub.add(ToggleSetting("Cast Alert", "Title when you start casting", FishSettings::ragnarockCastAlert))
            f.sub.add(ToggleSetting("Cancelled Alert", "Title when a cast is interrupted", FishSettings::ragnarockCancelAlert))
            f.sub.add(ToggleSetting("Announce Cast to Party", "", FishSettings::ragnarockAnnounceParty))
            f.sub.add(ToggleSetting("Strength Timer", "Moveable HUD countdown of the 10s Ragnarock buff (edit position in the HUD editor)", FishSettings::ragnarockTimer))
            f.sub.add(ToggleSetting("P5 Rag", "Title \"Rag\" when Wither King's pre-fight taunt appears", FishSettings::p5RagEnabled))
            general.features.add(f)
        }
        run {
            val f = Feature("Animations", FishSettings::animEnabled)
            f.sub.add(SubcategoryHeader("First-person hand view-model"))
            f.sub.add(SliderDoubleSetting("Item Scale", "0 = normal", FishSettings::animItemScale, -1.5, 1.5))
            f.sub.add(SliderDoubleSetting("Pos X", "", FishSettings::animX, -2.0, 2.0))
            f.sub.add(SliderDoubleSetting("Pos Y", "", FishSettings::animY, -2.0, 2.0))
            f.sub.add(SliderDoubleSetting("Pos Z", "", FishSettings::animZ, -2.0, 2.0))
            f.sub.add(SliderDoubleSetting("Rot X", "degrees", FishSettings::animRotX, -50.0, 50.0))
            f.sub.add(SliderDoubleSetting("Rot Y", "degrees", FishSettings::animRotY, -50.0, 50.0))
            f.sub.add(SliderDoubleSetting("Rot Z", "degrees", FishSettings::animRotZ, -50.0, 50.0))
            f.sub.add(SliderDoubleSetting("Swing X", "1 = normal", FishSettings::animSwingX, 0.0, 2.0))
            f.sub.add(SliderDoubleSetting("Swing Y", "1 = normal", FishSettings::animSwingY, 0.0, 2.0))
            f.sub.add(SliderDoubleSetting("Swing Z", "1 = normal", FishSettings::animSwingZ, 0.0, 2.0))
            f.sub.add(SliderDoubleSetting("Swing Speed", "0 normal · +5 fast · -5 slow", FishSettings::animSwingSpeed, -5.0, 5.0))
            f.sub.add(ToggleSetting("Ignore Haste", "Swing speed isn't affected by Haste", FishSettings::animIgnoreHaste))
            f.sub.add(ToggleSetting("No Equip Animation", "", FishSettings::animNoEquip))
            f.sub.add(ToggleSetting("No Hand Movement", "Stop the item bobbing when you look around", FishSettings::animNoHandMove))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Etherwarp Helper", FishSettings::etherwarpHelperEnabled)
            f.sub.add(SubcategoryHeader("Sneak + hold an AOTV-type item to see the landing guess"))
            f.sub.add(ToggleSetting("Show Guess Box", "", FishSettings::etherwarpShowGuess))
            f.sub.add(ColorPickerSetting("Box Color", "", FishSettings::etherwarpColor).gatedBy { FishSettings.etherwarpShowGuess })
            f.sub.add(ToggleSetting("Show When Failed", "", FishSettings::etherwarpShowFail))
            f.sub.add(ColorPickerSetting("Failed Color", "", FishSettings::etherwarpFailColor).gatedBy { FishSettings.etherwarpShowFail })
            f.sub.add(ToggleSetting("Full Block", "Box the whole block, not its shape", FishSettings::etherwarpFullBlock))
            f.sub.add(ToggleSetting("Through Walls", "", FishSettings::etherwarpDepth))
            f.sub.add(SliderIntSetting("Range", "Blocks", FishSettings::etherwarpRange, 1, 61))
            f.sub.add(ToggleSetting("Cast Sound", "", FishSettings::etherwarpSoundEnabled))
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.etherwarpSoundName }, { v -> FishSettings.etherwarpSoundName = v },
                { FishSettings.etherwarpSoundVolume }, { FishSettings.etherwarpSoundPitch }).gatedBy { FishSettings.etherwarpSoundEnabled })
            f.sub.add(SliderIntSetting("Sound Volume %", "Above 100 = louder (stacked plays)", FishSettings::etherwarpSoundVolume, 0, 500, 10).gatedBy { FishSettings.etherwarpSoundEnabled })
            f.sub.add(SliderDoubleSetting("Sound Pitch", "", FishSettings::etherwarpSoundPitch, 0.5, 2.0).gatedBy { FishSettings.etherwarpSoundEnabled })
            visuals.features.add(f)
        }
        run {
            val f = Feature("Lava To Water", FishSettings::lavaToWaterEnabled)
            f.sub.add(ToggleSetting("Custom Tint", "", FishSettings::lavaToWaterTint))
            f.sub.add(ColorPickerSetting("Tint Color", "", FishSettings::lavaToWaterColor).gatedBy { FishSettings.lavaToWaterTint })
            f.sub.add(ToggleSetting("Hide Lava Fog", "", FishSettings::lavaToWaterHideFog))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Storage Overlay", FishSettings::storageOverlayEnabled)
            f.sub.add(SubcategoryHeader("All pages + search over /storage; also /storageview"))
            f.sub.add(SliderIntSetting("Columns (width)", "", FishSettings::storageViewerColumns, 1, 10))
            f.sub.add(SliderIntSetting("Max Height", "", FishSettings::storageMaxHeight, 80, 900))
            f.sub.add(SliderDoubleSetting("Scale", "Overlay size", FishSettings::storageOverlayScale, 0.5, 2.0))
            f.sub.add(SliderIntSetting("Scroll Speed", "", FishSettings::storageScrollSpeed, 1, 50))
            f.sub.add(ToggleSetting("Retain Scroll", "Keep scroll offset after closing", FishSettings::storageRetainScroll))
            f.sub.add(ToggleSetting("Hide Non-Matching Pages", "While searching", FishSettings::storageHideNonMatching))
            f.sub.add(KeybindSetting("Open Viewer", "Standalone cache browser", { fishmod.utils.Keybinds.storageViewer }))
            invStorage.features.add(f)
        }
        invStorage.features.add(Feature("Container Value", FishSettings::containerValueEnabled))
        run {
            val f = Feature("Guild Bridge Bot", FishSettings::bridgeBotEnabled)
            f.sub.add(SubcategoryHeader("Reformats \"Guild > Bot: Player » msg\" and hides the raw bot line"))
            f.sub.add(InputSetting("Bot Name", "The bridge bot's exact in-game name",
                { FishSettings.bridgeBotName },
                { v -> FishSettings.bridgeBotName = v; fishmod.features.BridgeBot.rebuildPattern() }))
            general.features.add(f)
        }
        run {
            val f = Feature("Twitch Bridge",
                { FishSettings.twitchBridgeEnabled },
                { v -> twitchbridge.TwitchBridgeClient.setEnabled(v) })
            f.sub.add(SubcategoryHeader("Shows a Twitch channel's chat in the MC chat window — read-only, no login"))
            f.sub.add(InputSetting("Channel", "Twitch channel login name (e.g. shroud)",
                { twitchbridge.TwitchBridgeClient.config().channel },
                { v -> twitchbridge.TwitchBridgeClient.setChannel(v) }))
            f.sub.add(InputSetting("Line Prefix", "Text before every bridged line",
                { twitchbridge.TwitchBridgeClient.config().prefix },
                { v -> twitchbridge.TwitchBridgeClient.config().prefix = v; twitchbridge.TwitchBridgeClient.config().save() }))
            f.sub.add(ToggleSetting("Twitch Name Colors", "Use each chatter's own name colour",
                { twitchbridge.TwitchBridgeClient.config().useTwitchColors },
                { v -> twitchbridge.TwitchBridgeClient.config().useTwitchColors = v; twitchbridge.TwitchBridgeClient.config().save() }))
            f.sub.add(ToggleSetting("Timestamps", "Prefix each line with local HH:mm",
                { twitchbridge.TwitchBridgeClient.config().showTimestamps },
                { v -> twitchbridge.TwitchBridgeClient.config().showTimestamps = v; twitchbridge.TwitchBridgeClient.config().save() }))
            f.sub.add(ToggleSetting("Sub / Raid Notices", "Also show sub/raid/announcement events",
                { twitchbridge.TwitchBridgeClient.config().showEvents },
                { v -> twitchbridge.TwitchBridgeClient.config().showEvents = v; twitchbridge.TwitchBridgeClient.config().save() }))
            f.sub.add(ToggleSetting("Auto-Connect on Launch", "Reconnect automatically each game start",
                { twitchbridge.TwitchBridgeClient.config().autoConnect },
                { v -> twitchbridge.TwitchBridgeClient.config().autoConnect = v; twitchbridge.TwitchBridgeClient.config().save() }))
            general.features.add(f)
        }
        run {
            val f = Feature("Auto Sprint", FishSettings::autoSprintEnabled)
            f.sub.add(ToggleSetting("Dungeons Only", "", FishSettings::autoSprintDungeonOnly))
            general.features.add(f)
        }
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
                { v -> FishSettings.compactTabStatBarPosition = v }).gatedBy { FishSettings.compactTabStatBarEnabled })
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
        // Dungeon Score lives entirely under the Dungeon Map column now (Info HUD readout + Score Messages alerts)
        dungeonTrackers.features.add(Feature("PB Pace", FishSettings::pbPaceEnabled))
        solvers.features.add(Feature("Puzzle Overlay", FishSettings::showPuzzles))
        run {
            val f = Feature("Puzzle Solvers", FishSettings::puzzleSolversEnabled)
            f.sub.add(DropdownSetting("Box Style", "", arrayOf("Filled", "Outline", "Filled Outline"),
                { FishSettings.puzzleSolverStyle }, { v -> FishSettings.puzzleSolverStyle = v }))
            f.sub.add(SubcategoryHeader("Three Weirdos"))
            f.sub.add(ToggleSetting("Weirdos Solver", "", FishSettings::weirdosSolver))
            f.sub.add(ColorPickerSetting("Correct Color", "", FishSettings::weirdosCorrectColor).gatedBy { FishSettings.weirdosSolver })
            f.sub.add(ColorPickerSetting("Wrong Color", "", FishSettings::weirdosWrongColor).gatedBy { FishSettings.weirdosSolver })
            f.sub.add(SubcategoryHeader("Blaze"))
            f.sub.add(ToggleSetting("Blaze Solver", "", FishSettings::blazeSolver))
            f.sub.add(ColorPickerSetting("Next Blaze", "", FishSettings::blazeFirstColor).gatedBy { FishSettings.blazeSolver })
            f.sub.add(ColorPickerSetting("Second Blaze", "", FishSettings::blazeSecondColor).gatedBy { FishSettings.blazeSolver })
            f.sub.add(ColorPickerSetting("Other Blazes", "", FishSettings::blazeOtherColor).gatedBy { FishSettings.blazeSolver })
            f.sub.add(ToggleSetting("Connecting Line", "", FishSettings::blazeLine).gatedBy { FishSettings.blazeSolver })
            f.sub.add(SliderIntSetting("Line Count", "", FishSettings::blazeLineCount, 1, 9).gatedBy { FishSettings.blazeSolver && FishSettings.blazeLine })
            f.sub.add(SubcategoryHeader("Quiz"))
            f.sub.add(ToggleSetting("Quiz Solver", "", FishSettings::quizSolver))
            f.sub.add(ColorPickerSetting("Quiz Color", "", FishSettings::quizColor).gatedBy { FishSettings.quizSolver })
            f.sub.add(SubcategoryHeader("Water Board"))
            f.sub.add(ToggleSetting("Water Solver", "", FishSettings::waterSolver))
            f.sub.add(ColorPickerSetting("Next Lever", "", FishSettings::waterFirstColor).gatedBy { FishSettings.waterSolver })
            f.sub.add(ColorPickerSetting("Then Lever", "", FishSettings::waterSecondColor).gatedBy { FishSettings.waterSolver })
            f.sub.add(SubcategoryHeader("Creeper Beams"))
            f.sub.add(ToggleSetting("Beams Solver", "", FishSettings::beamsSolver))
            f.sub.add(ToggleSetting("Beams Tracer", "", FishSettings::beamsTracer).gatedBy { FishSettings.beamsSolver })
            f.sub.add(SubcategoryHeader("Teleport Maze"))
            f.sub.add(ToggleSetting("TP Maze Solver", "", FishSettings::tpMazeSolver))
            f.sub.add(ColorPickerSetting("Next Pad", "", FishSettings::tpMazeNextColor).gatedBy { FishSettings.tpMazeSolver })
            f.sub.add(ColorPickerSetting("Visited Pad", "", FishSettings::tpMazeVisitedColor).gatedBy { FishSettings.tpMazeSolver })
            f.sub.add(SubcategoryHeader("Tic Tac Toe"))
            f.sub.add(ToggleSetting("TTT Solver", "", FishSettings::tttSolver))
            f.sub.add(ColorPickerSetting("TTT Color", "", FishSettings::tttColor).gatedBy { FishSettings.tttSolver })
            f.sub.add(ToggleSetting("Prevent Miss-Click", "Block right-clicking a non-optimal button", FishSettings::tttPreventMissClick).gatedBy { FishSettings.tttSolver })
            f.sub.add(ToggleSetting("Prediction", "Show the guardian's reply + your prefire buttons", FishSettings::tttPrediction).gatedBy { FishSettings.tttSolver })
            f.sub.add(ColorPickerSetting("Prefire Color", "", FishSettings::tttPredictionColor).gatedBy { FishSettings.tttSolver && FishSettings.tttPrediction })
            f.sub.add(SubcategoryHeader("Boulder"))
            f.sub.add(ToggleSetting("Boulder Solver", "", FishSettings::boulderSolver))
            f.sub.add(ToggleSetting("Show All Clicks", "", FishSettings::boulderShowAll).gatedBy { FishSettings.boulderSolver })
            f.sub.add(ColorPickerSetting("Boulder Color", "", FishSettings::boulderColor).gatedBy { FishSettings.boulderSolver })
            f.sub.add(SubcategoryHeader("Ice Fill"))
            f.sub.add(ToggleSetting("Ice Fill Solver", "", FishSettings::iceFillSolver))
            f.sub.add(ToggleSetting("Optimized Patterns", "Use the harder/faster fill routes", FishSettings::iceFillOptimized).gatedBy { FishSettings.iceFillSolver })
            f.sub.add(ColorPickerSetting("Ice Fill Color", "", FishSettings::iceFillColor).gatedBy { FishSettings.iceFillSolver })
            solvers.features.add(f)
        }
        run {
            // Key Notifier + Room Timer — two small, unrelated on-screen-title alerts, one card.
            // Master toggle mirrors "either sub-feature on"; flipping it drives both at once.
            val f = Feature("Key & Room Timer",
                { Dungeons.enableKeyNotifier || FishSettings.roomTimerEnabled },
                { v -> Dungeons.enableKeyNotifier = v; FishSettings.roomTimerEnabled = v })
            f.sub.add(SubcategoryHeader("Key Notifier"))
            f.sub.add(ToggleSetting("Key Notifier", "", Dungeons::enableKeyNotifier))
            f.sub.add(ToggleSetting("Title", "", FishSettings::keyNotifierTitle).gatedBy { Dungeons.enableKeyNotifier })
            f.sub.add(ToggleSetting("Chat", "", FishSettings::keyNotifierChat).gatedBy { Dungeons.enableKeyNotifier })
            f.sub.add(ToggleSetting("Sound", "", FishSettings::keyNotifierSound).gatedBy { Dungeons.enableKeyNotifier })
            f.sub.add(SliderIntSetting("Title Duration (ms)", "", FishSettings::keyNotifierDurationMs, 500, 8000, 250)
                .gatedBy { Dungeons.enableKeyNotifier && FishSettings.keyNotifierTitle })
            f.sub.add(SubcategoryHeader("Room Timer"))
            f.sub.add(ToggleSetting("Room Timer", "On-screen title when the room you're in clears / all its secrets are done", FishSettings::roomTimerEnabled))
            f.sub.add(ToggleSetting("\"Cleared\" Title", "Show when the room's mobs are done", FishSettings::roomTimerClear).gatedBy { FishSettings.roomTimerEnabled })
            f.sub.add(ToggleSetting("\"Secrets Done\" Title", "Show when every secret in the room is done", FishSettings::roomTimerSecrets).gatedBy { FishSettings.roomTimerEnabled })
            f.sub.add(ToggleSetting("Show Time", "Append the time it took, e.g. Cleared (12.3s)", FishSettings::roomTimerShowTime).gatedBy { FishSettings.roomTimerEnabled })
            f.sub.add(ToggleSetting("Personal Bests", "Track & show the fastest clear / secrets per room", FishSettings::roomTimerPb).gatedBy { FishSettings.roomTimerEnabled })
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Boss Health Numbers", Dungeons::bossHealthNumbers))
        run {
            val f = Feature("Waypoints", FishSettings::dungeonWaypointsEnabled)
            f.sub.add(SubcategoryHeader("Master toggle for /fm wp — placed boxes, titles and route lines"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Leap", FishSettings::leapMenuEnabled)
            f.sub.add(SubcategoryHeader("Menu (custom 2x2 Spirit Leap GUI, click a cell or press 1-4)"))
            f.sub.add(ToggleSetting("Map View", "Show the dungeon map instead — click a teammate's head to leap (1-4 still work)", FishSettings::leapMenuMap))
            f.sub.add(ToggleSetting("Map View: Only After BR", "Map view stays off until the blood door is opened", FishSettings::leapMenuMapAfterBR).gatedBy { FishSettings.leapMenuMap })
            f.sub.add(SliderIntSetting("Menu Scale %", "", FishSettings::leapMenuScale, 40, 220))
            f.sub.add(ToggleSetting("Number Keybinds", "1-4 leap to that cell", FishSettings::leapMenuKeybinds))
            f.sub.add(ToggleSetting("Left-Click Only", "Ignore right/middle click", FishSettings::leapMenuLeftClickOnly))
            f.sub.add(ToggleSetting("Tint Dead Players", "", FishSettings::leapMenuTintDead))
            f.sub.add(ToggleSetting("Show Name", "", FishSettings::leapMenuShowName))
            f.sub.add(ToggleSetting("Show Class", "", FishSettings::leapMenuShowClass))
            f.sub.add(DropdownSetting("Sort By", "", arrayOf("Class Order", "Name A-Z", "Odin Sorting"),
                { arrayOf("Class Order", "Name A-Z", "Odin Sorting")[FishSettings.leapMenuSort] },
                { v -> FishSettings.leapMenuSort = arrayOf("Class Order", "Name A-Z", "Odin Sorting").indexOf(v).coerceAtLeast(0) }))
            f.sub.add(InputSetting("Class Order", "Comma-separated: MAGE,BERSERK,ARCHER,HEALER,TANK",
                { FishSettings.leapMenuClassOrder }, { v -> FishSettings.leapMenuClassOrder = v }).gatedBy { FishSettings.leapMenuSort == 0 })
            f.sub.add(SubcategoryHeader("Message"))
            f.sub.add(ToggleSetting("Leap Message", "", Dungeons::enableLeapMessages))
            f.sub.add(LabelSetting("{name} target  {class} class", "{c} class letter  ·  & = colours")
                .gatedBy { Dungeons.enableLeapMessages })
            f.sub.add(InputSetting("Message Text", "",
                { FishSettings.leapMessagesText }, { v -> FishSettings.leapMessagesText = v ?: "" })
                .gatedBy { Dungeons.enableLeapMessages })
            f.sub.add(ToggleSetting("As Title", "", FishSettings::leapMessagesTitle).gatedBy { Dungeons.enableLeapMessages })
            f.sub.add(ToggleSetting("Send to Party", "Post the message in party chat", FishSettings::leapMessagesParty).gatedBy { Dungeons.enableLeapMessages })
            f.sub.add(ToggleSetting("Cue Sound", "", FishSettings::leapMessagesSound).gatedBy { Dungeons.enableLeapMessages })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Mimic", FishSettings::mimicAnnounceEnabled)
            f.sub.add(SubcategoryHeader("Announces Mimic, Prince and Bat kills in party chat (F6/F7)"))
            f.sub.add(ToggleSetting("Send Mimic Message", "Toggles the mimic killed message.", FishSettings::mimicMsgEnabled))
            f.sub.add(InputSetting("Mimic Text", "", { FishSettings.mimicMsgText }, { v -> FishSettings.mimicMsgText = v ?: "" })
                .gatedBy { FishSettings.mimicMsgEnabled })
            f.sub.add(ButtonSetting("Mimic Killed", "Send the mimic message now.", Runnable { fishmod.features.dungeon.MimicAnnounce.mimicKilled(true) }))
            f.sub.add(ToggleSetting("Send Prince Message", "Toggles the prince killed message.", FishSettings::princeMsgEnabled))
            f.sub.add(InputSetting("Prince Text", "", { FishSettings.princeMsgText }, { v -> FishSettings.princeMsgText = v ?: "" })
                .gatedBy { FishSettings.princeMsgEnabled })
            f.sub.add(ButtonSetting("Prince Killed", "Send the prince message now.", Runnable { fishmod.features.dungeon.MimicAnnounce.princeKilled(true) }))
            f.sub.add(ToggleSetting("Send Bat Message", "Toggles the bat killed message.", FishSettings::batMsgEnabled))
            f.sub.add(InputSetting("Bat Text", "", { FishSettings.batMsgText }, { v -> FishSettings.batMsgText = v ?: "" })
                .gatedBy { FishSettings.batMsgEnabled })
            f.sub.add(ButtonSetting("Bat Killed", "Send the bat message now.", Runnable { fishmod.features.dungeon.MimicAnnounce.batKilled(true) }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Extra Stats", FishSettings::extraStatsEnabled)
            f.sub.add(SubcategoryHeader("Replaces Hypixel's post-run stats block with a tidy summary"))
            f.sub.add(ToggleSetting("Show Bits", "", FishSettings::extraStatsBits))
            f.sub.add(ToggleSetting("Show Class EXP", "", FishSettings::extraStatsClassExp))
            f.sub.add(ToggleSetting("Show Combat Stats", "Damage / kills / healing", FishSettings::extraStatsCombat))
            f.sub.add(ToggleSetting("Show Teammates", "", FishSettings::extraStatsTeammates))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Terracotta Timer", FishSettings::terracottaTimerEnabled))
        run {
            val f = Feature("Spirit Bear", FishSettings::spiritBearEnabled)
            f.sub.add(SubcategoryHeader("F4/M4 (Thorn): kills-to-spawn count, then spawn timer, then Alive — movable HUD"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Livid Solver", FishSettings::lividSolverEnabled)
            f.sub.add(SubcategoryHeader("Boxes the real Livid in the F5/M5 boss (reads the wool colour)"))
            f.sub.add(ColorPickerSetting("Box Color", "", FishSettings::lividSolverColor))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Architect Draft Refill", FishSettings::architectDraftRefill)
            f.sub.add(SubcategoryHeader("Auto /gfs a First Draft after a puzzle fail"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dungeon Abilities", FishSettings::dungeonAbilitiesEnabled)
            f.sub.add(SubcategoryHeader("Ult = tap-drop (one item) · Mini Ult = ctrl-drop (whole stack)"))
            f.sub.add(KeybindSetting("Ult (drop)", "", { fishmod.utils.Keybinds.dungeonAbility }))
            f.sub.add(KeybindSetting("Mini Ult (ctrl+drop)", "", { fishmod.utils.Keybinds.dungeonAbilityMini }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Blessing Display", FishSettings::blessingDisplayEnabled)
            f.sub.add(ToggleSetting("Power", "", FishSettings::blessingPower))
            f.sub.add(ColorPickerSetting("Power Color", "", FishSettings::blessingPowerColor).gatedBy { FishSettings.blessingPower })
            f.sub.add(ToggleSetting("Time", "", FishSettings::blessingTime))
            f.sub.add(ColorPickerSetting("Time Color", "", FishSettings::blessingTimeColor).gatedBy { FishSettings.blessingTime })
            f.sub.add(ToggleSetting("Stone", "", FishSettings::blessingStone))
            f.sub.add(ColorPickerSetting("Stone Color", "", FishSettings::blessingStoneColor).gatedBy { FishSettings.blessingStone })
            f.sub.add(ToggleSetting("Life", "", FishSettings::blessingLife))
            f.sub.add(ColorPickerSetting("Life Color", "", FishSettings::blessingLifeColor).gatedBy { FishSettings.blessingLife })
            f.sub.add(ToggleSetting("Wisdom", "", FishSettings::blessingWisdom))
            f.sub.add(ColorPickerSetting("Wisdom Color", "", FishSettings::blessingWisdomColor).gatedBy { FishSettings.blessingWisdom })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Invincibility Timer", Dungeons::displayInvincibilityTimer)
            f.sub.add(ToggleSetting("Announce Proc to Party", "", FishSettings::invincAnnounce))
            f.sub.add(ToggleSetting("Show Numeric Time", "X.Xs vs a dot", Dungeons::InvincibilityDuration))
            f.sub.add(ToggleSetting("State Colors", "Gold active / red cooldown / green ready", Dungeons::useStatusColorForInvincibility))
            f.sub.add(ToggleSetting("Mask Cooldown Bar", "Durability-style bar on the mask item", FishSettings::invincShowCooldown))
            f.sub.add(DropdownSetting("Show", "", arrayOf("Always", "Any", "Active", "Cooldown"),
                { FishSettings.invincShowWhen }, { v -> FishSettings.invincShowWhen = v }))
            f.sub.add(ToggleSetting("Only In Boss", "", FishSettings::invincShowInBoss))
            f.sub.add(ToggleSetting("Spirit Mask", "", FishSettings::invincShowSpirit))
            f.sub.add(ToggleSetting("Bonzo Mask", "", FishSettings::invincShowBonzo))
            f.sub.add(ToggleSetting("Phoenix Pet", "", FishSettings::invincShowPhoenix))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Secret Clicked", FishSettings::secretClickedEnabled)
            f.sub.add(ToggleSetting("Boxes", "", FishSettings::secretClickedBoxes))
            f.sub.add(ToggleSetting("Bat Secrets", "Count a killed secret bat you were next to", FishSettings::secretClickedBats))
            f.sub.add(ToggleSetting("Item Secrets", "Count a ground item you walked over", FishSettings::secretClickedItems))
            f.sub.add(DropdownSetting("Box Style", "", arrayOf("Filled", "Outline", "Filled Outline"),
                { FishSettings.secretClickedStyle }, { v -> FishSettings.secretClickedStyle = v }).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ColorPickerSetting("Color", "", FishSettings::secretClickedColor).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ColorPickerSetting("Locked Color", "", FishSettings::secretClickedLockedColor).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(SliderDoubleSetting("Line Width", "", FishSettings::secretClickedLineWidth, 0.5, 10.0).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(SliderIntSetting("Time To Stay (s)", "", FishSettings::secretClickedTimeToStay, 1, 20).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ToggleSetting("Through Walls", "", FishSettings::secretClickedDepthCheck).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ToggleSetting("Box In Boss", "", FishSettings::secretClickedInBoss).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ToggleSetting("Chime", "Sound on secret click", FishSettings::secretClickedChime))
            f.sub.add(ToggleSetting("Chime In Boss", "", FishSettings::secretClickedChimeInBoss).gatedBy { FishSettings.secretClickedChime })
            f.sub.add(SoundSearchSetting("Chime Sound", "Type to search every game sound",
                { FishSettings.secretClickedSoundName }, { v -> FishSettings.secretClickedSoundName = v },
                { FishSettings.secretClickedVolume }, { FishSettings.secretClickedPitch }).gatedBy { FishSettings.secretClickedChime })
            f.sub.add(SliderIntSetting("Chime Volume %", "Above 100 = louder", FishSettings::secretClickedVolume, 0, 500, 10).gatedBy { FishSettings.secretClickedChime })
            f.sub.add(SliderDoubleSetting("Chime Pitch", "", FishSettings::secretClickedPitch, 0.0, 2.0).gatedBy { FishSettings.secretClickedChime })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Auto Requeue", Dungeons::enableAutoRequeue)
            f.sub.add(SliderDoubleSetting("Delay (s)", "After the \"> EXTRA STATS <\" line",
                { FishSettings.autoRequeueDelayMs / 1000.0 },
                { v -> FishSettings.autoRequeueDelayMs = (v * 1000).toInt() }, 0.0, 10.0))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Warp Cooldown", Dungeons::enableWarpCooldown)
            f.sub.add(SliderIntSetting("Cooldown (s)", "Hypixel's real gate is 30s", FishSettings::warpCooldownSeconds, 1, 120))
            f.sub.add(ColorPickerSetting("Timer Color", "", FishSettings::warpCooldownColor))
            f.sub.add(ToggleSetting("Announce Kick", "Post to party chat if you get kicked mid-join", FishSettings::warpAnnounceKick))
            f.sub.add(InputSetting("Kick Text", "", { FishSettings.warpKickText }, { v -> FishSettings.warpKickText = v ?: "" })
                .gatedBy { FishSettings.warpAnnounceKick })
            dungeon.features.add(f)
        }
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
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Loot Tracker", FishSettings::lootTrackerEnabled)
            f.sub.add(DropdownSetting("Price", "",
                FishSettings.PriceMode.values(),
                { FishSettings.trackerPriceModeEnum },
                { v -> FishSettings.trackerPriceModeEnum = v; fishmod.features.croesus.CroesusPrices.applyPriceMode() }))
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Croesus Profit", FishSettings::croesusProfitEnabled)
            f.sub.add(SubcategoryHeader("On the Croesus chest-preview: values each chest, highlights the 2 best, lists profit"))
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Simon Says", FishSettings::simonSaysEnabled)
            f.sub.add(ToggleSetting("Show HUD", "", FishSettings::simonSaysHudEnabled))
            f.sub.add(ToggleSetting("To Party", "", FishSettings::simonSaysPartyChat))
            f.sub.add(ToggleSetting("Fail Msg", "", FishSettings::simonSaysFailEnabled))
            f.sub.add(InputSetting("Fail Text", "", FishSettings::simonSaysFailMessage).gatedBy { FishSettings.simonSaysFailEnabled })

            f.sub.add(SubcategoryHeader("HUD Text (shown when watching someone else's device)"))
            f.sub.add(ToggleSetting("Show Progress", "", FishSettings::ssProgressShowProgress))
            f.sub.add(InputSetting("Progress Text", "\"(n)\" is replaced by the current round", FishSettings::ssProgressProgressText).gatedBy { FishSettings.ssProgressShowProgress })
            f.sub.add(ColorPickerSetting("Progress Color", "", FishSettings::ssProgressProgressColor).gatedBy { FishSettings.ssProgressShowProgress })
            f.sub.add(ToggleSetting("Show Completed", "", FishSettings::ssProgressShowCompleted))
            f.sub.add(InputSetting("Completed Text", "", FishSettings::ssProgressCompletedText).gatedBy { FishSettings.ssProgressShowCompleted })
            f.sub.add(ColorPickerSetting("Completed Color", "", FishSettings::ssProgressCompletedColor).gatedBy { FishSettings.ssProgressShowCompleted })
            f.sub.add(ToggleSetting("Show Reset", "", FishSettings::ssProgressShowReset))
            f.sub.add(InputSetting("Reset Text", "", FishSettings::ssProgressResetText).gatedBy { FishSettings.ssProgressShowReset })
            f.sub.add(ColorPickerSetting("Reset Color", "", FishSettings::ssProgressResetColor).gatedBy { FishSettings.ssProgressShowReset })

            f.sub.add(SubcategoryHeader("Round 1"))
            f.sub.add(ToggleSetting("Announce", "", FishSettings::simon1Enabled))
            f.sub.add(InputSetting("Message", "", FishSettings::simon1Message).gatedBy { FishSettings.simon1Enabled })
            f.sub.add(SubcategoryHeader("Round 2"))
            f.sub.add(ToggleSetting("Announce", "", FishSettings::simon2Enabled))
            f.sub.add(InputSetting("Message", "", FishSettings::simon2Message).gatedBy { FishSettings.simon2Enabled })
            f.sub.add(SubcategoryHeader("Round 3"))
            f.sub.add(ToggleSetting("Announce", "", FishSettings::simon3Enabled))
            f.sub.add(InputSetting("Message", "", FishSettings::simon3Message).gatedBy { FishSettings.simon3Enabled })
            f.sub.add(SubcategoryHeader("Round 4"))
            f.sub.add(ToggleSetting("Announce", "", FishSettings::simon4Enabled))
            f.sub.add(InputSetting("Message", "", FishSettings::simon4Message).gatedBy { FishSettings.simon4Enabled })
            f.sub.add(SubcategoryHeader("Round 5"))
            f.sub.add(ToggleSetting("Announce", "", FishSettings::simon5Enabled))
            f.sub.add(InputSetting("Message", "", FishSettings::simon5Message).gatedBy { FishSettings.simon5Enabled })
            solvers.features.add(f)
        }
        dungeon.features.add(Feature("Class Colored Boots", FishSettings::classColoredBootsEnabled))
        run {
            val f = Feature("M7 Lever Waypoints", FishSettings::enableM7LeverWaypoints)
            f.sub.add(DropdownSetting("Style", "", arrayOf("Outline", "Fill", "Filled Outline"),
                { arrayOf("Outline", "Fill", "Filled Outline")[FishSettings.m7LeverWaypointMode] },
                { v -> FishSettings.m7LeverWaypointMode = arrayOf("Outline", "Fill", "Filled Outline").indexOf(v).coerceAtLeast(0) }))
            f.sub.add(ColorPickerSetting("Color", "", FishSettings::m7LeverWaypointColor))
            f.sub.add(SliderIntSetting("Fill Opacity %", "", FishSettings::m7LeverWaypointOpacity, 0, 100))
            floor7.features.add(f)
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
            val f = Feature("Nametag Stats", FishSettings::nametagStatsEnabled)
            f.sub.add(SubcategoryHeader("Networth under every player; Cata level + secret avg in the Dungeon Hub"))
            f.sub.add(ToggleSetting("Show Own", "", FishSettings::nametagStatsShowSelf))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Prestige Colors", FishSettings::prestigeColorsEnabled)
            f.sub.add(SubcategoryHeader("Recolors Hypixel's SkyBlock [level] badge by level"))
            f.sub.add(SubcategoryHeader("0–300: 15 solid tiers · 300–700: 20 gradient tiers"))
            f.sub.add(ToggleSetting("On Nametags", "", FishSettings::prestigeColorsNametags))
            f.sub.add(ToggleSetting("In Tab List", "", FishSettings::prestigeColorsTab))
            f.sub.add(ToggleSetting("In Chat", "", FishSettings::prestigeColorsChat))
            f.sub.add(ToggleSetting("Gradient Tiers (300+)", "", FishSettings::prestigeColorsGradientTiers))
            f.sub.add(ToggleSetting("Animate Gradients", "", FishSettings::prestigeColorsAnimated)
                .gatedBy { FishSettings.prestigeColorsGradientTiers })
            f.sub.add(SliderDoubleSetting("Animation Speed", "",
                { FishSettings.prestigeColorsAnimSpeed },
                { v -> FishSettings.prestigeColorsAnimSpeed = v }, 0.0, 3.0)
                .gatedBy { FishSettings.prestigeColorsGradientTiers && FishSettings.prestigeColorsAnimated })
            f.sub.add(DropdownSetting("Animation Style", "", arrayOf("FADE", "FLOW"),
                { FishSettings.prestigeColorsAnimStyle },
                { v -> FishSettings.prestigeColorsAnimStyle = v })
                .gatedBy { FishSettings.prestigeColorsGradientTiers && FishSettings.prestigeColorsAnimated })
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
            party.features.add(f)
        }
        run {
            val f = Feature("Command Access List", null, null)
            f.sub.add(SubcategoryHeader("Who can trigger .kick / .warp / .transfer / .promote / .demote"))
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
        run {
            val f = Feature("Party Finder Menu", FishSettings::pfMenuEnabled)
            f.sub.add(ToggleSetting("Level Req on Head", "Red Dungeon-Level-Required number", FishSettings::pfShowLevelReq))
            f.sub.add(ToggleSetting("Missing Classes on Head", "", FishSettings::pfShowMissingClasses))
            f.sub.add(ToggleSetting("Tooltip Stats", "Cata / Secrets / PB per listed member", FishSettings::pfTooltipStats))
            f.sub.add(ToggleSetting("Show Secrets", "", FishSettings::pfShowSecrets))
            f.sub.add(ToggleSetting("Show PB", "Fastest S+ for the listing's floor", FishSettings::pfShowPb))
            f.sub.add(ToggleSetting("Missing List in Tooltip", "", FishSettings::pfTooltipMissingList))
            f.sub.add(ToggleSetting("Highlight Joinable", "Green head for a party missing your class", FishSettings::pfHighlightJoinable))
            f.sub.add(ToggleSetting("Highlight Non-Cata-50", "Orange head for a party with a member below Cata 50", FishSettings::pfHighlightNonCata50))
            f.sub.add(DropdownSetting("My Class", "For the joinable highlight", arrayOf("Auto", "Archer", "Berserk", "Healer", "Mage", "Tank"),
                { FishSettings.pfMyClass }, { v -> FishSettings.pfMyClass = v ?: "Auto" }))
            party.features.add(f)
        }
        run {
            val f = Feature("Party Finder List", FishSettings::pfListPanel)
            f.sub.add(SubcategoryHeader("Scrollable party list — position it in the HUD editor; hover a row to highlight its head"))
            f.sub.add(SliderIntSetting("Max Rows", "Rows shown before scrolling", FishSettings::pfListMaxRows, 3, 10))
            f.sub.add(ToggleSetting("Worst PB", "Slowest listed member's S+ for the listing's floor (Hypixel API)", FishSettings::pfListWorstPb))
            f.sub.add(ToggleSetting("Show Notes", "Include the party note in the row", FishSettings::pfListNotes))
            f.sub.add(ToggleSetting("Click Row to Join", "Left-click a row to click that head", FishSettings::pfListClickJoin))
            f.sub.add(SubcategoryHeader("Filters — hide listings that don't match"))
            run {
                val floors = arrayOf("Any", "F1", "F2", "F3", "F4", "F5", "F6", "F7")
                f.sub.add(DropdownSetting("Filter: Floor", "", floors,
                    { floors[FishSettings.pfFilterFloor.coerceIn(0, 7)] },
                    { v -> FishSettings.pfFilterFloor = floors.indexOf(v).coerceAtLeast(0) }))
                val modes = arrayOf("Any", "Catacombs", "Master Mode")
                f.sub.add(DropdownSetting("Filter: Mode", "", modes,
                    { modes[FishSettings.pfFilterMode.coerceIn(0, 2)] },
                    { v -> FishSettings.pfFilterMode = modes.indexOf(v).coerceAtLeast(0) }))
                val classes = arrayOf("Any", "Archer", "Berserk", "Healer", "Mage", "Tank")
                f.sub.add(DropdownSetting("Filter: Needs Class", "Only parties still missing this class", classes,
                    { classes[FishSettings.pfFilterClass.coerceIn(0, 5)] },
                    { v -> FishSettings.pfFilterClass = classes.indexOf(v).coerceAtLeast(0) }))
            }
            f.sub.add(ToggleSetting("Filter: Hide Full", "Hide 5/5 parties", FishSettings::pfFilterHideFull))
            f.sub.add(SliderIntSetting("Filter: Max Level Req", "0 = off; hide parties requiring a higher dungeon level", FishSettings::pfFilterMaxLevel, 0, 60))
            party.features.add(f)
        }
        run {
            val f = Feature("Party Finder Auto Kick", FishSettings::pfAutoKick)
            f.sub.add(SubcategoryHeader("Only fires while you're party leader"))
            f.sub.add(ToggleSetting("Master Mode", "Check Master PBs instead of normal", FishSettings::pfAutoKickMaster))
            f.sub.add(SliderIntSetting("Floor", "Which floor's PB to check", FishSettings::pfAutoKickFloor, 1, 7))
            f.sub.add(SliderIntSetting("Max S+ Seconds", "Kick if their S+ PB is slower (or missing)", FishSettings::pfAutoKickMaxSeconds, 60, 480, 5))
            f.sub.add(SliderIntSetting("Min Secrets (k)", "0 = don't check secrets", FishSettings::pfAutoKickMinSecretsK, 0, 200))
            f.sub.add(ToggleSetting("Announce in Party", "Send a /pc line before kicking", FishSettings::pfAutoKickInform))
            f.sub.add(SubcategoryHeader("Per-class minimums — 0 = off. Class read from the join message."))
            f.sub.add(SubcategoryHeader("Archer"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick an Archer below this Catacombs level", FishSettings::pfAutoKickArcherCata, 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick an Archer below this SkyBlock level", FishSettings::pfAutoKickArcherSb, 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick an Archer below this Magical Power", FishSettings::pfAutoKickArcherMp, 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Berserk"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Berserk below this Catacombs level", FishSettings::pfAutoKickBerserkCata, 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Berserk below this SkyBlock level", FishSettings::pfAutoKickBerserkSb, 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Berserk below this Magical Power", FishSettings::pfAutoKickBerserkMp, 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Healer"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Healer below this Catacombs level", FishSettings::pfAutoKickHealerCata, 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Healer below this SkyBlock level", FishSettings::pfAutoKickHealerSb, 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Healer below this Magical Power", FishSettings::pfAutoKickHealerMp, 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Mage"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Mage below this Catacombs level", FishSettings::pfAutoKickMageCata, 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Mage below this SkyBlock level", FishSettings::pfAutoKickMageSb, 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Mage below this Magical Power", FishSettings::pfAutoKickMageMp, 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Tank"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Tank below this Catacombs level", FishSettings::pfAutoKickTankCata, 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Tank below this SkyBlock level", FishSettings::pfAutoKickTankSb, 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Tank below this Magical Power", FishSettings::pfAutoKickTankMp, 0, 1500, 25))
            party.features.add(f)
        }

        run {
            val f = Feature("Cooldown Overlay", FishSettings::cooldownOverlayEnabled)
            f.sub.add(ToggleSetting("Show Number", "", FishSettings::cooldownShowText))
            f.sub.add(ToggleSetting("Under 3s Only", "", FishSettings::cooldownOnlyUnder3s))
            f.sub.add(ToggleSetting("In Inventory", "", FishSettings::cooldownInInventory))
            hud.features.add(f)
        }
        hud.features.add(Feature("Catacombs Overflow Levels", FishSettings::catacombsOverflowEnabled))
        run {
            val f = Feature("Action Bar", FishSettings::actionBarEnabled)
            f.sub.add(SubcategoryHeader("Hide segments of Hypixel's SkyBlock action bar"))
            f.sub.add(ToggleSetting("Health", "", FishSettings::abHideHealth))
            f.sub.add(ToggleSetting("Defence", "", FishSettings::abHideDefense))
            f.sub.add(ToggleSetting("True Defence", "", FishSettings::abHideTrueDefense))
            f.sub.add(ToggleSetting("Mana", "", FishSettings::abHideMana))
            f.sub.add(ToggleSetting("Overflow Mana", "The ʬʬ counter", FishSettings::abHideOverflowMana))
            f.sub.add(ToggleSetting("Mana Use", "\"-40 Mana (Ability)\" flashes", FishSettings::abHideManaUse))
            f.sub.add(ToggleSetting("Skill XP", "\"+12.5 Combat (…)\" popups", FishSettings::abHideSkillXp))
            f.sub.add(ToggleSetting("Armor Stacks", "The \"31x … Arrow\" bow count", FishSettings::abHideArmorStacks))
            f.sub.add(ToggleSetting("Rag Axe Timer", "Ragnarock Axe countdown", FishSettings::abHideRagAxeTimer))
            f.sub.add(ToggleSetting("Term Laser", "", FishSettings::abHideTermLaser))
            f.sub.add(ToggleSetting("Bits Gained", "", FishSettings::abHideBits))
            f.sub.add(ToggleSetting("Secrets", "Dungeon secret count", FishSettings::abHideSecrets))
            f.sub.add(ToggleSetting("Vitality", "", FishSettings::abHideVitality))
            f.sub.add(SubcategoryHeader("Vanilla HUD (SkyBlock only)"))
            f.sub.add(ToggleSetting("XP Bar", "Vanilla experience bar + level number", FishSettings::abHideXpBar))
            f.sub.add(ToggleSetting("Armor Display", "The armor-icon row above health", FishSettings::abHideArmorRow))
            f.sub.add(ToggleSetting("Absorption Hearts", "The gold absorption hearts", FishSettings::abHideAbsorption))
            hud.features.add(f)
        }
        run {
            val f = Feature("Pet HUD", FishSettings::petHudEnabled)
            f.sub.add(ToggleSetting("Show Level", "", FishSettings::petHudShowLevel))
            f.sub.add(ToggleSetting("Show Rarity", "Colour the pet name by its rarity", FishSettings::petHudShowRarity))
            f.sub.add(ToggleSetting("Fade Idle", "", FishSettings::petHudFadeIdle))
            f.sub.add(SliderIntSetting("Fade ms", "", FishSettings::petHudFadeMs, 1000, 30000).gatedBy { FishSettings.petHudFadeIdle })
            hud.features.add(f)
        }
        run {
            val f = Feature("Soulflow HUD", FishSettings::soulflowHudEnabled)
            f.sub.add(InputIntSetting("Warning", "", FishSettings::soulflowWarningThreshold))
            f.sub.add(ToggleSetting("Missing Warn", "", FishSettings::soulflowMissingNotifier))
            hud.features.add(f)
        }
        hud.features.add(Feature("Fire Freeze Timer", FishSettings::fireFreezeTimerEnabled))
        hud.features.add(Feature("Loadout Title", FishSettings::loadoutTitleEnabled))
        run {
            val f = Feature("Time Changer", FishSettings::timeChangerEnabled)
            f.sub.add(DropdownSetting("Time", "", fishmod.features.TimeChanger.modes(),
                { FishSettings.timeChangerMode }, { v -> FishSettings.timeChangerMode = v }))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Arrow Hit Sound", FishSettings::arrowHitSoundEnabled)
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.arrowHitSoundName }, { v -> FishSettings.arrowHitSoundName = v },
                { FishSettings.arrowHitSoundVolume }, { FishSettings.arrowHitSoundPitch }))
            f.sub.add(SliderIntSetting("Volume %", "Above 100 = louder (stacked plays)", FishSettings::arrowHitSoundVolume, 0, 500, 10))
            f.sub.add(SliderDoubleSetting("Pitch", "", FishSettings::arrowHitSoundPitch, 0.0, 2.0))
            f.sub.add(ToggleSetting("Suppress Vanilla Sound", "", FishSettings::arrowHitSoundSuppress))
            general.features.add(f)
        }
        run {
            val f = Feature("Block Overlay", FishSettings::blockOverlayEnabled)
            f.sub.add(DropdownSetting("Mode", "", arrayOf("Outline", "Fill", "Filled Outline"),
                { arrayOf("Outline", "Fill", "Filled Outline")[FishSettings.blockOverlayMode] },
                { v -> FishSettings.blockOverlayMode = arrayOf("Outline", "Fill", "Filled Outline").indexOf(v).coerceAtLeast(0) }))
            f.sub.add(ColorPickerSetting("Fill Color", "", FishSettings::blockOverlayFillColor))
            f.sub.add(SliderIntSetting("Fill Opacity %", "", FishSettings::blockOverlayOpacity, 0, 100))
            f.sub.add(ColorPickerSetting("Outline Color", "", FishSettings::blockOverlayOutlineColor))
            f.sub.add(ToggleSetting("Phase (through walls)", "", FishSettings::blockOverlayPhase))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Item Rarity Background", Visual::itemRarityBackground)
            f.sub.add(SliderIntSetting("Opacity %", "", Visual::itemRarityOpacity, 0, 100))
            f.sub.add(ToggleSetting("Hypixel Colors", "Brighter, accurate per-rarity colours", Visual::itemRarityHypixelColors))
            f.sub.add(ToggleSetting("Circular", "", Visual::circularRarityBackground))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Item Tooltip", FishSettings::itemTooltipPrices)
            f.sub.add(SubcategoryHeader("Extra lines on SkyBlock item tooltips"))
            f.sub.add(ToggleSetting("Prices", "Value = base + modifiers (enchants, HPB, recomb, gems, reforge…)", FishSettings::itemTooltipPrices))
            f.sub.add(ToggleSetting("NPC Sell Price", "", FishSettings::itemTooltipNpcSell))
            f.sub.add(ToggleSetting("Dungeon Quality", "Stat-boost % + floor", FishSettings::itemQualityTooltip))
            f.sub.add(SubcategoryHeader("Scrollable Tooltips — scroll: move · shift: sideways · ctrl: scale"))
            f.sub.add(ToggleSetting("Scrollable Tooltips", "", FishSettings::tooltipScrollEnabled))
            f.sub.add(SliderIntSetting("Tooltip Scale %", "", FishSettings::tooltipScrollScale, 30, 150, 5).gatedBy { FishSettings.tooltipScrollEnabled })
            f.sub.add(SliderIntSetting("Scroll Speed", "", FishSettings::tooltipScrollSpeed, 1, 10).gatedBy { FishSettings.tooltipScrollEnabled })
            hud.features.add(f)
        }
        run {
            val f = Feature("Auto BIN Price", FishSettings::auctionPriceAutofillEnabled)
            f.sub.add(SubcategoryHeader("Prefills the AH \"Create Auction\" price field with item value minus a discount"))
            f.sub.add(SliderIntSetting("Discount %", "", FishSettings::auctionAutofillPercent, 0, 50))
            hud.features.add(f)
        }
        run {
            val f = Feature("Render Optimizer", Visual::renderOptimizer)
            f.sub.add(ToggleSetting("Hide Nearby Players", "Hide other players within range", Visual::hidePlayersInRange))
            f.sub.add(SliderDoubleSetting("Player Range", "Blocks", Visual::hidePlayerRange, 1.0, 12.0).gatedBy { Visual.hidePlayersInRange })
            f.sub.add(ToggleSetting("Hide Dead Entities", "Drop dying / 0-HP mobs from the render pass", Visual::hideDeadEntities))
            f.sub.add(ToggleSetting("No Swing Animation", "Suppress the first-person hand swing", Visual::noSwingAnimation))
            f.sub.add(ToggleSetting("Swing: Terminator Only", "Only suppress while holding a Terminator", Visual::noSwingTerminatorOnly).gatedBy { Visual.noSwingAnimation })
            f.sub.add(ToggleSetting("Stop Shovel Flattening", "Cancel the shovel make-path interaction", Visual::stopShovelFlattening))
            f.sub.add(SubcategoryHeader("Clutter hiders"))
            f.sub.add(ToggleSetting("Hide Falling Blocks", "", Visual::roHideFallingBlocks))
            f.sub.add(ToggleSetting("Hide Lightning", "", Visual::roHideLightning))
            f.sub.add(ToggleSetting("Hide Experience Orbs", "", Visual::roHideExperienceOrbs))
            f.sub.add(ToggleSetting("Hide Death Animation", "Hide mobs that are dying", Visual::roHideDeathAnimation))
            f.sub.add(ToggleSetting("Hide Armor Stands", "Nametag stands on dying mobs (needs Hide Death Animation)", Visual::roHideDyingArmorStands).gatedBy { Visual.roHideDeathAnimation })
            f.sub.add(ToggleSetting("Hide Explosion Particles", "", Visual::roHideExplosionParticles))
            f.sub.add(ToggleSetting("Hide Archer Passive", "The archer passive's floating bone meal", Visual::roHideArcherPassive))
            f.sub.add(ToggleSetting("Hide Healer Fairy", "The healer fairy held by some mobs", Visual::roHideHealerFairy))
            f.sub.add(ToggleSetting("Hide Soul Weaver", "The soul weaver helmet worn by some mobs", Visual::roHideSoulWeaver))
            f.sub.add(ToggleSetting("Hide Tentacle Head", "The tentacle head worn by some mobs", Visual::roHideTentacleHead))
            f.sub.add(ToggleSetting("Hide Fire Overlay", "The first-person fire overlay", Visual::roHideFireOverlay))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Gyro Helper", FishSettings::gyroHelperEnabled)
            f.sub.add(ColorPickerSetting("Box Color", "", FishSettings::gyroBoxColor))
            f.sub.add(ColorPickerSetting("Ring Color", "", FishSettings::gyroRingColor))
            general.features.add(f)
        }
        run {
            val f = Feature("Mage Beam", FishSettings::mageBeamEnabled)
            f.sub.add(ColorPickerSetting("Color", "", FishSettings::mageBeamColor))
            f.sub.add(SliderIntSetting("Duration (ticks)", "", FishSettings::mageBeamDurationTicks, 1, 100))
            f.sub.add(ToggleSetting("Hide Particles", "", FishSettings::mageBeamHideParticles))
            f.sub.add(ToggleSetting("Depth Check", "", FishSettings::mageBeamDepth))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Spring Boots", FishSettings::springBootsEnabled)
            f.sub.add(ToggleSetting("Show Blocks", "Blocks instead of charge %", FishSettings::springBootsShowBlocks))
            f.sub.add(ToggleSetting("Landing Box", "", FishSettings::springBootsBox))
            f.sub.add(ColorPickerSetting("Box Color", "", FishSettings::springBootsBoxColor).gatedBy { FishSettings.springBootsBox })
            hud.features.add(f)
        }
        run {
            val f = Feature("Tac Timer", FishSettings::tacTimerEnabled)
            f.sub.add(ToggleSetting("Reverse (count up)", "", FishSettings::tacTimerReverse))
            f.sub.add(ToggleSetting("\"Tac:\" Prefix", "", FishSettings::tacTimerPrefix))
            f.sub.add(ToggleSetting("\"s\" Suffix", "", FishSettings::tacTimerSuffix))
            f.sub.add(ToggleSetting("Start Waypoint", "", FishSettings::tacTimerWaypoint))
            f.sub.add(ColorPickerSetting("Waypoint Color", "", FishSettings::tacTimerColor).gatedBy { FishSettings.tacTimerWaypoint })
            hud.features.add(f)
        }
        run {
            val f = Feature("Visual Effects", FishSettings::cameraTweaksEnabled)
            f.sub.add(ToggleSetting("Full Bright", "", FishSettings::cameraFullBright))
            f.sub.add(ToggleSetting("Disable Blindness", "", FishSettings::cameraNoBlindness))
            f.sub.add(ToggleSetting("Disable Nausea", "", FishSettings::cameraNoNausea))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Explosive Shot", FishSettings::explosiveShotEnabled)
            f.sub.add(ToggleSetting("Announce to Party (Archer)", "", FishSettings::explosiveShotAnnounceParty))
            dungeon.features.add(f)
        }

        // Floor 7
        run {
            val f = Feature("Tick Timers", Floor7::enableTickTimers)
            f.sub.add(SubcategoryHeader("Maxor"))
            f.sub.add(ToggleSetting("Maxor", "", Floor7::enableMaxorTickTimer))
            f.sub.add(SubcategoryHeader("Storm"))
            f.sub.add(ToggleSetting("Storm", "", Floor7::enableStormTickTimer))
            f.sub.add(ToggleSetting("Tick Down From 5", "", Floor7::tickDownStormTickTimer).gatedBy { Floor7.enableStormTickTimer })
            f.sub.add(ColorPickerSetting("Storm Timer Color", "", Floor7::stormTickTimerColor).gatedBy { Floor7.enableStormTickTimer })
            f.sub.add(ToggleSetting("Storm Death Time", "", Floor7::enableStormDeathTime))
            f.sub.add(ToggleSetting("LB Release Timer", "", Floor7::enableLbReleaseTimer))
            f.sub.add(ColorPickerSetting("LB Release Timer Color", "", Floor7::lbReleaseTimerColor).gatedBy { Floor7.enableLbReleaseTimer })
            f.sub.add(SliderIntSetting("LB Release Ping (ms)", "Fires the release cue this much earlier to offset latency", Floor7::lbReleaseTimerPingMs, 0, 500).gatedBy { Floor7.enableLbReleaseTimer })
            f.sub.add(ToggleSetting("Storm Crushed Noti", "", Floor7::notifyStormCrush))
            f.sub.add(SubcategoryHeader("Goldor"))
            f.sub.add(ToggleSetting("Goldor", "", Floor7::enableGoldorTickTimer))
            f.sub.add(ToggleSetting("In 3s Increments", "", Floor7::inDeathTicks).gatedBy { Floor7.enableGoldorTickTimer })
            f.sub.add(ToggleSetting("Tick Up", "", Floor7::makeGoldorTickUp).gatedBy { Floor7.enableGoldorTickTimer })
            f.sub.add(ToggleSetting("Term Start Timer", "", Floor7::enableTermStartTimer))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Crystal Spawn", Floor7::enableCrystalSpawnTime)
            f.sub.add(ToggleSetting("Place Reminder", "", Floor7::crystalPlaceReminder))
            f.sub.add(ToggleSetting("Instant Reminder", "", Floor7::instantlyDisplayCrystalReminder).gatedBy { Floor7.crystalPlaceReminder })
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
            val f = Feature("Blood Solver", Floor7::bloodSolverEnabled)
            f.sub.add(ToggleSetting("Kill Mobs Title", "Title when the blood mobs are ready to kill — learn your own move times for accuracy", Floor7::bloodSolverKillTitle))
            f.sub.add(ToggleSetting("Watcher Speed Alert", "Title + sound for fast / normal / slow Watcher", Floor7::bloodSolverSpeedAlert))
            f.sub.add(ToggleSetting("Send Speed In Party", "Also /pc the Watcher speed", Floor7::bloodSolverSpeedAlertParty).gatedBy { Floor7.bloodSolverSpeedAlert })
            f.sub.add(SliderIntSetting("Timer Decimals", "", Floor7::bloodSolverDecimals, 0, 2))
            f.sub.add(ColorPickerSetting("Box Color", "", Floor7::bloodSolverBoxColor))
            f.sub.add(ColorPickerSetting("Line Color", "", Floor7::bloodSolverLineColor))
            f.sub.add(ColorPickerSetting("Timer Color", "", Floor7::bloodSolverTimerColor))
            floor7.features.add(f)
        }
        run {
            val f = Feature("S4 Term/Leap Tracker", Floor7::s4TrackerEnabled)
            f.sub.add(ToggleSetting("Debug HUD", "", Floor7::s4DebugHudEnabled))
            f.sub.add(ToggleSetting("Alerts", "", Floor7::s4AlertsEnabled))
            f.sub.add(ToggleSetting("Alert Sound", "", Floor7::s4AlertSoundEnabled).gatedBy { Floor7.s4AlertsEnabled })
            f.sub.add(ToggleSetting("Early Leap Alert", "", Floor7::s4EarlyLeapAlert).gatedBy { Floor7.s4AlertsEnabled })
            f.sub.add(ToggleSetting("Late Leap Alert", "", Floor7::s4LateLeapAlert).gatedBy { Floor7.s4AlertsEnabled })
            f.sub.add(ToggleSetting("Missed Term Alert", "", Floor7::s4MissedTermAlert).gatedBy { Floor7.s4AlertsEnabled })
            f.sub.add(ToggleSetting("Death Alert", "", Floor7::s4DeathAlert).gatedBy { Floor7.s4AlertsEnabled })
            f.sub.add(SliderIntSetting("Late Leap Threshold (ticks)", "",
                { Floor7.s4LateLeapThresholdTicks }, { v -> Floor7.s4LateLeapThresholdTicks = v }, 20, 400).gatedBy { Floor7.s4AlertsEnabled && Floor7.s4LateLeapAlert })
            f.sub.add(SliderIntSetting("Alert Duration (ticks)", "",
                { Floor7.s4AlertDurationTicks }, { v -> Floor7.s4AlertDurationTicks = v }, 20, 200).gatedBy { Floor7.s4AlertsEnabled })
            f.sub.add(SliderIntSetting("Alert Cooldown (ticks)", "",
                { Floor7.s4AlertCooldownTicks }, { v -> Floor7.s4AlertCooldownTicks = v }, 10, 200).gatedBy { Floor7.s4AlertsEnabled })
            floor7.features.add(f)
        }
        run {
            val f = Feature("Terminal Solver", FishSettings::terminalSolverEnabled)
            f.sub.add(DropdownSetting("Render Mode", "Custom GUI replaces the chest with a big rounded board",
                arrayOf("Overlay", "Custom GUI"),
                { arrayOf("Overlay", "Custom GUI")[FishSettings.terminalRenderMode] },
                { v -> FishSettings.terminalRenderMode = arrayOf("Overlay", "Custom GUI").indexOf(v).coerceAtLeast(0) }))
            f.sub.add(SliderDoubleSetting("Custom Scale", "", FishSettings::terminalCustomScale, 0.5, 3.0).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(SliderIntSetting("Custom Roundness", "", FishSettings::terminalCustomRoundness, 0, 15).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(SliderIntSetting("Custom Gap", "", FishSettings::terminalCustomGap, 0, 15).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(ColorPickerSetting("Custom Background", "", FishSettings::terminalCustomBg).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(ToggleSetting("Block Wrong Clicks", "", FishSettings::terminalBlockWrongClicks))
            f.sub.add(ToggleSetting("Middle Click GUI", "Send terminal clicks as a middle-click so items never touch the cursor (right stays right)", FishSettings::terminalMiddleClickGui))
            f.sub.add(ToggleSetting("Stop Tooltips", "Hide hover tooltips in terminals", FishSettings::terminalStopTooltips))
            f.sub.add(ToggleSetting("Hide Wrong Items", "Cover non-solution slots", FishSettings::terminalHideWrong))
            f.sub.add(ToggleSetting("Show Numbers", "Order # on Numbers/Rubix slots", FishSettings::terminalShowNumbers))
            f.sub.add(ToggleSetting("Stop Melody Solver", "", FishSettings::terminalStopMelody))
            f.sub.add(ToggleSetting("Solve Sound", "Ping when you finish a terminal", FishSettings::terminalSolverSound))
            f.sub.add(SliderIntSetting("First Click Protection (ms)","Block clicks for this long after a terminal opens (Odin: ~500 minus your ping; 0 = off)", FishSettings::terminalFirstClickProtMs, 0, 800, 10))
            f.sub.add(SubcategoryHeader("Colors"))
            f.sub.add(ColorPickerSetting("Panes", "", FishSettings::terminalHighlightColor))
            f.sub.add(ColorPickerSetting("Starts With", "", FishSettings::terminalStartsWithColor))
            f.sub.add(ColorPickerSetting("Select", "", FishSettings::terminalSelectColor))
            f.sub.add(ColorPickerSetting("Order 1st", "", FishSettings::terminalOrderColor1))
            f.sub.add(ColorPickerSetting("Order 2nd", "", FishSettings::terminalOrderColor2))
            f.sub.add(ColorPickerSetting("Order 3rd", "", FishSettings::terminalOrderColor3))
            f.sub.add(ColorPickerSetting("Rubix +1", "", FishSettings::terminalRubixColor))
            f.sub.add(ColorPickerSetting("Rubix +2", "", FishSettings::terminalRubixColor2))
            f.sub.add(ColorPickerSetting("Rubix -1", "", FishSettings::terminalRubixNeg1))
            f.sub.add(ColorPickerSetting("Rubix -2", "", FishSettings::terminalRubixNeg2))
            f.sub.add(ColorPickerSetting("Melody", "", FishSettings::terminalMelodyPointerColor))
            f.sub.add(ColorPickerSetting("Wrong-Item Cover", "", FishSettings::terminalWrongCover))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Arrow Align", FishSettings::arrowAlignEnabled)
            f.sub.add(ToggleSetting("Block Wrong Clicks", "Cancels rotating a frame that isn't in the solution (hold sneak to override)", FishSettings::arrowAlignBlockWrong))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Simon Says Solver", FishSettings::simonSolverEnabled)
            f.sub.add(ToggleSetting("Through Walls", "", FishSettings::simonSolverDepth))
            f.sub.add(ToggleSetting("Block Wrong Clicks", "Cancels clicks on the wrong button (hold sneak to override)", FishSettings::simonSolverBlockWrong))
            f.sub.add(ColorPickerSetting("Next", "", FishSettings::simonSolverColor1))
            f.sub.add(ColorPickerSetting("Second", "", FishSettings::simonSolverColor2))
            f.sub.add(ColorPickerSetting("Rest", "", FishSettings::simonSolverColor3))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Melody Message", FishSettings::melodyMessageEnabled)
            f.sub.add(ToggleSetting("Announce on Open", "Party message when the melody terminal opens", FishSettings::melodyMessageOnOpen))
            f.sub.add(InputSetting("Open Message", "",
                { FishSettings.melodyMessageText }, { v -> FishSettings.melodyMessageText = v ?: "" })
                .gatedBy { FishSettings.melodyMessageOnOpen })
            f.sub.add(ToggleSetting("Progress Calls", "Party-message 25/50/75% as the marker drops", FishSettings::melodyMessageProgress))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Arrows Device", FishSettings::arrowsDeviceEnabled)
            f.sub.add(ToggleSetting("Through Walls", "", FishSettings::arrowsDeviceDepth))
            f.sub.add(ToggleSetting("Complete Alert", "Title when the device finishes", FishSettings::arrowsDeviceCompleteAlert))
            f.sub.add(ColorPickerSetting("Target", "Emerald block to shoot", FishSettings::arrowsDeviceTargetColor))
            f.sub.add(ColorPickerSetting("Hit", "Already-shot block", FishSettings::arrowsDeviceMarkedColor))
            f.sub.add(ToggleSetting("Show Aim Positions", "Optimal aim points for hitting adjacent blocks together", FishSettings::arrowsDeviceShowAim))
            f.sub.add(ColorPickerSetting("Aim 1st", "", FishSettings::arrowsDeviceAim1Color).gatedBy { FishSettings.arrowsDeviceShowAim })
            f.sub.add(ColorPickerSetting("Aim 2nd", "", FishSettings::arrowsDeviceAim2Color).gatedBy { FishSettings.arrowsDeviceShowAim })
            f.sub.add(ColorPickerSetting("Aim 3rd", "", FishSettings::arrowsDeviceAim3Color).gatedBy { FishSettings.arrowsDeviceShowAim })
            solvers.features.add(f)
        }
        run {
            val f = Feature("Wither ESP", FishSettings::witherEspEnabled)
            f.sub.add(ColorPickerSetting("Maxor", "", FishSettings::witherEspMaxorColor))
            f.sub.add(ColorPickerSetting("Storm", "", FishSettings::witherEspStormColor))
            f.sub.add(ColorPickerSetting("Goldor", "", FishSettings::witherEspGoldorColor))
            f.sub.add(ColorPickerSetting("Necron", "", FishSettings::witherEspNecronColor))
            floor7.features.add(f)
        }
        run {
            val f = Feature("M7 Relics", Floor7::enableRelicStartTimer)
            f.sub.add(SliderIntSetting("Spawn Ticks", "Ticks after Necron's P5 line", Floor7::relicSpawnTicks, 1, 200))
            f.sub.add(ToggleSetting("Cauldron Box", "Box + tracer the cauldron for the relic you hold", Floor7::renderRelicHighlight))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Wither Dragons", FishSettings::witherDragonsEnabled)
            f.sub.add(ToggleSetting("Spawn Timer (World)", "In-world countdown on each dragon's hitbox", FishSettings::witherDragonsTimerWorld))
            f.sub.add(ToggleSetting("Spawn Timer (HUD)", "On-screen countdown for the priority dragon — movable in the HUD editor", FishSettings::witherDragonsTimerHud))
            f.sub.add(DropdownSetting("Timer Style", "", arrayOf("Milliseconds", "Seconds", "Ticks"),
                { arrayOf("Milliseconds", "Seconds", "Ticks")[FishSettings.witherDragonsTimerStyle] },
                { v -> FishSettings.witherDragonsTimerStyle = arrayOf("Milliseconds", "Seconds", "Ticks").indexOf(v).coerceAtLeast(0) })
                .gatedBy { FishSettings.witherDragonsTimerWorld || FishSettings.witherDragonsTimerHud })
            f.sub.add(ToggleSetting("Spawn Alert (Title)", "Title with the priority dragon's colour when a wave starts spawning (NoammAddons)", FishSettings::witherDragonsSpawnAlert))
            f.sub.add(ToggleSetting("Spawn Alert Sound", "", FishSettings::witherDragonsSpawnSound).gatedBy { FishSettings.witherDragonsSpawnAlert })
            f.sub.add(ToggleSetting("Spawn Alert → Party", "Call the priority dragon in party chat", FishSettings::witherDragonsSpawnParty).gatedBy { FishSettings.witherDragonsSpawnAlert })
            f.sub.add(ToggleSetting("Dragon Health", "", FishSettings::witherDragonsHealth))
            f.sub.add(ToggleSetting("Skip Box", "", FishSettings::witherDragonsSkipBox))
            f.sub.add(ToggleSetting("Fill Skip Box", "", FishSettings::witherDragonsBoxFill).gatedBy { FishSettings.witherDragonsSkipBox })
            f.sub.add(ToggleSetting("Priority Tracer", "Line to the highest-priority spawning dragon", FishSettings::witherDragonsTracer))
            f.sub.add(ToggleSetting("Aim Assist", "Box at the arrow-lead point for the ice spray", FishSettings::witherDragonsAimAssist))
            f.sub.add(ColorPickerSetting("Tracer / Aim Color", "", FishSettings::witherDragonsAimColor)
                .gatedBy { FishSettings.witherDragonsTracer || FishSettings.witherDragonsAimAssist })
            f.sub.add(ToggleSetting("Send Kill Stats", "Time / arrows / spray to chat on each dragon death", FishSettings::witherDragonsSendStats))
            f.sub.add(SubcategoryHeader("Priority"))
            f.sub.add(ToggleSetting("Custom Priority", "Factor in blessing power + your class", FishSettings::witherDragonsPriority))
            f.sub.add(SliderDoubleSetting("Normal Power", "", FishSettings::witherDragonsNormalPower, 0.0, 32.0).gatedBy { FishSettings.witherDragonsPriority })
            f.sub.add(SliderDoubleSetting("Easy Power", "", FishSettings::witherDragonsEasyPower, 0.0, 32.0).gatedBy { FishSettings.witherDragonsPriority })
            f.sub.add(DropdownSetting("Purple Solo Debuff", "", arrayOf("Tank", "Healer"),
                { arrayOf("Tank", "Healer")[FishSettings.witherDragonsSoloDebuff] },
                { v -> FishSettings.witherDragonsSoloDebuff = arrayOf("Tank", "Healer").indexOf(v).coerceAtLeast(0) })
                .gatedBy { FishSettings.witherDragonsPriority })
            f.sub.add(ToggleSetting("Solo Debuff on All Splits", "", FishSettings::witherDragonsSoloDebuffAll).gatedBy { FishSettings.witherDragonsPriority })
            floor7.features.add(f)
        }

        for (et in FishModAddonApi.dungeonToggles) {
            dungeon.features.add(Feature(et.name(), { et.get().get() }, { v -> et.set().accept(v) }))
        }

        // Dungeon Map
        run {
            // Legit Mode / Insight Legit toggles live in FishModAddons only; this core mod forces legit mode on every join (see FishModInit)
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
            f.sub.add(ToggleSetting("Secrets Tail = Left (not total)", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowLeft)
                .gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapInfoShowSecrets })
            f.sub.add(ToggleSetting("Show Score", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowScore))
            f.sub.add(ToggleSetting("Show Deaths", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowDeaths))
            f.sub.add(ToggleSetting("Show Mimic", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowMimic))
            f.sub.add(ToggleSetting("Show Prince", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowPrince))
            f.sub.add(ToggleSetting("Show Crypts", "", fishmod.utils.config.values.DungeonMapSettings::mapInfoShowCrypts))
            f.sub.add(ToggleSetting("Hide When Done", "Drop Mimic/Prince/Crypts once complete", fishmod.utils.config.values.DungeonMapSettings::mapInfoHideCompleted))
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
            f.sub.add(ToggleSetting("Mimic on Insight", "", fishmod.utils.config.values.DungeonMapSettings::mapMimicOnInsight)
                .gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapRoomAdditionsMimic })
            f.sub.add(ColorPickerSetting("Mimic Room Color", "", fishmod.utils.config.values.DungeonMapSettings::mapMimicRoomColor)
                .gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapRoomAdditionsMimic })
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
            f.sub.add(t270t.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore270Title })
            f.sub.add(ToggleSetting("Party Chat", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270MessageEnabled))
            val t270c = InputSetting("Party Chat Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270Message)
            t270c.hint = "<time> ok"
            f.sub.add(t270c.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore270MessageEnabled })
            f.sub.add(ToggleSetting("Client-only Msg", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270ClientEnabled))
            val t270cl = InputSetting("Client-only Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore270ClientMessage)
            t270cl.hint = "& color codes, <time> ok"
            f.sub.add(t270cl.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore270ClientEnabled })
            f.sub.add(SubcategoryHeader("300 Score"))
            f.sub.add(ToggleSetting("Title", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300Title))
            val t300t = InputSetting("Title Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300TitleText)
            t300t.hint = "& color codes, <time> ok"
            f.sub.add(t300t.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore300Title })
            f.sub.add(ToggleSetting("Party Chat", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300MessageEnabled))
            val t300c = InputSetting("Party Chat Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300Message)
            t300c.hint = "<time> ok"
            f.sub.add(t300c.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore300MessageEnabled })
            f.sub.add(ToggleSetting("Client-only Msg", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300ClientEnabled))
            val t300cl = InputSetting("Client-only Text", "", fishmod.utils.config.values.DungeonMapSettings::mapScore300ClientMessage)
            t300cl.hint = "& color codes, <time> ok"
            f.sub.add(t300cl.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore300ClientEnabled })
            f.sub.add(SubcategoryHeader("Title Display"))
            f.sub.add(SliderDoubleSetting("Title Scale", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale = v.toFloat() },
                0.5, 4.0))
            f.sub.add(ToggleSetting("Title Sound", "", fishmod.utils.config.values.DungeonMapSettings::mapScoreTitleSound))
            f.sub.add(DropdownSetting("Sound", "", fishmod.features.dungeon.map.ScoreMessages.SOUND_OPTIONS,
                { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSoundId },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSoundId = v })
                .gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSound })
            dungeonMap.features.add(f)
        }

        val slayer = Column("Slayer", "slider")
        run {
            // Each Slayer feature is its own toggle in the column — no shared master.
            val spawnAlert = Feature("Mini/Boss Spawn Alert", FishSettings::slayerSpawnAlertEnabled)
            spawnAlert.sub.add(ToggleSetting("Mini-Boss Alerts", "Alert when a slayer miniboss spawns", FishSettings::slayerMiniBossAlert))
            spawnAlert.sub.add(ToggleSetting("Boss Alerts", "Alert when the main slayer boss spawns", FishSettings::slayerBossAlert))
            spawnAlert.sub.add(SliderIntSetting("Alert Duration (ms)", "On-screen time for spawn alerts", FishSettings::slayerAlertDurationMs, 250, 8000, 250))
            slayer.features.add(spawnAlert)

            val cocoon = Feature("Cocoon Alert", FishSettings::slayerCocoonAlertEnabled)
            cocoon.sub.add(SubcategoryHeader("Fires on \"YOU COCOONED YOUR SLAYER BOSS\""))
            cocoon.sub.add(SliderIntSetting("Alert Duration (ms)", "", FishSettings::slayerCocoonAlertDurationMs, 250, 8000, 250))
            slayer.features.add(cocoon)

            val spawnHud = Feature("Spawn Progress HUD", FishSettings::slayerSpawnHudEnabled)
            spawnHud.sub.add(SubcategoryHeader("Live spawn-bar %  ·  drag position with Edit HUD"))
            spawnHud.sub.add(SliderDoubleSetting("Scale", "", FishSettings::slayerSpawnHudScale, 0.5, 3.0))
            slayer.features.add(spawnHud)

            val statsHud = Feature("Slayer Stats HUD", FishSettings::slayerStatsHudEnabled)
            statsHud.sub.add(SubcategoryHeader("Session XP / kills / rates  ·  drag position with Edit HUD"))
            statsHud.sub.add(ToggleSetting("Show XP", "", FishSettings::slayerStatsShowXp))
            statsHud.sub.add(ToggleSetting("Show Kills", "", FishSettings::slayerStatsShowKills))
            statsHud.sub.add(ToggleSetting("Show XP/hr", "", FishSettings::slayerStatsShowXpHr))
            statsHud.sub.add(ToggleSetting("Show Kills/hr", "", FishSettings::slayerStatsShowKillsHr))
            statsHud.sub.add(ToggleSetting("Background", "Dark panel behind the stats", FishSettings::slayerStatsBackground))
            statsHud.sub.add(SliderDoubleSetting("Scale", "", FishSettings::slayerStatsHudScale, 0.5, 3.0))
            statsHud.sub.add(ButtonSetting("Reset Session Stats", "Zero the XP / kills / time counters", Runnable { fishmod.features.slayers.SlayerStatsTracker.reset() }))
            slayer.features.add(statsHud)

            val profit = Feature("Profit Tracker", FishSettings::slayerProfitEnabled)
            profit.sub.add(SubcategoryHeader("SkyHanni-style: prices real drops for coins/hr  ·  drag with Edit HUD"))
            profit.sub.add(SubcategoryHeader("With chat open: click the mode line to switch  ·  left-click a row to hide it  ·  right-click the title to reset"))
            profit.sub.add(DropdownSetting("Display", "Total = all-time (saved); This Session = since this launch", arrayOf("Total", "This Session"),
                { FishSettings.slayerProfitDisplayMode },
                { v -> FishSettings.slayerProfitDisplayMode = v }))
            profit.sub.add(SliderIntSetting("Drop Rows", "Max item rows shown (highest value first); the rest fold into one row", FishSettings::slayerProfitLines, 3, 30, 1))
            profit.sub.add(SliderIntSetting("Hide Below (coins)", "Rows worth less than this fold into the \"N more items\" row (0 = show all)", FishSettings::slayerProfitMinValue, 0, 1_000_000, 10_000))
            profit.sub.add(ToggleSetting("Count Mob Kill Coins", "Count small purse gains while grinding as a \"Mob Kill Coins\" drop row + profit", FishSettings::slayerProfitCountKillCoins))
            profit.sub.add(ToggleSetting("Always Show Hidden Rows", "Keep hidden rows on screen (dark + struck) even when chat is closed", FishSettings::slayerProfitShowHidden))
            profit.sub.add(SliderIntSetting("Idle Pause (s)", "No drop/kill this long → pause & rewind the clock by this much", FishSettings::slayerProfitIdleSeconds, 15, 600, 15))
            profit.sub.add(ToggleSetting("Background", "Dark panel behind the tracker", FishSettings::slayerProfitBackground))
            profit.sub.add(SliderDoubleSetting("Scale", "", FishSettings::slayerProfitHudScale, 0.5, 3.0))
            profit.sub.add(ButtonSetting("Reset This Mode", "Clear drops / bosses / time for the current Display mode, every slayer", Runnable { fishmod.features.slayers.SlayerProfitTracker.reset() }))
            slayer.features.add(profit)

            val timer = Feature("Boss Timer", FishSettings::slayerTimerEnabled)
            timer.sub.add(SubcategoryHeader("Spawn-to-kill time + PB  ·  drag position with Edit HUD"))
            timer.sub.add(DropdownSetting("Start Mode", "When the clock starts", arrayOf("Spawned", "Fully Spawned"),
                { FishSettings.slayerTimerStartMode },
                { v -> FishSettings.slayerTimerStartMode = v }))
            timer.sub.add(ToggleSetting("Show Current Timer", "", FishSettings::slayerTimerShowCurrent))
            timer.sub.add(ToggleSetting("Show PB", "", FishSettings::slayerTimerShowPb))
            timer.sub.add(ToggleSetting("Show New PB", "", FishSettings::slayerTimerShowNewPb))
            timer.sub.add(ToggleSetting("Show Cycle", "Full kill-to-kill time (fight + loot + walk + refill) + a live 'since kill' counter", FishSettings::slayerTimerShowCycle))
            timer.sub.add(SliderDoubleSetting("Scale", "", FishSettings::slayerTimerHudScale, 0.5, 3.0))
            slayer.features.add(timer)

            val phases = Feature("Boss Phases", FishSettings::slayerPhaseEnabled)
            phases.sub.add(SubcategoryHeader("SkyHanni-style attack/phase cues on the boss — all 6 slayers"))
            phases.sub.add(SubcategoryHeader("Voidgloom laser/hits/beacon · Inferno shield+dagger/fire pillar/pits · Bloodfiend twinclaws/steak/mania · Rev BOOM · Sven PUPS · Tara hatchlings"))
            phases.sub.add(ToggleSetting("World Text", "Draw the cue as text above the boss", FishSettings::slayerPhaseWorldText))
            phases.sub.add(ToggleSetting("Title Warnings", "Big title for BOOM / PUPS / HATCHLINGS / FIRE PITS / TWINCLAWS / STEAK / BEACON", FishSettings::slayerPhaseTitles))
            phases.sub.add(ToggleSetting("Health Phase Split", "Show the 1/3 · 2/3 phase fraction (Voidgloom / Inferno)", FishSettings::slayerPhaseHealthSplit))
            slayer.features.add(phases)
        }

        columns.add(general)
        columns.add(invStorage)
        columns.add(party)
        columns.add(dungeon)
        columns.add(dungeonTrackers)
        columns.add(dungeonMap)
        columns.add(solvers)
        columns.add(floor7)
        columns.add(hud)
        columns.add(slayer)
        columns.add(visuals)
        columns.add(cosmetics)
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
        // validate every name resolves and appears exactly once before mutating, so a stale save can't half-group columns
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

    // --- Cascading curtain animation -----------------------------------------------------------

    // "Menu Animations" off → zero out all cascade timing so the screen just appears / closes.
    private fun fmDropMs(): Int = if (FishSettings.fmAnimations) FishSettings.fmDropDurationMs else 0
    private fun fmStagMs(): Int = if (FishSettings.fmAnimations) FishSettings.fmStaggerDelayMs else 0

    /** Total wall-clock time the exit animation needs, given the current column count/settings. */
    private fun exitTotalDurationMs(): Long {
        val n = visibleColumns().size
        return fmDropMs().toLong() + fmStagMs().toLong() * Math.max(0, n - 1)
    }

    /** Starts the exit (closing) animation instead of closing immediately. Safe to call more than
     *  once — a second ESC/click during the animation is a no-op. */
    private fun requestClose() {
        if (closing) return
        closing = true
        closeStartTime = System.currentTimeMillis()
    }

    /** 0f (not started) .. ~1f at rest, with a brief >1 overshoot for the elastic landing feel. */
    private fun openEase(index: Int): Float {
        if (!FishSettings.fmAnimations) return 1f
        val delay = index.toLong() * fmStagMs()
        val elapsed = System.currentTimeMillis() - screenOpenTime - delay
        if (elapsed <= 0L) return 0f
        val dur = fmDropMs().toFloat()
        val t = if (dur <= 0f) 1f else Mth.clamp(elapsed / dur, 0f, 1f)
        return Easing.easeOutBack(t)
    }

    /** 0f (not started) .. 1f (fully off-screen), eased. Column order is reversed vs. open: the
     *  last (rightmost) visible column leads the exit wave. */
    private fun closeEase(index: Int): Float {
        if (!FishSettings.fmAnimations) return 1f
        val n = visibleColumns().size
        val order = n - 1 - index
        val delay = order.toLong() * fmStagMs()
        val elapsed = System.currentTimeMillis() - closeStartTime - delay
        if (elapsed <= 0L) return 0f
        val dur = fmDropMs().toFloat()
        val t = if (dur <= 0f) 1f else Mth.clamp(elapsed / dur, 0f, 1f)
        return Easing.easeInOutCubic(t)
    }

    private fun isColumnOpening(index: Int): Boolean {
        if (!FishSettings.fmAnimations) return false
        val delay = index.toLong() * fmStagMs()
        val elapsed = System.currentTimeMillis() - screenOpenTime - delay
        return elapsed < fmDropMs()
    }

    private fun isColumnClosing(index: Int): Boolean {
        if (!closing || !FishSettings.fmAnimations) return false
        val n = visibleColumns().size
        val order = n - 1 - index
        val delay = order.toLong() * fmStagMs()
        val elapsed = System.currentTimeMillis() - closeStartTime - delay
        return elapsed < fmDropMs()
    }

    /** True while any visible column is still mid drop-in or exit animation — used to suppress
     *  mouse interaction so hit-testing (which doesn't account for the visual offset) never fires
     *  on the wrong spot. */
    private fun anyColumnAnimating(): Boolean {
        val n = visibleColumns().size
        for (i in 0 until n) {
            if (isColumnOpening(i) || isColumnClosing(i)) return true
        }
        return false
    }

    /** Pixel offset (added to a column's header Y) for the drop-in/exit animation. [restTop] is
     *  where the column sits once idle (used as the open animation's travel distance from y=0, the
     *  top of the viewport). */
    private fun columnYOffset(index: Int, restTop: Int): Float {
        if (closing) {
            val ease = closeEase(index)
            if (ease <= 0f) return 0f
            val dist = (bottom() - top()).toFloat()
            return if (FishSettings.fmExitStyle == "Reverse Curtain") -dist * ease else dist * ease
        }
        val ease = openEase(index)
        return -restTop.toFloat() * (1f - ease)
    }

    /** Resolves the active column-card background colour: a preset swatch or the custom picker
     *  colour, with [FishSettings.fmBgAlpha] applied as the alpha channel. Only affects this
     *  screen's cards — [ScreenTheme.CARD_BG] itself (shared by other screens) is untouched. */
    private fun currentCardBg(): Int {
        val rgb = when (FishSettings.fmBgPreset) {
            "Deep Blue" -> 0x0F1E3D
            "Crimson" -> 0x3D0F14
            "Violet" -> 0x2A0F3D
            "Custom" -> FishSettings.fmBgCustomColor and 0xFFFFFF
            else -> 0x14181D // Dark Glass - today's default look
        }
        val alpha = Mth.clamp(Math.round(FishSettings.fmBgAlpha * 2.55f), 0, 255)
        return (alpha shl 24) or rgb
    }

    /** Brightens each RGB channel of [rgb] by [amount] (0-255), clamped, keeping its alpha. Mirrors
     *  how today's fixed hover colour (0xFF3AD8D1) relates to the base accent (0xFF24B6B0). */
    private fun brighten(rgb: Int, amount: Int): Int {
        val a = (rgb ushr 24) and 0xFF
        val r = Mth.clamp(((rgb ushr 16) and 0xFF) + amount, 0, 255)
        val g = Mth.clamp(((rgb ushr 8) and 0xFF) + amount, 0, 255)
        val b = Mth.clamp((rgb and 0xFF) + amount, 0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Recomputes this screen's local [ACCENT]/[ACCENT_HOVER] from [FishSettings.fmButtonColor] and
     *  [FishSettings.fmButtonAlpha]. Only affects this screen's own accent — [ScreenTheme.ACCENT]
     *  itself (shared by other screens) is untouched. Mirrors [currentCardBg]; call once per frame. */
    private fun refreshButtonTheme() {
        val rgb = FishSettings.fmButtonColor and 0xFFFFFF
        val alpha = Mth.clamp(Math.round(FishSettings.fmButtonAlpha * 2.55f), 0, 255)
        ACCENT = (alpha shl 24) or rgb
        ACCENT_HOVER = brighten(ACCENT, 28)

        val rowRgb = FishSettings.fmRowColor and 0xFFFFFF
        val rowAlpha = Mth.clamp(Math.round(FishSettings.fmRowAlpha * 2.55f), 0, 255)
        ROW_ENABLED = (rowAlpha shl 24) or rowRgb
    }

    private fun columnWidth(): Int {
        val n = visibleColumns().size
        if (n == 0) return 0
        val avail = (cx1() - cx0()) - (n - 1) * COLUMN_GUTTER
        // cap the width so a search matching one or two columns keeps them at normal size, not stretched across the screen
        return (avail / n).coerceIn(MIN_COLUMN_W, MAX_COLUMN_W)
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
        refreshButtonTheme()
        if (resetArmed && System.currentTimeMillis() - resetArmedAt > 3000) resetArmed = false
        clampAllScrolls()
        clampHScroll()

        // wall-clock check for the exit ("cascading curtain") animation finishing — driven by ms
        // timestamps rather than a frame counter since this is an immediate-mode NanoVG renderer
        if (closing && !closeFinalized && System.currentTimeMillis() - closeStartTime >= exitTotalDurationMs()) {
            closeFinalized = true
            Config.manager.save()
            FishConfig.manager.save()
            super.onClose()
            return
        }

        // draw commands replayed later in paintNvgOverlay() after the vanilla GUI flush
        NvgRecorder.clear()

        extractBlurredBackground(ctx)
        ctx.fillGradient(0, 0, this.width, this.height, DIM_TOP, DIM_BOT)

        // this class works in the virtual (pre-shrink) coordinate space right()/bottom() use; convert the real mouse position once here
        val vmx = vx(mouseX)
        val vmy = vx(mouseY)

        hoverDesc = null
        try {
            renderTopBar(ctx, vmx, vmy)
            renderContent(ctx, vmx, vmy)
            renderSearchBar(ctx, vmx, vmy)
            renderHint(ctx)
            renderHoverTooltip(ctx)
        } catch (t: Throwable) {
            // blur/dim are already in the render state here; don't let a widget-layer exception strand the screen blur-only with no diagnostic (log once)
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

    /** Small controls cheat-sheet, bottom-right of the screen. */
    private fun renderHint(ctx: GuiGraphicsExtractor) {
        val lines = arrayOf(
            "Scroll inside a column to see more of it",
            "Drag a column's header to move it",
            "Right-drag a header onto another to merge them",
        )
        val sc = 0.8f
        val lh = 9
        var y = bottom() - BOTTOM_RESERVE + (BOTTOM_RESERVE - lines.size * lh) / 2 - 8
        for (line in lines) {
            sst(ctx, this.font, line, right() - MARGIN - sw(this.font, line, sc), y, HINT_COLOR, sc)
            y += lh
        }
    }

    private fun renderColumnCard(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, headerTop: Int, cardBottom: Int, mouseX: Int, mouseY: Int, showPopOut: Boolean = false) {
        val hy = headerTop
        val w = x1 - x0
        NvgRecorder.dropShadow(x0.toFloat(), hy.toFloat(), w.toFloat(), (cardBottom - hy).toFloat(), CARD_RADIUS.toFloat(), 10f, 0x60000000)
        roundedRect(ctx, x0, hy, w, cardBottom - hy, CARD_RADIUS, currentCardBg())
        NvgRecorder.fillRectTopRounded(x0.toFloat(), hy.toFloat(), w.toFloat(), HEADER_STRIP_H.toFloat(), CARD_RADIUS.toFloat(), ScreenTheme.ACCENT)
        // For a stacked slot the visible content is the active child — its HUDs, not the group's.
        val hudCol = if (c.isGroup()) c.content() else c
        val hudBtn = hudBtnRect(hudCol, x1, hy, showPopOut)
        val titleClip = w - (if (showPopOut) 40 else 20) - (if (hudBtn != null) hudBtn[2] - hudBtn[0] + 6 else 0)
        sst(ctx, this.font, ellipsize(c.name, titleClip), x0 + 10, hy + HEADER_STRIP_H + 6, TEXT_COLOR, 1f)
        if (hudBtn != null) {
            val hov = mouseX in hudBtn[0]..hudBtn[2] && mouseY in hudBtn[1]..hudBtn[3]
            drawPillButton(ctx, hudBtn[0], hudBtn[1], hudBtn[2] - hudBtn[0], hudBtn[3] - hudBtn[1], "Edit HUD", false, ACCENT, hov)
        }
        if (showPopOut) {
            val r = popOutIconRect(x1, hy)
            val hov = mouseX in r[0]..r[2] && mouseY in r[1]..r[3]
            NvgRecorder.popOutIcon(r[0].toFloat(), r[1].toFloat(), (r[2] - r[0]).toFloat(), if (hov) ACCENT else SUBTEXT_COLOR)
        }
    }

    /**
     * Header "Edit HUD" button box for a column that owns movable HUDs, else null. When [leftOfPopOut]
     * the button is shifted left to clear the stacked-child pop-out icon.
     */
    private fun hudBtnRect(c: Column, x1: Int, headerTop: Int, leftOfPopOut: Boolean = false): IntArray? {
        if (FishHudEditor.columnHuds(c.name) == null) return null
        val bw = sw(this.font, "Edit HUD", 0.85f) + 12
        val bh = HEADER_H - HEADER_STRIP_H - 4
        val bx = x1 - (if (leftOfPopOut) 26 else 8) - bw
        val by = headerTop + HEADER_STRIP_H + 2
        return intArrayOf(bx, by, bx + bw, by + bh)
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
            val yOff = Math.round(columnYOffset(i, top - HEADER_H))
            renderOneColumn(ctx, c, columnX0(i), colW, top, bot, mouseX, mouseY, yOff)
        }

        // Dragged column renders last (on top of its neighbors) and follows the mouse instead of its slot.
        // (Interaction is suppressed while any column is animating, so this can never be mid-animation.)
        if (dc != null) {
            renderOneColumn(ctx, dc, dragMouseX - dragGrabDX, colW, top, bot, mouseX, mouseY)
        }
    }

    private fun renderOneColumn(ctx: GuiGraphicsExtractor, c: Column, x0: Int, colW: Int, top: Int, bot: Int, mouseX: Int, mouseY: Int, yOffset: Int = 0) {
        val x1 = x0 + colW

        if (c.isGroup()) {
            val dragged = if (dragTabParent === c) dragTabChild else null
            for (seg in stackSegments(c, top - HEADER_H, bot)) {
                if (seg.col === dragged) continue
                renderColumnBlock(ctx, seg.col, x0, x1, seg.segTop + yOffset, seg.segBot + yOffset, mouseX, mouseY, showPopOut = true)
            }
            // the child dragged around the stack floats at the cursor as a full card, not a bare label, so it reads like any other column mid-drag
            if (dragged != null) {
                renderColumnBlock(ctx, dragged, x0, x1, dragTabMouseY - dragTabGrabDY, bot, mouseX, mouseY)
            }
            return
        }

        renderColumnBlock(ctx, c, x0, x1, top - HEADER_H + yOffset, bot + yOffset, mouseX, mouseY)
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
        if (on) NvgRecorder.fillPillBar((x0 + 2).toFloat(), (top + 3).toFloat(), 2f, (ROW_H - 6).toFloat(), ACCENT)

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
        val leftX = x0 + 14
        val rightX = x1 - 12
        var sy = top + 6
        for (s in f.sub) {
            if (s.hiddenByGate()) continue
            val sh = s.getHeight()
            if (s !is SubcategoryHeader && s !is LabelSetting && s !is InputSetting && s !is SliderIntSetting && s !is SliderDoubleSetting &&
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
        // Suppress all mouse interaction while the cascading curtain is still animating any column
        // in/out — the animated Y-offset isn't reflected in hit-testing, and this also prevents a
        // second ESC/"Save & Close" click from re-triggering the exit animation mid-flight.
        if (anyColumnAnimating()) return true

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

        val headerTop = cyTop() - HEADER_H
        run {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val slot = cols[ci]
                val x1 = columnX0(ci) + colW
                if (slot.isGroup()) {
                    for (seg in stackSegments(slot, cyTop() - HEADER_H, cyBot())) {
                        val hc = seg.col
                        val r = hudBtnRect(hc, x1, seg.segTop, leftOfPopOut = true) ?: continue
                        if (mx in r[0]..r[2] && my in r[1]..r[3]) {
                            Minecraft.getInstance().setScreen(FishHudEditor(this, FishHudEditor.columnHuds(hc.name)))
                            return true
                        }
                    }
                } else if (my >= headerTop && my < cyTop()) {
                    val r = hudBtnRect(slot, x1, headerTop) ?: continue
                    if (mx in r[0]..r[2] && my in r[1]..r[3]) {
                        Minecraft.getInstance().setScreen(FishHudEditor(this, FishHudEditor.columnHuds(slot.name)))
                        return true
                    }
                }
            }
        }

        // grabbing a column header starts a drag (only when no search filter, so visible order matches the master list 1:1); left-drag reorders, right-drag stacks/pops (see mouseReleased)
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

        // a stacked column's mini segment headers sit in the content band; grabbing one starts the same drag, scoped to reorder/pop within the stack
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
                    if (s.hiddenByGate()) continue
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

    /** Wheel routed to the sub-setting under the cursor (parallels [handleRowClick]); returns true
     *  only when that setting consumed the scroll, so the column keeps scrolling otherwise. */
    private fun handleRowScroll(col: Column, x0: Int, x1: Int, mx: Int, my: Int, dir: Int, topY: Int): Boolean {
        for (rl in layoutColumn(col, col.scroll, topY)) {
            val f = rl.feature
            val subH = rl.subBottom - rl.subTop
            if (subH > 0 && my >= rl.subTop && my <= rl.subBottom) {
                val leftX = x0 + 14
                val rightX = x1 - 12
                var ssy = rl.subTop + 6
                for (s in f.sub) {
                    if (s.hiddenByGate()) continue
                    val sh = s.getHeight()
                    if (my >= ssy && my <= ssy + sh && s.onScroll(mx, my, leftX, rightX, ssy, dir)) return true
                    ssy += sh
                }
                return false
            }
        }
        return false
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val slider = activeSlider
        if (slider != null) { slider.onDrag(vx(click.x()), activeSliderX, activeSliderW); return true }
        val dc = dragColumn
        if (dc != null) {
            dragMouseX = vx(click.x())
            // merge-mode (right-drag) leaves slot order alone so the target header stays under the cursor; only left-drag live-snaps
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
        // bands aren't uniform height, so use the dragged child's own natural height to convert its grabbed point into a comparable center
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
        // flatten rather than nest: if incoming is itself a stack, fold its members in directly so stacks never nest
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
        if (anyColumnAnimating()) return true

        // shift+wheel, a trackpad horizontal swipe, or wheeling over the column headers pans sideways
        val shiftDown = InputConstants.isKeyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
            InputConstants.isKeyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_RIGHT_SHIFT)

        val mouseX = vx(mouseX).toDouble()
        val mouseY = vx(mouseY).toDouble()
        val cols = visibleColumns()
        val colW = columnWidth()
        if (horizontalAmount == 0.0 && !shiftDown && mouseY >= cyTop()) {
            // First give the setting under the cursor a chance to consume the wheel.
            val dir = if (verticalAmount > 0) -1 else 1
            for (i in cols.indices) {
                val x0 = columnX0(i)
                val x1 = x0 + colW
                if (mouseX < x0 || mouseX > x1) continue
                val slot = cols[i]
                if (slot.isGroup()) {
                    for (seg in stackSegments(slot, cyTop() - HEADER_H, cyBot())) {
                        if (mouseY < seg.bodyTop || mouseY > seg.segBot) continue
                        if (handleRowScroll(seg.col, x0, x1, mouseX.toInt(), mouseY.toInt(), dir, seg.bodyTop)) return true
                    }
                } else if (handleRowScroll(slot, x0, x1, mouseX.toInt(), mouseY.toInt(), dir, cyTop())) {
                    return true
                }
            }
            for (i in cols.indices) {
                val x0 = columnX0(i)
                val x1 = x0 + colW
                if (mouseX < x0 || mouseX > x1) continue
                val slot = cols[i]
                if (slot.isGroup()) {
                    for (seg in stackSegments(slot, cyTop() - HEADER_H, cyBot())) {
                        if (mouseY < seg.bodyTop || mouseY > seg.segBot) continue
                        val vp = seg.segBot - seg.bodyTop
                        if (maxScrollFor(seg.col, vp) <= 0) break
                        seg.col.scroll = Mth.clamp((seg.col.scroll - verticalAmount * 18).toInt(), 0, maxScrollFor(seg.col, vp))
                        return true
                    }
                    break
                }
                val colBottom = Math.min(cyTop() + columnContentHeight(slot), cyBot())
                if (mouseY <= colBottom) {
                    if (maxScrollFor(slot, colBottom - cyTop()) <= 0) break
                    slot.scroll = Mth.clamp((slot.scroll - verticalAmount * 18).toInt(), 0, maxScrollFor(slot, colBottom - cyTop()))
                    return true
                }
            }
        }

        // anywhere else in the panel (headers, gutters, below a short column) pans sideways instead of doing nothing
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

    /** glGetError forces a driver sync, so only drain on the first paint rather than every frame. */
    private fun fishmod_glCheck(where: String) {
        if (paintCount > 1) return
        var err: Int
        while (org.lwjgl.opengl.GL11.glGetError().also { err = it } != org.lwjgl.opengl.GL11.GL_NO_ERROR) {
            fishmod.utils.debug.Debug.LOGGER.warn("[NanoVG] GL error 0x{} at {}", Integer.toHexString(err), where)
        }
    }

    override fun isPauseScreen(): Boolean = false

    /** Intercepts every close request (ESC via vanilla's default keyPressed handling, and the
     *  "Save & Close" pill) to play the exit ("cascading curtain") animation first; the real save
     *  + super.onClose() happens once that finishes, checked each frame in extractRenderState(). */
    override fun onClose() {
        requestClose()
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
            for (s in sub) if (!s.hiddenByGate()) total += s.getHeight()
            if (total == 0) return 0
            return total + 10
        }
        fun animatedSubHeight(): Int {
            val natural = naturalSubHeight()
            return if (natural == 0) 0 else Math.round(natural * expandAnim.progress())
        }
    }

    abstract class Setting(var name: String, var description: String) {
        /** When set and it returns false, this setting is laid out with zero height and not drawn or
         *  hit-tested — used to hide a field while the toggle that gates it is off. */
        var gate: (() -> Boolean)? = null
        fun hiddenByGate(): Boolean = gate?.let { !it() } ?: false
        /** Fluent: hide this setting whenever [pred] is false. */
        fun gatedBy(pred: () -> Boolean): Setting { gate = pred; return this }

        abstract fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, settingY: Int, mouseX: Int, mouseY: Int, tr: Font)
        open fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, settingY: Int, button: Int): Boolean = false
        /** Wheel over this setting. [dir] is -1 for wheel-up, +1 for wheel-down. Return true to
         *  consume the event (otherwise the column scrolls as usual). */
        open fun onScroll(mx: Int, my: Int, leftX: Int, rightX: Int, settingY: Int, dir: Int): Boolean = false
        open fun onDrag(mx: Int, sx: Int, sliderW: Int) {}
        open fun getHeight(): Int = ITEM_HEIGHT

        /** Trim [s] with an ellipsis so it fits within [maxW] px at the sub-panel text size. */
        protected fun fit(s: String, maxW: Int): String {
            fun w(t: String) = Math.ceil(NvgRecorder.textWidth(t, NVG_BASE_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()
            if (maxW <= 4 || w(s) <= maxW) return s
            var t = s
            while (t.length > 1 && w("$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }
    }

    class SubcategoryHeader(name: String) : Setting(name, "") {
        private fun w(t: String) = Math.ceil(NvgRecorder.textWidth(t, SUBCAT_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()

        // Decide 1- vs 2-line height against the tightest column width so this never truncates
        // regardless of how wide the column ends up being; render() wraps for real against the
        // actual width either way, so a wider column just leaves a little breathing room.
        private val twoLine: Boolean = w(name) > (MIN_COLUMN_W - 38)

        override fun getHeight(): Int = if (twoLine) SUBCAT_HEIGHT_2 else SUBCAT_HEIGHT

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val h = getHeight()
            roundRect(ctx, leftX, sy, rightX, sy + h, 3, 0xFF11131A.toInt())
            val maxW = rightX - leftX - 12
            val lines = wrap(name, maxW)
            if (lines.size <= 1) {
                stBold(ctx, tr, lines.getOrElse(0) { name }, leftX + 6, sy + (h - 8) / 2, ACCENT)
            } else {
                stBold(ctx, tr, lines[0], leftX + 6, sy + 4, ACCENT)
                stBold(ctx, tr, lines[1], leftX + 6, sy + 13, ACCENT)
            }
        }

        /** Greedy word-wrap into at most 2 lines; a still-too-long 2nd line gets ellipsized. */
        private fun wrap(s: String, maxW: Int): List<String> {
            if (w(s) <= maxW) return listOf(s)
            val words = s.split(" ")
            var line1 = ""
            var i = 0
            while (i < words.size) {
                val candidate = if (line1.isEmpty()) words[i] else "$line1 ${words[i]}"
                if (w(candidate) > maxW && line1.isNotEmpty()) break
                line1 = candidate
                i++
            }
            if (line1.isEmpty() && words.isNotEmpty()) { line1 = words[0]; i = 1 }
            val rest = words.drop(i).joinToString(" ")
            return if (rest.isEmpty()) listOf(line1) else listOf(line1, fit(rest, maxW))
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
        // own compact size (not the shared PILL_H) so the toggle reads as a small switch, not a big pill
        companion object { const val W = 26; const val H = 14 }
    }

    /** Slider/text-input settings render on two lines: name on line 1 (full-width, left-aligned,
     *  no competing control), the actual control on line 2 below it — so a long label never
     *  visually overlaps a right-aligned control on the same row. See TWO_LINE_H. */
    class SliderIntSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit, val min: Int, val max: Int, val step: Int = 1) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Int>, min: Int, max: Int, step: Int = 1) : this(name, desc, { prop.get() }, { prop.set(it) }, min, max, step)

        private fun snap(v: Int): Int {
            if (step <= 1) return v.coerceIn(min, max)
            return (min + Math.round((v - min).toFloat() / step) * step).coerceIn(min, max)
        }

        override fun getHeight(): Int = SLIDER_ROW_H
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
            setter(snap(min + (pct * (max - min)).toInt()))
        }
    }

    class SliderDoubleSetting(name: String, desc: String, val getter: () -> Double, val setter: (Double) -> Unit, val min: Double, val max: Double) : Setting(name, desc) {
        constructor(name: String, desc: String, prop: KMutableProperty0<Double>, min: Double, max: Double) : this(name, desc, { prop.get() }, { prop.set(it) }, min, max)

        override fun getHeight(): Int = SLIDER_ROW_H
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
            if (!tf.isFocused) {
                if (tf.value != getter()) tf.value = getter()   // reflect external changes (e.g. a button that rewrites the backing value)
                tf.cursorPosition = 0; tf.setHighlightPos(0)
            }
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

    /**
     * Type to filter every sound event in the registry; shows the top 10 matches as clickable rows.
     * Stores the picked id string (resolved back to a SoundEvent by [fishmod.utils.sound.SoundManager.preset]).
     */
    class SoundSearchSetting(
        name: String,
        desc: String,
        private val valueGetter: () -> String,
        private val valueSetter: (String) -> Unit,
        // optional: "Test" previews at the feature's configured volume/pitch, not a flat 100%/1.0; volumePct is 0..500 (percent)
        private val volumePct: (() -> Int)? = null,
        private val pitchGetter: (() -> Double)? = null,
    ) : InputSetting(name, desc, { "" }, { }) {

        private var query = ""
        private var cacheKey: String? = null
        private var cached: List<String> = emptyList()
        private val rowRects = ArrayList<Pair<IntArray, String>>()
        private var testRect: IntArray? = null

        private fun preview() {
            val vol = (volumePct?.invoke() ?: 100).coerceIn(0, 500) / 100f
            val pit = (pitchGetter?.invoke() ?: 1.0).toFloat().coerceIn(0f, 2f)
            fishmod.utils.Misc.sendSound(fishmod.utils.sound.SoundManager.preset(valueGetter()), vol, pit)
        }

        override fun initField(tr: Font) {
            if (textField == null) {
                val tf = EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty())
                tf.setMaxLength(128)
                tf.value = ""
                tf.setResponder { s -> query = s }
                textField = tf
            }
        }

        private fun matches(): List<String> {
            val cur = valueGetter()
            val key = "${query.trim().lowercase()}|$cur"
            cacheKey?.let { if (it == key) return cached }
            val q = query.trim().lowercase().replace(' ', '_')
            val out = if (q.isEmpty()) {
                (listOf(cur).filter { it.isNotBlank() && ':' in it } +
                    fishmod.utils.sound.SoundManager.shortlist).distinct().take(10)
            } else {
                fishmod.utils.sound.SoundManager.allSoundIds.asSequence()
                    .filter { it.contains(q) }
                    .sortedWith(compareBy({ !it.substringAfter(':').startsWith(q) }, { it.length }, { it }))
                    .take(10).toList()
            }
            cacheKey = key; cached = out
            return out
        }

        /** Full (uncapped) candidate list for wheel-browsing — respects a partial query if typed,
         *  otherwise every known sound id, alphabetically. */
        private fun browseList(): List<String> {
            val q = query.trim().lowercase().replace(' ', '_')
            val all = fishmod.utils.sound.SoundManager.allSoundIds
            return if (q.isEmpty()) all.sorted()
            else all.filter { it.contains(q) }
                .sortedWith(compareBy({ !it.substringAfter(':').startsWith(q) }, { it.length }, { it }))
        }

        override fun onScroll(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, dir: Int): Boolean {
            // Only the label / field strip browses; scrolling over the result list scrolls the column.
            if (my > sy + 24) return false
            val list = browseList()
            if (list.isEmpty()) return false
            val cur = valueGetter()
            val idx = list.indexOf(cur).let { if (it < 0) list.indexOf("minecraft:$cur") else it }
            val next = if (idx < 0) (if (dir > 0) 0 else list.size - 1)
            else ((idx + dir) % list.size + list.size) % list.size
            valueSetter(list[next])
            fishmod.utils.sound.SoundManager.play(fishmod.utils.sound.SoundManager.preset(list[next]), 1f, 1f)
            cacheKey = null
            return true
        }

        override fun getHeight(): Int = 28 + matches().size * OPTION_H + 4

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            initField(tr)
            st(ctx, tr, name, leftX + 2, sy + 1, TEXT_COLOR)
            val btnW = stw(tr, "Test") + 14
            val btnH = 11
            val btnX = rightX - btnW - 2
            val btnHov = mx >= btnX && mx <= btnX + btnW && my >= sy && my <= sy + btnH
            roundedRect(ctx, btnX, sy, btnW, btnH, btnH / 2, if (btnHov) ACCENT else TRACK_OFF)
            st(ctx, tr, "Test", btnX + 7, sy + 2, TEXT_COLOR)
            testRect = intArrayOf(btnX, sy, btnX + btnW, sy + btnH)
            val cur = valueGetter()
            if (cur.isNotBlank()) {
                // right-align against the Test button but never cross the "name" label; left-ellipsize (the tail is the useful part)
                val nameEnd = leftX + 2 + stw(tr, name) + 8
                val avail = btnX - 6 - nameEnd
                var shown = cur.removePrefix("minecraft:")
                if (stw(tr, shown) > avail) {
                    while (shown.length > 1 && stw(tr, "…$shown") > avail) shown = shown.substring(1)
                    shown = "…$shown"
                }
                if (avail >= stw(tr, "…")) st(ctx, tr, shown, btnX - 6 - stw(tr, shown), sy + 1, ACCENT_HOVER)
            }
            val ix = leftX + 2
            val iy = sy + 12
            val fieldW = rightX - leftX - 4
            nvgTextField(textField!!, ix, iy, fieldW, INPUT_H)
            rowRects.clear()
            var ry = iy + INPUT_H + 2
            for (id in matches()) {
                val hov = mx >= leftX + 2 && mx <= rightX - 2 && my >= ry && my <= ry + OPTION_H
                if (hov) roundedRect(ctx, leftX + 4, ry + 1, rightX - leftX - 8, OPTION_H - 2, 4, ROW_HOVER)
                val sel = id == cur
                val short = id.removePrefix("minecraft:")
                st(ctx, tr, short, leftX + 10, ry + (OPTION_H - 8) / 2,
                    if (sel) ACCENT_HOVER else if (hov) TEXT_COLOR else SUBTEXT_COLOR)
                rowRects.add(intArrayOf(leftX + 2, ry, rightX - 2, ry + OPTION_H) to id)
                ry += OPTION_H
            }
        }

        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            testRect?.let { r ->
                if (mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]) { preview(); return true }
            }
            val ix = leftX + 2
            val iy = sy + 12
            val fieldW = rightX - leftX - 4
            if (mx >= ix && mx <= ix + fieldW && my >= iy && my <= iy + INPUT_H) {
                textField?.let { it.setFocused(true); val n = it.value.length; it.cursorPosition = n; it.setHighlightPos(n) }
                return true
            }
            for ((r, id) in rowRects) {
                if (mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]) {
                    valueSetter(id)
                    fishmod.utils.sound.SoundManager.play(fishmod.utils.sound.SoundManager.preset(id), 1f, 1f)
                    cacheKey = null
                    return true
                }
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
        override fun getHeight(): Int = if (description.isEmpty()) ITEM_HEIGHT else TWO_LINE_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val w = rightX - leftX - 4
            st(ctx, tr, fit(name, w), leftX + 2, sy + 2, TEXT_COLOR)
            if (description.isNotEmpty()) st(ctx, tr, fit(description, w), leftX + 2, sy + 12, SUBTEXT_COLOR)
        }
    }

    companion object {
        private var ACCENT: Int = ScreenTheme.ACCENT
        private var ACCENT_HOVER: Int = ScreenTheme.ACCENT_HOVER
        private const val DIM_TOP = 0x2E000000
        private const val DIM_BOT = 0x50000000
        private val CARD_BG = ScreenTheme.CARD_BG
        private const val ROW_HOVER = 0x1EFFFFFF
        private var ROW_ENABLED: Int = 0x2624B6B0
        private val SUBROW_BG = 0xFF0F1317.toInt()
        private val TRACK_OFF = 0xFF3A3F48.toInt()
        private val TEXT_COLOR = ScreenTheme.TEXT_COLOR
        private val SUBTEXT_COLOR = ScreenTheme.SUBTEXT_COLOR
        private val HINT_COLOR = 0xFFFFFFFF.toInt()
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
        private const val MAX_COLUMN_W = 260 // ceiling so a narrow search result doesn't stretch a column across the whole screen

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
        private const val SUBCAT_HEIGHT_2 = 22 // two-line variant, for headers whose text wraps
        // sliders/text-inputs render name + control on two lines (see SliderIntSetting docs), not a right-aligned control on the name row
        private const val TWO_LINE_H = 36
        // sliders have no control body below the track, so they can stack tighter than text inputs
        private const val SLIDER_ROW_H = 28
        private const val TWO_LINE_CTRL_Y = 20

        /** Roughly matches Minecraft's default font weight; TEXT_SCALE multiplies this. */
        private const val NVG_BASE_TEXT_SIZE = 9.5f
        private const val INPUT_TEXT_SIZE = 7f

        /** SubcategoryHeader text size — a bit larger than body text so it reads as a heading. */
        private const val SUBCAT_TEXT_SIZE = NVG_BASE_TEXT_SIZE * 1.15f

        // shape/text helpers push into NvgRecorder, not `ctx`, so they paint in the deferred paintNvgOverlay() pass; `ctx` is kept only for call-site compat and vanilla widget state
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

        /** Bold heading text (see [SubcategoryHeader]) — same faux-bold trick as [NvgRecorder.textBold]. */
        fun stBold(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int) {
            NvgRecorder.textBold(s, x.toFloat(), y.toFloat(), SUBCAT_TEXT_SIZE * TEXT_SCALE, color)
        }
        fun stwBold(tr: Font, s: String): Int = Math.ceil(NvgRecorder.textWidth(s, SUBCAT_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()

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
                "Chat" -> "Smart Copy, Compact Chat, Infinite History, Search, Filter"
                "Mod Prefix" -> "Tag FishMod's chat output with a prefix"
                "Inventory Buttons" -> "Clickable command buttons in your inventory"
                "Smart Copy Chat" -> "Right-click a chat line to copy it"
                "Compact Tab" -> "Cleaner custom tab player list"
                "Chat Filter" -> "Hide selected chat spam + NoammAddons' list + custom regex"
                "Explosive Shot" -> "Title with per-enemy damage"
                "Dungeon Score" -> "Live S+ score tracker overlay"
                "Puzzle Overlay" -> "Show solved puzzle names"
                "Auto Sprint" -> "Keep sprinting while holding forward"
                "Sound Manager" -> "Master toggle & volume for FishMod cues"
                "Leap Messages" -> "Title with the Spirit-Leap target"
                "Key Notifier" -> "Title + cue on Wither/Blood key pickup"
                "Boss Health Numbers" -> "Numeric HP on the M7 boss bar"
                "Blessing Display" -> "Active dungeon blessings from the tab footer"
                "Invincibility Timer" -> "Spirit / Bonzo / Phoenix proc + cooldown timers"
                "Secret Clicked" -> "Box + chime when you click a dungeon secret"
                "Puzzle Solvers" -> "In-world solutions for dungeon puzzles"
                "Arrow Align" -> "F7 P3 arrow device — clicks needed per frame"
                "Ragnarock" -> "Alerts when your Ragnarock Axe cast succeeds or is cancelled"
                "Arrows Device" -> "F7 P3 Sharp Shooter — boxes targets vs already-hit blocks"
                "Simon Says Solver" -> "F7 P3 Goldor device — boxes the buttons to press, in order"
                "Melody Message" -> "Party-announce the F7 melody terminal + its progress"
                "Item Rarity Background" -> "Rarity-tinted sprite behind every item"
                "Item Quality Tooltip" -> "Dungeon-item stat boost % + floor in the tooltip"
                "Gyro Helper" -> "Gyrokinetic Wand landing box + sucking-range ring"
                "Wither ESP" -> "Outline the F7 wither boss by phase"
                "M7 Relics" -> "P5 relic spawn timer + cauldron box"
                "Auto Requeue" -> "Re-queue the same floor when a run ends (leader only)"
                "Warp Cooldown" -> "Countdown until you can /warp again"
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
                "Party Finder Join Stats" -> "Whisper or PF-join prints their MP/PB/Cata/Gear to chat — also /pfs [name]"
                "Party Finder Menu" -> "Level req + missing classes on heads, stats in party-member tooltips"
                "Party Finder List" -> "Scrollable party summary beside the menu; hover a row to highlight its head"
                "Party Finder Auto Kick" -> "As leader, kick joiners who miss the S+ PB / secrets bar"
                "Container Value" -> "No-background value list + total beside the open container / storage overlay"
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
