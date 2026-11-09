package fishmod.features
import fishmod.compat.reloadChunks
import fishmod.compat.curScreen
import fishmod.compat.showScreen

import fishmod.utils.debug.FishDiag
import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Easing
import fishmod.utils.config.ConfigRoot
import fishmod.utils.config.FishConfig
import fishmod.utils.config.UserColorStore
import fishmod.utils.config.values.InvButtonSettings
import fishmod.utils.config.values.DungeonSettings
import fishmod.cosmetic.NickState
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.config.values.VisualSettings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import fishmod.features.dungeon.f7.TerminalTracker
import fishmod.utils.dungeon.RunSplit
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
import fishmod.utils.rendering.UiRecorder
import fishmod.compat.GLFW
import fishmod.utils.config.SettingRef
import fishmod.utils.config.ColorDefaults

private val FORMAT_CODE_RE = Regex("[&§][0-9a-fk-orxA-FK-ORX]")

private val HEX_CODE_RE = Regex("&#[0-9a-fA-F]{6}")

class FishModScreen : Screen(Component.literal("FishMod")), HasUiOverlay {

    private val columns: MutableList<Column> = ArrayList()
    private var searchText = ""

    private var visibleCacheSearch: String? = null
    private val visibleFeaturesCache = HashMap<Column, List<Feature>>()
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

    private var pressX = 0
    private var pressY = 0
    private var dragMoved = false
    private val foldedColumns = HashSet<String>()
    private var stackScroll = 0
    private var stackDragCol: Column? = null
    private var stackDragY = 0
    private var stackGrabDY = 0
    // eased per-tab stack Y, keyed by column name
    private val stackAnimY = HashMap<String, Double>()
    // unfold drop start time per column name
    private val unfoldStart = HashMap<String, Long>()
    private val folders = ArrayList<Folder>()
    private var stackDragFolder: Folder? = null
    private var renamingFolder: Folder? = null
    private var renameField: EditBox? = null

    private val screenOpenTime = System.currentTimeMillis()
    private var closing = false
    private var closeStartTime = 0L
    private var closeFinalized = false

    init {
        try { buildCategories() } catch (t: Throwable) { FishDiag.fail("FishModScreen.2", "buildCategories failed after ${columns.size} columns", t) }
        try { applySavedColumnOrder() } catch (t: Throwable) { FishDiag.fail("FishModScreen.3", "applySavedColumnOrder failed for '${FishSettings.fmColumnOrder}'", t) }
        try { loadFolders() } catch (t: Throwable) { FishDiag.fail("FishModScreen.29", "loadFolders failed for '${FishSettings.fmSidebarFolders}'", t) }
        loadFoldedColumns()
        fishmod.utils.TickScheduler.scheduleTask({
            if (paintCount == 0 && Minecraft.getInstance().curScreen === this) {
                FishDiag.fail("FishModScreen.1", "paintUiOverlay never invoked 40 ticks after open (GameRendererUiMixin hook did not fire)")
                fishmod.utils.debug.FishDebug.LOGGER.error("[UiRenderer] paintUiOverlay was never invoked - the GameRendererUiMixin hook didn't fire (likely a rendering-mod conflict)")
                fishmod.utils.FishUtil.addChatMessage(Component.literal(
                    "§c[FishMod] The /fm screen failed to render (a rendering mod may be conflicting). Please report this to the mod author."
                ))
            }
        }, 40)
    }

    private fun saveTwitchConfig() {
        try {
            twitchbridge.TwitchBridgeClient.config().save()
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.11", "Twitch Bridge config save failed", t)
        }
    }

    private fun buildCategories() {
        val general = Column("General")
        val invStorage = Column("Inventory & Storage")
        val party = Column("Party & Social")
        val dungeon = Column("Dungeons")
        val dungeonTrackers = Column("Dungeon Trackers")
        val dungeonMap = Column("Dungeon Map")
        val solvers = Column("Dungeon Solvers")
        val floor7 = Column("Floor 7")
        val hud = Column("HUD & Overlays")
        val visuals = Column("Visuals & Rendering")
        val cosmetics = Column("Cosmetics")

        run {
            val f = Feature("UI Customization", null, null)
            f.sub.add(SubcategoryHeader("Background"))
            f.sub.add(DropdownSetting("Background Preset", "", arrayOf("Dark Glass", "Deep Blue", "Crimson", "Violet", "Custom"),
                { FishSettings.fmBgPreset },
                { v -> FishSettings.fmBgPreset = v }))
            f.sub.add(ColorPickerSetting("Custom Color", "", "fmBgCustomColor").gatedBy { FishSettings.fmBgPreset == "Custom" })
            f.sub.add(SliderIntSetting("Background Alpha %", "0% invisible - 100% solid", "fmBgAlpha", 0, 100))
            f.sub.add(SubcategoryHeader("Accent"))
            f.sub.add(ColorPickerSetting("Accent Color", "", "fmButtonColor"))
            f.sub.add(SliderIntSetting("Accent Opacity %", "0% invisible - 100% solid", "fmButtonAlpha", 0, 100))
            f.sub.add(SubcategoryHeader("Buttons"))
            f.sub.add(SliderIntSetting("Button Opacity %", "Feature row fill, separate from the background", "fmRowBgAlpha", 0, 100))
            f.sub.add(ColorPickerSetting("Enabled Tint Color", "Tint on an enabled row, e.g. Door Colors", "fmRowColor"))
            f.sub.add(SliderIntSetting("Enabled Tint %", "0% invisible - 100% solid", "fmRowAlpha", 0, 100))
            f.sub.add(SubcategoryHeader("Layout"))
            f.sub.add(ToggleSetting("Compact GUI", "Tighter rows, gaps and columns so more fits on screen", "fmCompact"))
            f.sub.add(SubcategoryHeader("Cascade Animation"))
            f.sub.add(ToggleSetting("Menu Animations", "Off = the menu just appears; no cascade / expand / toggle slides", "fmAnimations"))
            f.sub.add(SliderIntSetting("Drop Duration (ms)", "200 snappy - 1200 dramatic", "fmDropDurationMs", 200, 1200, 25).gatedBy { FishSettings.fmAnimations })
            f.sub.add(SliderIntSetting("Stagger Delay (ms)", "Extra delay per column, 0 = all at once", "fmStaggerDelayMs", 0, 120, 5).gatedBy { FishSettings.fmAnimations })
            f.sub.add(DropdownSetting("Exit Style", "How columns animate on close", arrayOf("Floor Fall", "Reverse Curtain"),
                { FishSettings.fmExitStyle },
                { v -> FishSettings.fmExitStyle = v }).gatedBy { FishSettings.fmAnimations })
            general.features.add(f)
        }
        run {
            val f = Feature("GUI Settings", "guiSettings")
            f.sub.add(SliderIntSetting("Main GUI Scale %", "100% = default size", "guiScaleMain", 50, 150, 5))
            f.sub.add(SliderIntSetting("Chat & Commands Scale %", "100% = default size", "guiScaleChatCommands", 50, 150, 5))
            f.sub.add(SliderIntSetting("Party & Loot Scale %", "100% = default size", "guiScalePartyLoot", 50, 150, 5))
            f.sub.add(SliderIntSetting("HUD Editor Scale %", "100% = default size", "guiScaleHudEditor", 50, 150, 5))
            f.sub.add(SliderIntSetting("Item Customize Scale %", "100% = default size", "guiScaleItemCustomize", 50, 150, 5))
            f.sub.add(SliderIntSetting("Storage Viewer Scale %", "100% = default size", "guiScaleStorage", 50, 150, 5))
            f.sub.add(SliderIntSetting("Auction Price Scale %", "100% = default size", "guiScaleAuction", 50, 150, 5))
            f.sub.add(SliderIntSetting("Waypoint Title Scale %", "100% = default size", "guiScaleWaypoint", 50, 150, 5))
            f.sub.add(SliderIntSetting("Credits Scale %", "100% = default size", "guiScaleCredits", 50, 150, 5))
            f.sub.add(SliderIntSetting("Profile Viewer Scale %", "100% = default size", "guiScaleProfileViewer", 50, 150, 5))
            f.sub.add(SliderIntSetting("Diana Profit Scale %", "100% = default size", "guiScaleDianaProfit", 50, 150, 5))
            general.features.add(f)
        }
        run {
            val f = Feature("Update Notice", "updateNotice")
            f.sub.add(DropdownSetting("Style", "Chat Message = clickable download link, Popup = full update window", arrayOf("Chat Message", "Popup"),
                { FishSettings.updateNoticeStyle },
                { v -> FishSettings.updateNoticeStyle = v }))
            general.features.add(f)
        }
        general.features.add(Feature("Messages from RedFish", "dashboardMessagesEnabled"))
        run {
            val f = Feature("Debug Reports", "debugReports")
            f.sub.add(ToggleSetting("Chat Notice", "Short chat line with a copy button when something breaks (max once a minute)", "debugChatNotices"))
            general.features.add(f)
        }
        run {
            val f = Feature("Board Reports", "boardReports")
            f.sub.add(SubcategoryHeader("/fm bug <text> and /fm suggest <text> send to the test board. No name, IP or token is sent."))
            f.sub.add(ToggleSetting("Auto-Report Errors", "Send each new kind of \"Something didn't work right\" error once (version, area, details)", "boardAutoSend"))
            f.sub.add(SliderIntSetting("Auto Reports Per Hour", "", "boardAutoPerHour", 1, 20).gatedBy { FishSettings.boardAutoSend })
            general.features.add(f)
        }
        run {
            val f = Feature("Mod Prefix", "modPrefixEnabled")
            f.sub.add(InputSetting("Prefix", "",
                { FishSettings.modPrefix },
                { v -> FishSettings.modPrefix = if (v != null && v.length > 10) v.substring(0, 10) else v }))
            general.features.add(f)
        }
        run {
            val f = Feature("Inventory Buttons", InvButtonSettings::class.java, "enableInventoryButtons")
            f.sub.add(ButtonSetting("Edit Buttons", "Opens your inventory to add, move and edit buttons", "Edit",
                Runnable { fishmod.features.other.InvShortcutButtons.openEditMode() }))
            general.features.add(f)
        }
        run {
            val f = Feature("Inventory Equipment", InvButtonSettings::class.java, "showEquipment")
            f.sub.add(SubcategoryHeader("Necklace/cloak/belt/gloves left of your inventory. Open /equipment once to load them."))
            general.features.add(f)
        }
        run {
            general.features.add(Feature("Hide Inventory Potion Effects", fishmod.utils.config.values.VisualSettings::class.java, "hideEffectHud"))
        }
        run {
            val f = Feature("Wardrobe/Loadout Hotkeys", "wardrobeHotkeysEnabled")
            f.sub.add(ToggleSetting("Auto-Close GUI", "", "wardrobeHotkeysAutoClose"))
            f.sub.add(SubcategoryHeader("Click a slot, then press a key/mouse button (Esc unbinds)"))
            val slots = fishmod.utils.FishKeys.wardrobeSlots
            if (slots != null) {
                for (i in slots.indices) {
                    val idx = i
                    f.sub.add(KeybindSetting("Slot " + (idx + 1), "",
                        { fishmod.utils.FishKeys.wardrobeSlots?.getOrNull(idx) }))
                }
            }
            f.sub.add(SubcategoryHeader("Pagination"))
            f.sub.add(KeybindSetting("Next Page", "", { fishmod.utils.FishKeys.wardrobeNextPage }))
            f.sub.add(KeybindSetting("Previous Page", "", { fishmod.utils.FishKeys.wardrobePrevPage }))
            general.features.add(f)
        }
        run {
            val f = Feature("Pet Keybinds", "petKeybindsEnabled")
            f.sub.add(ToggleSetting("Auto-Close GUI", "", "petKeybindsAutoClose"))
            f.sub.add(ToggleSetting("Don't Despawn Active Pet", "Pressing the key for your current pet does nothing", "petKeybindsNoDespawn"))
            f.sub.add(SubcategoryHeader("Open /pets yourself, then press a key to select that pet"))
            for (i in 0 until fishmod.features.other.PetKeybinds.COUNT) {
                val idx = i
                f.sub.add(KeybindSetting(fishmod.features.other.PetKeybinds.PETS[idx], "", { fishmod.utils.FishKeys.petKeybinds?.getOrNull(idx) }))
            }
            general.features.add(f)
        }
        run {
            val f = Feature("Slot Binds", "slotBindsEnabled")
            f.sub.add(SubcategoryHeader("Hold the bind key + click a hotbar slot then an inv slot to link; shift-left-click to swap"))
            f.sub.add(KeybindSetting("Bind Key (hold)", "Default R", { fishmod.utils.FishKeys.slotBind }))
            f.sub.add(SubcategoryHeader("Profiles — separate bind sets you can switch between"))
            val slotBindProfiles: Array<String> = fishmod.features.SlotBinds.profileNames().toTypedArray()
            f.sub.add(DropdownSetting("Profile", "", slotBindProfiles,
                { if (FishSettings.slotBindsProfile in slotBindProfiles) FishSettings.slotBindsProfile else slotBindProfiles[0] },
                { v -> FishSettings.slotBindsProfile = v }))
            f.sub.add(ButtonSetting("New Profile", "Fresh empty set", Runnable { fishmod.features.SlotBinds.newProfile() }))
            f.sub.add(ButtonSetting("Delete Profile", "Remove the current set (Default is only cleared)", Runnable { fishmod.features.SlotBinds.deleteActiveProfile() }))
            f.sub.add(KeybindSetting("Cycle Profile Key", "", { fishmod.utils.FishKeys.slotBindCycleProfile }))
            f.sub.add(ToggleSetting("Show Bound Slots", "", "slotBindsShow"))
            f.sub.add(ToggleSetting("Connecting Line", "Draw a line between a bound pair", "slotBindsLine").gatedBy { FishSettings.slotBindsShow })
            f.sub.add(ToggleSetting("Slot Border", "Outline each bound slot", "slotBindsBorder").gatedBy { FishSettings.slotBindsShow })
            f.sub.add(ToggleSetting("Hover Only", "Only show a link when hovering one of its slots", "slotBindsHoverOnly").gatedBy { FishSettings.slotBindsShow })
            f.sub.add(ColorPickerSetting("Colour", "", "slotBindsColor").gatedBy { FishSettings.slotBindsShow })
            general.features.add(f)
        }
        run {
            val f = Feature("Slot Locking", "slotLockingEnabled")
            f.sub.add(SubcategoryHeader("Hover an inventory slot and press the key to lock/unlock it. Locked slots can't be dropped (Q, Ctrl+Q or clicking outside the window)"))
            f.sub.add(KeybindSetting("Lock Key", "Default L", { fishmod.utils.FishKeys.slotLock }))
            f.sub.add(ColorPickerSetting("Colour", "", "slotLockingColor"))
            f.sub.add(SliderIntSetting("Tint Opacity", "How strongly locked slots are filled (%)", "slotLockingOpacity", 0, 100, 5))
            f.sub.add(ToggleSetting("Outline", "Border around locked slots", "slotLockingOutline"))
            general.features.add(f)
        }
        run {
            val f = Feature("Inventory Search", "inventorySearchEnabled")
            f.sub.add(SubcategoryHeader("Ctrl+F in any container to search names + lore; non-matches are dimmed. Math works too (e.g. 64*9)"))
            f.sub.add(ToggleSetting("Always Show Bar", "Show the bar without pressing Ctrl+F", "inventorySearchAlwaysShow"))
            f.sub.add(ToggleSetting("Outline Matches", "", "inventorySearchHighlight"))
            f.sub.add(ColorPickerSetting("Outline Colour", "", "inventorySearchHighlightColor").gatedBy { FishSettings.inventorySearchHighlight })
            general.features.add(f)
        }
        run {
            val f = Feature("Zoom", "zoomEnabled")
            f.sub.add(KeybindSetting("Zoom Key (hold)", "", { fishmod.utils.FishKeys.zoom }))
            f.sub.add(SliderDoubleSetting("Zoom Amount", "How far the key zooms in (x times)", { FishSettings.zoomFactor }, { FishSettings.zoomFactor = it }, 1.5, 10.0))
            f.sub.add(ToggleSetting("Scroll to Zoom", "Scroll while holding the key to zoom further in or out", "zoomScroll"))
            f.sub.add(ToggleSetting("Smooth Zoom", "Animate the zoom instead of snapping", "zoomSmoothAnim"))
            f.sub.add(ToggleSetting("Smooth Camera", "Cinematic mouse movement while zoomed", "zoomSmoothCamera"))
            general.features.add(f)
        }
        run {
            val f = Feature("Chat", "chatFeatureEnabled")
            f.sub.add(ToggleSetting("Smart Copy Chat", "", "smartCopyChat"))
            f.sub.add(ToggleSetting("Compact Chat", "Collapse identical messages within the last minute into one \"(N)\" line", "chatCompact"))
            f.sub.add(SubcategoryHeader("Infinite Chat History"))
            f.sub.add(ToggleSetting("Infinite Chat History", "", "infiniteChatHistory"))
            f.sub.add(SliderIntSetting("Max Lines", "Scrollback + sent-message history kept (vanilla is 100)",
                "infiniteChatHistoryLimit", 500, 20000, 500).gatedBy { FishSettings.infiniteChatHistory })
            f.sub.add(SubcategoryHeader("Chat Search"))
            f.sub.add(ToggleSetting("Chat Search", "", "chatSearch"))
            f.sub.add(SubcategoryHeader("Bind \"Toggle Chat Search\" in Options → Controls; press it while chat is open to show the search field")
                .gatedBy { FishSettings.chatSearch })
            f.sub.add(SubcategoryHeader("Chat Peek"))
            f.sub.add(ToggleSetting("Chat Peek", "Hold the bound key to pull up chat fully opaque and scroll through it, without opening the chat box", "chatPeek"))
            f.sub.add(KeybindSetting("Peek Key (hold)", "Unbound by default", { fishmod.utils.FishKeys.chatPeek }).gatedBy { FishSettings.chatPeek })
            general.features.add(f)
        }
        run {
            val f = Feature("Chat Filter", "chatFilterEnabled")
            f.sub.add(SubcategoryHeader("Hides lines from your chat only; they still go to the log and other mods"))
            f.sub.add(ToggleSetting("Common Hypixel Spam", "Most frequent useless lines from real logs (event rewards, stash, class perks, leaps...)", "cfCommonSpam"))
            f.sub.add(ToggleSetting("  Event Rewards", "", "cfSpamEventRewards").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Radio", "", "cfSpamRadio").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Potions", "", "cfSpamPotions").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Class Milestones & Abilities", "", "cfSpamClassAbilities").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Equip & Autopet", "", "cfSpamEquip").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Sacks & Stash", "", "cfSpamSacksStash").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Hype & Tips", "", "cfSpamHype").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Hoppity Eggs", "", "cfSpamHoppity").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Dungeon Progress (keys, crystals, devices, leaps)", "", "cfSpamDungeonProgress").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Abiphone Calls", "", "cfSpamAbiphone").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Launch Pad", "", "cfSpamLaunchPad").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Loot Share", "", "cfSpamLootShare").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("  Other Common Spam", "", "cfSpamMisc").gatedBy { FishSettings.cfCommonSpam })
            f.sub.add(ToggleSetting("Skyblock/Dungeon Spam", "NoammAddons' full useless-message list", "cfNoammSpam"))
            f.sub.add(ToggleSetting("Kill Combo", "", "cfKillCombo"))
            f.sub.add(ToggleSetting("Boss Messages", "", "cfBossMessages"))
            f.sub.add(ToggleSetting("Friend Join/Leave", "", "cfFriendJoinLeave"))
            f.sub.add(ToggleSetting("Guild Message Of The Day", "Hides the guild MOTD box shown when you join", "cfGuildMotd"))
            f.sub.add(ToggleSetting("Fishing Trophy Catches", "Hides the TROPHY FISH/FROG catch line for the rarities ticked below", "cfTrophyFish"))
            f.sub.add(ToggleSetting("  Bronze", "", "cfTrophyBronze").gatedBy { FishSettings.cfTrophyFish })
            f.sub.add(ToggleSetting("  Silver", "", "cfTrophySilver").gatedBy { FishSettings.cfTrophyFish })
            f.sub.add(ToggleSetting("  Gold", "", "cfTrophyGold").gatedBy { FishSettings.cfTrophyFish })
            f.sub.add(ToggleSetting("  Diamond", "", "cfTrophyDiamond").gatedBy { FishSettings.cfTrophyFish })
            f.sub.add(ToggleSetting("Bazaar", "", "cfBazaar"))
            f.sub.add(ToggleSetting("Warping", "", "cfWarping"))
            f.sub.add(ToggleSetting("Collapse Blank Lines", "Drop repeated empty chat lines", "cfCollapseBlank"))
            f.sub.add(ToggleSetting("Custom Messages", "Hide the messages listed below", "cfCustom"))
            f.sub.add(ButtonSetting("Edit Messages", "Add, test and import messages to hide (partial, exact or regex)", "Open",
                Runnable { Minecraft.getInstance().showScreen(ChatCommandsScreen(ChatCommandsScreen.Tab.FILTER, Minecraft.getInstance().curScreen)) }))
            general.features.add(f)
        }
        run {
            val f = Feature("No Cursor Reset", "noCursorReset")
            f.sub.add(SliderIntSetting("Unhook Timeout (ms)", "Window after a GUI opens where the cursor is kept in place",
                "noCursorResetMs", 0, 1000, 10))
            general.features.add(f)
        }
        general.features.add(Feature("Arrow Fix (shortbow pullback)", "arrowFixEnabled"))
        general.features.add(Feature("Mono Audio", "monoAudioEnabled"))
        general.features.add(Feature("Sword Blocking", "swordBlockingEnabled"))
        general.features.add(Feature("Disable Front-Facing Camera", "disableFrontFacingCamera"))
        run {
            val f = Feature("Ragnarock", "ragnarockEnabled")
            f.sub.add(ToggleSetting("Cast Alert", "Title when you start casting", "ragnarockCastAlert"))
            f.sub.add(ToggleSetting("Cancelled Alert", "Title when a cast is interrupted", "ragnarockCancelAlert"))
            f.sub.add(ToggleSetting("Announce Cast to Party", "", "ragnarockAnnounceParty"))
            f.sub.add(ToggleSetting("Strength Gained Notification", "Show the Strength gained on screen when you cast", "ragnarockStrengthTitle"))
            f.sub.add(ToggleSetting("Strength Gained in Party Chat", "Send the Strength gained to party chat when you cast", "ragnarockStrengthParty"))
            f.sub.add(ToggleSetting("Strength Timer", "Moveable HUD countdown of the 10s Ragnarock buff (edit position in the HUD editor)", "ragnarockTimer"))
            f.sub.add(ToggleSetting("P5 Rag", "Title \"Rag\" when Wither King's pre-fight taunt appears", "p5RagEnabled"))
            general.features.add(f)
        }
        run {
            val f = Feature("Animations", "animEnabled")
            f.sub.add(SubcategoryHeader("First-person hand view-model"))
            f.sub.add(SliderDoubleSetting("Item Scale", "0 = normal", "animItemScale", -1.5, 1.5))
            f.sub.add(SliderDoubleSetting("Pos X", "", "animX", -2.0, 2.0))
            f.sub.add(SliderDoubleSetting("Pos Y", "", "animY", -2.0, 2.0))
            f.sub.add(SliderDoubleSetting("Pos Z", "", "animZ", -2.0, 2.0))
            f.sub.add(SliderDoubleSetting("Rot X", "degrees", "animRotX", -50.0, 50.0))
            f.sub.add(SliderDoubleSetting("Rot Y", "degrees", "animRotY", -50.0, 50.0))
            f.sub.add(SliderDoubleSetting("Rot Z", "degrees", "animRotZ", -50.0, 50.0))
            f.sub.add(SliderDoubleSetting("Swing X", "1 = normal", "animSwingX", 0.0, 2.0))
            f.sub.add(SliderDoubleSetting("Swing Y", "1 = normal", "animSwingY", 0.0, 2.0))
            f.sub.add(SliderDoubleSetting("Swing Z", "1 = normal", "animSwingZ", 0.0, 2.0))
            f.sub.add(SliderDoubleSetting("Swing Speed", "0 normal · +5 fast · -5 slow", "animSwingSpeed", -5.0, 5.0))
            f.sub.add(ToggleSetting("Ignore Haste", "Swing speed isn't affected by Haste", "animIgnoreHaste"))
            f.sub.add(ToggleSetting("No Equip Animation", "", "animNoEquip"))
            f.sub.add(ToggleSetting("No Hand Movement", "Stop the item bobbing when you look around", "animNoHandMove"))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Etherwarp Helper", "etherwarpHelperEnabled")
            f.sub.add(SubcategoryHeader("Sneak + hold an AOTV-type item to see the landing guess"))
            f.sub.add(ToggleSetting("Show Guess Box", "", "etherwarpShowGuess"))
            f.sub.add(ColorPickerSetting("Box Color", "", "etherwarpColor").gatedBy { FishSettings.etherwarpShowGuess })
            f.sub.add(ToggleSetting("Show When Failed", "", "etherwarpShowFail"))
            f.sub.add(ColorPickerSetting("Failed Color", "", "etherwarpFailColor").gatedBy { FishSettings.etherwarpShowFail })
            f.sub.add(ToggleSetting("Full Block", "Box the whole block, not its shape", "etherwarpFullBlock"))
            f.sub.add(ToggleSetting("Through Walls", "", "etherwarpDepth"))
            f.sub.add(SliderIntSetting("Range", "Blocks", "etherwarpRange", 1, 61))
            f.sub.add(ToggleSetting("Cast Sound", "", "etherwarpSoundEnabled"))
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.etherwarpSoundName }, { v -> FishSettings.etherwarpSoundName = v },
                { FishSettings.etherwarpSoundVolume }, { FishSettings.etherwarpSoundPitch }).gatedBy { FishSettings.etherwarpSoundEnabled })
            f.sub.add(SliderIntSetting("Sound Volume %", "Above 100 = louder (stacked plays)", "etherwarpSoundVolume", 0, 500, 10).gatedBy { FishSettings.etherwarpSoundEnabled })
            f.sub.add(SliderDoubleSetting("Sound Pitch", "", "etherwarpSoundPitch", 0.5, 2.0).gatedBy { FishSettings.etherwarpSoundEnabled })
            visuals.features.add(f)
        }
        run {
            val f = Feature("Lava To Water", "lavaToWaterEnabled")
            f.sub.add(ToggleSetting("Custom Tint", "", "lavaToWaterTint"))
            f.sub.add(ColorPickerSetting("Tint Color", "", "lavaToWaterColor").gatedBy { FishSettings.lavaToWaterTint })
            f.sub.add(ToggleSetting("Hide Lava Fog", "", "lavaToWaterHideFog"))
            visuals.features.add(f)
        }
        visuals.features.add(Feature("No Fog", "noFogEnabled"))
        run {
            val f = Feature("Corpse Highlight", "corpseHighlightEnabled")
            f.sub.add(SubcategoryHeader("Boxes Glacite Mineshaft corpses through walls"))
            f.sub.add(ColorPickerSetting("Colour", "", "corpseHighlightColor"))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Ghost Box", "ghostBoxEnabled")
            f.sub.add(ToggleSetting("Hide Ghost Model", "", "ghostBoxHideModel"))
            f.sub.add(ColorPickerSetting("Colour", "", "ghostBoxColor"))
            f.sub.add(ToggleSetting("Filled", "", "ghostBoxFilled"))
            f.sub.add(ColorPickerSetting("Fill Colour", "", "ghostBoxFillColor").gatedBy { FishSettings.ghostBoxFilled })
            visuals.features.add(f)
        }
        run {
            val f = Feature("Custom Water Colour",
                { FishSettings.customWaterEnabled },
                { v -> FishSettings.customWaterEnabled = v; net.minecraft.client.Minecraft.getInstance().reloadChunks() })
            f.sub.add(ColorPickerSetting("Water Colour", "",
                { FishSettings.customWaterColor },
                { v -> FishSettings.customWaterColor = v; net.minecraft.client.Minecraft.getInstance().reloadChunks() }))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Storage Overlay", "storageOverlayEnabled")
            f.sub.add(SubcategoryHeader("All pages + search over /storage; also /storageview"))
            f.sub.add(SliderIntSetting("Columns (width)", "", "storageViewerColumns", 1, 10))
            f.sub.add(SliderIntSetting("Max Height", "", "storageMaxHeight", 80, 900))
            f.sub.add(SliderDoubleSetting("Scale", "Overlay size", "storageOverlayScale", 0.5, 2.0))
            f.sub.add(SliderIntSetting("Scroll Speed", "", "storageScrollSpeed", 1, 50))
            f.sub.add(ToggleSetting("Retain Scroll", "Keep scroll offset after closing", "storageRetainScroll"))
            f.sub.add(ToggleSetting("Keep Search", "Keep the search text when the overlay reopens (off also clears it when switching pages)", "storageKeepSearch"))
            f.sub.add(ToggleSetting("Autofocus Search", "Focus the search bar when the overlay opens", "storageAutoFocusSearch"))
            f.sub.add(ToggleSetting("Hide Non-Matching Pages","While searching", "storageHideNonMatching"))
            invStorage.features.add(f)
        }
        invStorage.features.add(Feature("Container Value", "containerValueEnabled"))
        run {
            val f = Feature("Guild Bridge Bot", "bridgeBotEnabled")
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
                { v -> twitchbridge.TwitchBridgeClient.config().prefix = v; saveTwitchConfig() }))
            f.sub.add(ToggleSetting("Twitch Name Colors", "Use each chatter's own name colour",
                { twitchbridge.TwitchBridgeClient.config().useTwitchColors },
                { v -> twitchbridge.TwitchBridgeClient.config().useTwitchColors = v; saveTwitchConfig() }))
            f.sub.add(ToggleSetting("Timestamps", "Prefix each line with local HH:mm",
                { twitchbridge.TwitchBridgeClient.config().showTimestamps },
                { v -> twitchbridge.TwitchBridgeClient.config().showTimestamps = v; saveTwitchConfig() }))
            f.sub.add(ToggleSetting("Sub / Raid Notices", "Also show sub/raid/announcement events",
                { twitchbridge.TwitchBridgeClient.config().showEvents },
                { v -> twitchbridge.TwitchBridgeClient.config().showEvents = v; saveTwitchConfig() }))
            f.sub.add(ToggleSetting("Auto-Connect on Launch", "Reconnect automatically each game start",
                { twitchbridge.TwitchBridgeClient.config().autoConnect },
                { v -> twitchbridge.TwitchBridgeClient.config().autoConnect = v; saveTwitchConfig() }))
            general.features.add(f)
        }
        run {
            val f = Feature("Auto Sprint", "autoSprintEnabled")
            f.sub.add(ToggleSetting("Dungeons Only", "", "autoSprintDungeonOnly"))
            general.features.add(f)
        }
        run {
            val f = Feature("Sound Manager", "soundMasterEnabled")
            f.sub.add(SliderIntSetting("Master Volume %", "Applied to every FishMod feature cue", "soundMasterVolume", 0, 100))
            general.features.add(f)
        }
        run {
            val f = Feature("Profile Viewer", "profileViewerEnabled")
            f.sub.add(LabelSetting("Opens with /pv <player> [page]", ""))
            f.sub.add(DropdownSetting("Theme", "Glass colours for the profile viewer", fishmod.pv.gui.PvTheme.NAMES,
                { FishSettings.pvTheme },
                { v -> FishSettings.pvTheme = v }))
            f.sub.add(ToggleSetting("Show Overflow Skill Levels", "Uncapped skill levels in the viewer; maxed bars stay gold but fill toward the next level", "pvOverflowSkills"))
            f.sub.add(ColorPickerSetting("Maxed Color", "Fill colour of maxed bars", fishmod.features.extras.ExtrasSettings::class.java, "pvMaxedColor"))
            f.sub.add(ToggleSetting("Maxed Chroma", "Maxed bars slide through every colour", fishmod.features.extras.ExtrasSettings::class.java, "pvMaxedRainbow"))
            f.sub.add(ToggleSetting("Click Suggestion For /ahs", "Clicking a missing pet / museum suggestion in FishMod screens runs /ahs <item>", fishmod.features.extras.ExtrasSettings::class.java, "suggestionClickAhs"))
            general.features.add(f)
        }
        run {
            val x = fishmod.features.extras.ExtrasSettings
            val f = Feature("Enchant Colors", x.javaClass, "enchantColors")
            f.sub.add(LabelSetting("Colours enchants by tier in item tooltips and /pv", ""))
            f.sub.add(ColorPickerSetting("Max Tier Color", "Enchant at its max level", x.javaClass, "enchantMaxColor").gatedBy { !x.enchantMaxChroma })
            f.sub.add(ToggleSetting("Max Tier Chroma", "Same rainbow as the profile viewer", x.javaClass, "enchantMaxChroma"))
            f.sub.add(ColorPickerSetting("Max-1 Tier Color", "One below max", x.javaClass, "enchantHighColor").gatedBy { !x.enchantHighChroma })
            f.sub.add(ToggleSetting("Max-1 Tier Chroma", "", x.javaClass, "enchantHighChroma"))
            f.sub.add(ColorPickerSetting("Ultimate Color", "Ultimate enchants at any level", x.javaClass, "enchantUltColor").gatedBy { !x.enchantUltChroma })
            f.sub.add(ToggleSetting("Ultimate Chroma", "", x.javaClass, "enchantUltChroma"))
            f.sub.add(ColorPickerSetting("Normal Color", "Every other enchant", x.javaClass, "enchantNormalColor").gatedBy { !x.enchantNormalChroma })
            f.sub.add(ToggleSetting("Normal Chroma", "", x.javaClass, "enchantNormalChroma"))
            f.sub.add(ToggleSetting("Bold Top Tiers", "Bold max and max-1", x.javaClass, "enchantBold"))
            general.features.add(f)
        }
        run {
            val f = Feature("Hide HUDs Under Tab", "hideHudsUnderTab")
            f.sub.add(LabelSetting("Hides any HUD touching the tab list while tab is held", ""))
            general.features.add(f)
        }
        run {
            val f = Feature("Compact Tab", "compactTabEnabled")
            f.sub.add(SliderIntSetting("Opacity %", "", "compactTabOpacity", 0, 100))
            f.sub.add(ToggleSetting("Stat Bar", "SERVER/TPS/FPS/PING strip", "compactTabStatBarEnabled"))
            f.sub.add(DropdownSetting("Stat Bar Position", "", arrayOf("TOP", "BOTTOM", "LEFT", "RIGHT"),
                { FishSettings.compactTabStatBarPosition },
                { v -> FishSettings.compactTabStatBarPosition = v }).gatedBy { FishSettings.compactTabStatBarEnabled })
            f.sub.add(DropdownSetting("Tab Sort", "", arrayOf(
                "Rank (Default)", "SB Level", "Name (Abc)", "Ironman/Bingo", "Random"),
                { FishSettings.compactTabSortMode },
                { v -> FishSettings.compactTabSortMode = v }))
            f.sub.add(ToggleSetting("Pin YT/Staff On Top", "Keep YouTube + staff ranks first when sorting by level/name", "compactTabPinStaff"))
            f.sub.add(SliderIntSetting("Tab Scale %", "", "compactTabScale", 40, 150, 5))
            f.sub.add(DropdownSetting("Bottom Line", "Stats = cookie / god pot / effects from the tab footer", arrayOf("Hypixel", "Stats", "Custom", "Off"),
                { FishSettings.compactTabFooterMode },
                { v -> FishSettings.compactTabFooterMode = v }))
            f.sub.add(InputSetting("Bottom Line Text", "& colours; {cookie} {godpot} {effects}",
                "compactTabFooterText").gatedBy { FishSettings.compactTabFooterMode == "Custom" })
            f.sub.add(ToggleSetting("Show Potion Effects", "Effects count on the bottom line", "compactTabShowEffects")
                .gatedBy { FishSettings.compactTabFooterMode == "Stats" || FishSettings.compactTabFooterMode == "Custom" })
            general.features.add(f)
        }
        run {
            val f = Feature("Custom Scoreboard", "customScoreboardEnabled")
            f.sub.add(SliderIntSetting("Opacity %", "", "customScoreboardOpacity", 0, 100))
            f.sub.add(SliderIntSetting("Corner Radius", "", "customScoreboardRadius", 0, 10))
            f.sub.add(DropdownSetting("Position", "Custom = HUD editor spot",
                arrayOf("Custom", "Top Left", "Middle Left", "Bottom Left", "Top Right", "Middle Right", "Bottom Right"),
                { FishSettings.customScoreboardAnchor },
                { v -> FishSettings.customScoreboardAnchor = v }))
            f.sub.add(ToggleSetting("Title", "SKYBLOCK title", "sbSectionTitle"))
            f.sub.add(ToggleSetting("Date", "11/02/26", "sbSectionHeader"))
            f.sub.add(ToggleSetting("IRL Time", "Real time next to the date", "sbHeaderIrlTime"))
            f.sub.add(ToggleSetting("Server", "m117DM, own line", "sbSectionServer"))
            f.sub.add(ToggleSetting("Skyblock Date", "", "sbSectionDate"))
            f.sub.add(ToggleSetting("Skyblock Time", "", "sbSectionTime"))
            f.sub.add(ToggleSetting("Location", "", "sbSectionLocation"))
            f.sub.add(ToggleSetting("Level", "Prestige colours if enabled", "sbSectionLevel"))
            f.sub.add(ToggleSetting("Purse", "", "sbSectionPurse"))
            f.sub.add(ToggleSetting("Bits", "", "sbSectionBits"))
            f.sub.add(ToggleSetting("Motes", "", "sbSectionMotes"))
            f.sub.add(ToggleSetting("Gems", "", "sbSectionGems"))
            f.sub.add(ToggleSetting("Other", "Other Hypixel lines", "sbSectionOther"))
            f.sub.add(ToggleSetting("Slayer", "Only while a quest is active", "sbSectionSlayer"))
            f.sub.add(ToggleSetting("Powder", "Mining islands only", "sbSectionPowder"))
            f.sub.add(ToggleSetting("Mayor", "", "sbSectionMayor"))
            f.sub.add(ToggleSetting("Party", "Only while in a party", "sbSectionParty"))
            f.sub.add(ToggleSetting("Footer", "", "sbSectionFooter"))
            f.sub.add(InputSetting("Footer Text", "& colours; blank = Hypixel's", "sbFooterText"))
            general.features.add(f)
        }
        dungeonTrackers.features.add(Feature("PB Pace", "pbPaceEnabled"))
        solvers.features.add(Feature("Puzzle Overlay", "showPuzzles"))
        run {
            val f = Feature("Puzzle Solvers", "puzzleSolversEnabled")
            f.sub.add(DropdownSetting("Box Style", "", arrayOf("Filled", "Outline", "Filled Outline"),
                { FishSettings.puzzleSolverStyle }, { v -> FishSettings.puzzleSolverStyle = v }))
            f.sub.add(SubcategoryHeader("Three Weirdos"))
            f.sub.add(ToggleSetting("Weirdos Solver", "", "weirdosSolver"))
            f.sub.add(ColorPickerSetting("Correct Color", "", "weirdosCorrectColor").gatedBy { FishSettings.weirdosSolver })
            f.sub.add(ColorPickerSetting("Wrong Color", "", "weirdosWrongColor").gatedBy { FishSettings.weirdosSolver })
            f.sub.add(SubcategoryHeader("Blaze"))
            f.sub.add(ToggleSetting("Blaze Solver", "", "blazeSolver"))
            f.sub.add(ColorPickerSetting("Next Blaze", "", "blazeFirstColor").gatedBy { FishSettings.blazeSolver })
            f.sub.add(ColorPickerSetting("Second Blaze", "", "blazeSecondColor").gatedBy { FishSettings.blazeSolver })
            f.sub.add(ToggleSetting("Show Third Blaze", "", "blazeThirdEnabled").gatedBy { FishSettings.blazeSolver })
            f.sub.add(ColorPickerSetting("Third Blaze", "", "blazeThirdColor").gatedBy { FishSettings.blazeSolver && FishSettings.blazeThirdEnabled })
            f.sub.add(ToggleSetting("Hide Blaze Mobs", "Hides the blaze mobs in the blaze puzzle room", "blazeHideMobs").gatedBy { FishSettings.blazeSolver })
            f.sub.add(ToggleSetting("Connecting Line", "", "blazeLine").gatedBy { FishSettings.blazeSolver })
            f.sub.add(SliderIntSetting("Line Count", "", "blazeLineCount", 1, 2).gatedBy { FishSettings.blazeSolver && FishSettings.blazeLine })
            f.sub.add(SubcategoryHeader("Quiz"))
            f.sub.add(ToggleSetting("Quiz Solver", "", "quizSolver"))
            f.sub.add(ColorPickerSetting("Quiz Color", "", "quizColor").gatedBy { FishSettings.quizSolver })
            f.sub.add(ToggleSetting("Quiz Timer HUD", "11s to the first question, then 5s after each answer (move in HUD editor)", "quizHudEnabled"))
            f.sub.add(SubcategoryHeader("Water Board"))
            f.sub.add(ToggleSetting("Water Solver", "", "waterSolver"))
            f.sub.add(ColorPickerSetting("Next Lever", "", "waterFirstColor").gatedBy { FishSettings.waterSolver })
            f.sub.add(ColorPickerSetting("Then Lever", "", "waterSecondColor").gatedBy { FishSettings.waterSolver })
            f.sub.add(SubcategoryHeader("Creeper Beams"))
            f.sub.add(ToggleSetting("Beams Solver", "", "beamsSolver"))
            f.sub.add(ToggleSetting("Beams Tracer", "", "beamsTracer").gatedBy { FishSettings.beamsSolver })
            f.sub.add(SubcategoryHeader("Teleport Maze"))
            f.sub.add(ToggleSetting("TP Maze Solver", "", "tpMazeSolver"))
            f.sub.add(ColorPickerSetting("Next Pad", "", "tpMazeNextColor").gatedBy { FishSettings.tpMazeSolver })
            f.sub.add(ColorPickerSetting("Visited Pad", "", "tpMazeVisitedColor").gatedBy { FishSettings.tpMazeSolver })
            f.sub.add(SubcategoryHeader("Tic Tac Toe"))
            f.sub.add(ToggleSetting("TTT Solver", "", "tttSolver"))
            f.sub.add(ColorPickerSetting("TTT Color", "", "tttColor").gatedBy { FishSettings.tttSolver })
            f.sub.add(ToggleSetting("Prevent Miss-Click", "Block right-clicking a non-optimal button", "tttPreventMissClick").gatedBy { FishSettings.tttSolver })
            f.sub.add(ToggleSetting("Prediction", "Show the guardian's reply + your prefire buttons", "tttPrediction").gatedBy { FishSettings.tttSolver })
            f.sub.add(ColorPickerSetting("Prefire Color", "", "tttPredictionColor").gatedBy { FishSettings.tttSolver && FishSettings.tttPrediction })
            f.sub.add(SubcategoryHeader("Boulder"))
            f.sub.add(ToggleSetting("Boulder Solver", "", "boulderSolver"))
            f.sub.add(ToggleSetting("Show All Clicks", "", "boulderShowAll").gatedBy { FishSettings.boulderSolver })
            f.sub.add(ColorPickerSetting("Boulder Color", "", "boulderColor").gatedBy { FishSettings.boulderSolver })
            f.sub.add(ToggleSetting("Boulder Through Walls", "See the click blocks through the boulders while in the room", "boulderThroughWalls").gatedBy { FishSettings.boulderSolver })
            f.sub.add(SubcategoryHeader("Ice Fill"))
            f.sub.add(ToggleSetting("Ice Fill Solver", "", "iceFillSolver"))
            f.sub.add(ToggleSetting("Optimized Patterns", "Use the harder/faster fill routes", "iceFillOptimized").gatedBy { FishSettings.iceFillSolver })
            f.sub.add(ColorPickerSetting("Ice Fill Color", "", "iceFillColor").gatedBy { FishSettings.iceFillSolver })
            solvers.features.add(f)
        }
        run {
            val f = Feature("Key & Room Timer",
                { DungeonSettings.keyPickupAlert || FishSettings.roomTimerEnabled || FishSettings.keyHighlight },
                { v -> DungeonSettings.keyPickupAlert = v; FishSettings.roomTimerEnabled = v; FishSettings.keyHighlight = v })
            f.sub.add(SubcategoryHeader("Key Pickup Alert"))
            f.sub.add(ToggleSetting("Key Pickup Alert", "", DungeonSettings::class.java, "keyPickupAlert"))
            f.sub.add(ToggleSetting("Title", "", "keyNotifierTitle").gatedBy { DungeonSettings.keyPickupAlert })
            f.sub.add(ToggleSetting("Chat", "", "keyNotifierChat").gatedBy { DungeonSettings.keyPickupAlert })
            f.sub.add(ToggleSetting("Sound", "", "keyNotifierSound").gatedBy { DungeonSettings.keyPickupAlert })
            f.sub.add(SliderIntSetting("Title Duration (ms)", "", "keyNotifierDurationMs", 500, 8000, 250)
                .gatedBy { DungeonSettings.keyPickupAlert && FishSettings.keyNotifierTitle })
            f.sub.add(SubcategoryHeader("Key Highlight"))
            f.sub.add(ToggleSetting("Key Highlight", "Box dropped Wither and Blood keys through walls", "keyHighlight"))
            f.sub.add(ToggleSetting("Tracer", "Line from your crosshair to the key", "keyHighlightTracer").gatedBy { FishSettings.keyHighlight })
            f.sub.add(ColorPickerSetting("Wither Key Color", "", "keyHighlightWitherColor").gatedBy { FishSettings.keyHighlight })
            f.sub.add(ColorPickerSetting("Blood Key Color", "", "keyHighlightBloodColor").gatedBy { FishSettings.keyHighlight })
            f.sub.add(SubcategoryHeader("Room Timer"))
            f.sub.add(ToggleSetting("Room Timer", "On-screen title when the room you're in clears / all its secrets are done", "roomTimerEnabled"))
            f.sub.add(ToggleSetting("\"Cleared\" Title", "Show when the room's mobs are done", "roomTimerClear").gatedBy { FishSettings.roomTimerEnabled })
            f.sub.add(ToggleSetting("\"Secrets Done\" Title", "Show when every secret in the room is done", "roomTimerSecrets").gatedBy { FishSettings.roomTimerEnabled })
            f.sub.add(ToggleSetting("Show Time", "Append the time it took, e.g. Cleared (12.3s)", "roomTimerShowTime").gatedBy { FishSettings.roomTimerEnabled })
            f.sub.add(ToggleSetting("Personal Bests", "Track & show the fastest clear / secrets per room", "roomTimerPb").gatedBy { FishSettings.roomTimerEnabled })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dungeon Breaker", "dungeonBreakerEnabled")
            f.sub.add(ToggleSetting("Charges HUD", "Odin-style Dungeonbreaker charges display", "dungeonBreakerHudEnabled"))
            f.sub.add(ToggleSetting("Dungeons Only", "Only show / play inside dungeons", "dungeonBreakerDungeonOnly"))
            f.sub.add(SubcategoryHeader("Break Sound"))
            f.sub.add(ToggleSetting("Break Sound", "Play a sound when a block you hit with the Dungeonbreaker breaks", "dungeonBreakerSoundEnabled"))
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.dungeonBreakerSoundName }, { v -> FishSettings.dungeonBreakerSoundName = v },
                { FishSettings.dungeonBreakerSoundVolume }, { FishSettings.dungeonBreakerSoundPitch }).gatedBy { FishSettings.dungeonBreakerSoundEnabled })
            f.sub.add(SliderIntSetting("Volume %", "Above 100 = louder (stacked plays)", "dungeonBreakerSoundVolume", 0, 500, 10).gatedBy { FishSettings.dungeonBreakerSoundEnabled })
            f.sub.add(SliderDoubleSetting("Pitch", "", "dungeonBreakerSoundPitch", 0.0, 2.0).gatedBy { FishSettings.dungeonBreakerSoundEnabled })
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Boss Health Numbers", DungeonSettings::class.java, "bossHpNumbers"))
        run {
            val f = Feature("Ice Spray Timer", "iceSprayTimerEnabled")
            f.sub.add(SubcategoryHeader("After your Ice Spray, one countdown above each group of frozen mobs"))
            f.sub.add(ColorPickerSetting("Color", "", "iceSprayColor"))
            f.sub.add(SliderDoubleSetting("Text Size", "", "iceSprayScale", 0.5, 4.0))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Player Highlight", "playerHighlightEnabled")
            f.sub.add(SubcategoryHeader("Outlines teammates in their class colour, through walls"))
            f.sub.add(SliderIntSetting("Range (blocks)", "", "playerHighlightRange", 5, 150, 5))
            f.sub.add(SubcategoryHeader("Class Colours (shared with the rest of the mod)"))
            f.sub.add(ColorPickerSetting("Archer", "", DungeonSettings::class.java, "roleColorArcher"))
            f.sub.add(ColorPickerSetting("Berserk", "", DungeonSettings::class.java, "roleColorBerserk"))
            f.sub.add(ColorPickerSetting("Healer", "", DungeonSettings::class.java, "roleColorHealer"))
            f.sub.add(ColorPickerSetting("Mage", "", DungeonSettings::class.java, "roleColorMage"))
            f.sub.add(ColorPickerSetting("Tank", "", DungeonSettings::class.java, "roleColorTank"))
            dungeon.features.add(f)
        }
        run {
            val wp = fishmod.features.dungeon.DungeonWaypoints
            val f = Feature("Waypoints", "dungeonWaypointsEnabled")
            f.sub.add(SubcategoryHeader("Master toggle for /fm wp — placed boxes, titles and route lines"))
            f.sub.add(ToggleSetting("Edit Mode", "Right-click to place/remove a waypoint where you're aiming", { wp.isEditMode() }, { wp.toggleEdit() }))
            f.sub.add(ToggleSetting("Fill", "Filled box vs outline for the next waypoint placed", { wp.isFill() }, { wp.setFill(it) }))
            f.sub.add(ToggleSetting("Use Block Size", "Match the aimed block's real shape instead of a fixed cube", { wp.isUseBlockSize() }, { wp.setUseBlockSize(it) }))
            f.sub.add(ToggleSetting("Pixel Mode", "1/16-block precision, snapped to the exact pixel you're aiming at", { wp.isPixelMode() }, { wp.setPixelMode(it) }))
            f.sub.add(ToggleSetting("Through Walls", "Render occluded, ignoring terrain in the way", { wp.isThrough() }, { wp.setThrough(it) }))
            f.sub.add(SliderDoubleSetting("Size", "Box size when Use Block Size is off", { wp.getSize() }, { wp.setSizeQuiet(it) }, 0.1, 1.0))
            f.sub.add(SliderIntSetting("Placement Distance", "Max raycast range for placement", { wp.getDistance() }, { wp.setDistanceQuiet(it) }, 1, 64))
            f.sub.add(SliderDoubleSetting("Line Width", "Outline thickness when Fill is off", { wp.getLineWidth() }, { wp.setLineWidthQuiet(it) }, 0.01, 0.5)
                .gatedBy { !wp.isFill() })
            f.sub.add(ColorPickerSetting("Color", "", { wp.getColorArgb() }, { wp.setColorArgb(it) }, ColorDefaults.of(FishSettings::class.java, "wpEditColor")))
            f.sub.add(DropdownSetting("Type", "Metadata tag for the next waypoint placed",
                fishmod.utils.dungeon.waypoints.WaypointType.values(),
                { wp.getType() }, { wp.setTypeEnum(it) }))
            f.sub.add(DropdownSetting("Timer Tag", "Speedrun-route tag for the next waypoint placed",
                fishmod.utils.dungeon.waypoints.TimerType.values(),
                { wp.getTimer() }, { wp.setTimerEnum(it) }))
            f.sub.add(SubcategoryHeader("Database"))
            f.sub.add(ButtonSetting("Export to Clipboard", "Copy this world's waypoint database as text", "Copy", Runnable { wp.exportToClipboard() }))
            f.sub.add(ButtonSetting("Import from Clipboard", "Replace with a previously exported database", "Paste", Runnable { wp.importFromClipboard() }))
            f.sub.add(ButtonSetting("Import Odin Config", "Add waypoints from Odin's packs in config/odin (keeps yours)", "Import", Runnable { fishmod.features.dungeon.OdinWaypointImport.importFromOdinConfig() }))
            f.sub.add(ButtonSetting("Import Odin Clipboard", "Add waypoints from an Odin /dwp export string (keeps yours)", "Paste", Runnable { fishmod.features.dungeon.OdinWaypointImport.importFromClipboard() }))
            f.sub.add(ButtonSetting("Clear Current Area", "Delete every waypoint in the room/zone you're in now", "Clear", Runnable { wp.resetCurrentArea() }))
            f.sub.add(SubcategoryHeader("Routes"))
            f.sub.add(ButtonSetting("Start/Stop Recording", "Tags each waypoint placed next, in order, as a route",
                { if (wp.isRecordingRoute()) "Stop" else "Start" }, Runnable { wp.toggleRoute(null) }))
            f.sub.add(ButtonSetting("Reset All Routes' Progress", "Makes every route's waypoints visible again this run", "Reset", Runnable { wp.endRoute(null) }))
            dungeon.features.add(f)
        }
        run {
            val wp = fishmod.features.dungeon.DungeonWaypoints
            val f = Feature("Positional Messages", "posMsgEnabled")
            f.sub.add(SubcategoryHeader("/fm pm to edit: right-click blocks to add/remove, shift+right-click to set the message. Stepping on them sends it to party chat"))
            f.sub.add(ToggleSetting("Edit Mode", "Right-click to place/remove a message block", { wp.isPmEditMode() }, { wp.togglePmEdit() }))
            f.sub.add(InputSetting("Message", "Sent for the next blocks you place", { wp.getPmMessage() }, { wp.setPmMessageQuiet(it) }))
            f.sub.add(ToggleSetting("Show Blocks", "Draw message blocks outside edit mode", "posMsgShowBlocks"))
            f.sub.add(ColorPickerSetting("Color", "For newly placed blocks", "posMsgColor"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Leap", "leapMenuEnabled")
            f.sub.add(SubcategoryHeader("Menu (custom 2x2 Spirit Leap GUI, click a cell or press 1-4)"))
            f.sub.add(ToggleSetting("Map View", "Show the dungeon map instead — click a teammate's head to leap (1-4 still work)", "leapMenuMap"))
            f.sub.add(ToggleSetting("Map View: Only After BR", "Map view stays off until the blood door is opened", "leapMenuMapAfterBR").gatedBy { FishSettings.leapMenuMap })
            f.sub.add(SliderIntSetting("Menu Scale %", "", "leapMenuScale", 40, 220))
            f.sub.add(ToggleSetting("Number Keybinds", "1-4 leap to that cell", "leapMenuKeybinds"))
            f.sub.add(ToggleSetting("Left-Click Only", "Ignore right/middle click", "leapMenuLeftClickOnly"))
            f.sub.add(ToggleSetting("Tint Dead Players", "", "leapMenuTintDead"))
            f.sub.add(ToggleSetting("Show Name", "", "leapMenuShowName"))
            f.sub.add(ToggleSetting("Show Class", "", "leapMenuShowClass"))
            val sortByOptions = arrayOf("Class Order", "Name A-Z", "Odin Sorting")
            f.sub.add(DropdownSetting("Sort By", "", sortByOptions,
                { sortByOptions.getOrElse(FishSettings.leapMenuSort) { sortByOptions[0] } },
                { v -> FishSettings.leapMenuSort = sortByOptions.indexOf(v).coerceAtLeast(0) }))
            f.sub.add(InputSetting("Class Order", "Comma-separated: MAGE,BERSERK,ARCHER,HEALER,TANK",
                { FishSettings.leapMenuClassOrder }, { v -> FishSettings.leapMenuClassOrder = v }).gatedBy { FishSettings.leapMenuSort == 0 })
            f.sub.add(SubcategoryHeader("Hide Nearby Players"))
            f.sub.add(ToggleSetting("Hide Players After Leap", "Hide other players for a moment after you leap", "leapHidePlayers"))
            f.sub.add(SliderDoubleSetting("Duration", "Seconds", "leapHidePlayersSeconds", 0.5, 5.0).gatedBy { FishSettings.leapHidePlayers })
            f.sub.add(SubcategoryHeader("Message"))
            f.sub.add(ToggleSetting("Leap Message", "", DungeonSettings::class.java, "leapAnnounce"))
            f.sub.add(LabelSetting("{name} target  {class} class", "{c} class letter  ·  & = colours")
                .gatedBy { DungeonSettings.leapAnnounce })
            f.sub.add(InputSetting("Message Text", "",
                { FishSettings.leapMessagesText }, { v -> FishSettings.leapMessagesText = v ?: "" })
                .gatedBy { DungeonSettings.leapAnnounce })
            f.sub.add(ToggleSetting("As Title", "", "leapMessagesTitle").gatedBy { DungeonSettings.leapAnnounce })
            f.sub.add(ToggleSetting("Send to Party", "Post the message in party chat", "leapMessagesParty").gatedBy { DungeonSettings.leapAnnounce })
            f.sub.add(ToggleSetting("Cue Sound", "", "leapMessagesSound").gatedBy { DungeonSettings.leapAnnounce })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Mimic", "mimicAnnounceEnabled")
            f.sub.add(SubcategoryHeader("Announces Mimic, Prince and Bat kills in party chat (F6/F7)"))
            f.sub.add(ToggleSetting("Send Mimic Message", "Toggles the mimic killed message.", "mimicMsgEnabled"))
            f.sub.add(InputSetting("Mimic Text", "", { FishSettings.mimicMsgText }, { v -> FishSettings.mimicMsgText = v ?: "" })
                .gatedBy { FishSettings.mimicMsgEnabled })
            f.sub.add(ButtonSetting("Mimic Killed", "Send the mimic message now.", Runnable { fishmod.features.dungeon.MimicAnnounce.mimicKilled(true) }))
            f.sub.add(ToggleSetting("Send Prince Message", "Toggles the prince killed message.", "princeMsgEnabled"))
            f.sub.add(InputSetting("Prince Text", "", { FishSettings.princeMsgText }, { v -> FishSettings.princeMsgText = v ?: "" })
                .gatedBy { FishSettings.princeMsgEnabled })
            f.sub.add(ButtonSetting("Prince Killed", "Send the prince message now.", Runnable { fishmod.features.dungeon.MimicAnnounce.princeKilled(true) }))
            f.sub.add(ToggleSetting("Send Bat Message", "Toggles the bat killed message.", "batMsgEnabled"))
            f.sub.add(InputSetting("Bat Text", "", { FishSettings.batMsgText }, { v -> FishSettings.batMsgText = v ?: "" })
                .gatedBy { FishSettings.batMsgEnabled })
            f.sub.add(ButtonSetting("Bat Killed", "Send the bat message now.", Runnable { fishmod.features.dungeon.MimicAnnounce.batKilled(true) }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Extra Stats", "extraStatsEnabled")
            f.sub.add(SubcategoryHeader("Replaces Hypixel's post-run stats block with a tidy summary"))
            f.sub.add(ToggleSetting("Show Bits", "", "extraStatsBits"))
            f.sub.add(ToggleSetting("Show Class EXP", "", "extraStatsClassExp"))
            f.sub.add(ToggleSetting("Show Combat Stats", "Damage / kills / healing", "extraStatsCombat"))
            f.sub.add(ToggleSetting("Show Teammates", "", "extraStatsTeammates"))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Terracotta Timer", "terracottaTimerEnabled"))
        run {
            val f = Feature("Spirit Bear", "spiritBearEnabled")
            f.sub.add(SubcategoryHeader("F4/M4 (Thorn): kills-to-spawn count, then spawn timer, then Alive — movable HUD"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Livid Solver", "lividSolverEnabled")
            f.sub.add(SubcategoryHeader("Boxes the real Livid in the F5/M5 boss (reads the wool colour)"))
            f.sub.add(ColorPickerSetting("Box Color", "", "lividSolverColor"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Architect Draft Refill", "architectDraftRefill")
            f.sub.add(SubcategoryHeader("Auto /gfs a First Draft after a puzzle fail"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dungeon Abilities", "dungeonAbilitiesEnabled")
            f.sub.add(SubcategoryHeader("Ult = tap-drop (one item) · Mini Ult = ctrl-drop (whole stack)"))
            f.sub.add(KeybindSetting("Ult (drop)", "", { fishmod.utils.FishKeys.dungeonAbility }))
            f.sub.add(KeybindSetting("Mini Ult (ctrl+drop)", "", { fishmod.utils.FishKeys.dungeonAbilityMini }))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Secret Overlay", "secretOverlayEnabled").also {
            it.sub.add(SubcategoryHeader("Secrets found in your current room on screen - move it in the HUD editor"))
        })
        run {
            val f = Feature("Blessing Display", "blessingDisplayEnabled")
            f.sub.add(ToggleSetting("Power", "", "blessingPower"))
            f.sub.add(ColorPickerSetting("Power Color", "", "blessingPowerColor").gatedBy { FishSettings.blessingPower })
            f.sub.add(ToggleSetting("Time", "", "blessingTime"))
            f.sub.add(ColorPickerSetting("Time Color", "", "blessingTimeColor").gatedBy { FishSettings.blessingTime })
            f.sub.add(ToggleSetting("Stone", "", "blessingStone"))
            f.sub.add(ColorPickerSetting("Stone Color", "", "blessingStoneColor").gatedBy { FishSettings.blessingStone })
            f.sub.add(ToggleSetting("Life", "", "blessingLife"))
            f.sub.add(ColorPickerSetting("Life Color", "", "blessingLifeColor").gatedBy { FishSettings.blessingLife })
            f.sub.add(ToggleSetting("Wisdom", "", "blessingWisdom"))
            f.sub.add(ColorPickerSetting("Wisdom Color", "", "blessingWisdomColor").gatedBy { FishSettings.blessingWisdom })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Mask & Phoenix Timer", DungeonSettings::class.java, "invincibilityTimerHud")
            f.sub.add(ToggleSetting("Announce Proc to Party", "", "invincAnnounce"))
            f.sub.add(ToggleSetting("Show Numeric Time", "X.Xs vs a dot", DungeonSettings::class.java, "invincibilityHud"))
            f.sub.add(ToggleSetting("State Colors", "Gold active / red cooldown / green ready", DungeonSettings::class.java, "invincibilityStatusColor"))
            f.sub.add(ToggleSetting("Mask Cooldown Bar", "Durability-style bar on the mask item", "invincShowCooldown"))
            f.sub.add(DropdownSetting("Show", "", arrayOf("Always", "Any", "Active", "Cooldown"),
                { FishSettings.invincShowWhen }, { v -> FishSettings.invincShowWhen = v }))
            f.sub.add(ToggleSetting("Only In Boss", "", "invincShowInBoss"))
            f.sub.add(ToggleSetting("Spirit Mask", "", "invincShowSpirit"))
            f.sub.add(ToggleSetting("Bonzo Mask", "", "invincShowBonzo"))
            f.sub.add(ToggleSetting("Phoenix Pet", "", "invincShowPhoenix"))
            f.sub.add(ToggleSetting("Show Icons", "Item icon instead of the name (learned once you've had the mask on you / opened /pets)", "invincIcons"))
            f.sub.add(SubcategoryHeader("Proc Title"))
            f.sub.add(ToggleSetting("On-Screen Title", "Big title when a mask / Phoenix procs", "invincProcTitle"))
            f.sub.add(SliderIntSetting("Title Duration (ms)", "", "invincProcTitleMs", 500, 6000, 250).gatedBy { FishSettings.invincProcTitle })
            f.sub.add(InputSetting("Spirit Message", "", "invincProcSpiritText").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(ColorPickerSetting("Spirit Color", "", "invincProcSpiritColor").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(InputSetting("Bonzo Message", "", "invincProcBonzoText").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(ColorPickerSetting("Bonzo Color", "", "invincProcBonzoColor").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(InputSetting("Phoenix Message", "", "invincProcPhoenixText").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(ColorPickerSetting("Phoenix Color", "", "invincProcPhoenixColor").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(ToggleSetting("Proc Sound", "", "invincProcSound").gatedBy { FishSettings.invincProcTitle })
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.invincProcSoundName }, { v -> FishSettings.invincProcSoundName = v },
                { FishSettings.invincProcVolume }, { FishSettings.invincProcPitch }).gatedBy { FishSettings.invincProcTitle && FishSettings.invincProcSound })
            f.sub.add(SliderIntSetting("Volume %", "", "invincProcVolume", 0, 500, 10).gatedBy { FishSettings.invincProcTitle && FishSettings.invincProcSound })
            f.sub.add(SliderDoubleSetting("Pitch", "", "invincProcPitch", 0.0, 2.0).gatedBy { FishSettings.invincProcTitle && FishSettings.invincProcSound })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dungeon Routes", "routeRecorderEnabled")
            f.sub.add(LabelSetting("/fm route record · stop · play", "skip · back · undo · clear · save/load <name> · list"))
            f.sub.add(ToggleSetting("Auto Load Per Room", "Saved route plays when you enter its room", "routeAutoLoad"))
            f.sub.add(ToggleSetting("Waypoints Through Walls", "Etherwarp, pearl, break, superboom", "routeThroughWalls"))
            f.sub.add(ToggleSetting("Secrets Through Walls", "Chest, lever/skull, item, bat", "routeSecretsThroughWalls"))
            f.sub.add(DropdownSetting("Box Style", "", arrayOf("Filled", "Outline", "Filled Outline"),
                { FishSettings.routeBoxStyle }, { v -> FishSettings.routeBoxStyle = v }))
            f.sub.add(SliderIntSetting("Fill Opacity %", "", "routeFillOpacity", 0, 100, 5).gatedBy { FishSettings.routeBoxStyle != "Outline" })
            f.sub.add(SliderIntSetting("Outline Opacity %", "", "routeOutlineOpacity", 0, 100, 5).gatedBy { FishSettings.routeBoxStyle != "Filled" })
            f.sub.add(SliderDoubleSetting("Outline Width", "", "routeOutlineWidth", 0.5, 10.0).gatedBy { FishSettings.routeBoxStyle != "Filled" })
            f.sub.add(ToggleSetting("Highlight Next Step", "Brighter box on the step you're on", "routeHighlightCurrent"))
            f.sub.add(ToggleSetting("Route Lines", "Line from each step to the next", "routeShowLines"))
            f.sub.add(SliderDoubleSetting("Line Width", "", "routeLineWidth", 0.5, 10.0).gatedBy { FishSettings.routeShowLines })
            f.sub.add(SliderIntSetting("Line Opacity %", "", "routeLineOpacity", 0, 100, 5).gatedBy { FishSettings.routeShowLines })
            f.sub.add(ToggleSetting("Line To Next Step", "Tracer from you while playing", "routeLineToNext"))
            f.sub.add(ToggleSetting("Labels", "", "routeShowLabels"))
            f.sub.add(SliderDoubleSetting("Label Scale", "", "routeLabelScale", 0.5, 3.0).gatedBy { FishSettings.routeShowLabels })
            f.sub.add(ColorPickerSetting("Etherwarp", "", "routeColorEtherwarp"))
            f.sub.add(ColorPickerSetting("Ender Pearl", "", "routeColorPearl"))
            f.sub.add(ColorPickerSetting("Dungeonbreaker", "", "routeColorBreak"))
            f.sub.add(ColorPickerSetting("Superboom", "", "routeColorSuperboom"))
            f.sub.add(ColorPickerSetting("Chest", "", "routeColorChest"))
            f.sub.add(ColorPickerSetting("Lever / Skull", "", "routeColorSecret"))
            f.sub.add(ColorPickerSetting("Item Pickup", "", "routeColorItem"))
            f.sub.add(ColorPickerSetting("Bat", "", "routeColorBat"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Secret Clicked", "secretClickedEnabled")
            f.sub.add(ToggleSetting("Boxes", "", "secretClickedBoxes"))
            f.sub.add(ToggleSetting("Bat Secrets", "Count a killed secret bat you were next to", "secretClickedBats"))
            f.sub.add(ToggleSetting("Item Secrets", "Count a ground item you walked over", "secretClickedItems"))
            f.sub.add(DropdownSetting("Box Style", "", arrayOf("Filled", "Outline", "Filled Outline"),
                { FishSettings.secretClickedStyle }, { v -> FishSettings.secretClickedStyle = v }).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ColorPickerSetting("Color", "", "secretClickedColor").gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ColorPickerSetting("Locked Color", "", "secretClickedLockedColor").gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(SliderDoubleSetting("Line Width", "", "secretClickedLineWidth", 0.5, 10.0).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(SliderIntSetting("Time To Stay (s)", "", "secretClickedTimeToStay", 1, 20).gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ToggleSetting("Through Walls", "", "secretClickedDepthCheck").gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ToggleSetting("Box In Boss", "", "secretClickedInBoss").gatedBy { FishSettings.secretClickedBoxes })
            f.sub.add(ToggleSetting("Chime", "Sound on secret click", "secretClickedChime"))
            f.sub.add(ToggleSetting("Chime In Boss", "", "secretClickedChimeInBoss").gatedBy { FishSettings.secretClickedChime })
            f.sub.add(SoundSearchSetting("Chime Sound", "Type to search every game sound",
                { FishSettings.secretClickedSoundName }, { v -> FishSettings.secretClickedSoundName = v },
                { FishSettings.secretClickedVolume }, { FishSettings.secretClickedPitch }).gatedBy { FishSettings.secretClickedChime })
            f.sub.add(SliderIntSetting("Chime Volume %", "Above 100 = louder", "secretClickedVolume", 0, 500, 10).gatedBy { FishSettings.secretClickedChime })
            f.sub.add(SliderDoubleSetting("Chime Pitch", "", "secretClickedPitch", 0.0, 2.0).gatedBy { FishSettings.secretClickedChime })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Auto Requeue", DungeonSettings::class.java, "autoRequeueEnabled")
            f.sub.add(SliderDoubleSetting("Delay (s)", "After the \"> EXTRA STATS <\" line",
                { FishSettings.autoRequeueDelayMs / 1000.0 },
                { v -> FishSettings.autoRequeueDelayMs = (v * 1000).toInt() }, 0.0, 10.0))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Party Warp Timer", DungeonSettings::class.java, "partyWarpTimer")
            f.sub.add(SliderIntSetting("Cooldown (s)", "Hypixel's real gate is 30s", "warpCooldownSeconds", 1, 120))
            f.sub.add(ColorPickerSetting("Timer Color", "", "warpCooldownColor"))
            f.sub.add(ToggleSetting("Announce Kick", "Post to party chat if you get kicked mid-join", "warpAnnounceKick"))
            f.sub.add(InputSetting("Kick Text", "", { FishSettings.warpKickText }, { v -> FishSettings.warpKickText = v ?: "" })
                .gatedBy { FishSettings.warpAnnounceKick })
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Death Message", "deathMessageEnabled")
            val tmpl = InputSetting("Template", "", "deathMessageTemplate")
            tmpl.hint = "{name} = player who died"
            f.sub.add(tmpl)
            f.sub.add(ToggleSetting("To Party", "", "deathMessageToParty"))
            dungeon.features.add(f)
        }
        dungeon.features.add(Feature("Send Lag to Party", "sendLagToParty"))
        run {
            val f = Feature("Run Splits", BossPhase::class.java, "runSplitsEnabled")
            f.sub.add(ToggleSetting("Total Time", "", BossPhase::class.java, "runSplitsTotal"))
            f.sub.add(ToggleSetting("Send in Chat", "", BossPhase::class.java, "runSplitsToChat"))
            f.sub.add(DropdownSetting("Clock Source", "",
                RunSplit.ClockKind.values(), { RunSplit.splitClock }, { v -> RunSplit.splitClock = v }))
            f.sub.add(ToggleSetting("Activated Only", "", BossPhase::class.java, "runSplitsReachedOnly"))
            f.sub.add(ToggleSetting("PB Colors", "Finished split time: pink = new PB, orange = faster than your average", "splitPbColors"))
            f.sub.add(SubcategoryHeader("Time Colors"))
            f.sub.add(ColorPickerSetting("New PB", "", "splitPbColor").gatedBy { FishSettings.splitPbColors })
            f.sub.add(ColorPickerSetting("Faster Than Avg", "", "splitAvgColor").gatedBy { FishSettings.splitPbColors })
            f.sub.add(ColorPickerSetting("Not Started", "", RunSplit::class.java, "splitRealIdle"))
            f.sub.add(ColorPickerSetting("Running", "", RunSplit::class.java, "splitRealLive"))
            f.sub.add(ColorPickerSetting("Finished", "", RunSplit::class.java, "splitRealDone"))
            f.sub.add(ColorPickerSetting("Tick Time", "The time in brackets", { RunSplit.splitTickDone },
                { v -> RunSplit.splitTickIdle = v; RunSplit.splitTickLive = v; RunSplit.splitTickDone = v },
                ColorDefaults.of(RunSplit::class.java, "splitTickDone")))
            f.sub.add(SubcategoryHeader("Split Name Colors"))
            for (s in BossPhase.distinctSplits()) {
                f.sub.add(ColorPickerSetting(s.name, "", { s.nameColor() }, { v -> RunSplit.setNameColor(s.name, v) }, s.defaultNameColor()))
            }
            f.sub.add(ButtonSetting("Reset Names", "Back to the default split name colors", { "Reset" }, Runnable { RunSplit.resetNameColors() }))
            f.sub.add(SubcategoryHeader("Reset Data — click twice to confirm"))
            val resetFloors = arrayOf("All") + BossPhase.splitFloors().toTypedArray()
            f.sub.add(DropdownSetting("Floor", "", resetFloors, { FishSettings.splitResetFloor.takeIf { it in resetFloors } ?: "All" }, { v -> FishSettings.splitResetFloor = v }))
            fun floorArg(): String? = FishSettings.splitResetFloor.takeIf { it != "All" && it in resetFloors }
            var pbArmedAt = 0L
            f.sub.add(ButtonSetting("Reset Split PBs", "Forget your best time for every split", {
                if (System.currentTimeMillis() - pbArmedAt < 3000) "Confirm?" else "Reset"
            }, Runnable {
                if (System.currentTimeMillis() - pbArmedAt > 3000) { pbArmedAt = System.currentTimeMillis(); return@Runnable }
                pbArmedAt = 0L
                val n = fishmod.features.dungeon.PbMessages.resetSplits(floorArg(), BossPhase.splitFloors())
                fishmod.utils.FishUtil.addChatMessage(Component.literal("§aCleared §f$n§a split PBs (${FishSettings.splitResetFloor})."))
            }))
            var avgArmedAt = 0L
            f.sub.add(ButtonSetting("Clear Split Averages", "Wipe the last-30-runs history used for averages and Est. Total", {
                if (System.currentTimeMillis() - avgArmedAt < 3000) "Confirm?" else "Clear"
            }, Runnable {
                if (System.currentTimeMillis() - avgArmedAt > 3000) { avgArmedAt = System.currentTimeMillis(); return@Runnable }
                avgArmedAt = 0L
                val n = fishmod.utils.dungeon.RunHistory.clear(floorArg())
                fishmod.utils.FishUtil.addChatMessage(Component.literal("§aCleared averages for §f$n§a splits (${FishSettings.splitResetFloor})."))
            }))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("PB Messages", "pbMessagesEnabled")
            f.sub.add(ToggleSetting("Only On PB", "Off = also print slower times with the gap to your PB", "pbMessagesOnlyPb"))
            f.sub.add(ToggleSetting("Run Splits", "Run splits (Blood Open, Maxor, Terminals, Run Time…)", "pbMessagesSplits"))
            f.sub.add(ToggleSetting("Goldor Sections", "S1–S4 terminal sections", "pbMessagesGoldor"))
            f.sub.add(ToggleSetting("Terminals", "Your open-to-solve time per terminal type", "pbMessagesTerminals"))
            f.sub.add(ToggleSetting("Relics", "P5 start to your relic placed (M7)", "pbMessagesRelics"))
            f.sub.add(ToggleSetting("Storm Kill", "P2 start to Storm's death (F7/M7)", "pbMessagesStormKill"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Session Stats", "sessionStatsEnabled")
            f.sub.add(ToggleSetting("In Dungeon", "", "sessionStatsInDungeon"))
            f.sub.add(ToggleSetting("In D Hub", "", "sessionStatsInDungeonHub"))
            f.sub.add(ToggleSetting("Reset Relog", "", "sessionStatsResetOnRelog"))
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Loot Tracker", "lootTrackerEnabled")
            f.sub.add(DropdownSetting("Price", "",
                FishSettings.PriceMode.values(),
                { FishSettings.trackerPriceModeEnum },
                { v -> FishSettings.trackerPriceModeEnum = v; fishmod.features.croesus.CroesusPrices.applyPriceMode() }))
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Croesus Profit", "croesusProfitEnabled")
            f.sub.add(SubcategoryHeader("On the Croesus chest-preview: values each chest, highlights the 2 best, lists profit"))
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Croesus Highlights", "croesusHighlightEnabled")
            f.sub.add(SubcategoryHeader("In the Croesus menu: colours runs by unopened, kismet not used or chest key not used"))
            f.sub.add(ColorPickerSetting("Unopened", "", "croesusUnopenedColor"))
            f.sub.add(ColorPickerSetting("Kismet Not Used", "", "croesusKismetColor"))
            f.sub.add(ColorPickerSetting("Chest Key Not Used", "", "croesusKeyColor"))
            f.sub.add(SliderIntSetting("Opacity %", "", "croesusHighlightOpacity", 0, 100))
            dungeonTrackers.features.add(f)
        }
        run {
            val f = Feature("Simon Says", "simonSaysEnabled")
            f.sub.add(ToggleSetting("Show HUD", "", "simonSaysHudEnabled"))
            f.sub.add(ToggleSetting("To Party", "", "simonSaysPartyChat"))
            f.sub.add(ToggleSetting("SS Skip Compatibility", "Count a skipped first round correctly", "simonSaysSkipCompat"))
            f.sub.add(ToggleSetting("Fail Msg", "", "simonSaysFailEnabled"))
            f.sub.add(InputSetting("Fail Text", "", "simonSaysFailMessage").gatedBy { FishSettings.simonSaysFailEnabled })

            f.sub.add(SubcategoryHeader("HUD Text (shown when watching someone else's device)"))
            f.sub.add(ToggleSetting("Show Progress", "", "ssProgressShowProgress"))
            f.sub.add(InputSetting("Progress Text", "\"(n)\" is replaced by the current round", "ssProgressProgressText").gatedBy { FishSettings.ssProgressShowProgress })
            f.sub.add(ColorPickerSetting("Progress Color", "", "ssProgressProgressColor").gatedBy { FishSettings.ssProgressShowProgress })
            f.sub.add(ToggleSetting("Show Completed", "", "ssProgressShowCompleted"))
            f.sub.add(InputSetting("Completed Text", "", "ssProgressCompletedText").gatedBy { FishSettings.ssProgressShowCompleted })
            f.sub.add(ColorPickerSetting("Completed Color", "", "ssProgressCompletedColor").gatedBy { FishSettings.ssProgressShowCompleted })
            f.sub.add(ToggleSetting("Show Reset", "", "ssProgressShowReset"))
            f.sub.add(InputSetting("Reset Text", "", "ssProgressResetText").gatedBy { FishSettings.ssProgressShowReset })
            f.sub.add(ColorPickerSetting("Reset Color", "", "ssProgressResetColor").gatedBy { FishSettings.ssProgressShowReset })

            f.sub.add(SubcategoryHeader("Round 1"))
            f.sub.add(ToggleSetting("Announce", "", "simon1Enabled"))
            f.sub.add(InputSetting("Message", "", "simon1Message").gatedBy { FishSettings.simon1Enabled })
            f.sub.add(SubcategoryHeader("Round 2"))
            f.sub.add(ToggleSetting("Announce", "", "simon2Enabled"))
            f.sub.add(InputSetting("Message", "", "simon2Message").gatedBy { FishSettings.simon2Enabled })
            f.sub.add(SubcategoryHeader("Round 3"))
            f.sub.add(ToggleSetting("Announce", "", "simon3Enabled"))
            f.sub.add(InputSetting("Message", "", "simon3Message").gatedBy { FishSettings.simon3Enabled })
            f.sub.add(SubcategoryHeader("Round 4"))
            f.sub.add(ToggleSetting("Announce", "", "simon4Enabled"))
            f.sub.add(InputSetting("Message", "", "simon4Message").gatedBy { FishSettings.simon4Enabled })
            f.sub.add(SubcategoryHeader("Round 5"))
            f.sub.add(ToggleSetting("Announce", "", "simon5Enabled"))
            f.sub.add(InputSetting("Message", "", "simon5Message").gatedBy { FishSettings.simon5Enabled })
            solvers.features.add(f)
        }
        dungeon.features.add(Feature("Class Colored Boots", "classColoredBootsEnabled"))
        run {
            val f = Feature("M7 Lever Waypoints", "enableM7LeverWaypoints")
            val leverStyleOptions = arrayOf("Outline", "Fill", "Filled Outline")
            f.sub.add(DropdownSetting("Style", "", leverStyleOptions,
                { leverStyleOptions.getOrElse(FishSettings.m7LeverWaypointMode) { leverStyleOptions[0] } },
                { v -> FishSettings.m7LeverWaypointMode = leverStyleOptions.indexOf(v).coerceAtLeast(0) }))
            f.sub.add(ColorPickerSetting("Color", "", "m7LeverWaypointColor"))
            f.sub.add(SliderIntSetting("Fill Opacity %", "", "m7LeverWaypointOpacity", 0, 100))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Starred Mob Highlight", "enableStarredMobHighlight")
            f.sub.add(ColorPickerSetting("Outline Color", "", "starredMobHighlightColor"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Class Colored Glow", DungeonSettings::class.java, "classColoredGlow")
            f.sub.add(ColorPickerSetting("Archer", "", DungeonSettings::class.java, "roleColorArcher"))
            f.sub.add(ColorPickerSetting("Berserk", "", DungeonSettings::class.java, "roleColorBerserk"))
            f.sub.add(ColorPickerSetting("Healer", "", DungeonSettings::class.java, "roleColorHealer"))
            f.sub.add(ColorPickerSetting("Mage", "", DungeonSettings::class.java, "roleColorMage"))
            f.sub.add(ColorPickerSetting("Tank", "", DungeonSettings::class.java, "roleColorTank"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Dupe Class Detector", DungeonSettings::class.java, "dupeClassAlert")
            f.sub.add(ToggleSetting("Ignore Mage", "", DungeonSettings::class.java, "dupeClassSkipMage"))
            f.sub.add(ToggleSetting("To Party", "", DungeonSettings::class.java, "dupeClassPartyChat"))
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
                { v -> FishSettings.nickColorStart = v; if (NickState.isActive()) NickState.applyFromSettings() },
                ColorDefaults.of(FishSettings::class.java, "nickColorStart")))
            f.sub.add(ConditionalColorPickerSetting("Mid Color", "",
                { "GRADIENT3".equals(FishSettings.nickColorMode, ignoreCase = true) },
                { FishSettings.nickColorMid },
                { v -> FishSettings.nickColorMid = v; if (NickState.isActive()) NickState.applyFromSettings() },
                ColorDefaults.of(FishSettings::class.java, "nickColorMid")))
            f.sub.add(ConditionalColorPickerSetting("End Color", "",
                { "GRADIENT".equals(FishSettings.nickColorMode, ignoreCase = true) || "GRADIENT3".equals(FishSettings.nickColorMode, ignoreCase = true) },
                { FishSettings.nickColorEnd },
                { v -> FishSettings.nickColorEnd = v; if (NickState.isActive()) NickState.applyFromSettings() },
                ColorDefaults.of(FishSettings::class.java, "nickColorEnd")))
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
            f.sub.add(ToggleSetting("See Others", "", "remoteNicksEnabled"))
            f.sub.add(ButtonSetting("Refresh Now", "", Runnable { fishmod.cosmetic.RemoteNicks.forceRefresh() }))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("See Others' Items",
                { FishSettings.remoteItemsEnabled },
                { v ->
                    FishSettings.remoteItemsEnabled = v
                    if (v) fishmod.cosmetic.RemoteSync.forceSync() else fishmod.cosmetic.RemoteItems.clearAll()
                })
            f.sub.add(SubcategoryHeader("Shows other FishMod users' custom item dye, trim, model and name; also shares yours"))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Nametag", "nickPreviewEnabled")
            f.sub.add(SliderDoubleSetting("Height", "", "nickPreviewYOffset", -1.5, 1.0))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Nametag Stats", "nametagStatsEnabled")
            f.sub.add(SubcategoryHeader("Networth + skill avg everywhere; Cata level + secret avg in the Dungeon Hub"))
            f.sub.add(DropdownSetting("Position", "", arrayOf("ABOVE", "BELOW"),
                { if (FishSettings.nametagStatsAbove) "ABOVE" else "BELOW" },
                { v -> FishSettings.nametagStatsAbove = v == "ABOVE" }))
            f.sub.add(ToggleSetting("Networth", "", "nametagStatsShowNetworth"))
            f.sub.add(ToggleSetting("Catacombs Level", "", "nametagStatsShowCataLevel"))
            f.sub.add(ToggleSetting("Secret Average", "", "nametagStatsShowSecretAvg"))
            f.sub.add(ToggleSetting("Skill Average", "", "nametagStatsShowSkillAvg"))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Prestige Colors", "prestigeColorsEnabled")
            f.sub.add(SubcategoryHeader("Recolors Hypixel's SkyBlock [level] badge by level"))
            f.sub.add(SubcategoryHeader("0–300: 15 solid tiers · 300–700: 20 gradient tiers"))
            f.sub.add(ToggleSetting("On Nametags", "", "prestigeColorsNametags"))
            f.sub.add(ToggleSetting("In Tab List", "", "prestigeColorsTab"))
            f.sub.add(ToggleSetting("In Chat", "", "prestigeColorsChat"))
            f.sub.add(ToggleSetting("Gradient Tiers (300+)", "", "prestigeColorsGradientTiers"))
            f.sub.add(ToggleSetting("Animate Gradients", "", "prestigeColorsAnimated")
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
            val f = Feature("Badges", "badgesEnabled")
            f.sub.add(SubcategoryHeader("Small icons beside a player's name, after the [level] badge — granted only by RedFish, never self-assignable"))
            f.sub.add(ToggleSetting("On Nametags", "", "badgesOnNametags"))
            f.sub.add(ToggleSetting("In Tab List", "", "badgesOnTab"))
            f.sub.add(ToggleSetting("In Chat", "", "badgesInChat"))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Custom Crosshair", "crosshairEnabled")
            f.sub.add(SubcategoryHeader("Replaces the vanilla crosshair with a built-in preset or a PNG from config/FishMod/crosshairs/"))
            f.sub.add(DropdownSetting("Type", "", arrayOf("Preset", "Image"),
                { FishSettings.crosshairMode },
                { v -> FishSettings.crosshairMode = v }))
            f.sub.add(DropdownSetting("Preset", "", fishmod.features.CustomCrosshair.PRESETS,
                { if (FishSettings.crosshairPreset in fishmod.features.CustomCrosshair.PRESETS) FishSettings.crosshairPreset else fishmod.features.CustomCrosshair.PRESETS[0] },
                { v -> FishSettings.crosshairPreset = v })
                .gatedBy { FishSettings.crosshairMode == "Preset" })
            f.sub.add(ButtonSetting("Open Crosshairs Folder", "") {
                try {
                    fishmod.features.CrosshairImageLoader.init()
                    fishmod.compat.GLFW.openUri(fishmod.features.CrosshairImageLoader.getImagesPath().toUri())
                } catch (e: Exception) {
                    FishDiag.fail("FishModScreen.4", "open crosshairs folder failed", e)
                }
            }.gatedBy { FishSettings.crosshairMode == "Image" })
            val crosshairImageNames: Array<String> = run {
                val names = fishmod.features.CrosshairImageLoader.getImageNames()
                (if (names.isEmpty()) listOf("No image") else names).toTypedArray()
            }
            f.sub.add(DropdownSetting("Image", "", crosshairImageNames,
                { if (FishSettings.crosshairImageSelection in crosshairImageNames) FishSettings.crosshairImageSelection else crosshairImageNames[0] },
                { v -> FishSettings.crosshairImageSelection = v })
                .gatedBy { FishSettings.crosshairMode == "Image" })
            f.sub.add(ColorPickerSetting("Color", "Tint applied to the crosshair", "crosshairColor"))
            f.sub.add(SliderDoubleSetting("Scale", "", "crosshairScale", 0.25, 4.0))
            f.sub.add(ToggleSetting("Hide in F3", "", "crosshairHideInF3"))
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
            val f = Feature("Force Others' Size", "forceOthersSizeEnabled")
            f.sub.add(SubcategoryHeader("Everyone else renders at this size, overriding shared sizes"))
            f.sub.add(SliderDoubleSetting("Size", "", "forceOthersSizeScale",
                fishmod.cosmetic.PlayerSize.MIN.toDouble(), fishmod.cosmetic.PlayerSize.MAX.toDouble()))
            cosmetics.features.add(f)
        }
        run {
            val f = Feature("Party Commands", "partyCommandsEnabled")
            f.sub.add(ToggleSetting(".ai", "", "pcAllinvite"))
            f.sub.add(ToggleSetting(".pb", "", "pcPb"))
            f.sub.add(ToggleSetting(".cata", "", "pcCata"))
            f.sub.add(ToggleSetting(".rtca", "", "pcRtca"))
            f.sub.add(ToggleSetting(".rtc", "", "pcRtc"))
            f.sub.add(ToggleSetting(".crtc", "", "pcCrtc"))
            f.sub.add(ToggleSetting(".dprofit", "", "pcDprofit"))
            f.sub.add(ToggleSetting(".crit", "", "pcCrit"))
            f.sub.add(ToggleSetting(".corpse", "", "pcCorpse"))
            f.sub.add(ToggleSetting(".f# / .m#", "", "pcJoinFloor"))
            f.sub.add(ToggleSetting(".fps", "", "pcFps"))
            f.sub.add(ToggleSetting(".tps", "", "pcTps"))
            f.sub.add(ToggleSetting(".ping", "", "pcPing"))
            f.sub.add(ToggleSetting(".secrets", "", "pcSecrets"))
            f.sub.add(ToggleSetting(".runs", "", "pcRuns"))
            f.sub.add(ToggleSetting(".d", "", "pcDisband"))
            f.sub.add(ToggleSetting(".mp", "", "pcMp"))
            f.sub.add(ToggleSetting(".collection", "", "pcCollection"))
            f.sub.add(ToggleSetting(".nw", "", "pcNw"))
            f.sub.add(ToggleSetting(".bank", "", "pcBank"))
            f.sub.add(ToggleSetting(".powder", "", "pcPowder"))
            f.sub.add(ToggleSetting(".level", "", "pcLevel"))
            f.sub.add(ToggleSetting(".farming", "", "pcFarming"))
            f.sub.add(ToggleSetting(".nuc", "", "pcNuc"))
            f.sub.add(ToggleSetting(".worm / .scatha", "", "pcWorm"))
            f.sub.add(ToggleSetting(".help / .?", "", "pcHelp"))
            f.sub.add(SubcategoryHeader("Party Actions"))
            f.sub.add(ToggleSetting(".kick / .k", "", "pcActionKick"))
            f.sub.add(ToggleSetting(".warp / .w", "", "pcActionWarp"))
            f.sub.add(ToggleSetting(".transfer / .pt / .ptme", "", "pcActionTransfer"))
            f.sub.add(ToggleSetting(".promote / .pro", "", "pcActionPromote"))
            f.sub.add(ToggleSetting(".demote / .dem", "", "pcActionDemote"))
            party.features.add(f)
        }
        run {
            val f = Feature("Command Access List", null, null)
            f.sub.add(SubcategoryHeader("Who can trigger .kick / .warp / .transfer / .promote / .demote"))
            f.sub.add(DropdownSetting("Who Can Trigger", "", arrayOf("off", "self", "whitelist", "blacklist", "everyone"),
                { FishSettings.pcPartyActionsMode }, { v -> FishSettings.pcPartyActionsMode = v }))
            f.sub.add(ButtonSetting("Whitelist", "Edit who may trigger party actions", "Open", Runnable {
                Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.WHITELIST, Minecraft.getInstance().curScreen))
            }))
            f.sub.add(ButtonSetting("Blacklist", "Edit who is always blocked", "Open", Runnable {
                Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.BLACKLIST, Minecraft.getInstance().curScreen))
            }))
            party.features.add(f)
        }
        run {
            val f = Feature("Kick List", { FishSettings.pcKickListEnabled }, { v -> FishSettings.pcKickListEnabled = v })
            f.sub.add(SubcategoryHeader("Anyone on this list is auto-kicked from your party whenever you're leader"))
            f.sub.add(ButtonSetting("Manage List", "Add/remove names, or /fm kicklist", "Open", Runnable {
                Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.KICK, Minecraft.getInstance().curScreen))
            }))
            party.features.add(f)
        }
        run {
            val f = Feature("Chat Channels", null, null)
            f.sub.add(ToggleSetting("Personal Messages", "", "chatPrivate"))
            f.sub.add(ToggleSetting("Party", "", "chatParty"))
            f.sub.add(ToggleSetting("Guild", "", "chatGuild"))
            f.sub.add(ToggleSetting("All", "", "chatAll"))
            party.features.add(f)
        }
        party.features.add(Feature("Party Finder Join Stats", "pfStatsEnabled"))
        run {
            val f = Feature("Party Finder Menu", "pfMenuEnabled")
            f.sub.add(ToggleSetting("Level Req on Head", "Red Dungeon-Level-Required number", "pfShowLevelReq"))
            f.sub.add(ToggleSetting("Missing Classes on Head", "", "pfShowMissingClasses"))
            f.sub.add(ToggleSetting("Tooltip Stats", "Cata / Secrets / PB per listed member", "pfTooltipStats"))
            f.sub.add(ToggleSetting("Show Secrets", "", "pfShowSecrets"))
            f.sub.add(ToggleSetting("Show PB", "Fastest S+ for the listing's floor", "pfShowPb"))
            f.sub.add(ToggleSetting("Missing List in Tooltip", "", "pfTooltipMissingList"))
            f.sub.add(ToggleSetting("Highlight Joinable", "Green head for a party missing your class", "pfHighlightJoinable"))
            f.sub.add(ToggleSetting("Highlight Non-Cata-50", "Orange head for a party with a member below Cata 50", "pfHighlightNonCata50"))
            f.sub.add(DropdownSetting("My Class", "For the joinable highlight", arrayOf("Auto", "Archer", "Berserk", "Healer", "Mage", "Tank"),
                { FishSettings.pfMyClass }, { v -> FishSettings.pfMyClass = v ?: "Auto" }))
            party.features.add(f)
        }
        run {
            val f = Feature("Party Finder List", "pfListPanel")
            f.sub.add(SubcategoryHeader("Scrollable party list — position it in the HUD editor; hover a row to highlight its head"))
            f.sub.add(SliderIntSetting("Max Rows", "Rows shown before scrolling", "pfListMaxRows", 3, 10))
            f.sub.add(ToggleSetting("Worst PB", "Slowest listed member's S+ for the listing's floor (Hypixel API)", "pfListWorstPb"))
            f.sub.add(ToggleSetting("Show Notes", "Include the party note in the row", "pfListNotes"))
            f.sub.add(ToggleSetting("Click Row to Join", "Left-click a row to click that head", "pfListClickJoin"))
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
            f.sub.add(ToggleSetting("Filter: Hide Full", "Hide 5/5 parties", "pfFilterHideFull"))
            f.sub.add(SliderIntSetting("Filter: Max Level Req", "0 = off; hide parties requiring a higher dungeon level", "pfFilterMaxLevel", 0, 60))
            party.features.add(f)
        }
        run {
            val f = Feature("Party Finder Auto Kick", "pfAutoKick")
            f.sub.add(SubcategoryHeader("Only fires while you're party leader"))
            f.sub.add(ToggleSetting("Master Mode", "Check Master PBs instead of normal", "pfAutoKickMaster"))
            f.sub.add(SliderIntSetting("Floor", "Which floor's PB to check", "pfAutoKickFloor", 1, 7))
            f.sub.add(SliderIntSetting("Max S+ Time", "Kick if their S+ PB is slower (or missing)", "pfAutoKickMaxSeconds", 60, 480, 5) { "${it / 60}:${"%02d".format(it % 60)}" })
            f.sub.add(SliderIntSetting("Min Secrets (k)", "0 = don't check secrets", "pfAutoKickMinSecretsK", 0, 200))
            f.sub.add(ToggleSetting("Announce in Party", "Send a /pc line before kicking", "pfAutoKickInform"))
            f.sub.add(SubcategoryHeader("Per-class minimums — 0 = off. Class read from the join message."))
            f.sub.add(SubcategoryHeader("Archer"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick an Archer below this Catacombs level", "pfAutoKickArcherCata", 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick an Archer below this SkyBlock level", "pfAutoKickArcherSb", 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick an Archer below this Magical Power", "pfAutoKickArcherMp", 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Berserk"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Berserk below this Catacombs level", "pfAutoKickBerserkCata", 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Berserk below this SkyBlock level", "pfAutoKickBerserkSb", 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Berserk below this Magical Power", "pfAutoKickBerserkMp", 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Healer"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Healer below this Catacombs level", "pfAutoKickHealerCata", 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Healer below this SkyBlock level", "pfAutoKickHealerSb", 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Healer below this Magical Power", "pfAutoKickHealerMp", 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Mage"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Mage below this Catacombs level", "pfAutoKickMageCata", 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Mage below this SkyBlock level", "pfAutoKickMageSb", 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Mage below this Magical Power", "pfAutoKickMageMp", 0, 1500, 25))
            f.sub.add(SubcategoryHeader("Tank"))
            f.sub.add(SliderIntSetting("Min Cata", "Kick a Tank below this Catacombs level", "pfAutoKickTankCata", 0, 60))
            f.sub.add(SliderIntSetting("Min SB", "Kick a Tank below this SkyBlock level", "pfAutoKickTankSb", 0, 500, 5))
            f.sub.add(SliderIntSetting("Min MP", "Kick a Tank below this Magical Power", "pfAutoKickTankMp", 0, 1500, 25))
            party.features.add(f)
        }

        run {
            val f = Feature("Cooldown Overlay", "cooldownOverlayEnabled")
            f.sub.add(ToggleSetting("Show Number", "", "cooldownShowText"))
            f.sub.add(ToggleSetting("Under 3s Only", "", "cooldownOnlyUnder3s"))
            f.sub.add(ToggleSetting("In Inventory", "", "cooldownInInventory"))
            f.sub.add(ToggleSetting("Disable Cooldown Background", "Hide the white cooldown sweep behind the timer", VisualSettings::class.java, "hideItemCooldown"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Huntaxe Lock", "huntaxeLockEnabled")
            f.sub.add(SubcategoryHeader("Single right click with a Huntaxe is cancelled; double right click opens it"))
            f.sub.add(SliderIntSetting("Double Click Window", "Max time between the two clicks (ms)", "huntaxeLockWindowMs", 150, 1000, 25))
            general.features.add(f)
        }
        hud.features.add(Feature("Catacombs Overflow Levels", "catacombsOverflowEnabled"))
        run {
            hud.features.add(Feature("Anita Medal Profit", "anitaProfitEnabled"))
            val s = Feature("Experiment Solver", "experimentSolverEnabled")
            s.sub.add(SubcategoryHeader("Highlights Chronomatron, Ultrasequencer and Superpairs slots. Never clicks."))
            general.features.add(s)
            val f = Feature("Experiment Profit Tracker", "expProfitEnabled")
            f.sub.add(SubcategoryHeader("Experimentation Table drops, value, Enchanting XP and XP per experiment  ·  drag with Edit HUD"))
            f.sub.add(ToggleSetting("Only On Private Island", "", "expProfitOnlyIsland"))
            f.sub.add(SliderIntSetting("Item Lines", "", "expProfitLines", 1, 30))
            f.sub.add(SliderDoubleSetting("Scale", "", "expProfitHudScale", 0.5, 3.0))
            f.sub.add(ButtonSetting("Reset", "Clear all tracked experiments", Runnable { fishmod.features.ExperimentProfitTracker.reset() }))
            general.features.add(f)
        }
        run {
            val f = Feature("Performance HUD", "perfHudEnabled")
            f.sub.add(SubcategoryHeader("Move/scale it in the HUD editor"))
            f.sub.add(ToggleSetting("FPS", "", "perfHudFps"))
            f.sub.add(ToggleSetting("TPS", "Server ticks per second", "perfHudTps"))
            f.sub.add(ToggleSetting("Ping", "", "perfHudPing"))
            f.sub.add(ToggleSetting("Clock", "Real-world local time", "perfHudClock"))
            f.sub.add(DropdownSetting("Clock Format", "", arrayOf("12 Hour", "24 Hour"),
                { if (FishSettings.perfHudClock24h) "24 Hour" else "12 Hour" },
                { v -> FishSettings.perfHudClock24h = v == "24 Hour" }))
            f.sub.add(ToggleSetting("Clock Seconds", "", "perfHudClockSeconds"))
            f.sub.add(ToggleSetting("Detach Clock", "Move the clock as its own HUD element", "perfHudClockDetached"))
            f.sub.add(ToggleSetting("Single Line", "Show all values side by side", "perfHudHorizontal"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Action Bar", "actionBarEnabled")
            f.sub.add(SubcategoryHeader("Hide segments of Hypixel's SkyBlock action bar"))
            f.sub.add(ToggleSetting("Health", "", "abHideHealth"))
            f.sub.add(ToggleSetting("Defence", "", "abHideDefense"))
            f.sub.add(ToggleSetting("True Defence", "", "abHideTrueDefense"))
            f.sub.add(ToggleSetting("Mana", "", "abHideMana"))
            f.sub.add(ToggleSetting("Overflow Mana", "The ʬʬ counter", "abHideOverflowMana"))
            f.sub.add(ToggleSetting("Mana Use", "\"-40 Mana (Ability)\" flashes", "abHideManaUse"))
            f.sub.add(ToggleSetting("Skill XP", "\"+12.5 Combat (…)\" popups", "abHideSkillXp"))
            f.sub.add(ToggleSetting("Armor Stacks", "The \"31x … Arrow\" bow count", "abHideArmorStacks"))
            f.sub.add(ToggleSetting("Rag Axe Timer", "Ragnarock Axe countdown", "abHideRagAxeTimer"))
            f.sub.add(ToggleSetting("Term Laser", "", "abHideTermLaser"))
            f.sub.add(ToggleSetting("Bits Gained", "", "abHideBits"))
            f.sub.add(ToggleSetting("Secrets", "Dungeon secret count", "abHideSecrets"))
            f.sub.add(ToggleSetting("Vitality", "", "abHideVitality"))
            f.sub.add(SubcategoryHeader("Vanilla HUD (SkyBlock only)"))
            f.sub.add(ToggleSetting("XP Bar", "Vanilla experience bar + level number", "abHideXpBar"))
            f.sub.add(ToggleSetting("Armor Display", "The armor-icon row above health", "abHideArmorRow"))
            f.sub.add(ToggleSetting("Absorption Hearts", "The gold absorption hearts", "abHideAbsorption"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Pet Swap Title", "petSwapTitleEnabled")
            f.sub.add(SubcategoryHeader("Big title when your pet changes (autopet or summon)"))
            f.sub.add(InputSetting("Format", "{pet} = the pet's name", "petSwapTitleFormat"))
            f.sub.add(ToggleSetting("Rarity Color", "Use the pet's rarity colour", "petSwapTitleRarityColor"))
            f.sub.add(ColorPickerSetting("Color", "When Rarity Color is off", "petSwapTitleColor").gatedBy { !FishSettings.petSwapTitleRarityColor })
            f.sub.add(SliderIntSetting("Duration (ms)", "", "petSwapTitleMs", 500, 5000, 250))
            hud.features.add(f)
        }
        run {
            val f = Feature("Spotify Lyrics", "spotifyLyricsEnabled")
            f.sub.add(SubcategoryHeader("Windows only, reads the Spotify desktop window title (no login). Move it in the HUD editor"))
            f.sub.add(ToggleSetting("Show Icon", "Animated bars before the title", "spotifyLyricsIcon"))
            f.sub.add(ToggleSetting("Lyrics", "Current lyric line under the pill (artist and song are sent to lrclib.net)", "spotifyLyricsLyrics"))
            f.sub.add(ToggleSetting("Connected Lyrics", "Lyric line inside the same box as the title (off = separate pill)", "spotifyLyricsConnected").gatedBy { FishSettings.spotifyLyricsLyrics })
            f.sub.add(SliderIntSetting("Max Characters", "Longer text scrolls in a loop", "spotifyLyricsMaxChars", 8, 80, 1))
            f.sub.add(SliderIntSetting("Lyric Offset (ms)", "Shift lyrics earlier (negative) or later", "spotifyLyricsLyricOffset", -5000, 5000, 100).gatedBy { FishSettings.spotifyLyricsLyrics })
            f.sub.add(ToggleSetting("Show Paused", "Show Paused instead of hiding when nothing plays", "spotifyLyricsShowPaused"))
            f.sub.add(ToggleSetting("Background", "", "spotifyLyricsBackground"))
            f.sub.add(ColorPickerSetting("Text Color", "", "spotifyLyricsTextColor"))
            f.sub.add(ColorPickerSetting("Background Color", "Alpha sets the opacity", "spotifyLyricsBgColor").gatedBy { FishSettings.spotifyLyricsBackground })
            hud.features.add(f)
        }
        run {
            val f = Feature("Pet HUD", "petHudEnabled")
            f.sub.add(ToggleSetting("Show Level", "", "petHudShowLevel"))
            f.sub.add(ToggleSetting("Show Icon", "Pet head instead of its name (learned when you open /pets)", "petHudIcon"))
            f.sub.add(ToggleSetting("Show Rarity", "Colour the pet name by its rarity", "petHudShowRarity"))
            f.sub.add(ToggleSetting("Fade Idle", "", "petHudFadeIdle"))
            f.sub.add(SliderIntSetting("Fade ms", "", "petHudFadeMs", 1000, 30000).gatedBy { FishSettings.petHudFadeIdle })
            hud.features.add(f)
        }
        run {
            val f = Feature("Hide Pet Nametag Level", "petNametagHideLevel")
            f.sub.add(SubcategoryHeader("Drops the [283] level off pet nametags (skins show overflow levels)"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Soulflow HUD", "soulflowHudEnabled")
            f.sub.add(InputIntSetting("Warning", "", "soulflowWarningThreshold"))
            f.sub.add(ToggleSetting("Missing Warn", "", "soulflowMissingNotifier"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Text Shadow", "textShadowEnabled")
            f.sub.add(SubcategoryHeader("Drop shadow on in-world text (chat already has one)"))
            f.sub.add(ToggleSetting("Nametags", "Player, mob and armor stand names", "textShadowNametags"))
            f.sub.add(ToggleSetting("World Text", "Floating text displays / holograms", "textShadowWorldText"))
            hud.features.add(f)
        }

        hud.features.add(Feature("Fire Freeze Timer", "fireFreezeTimerEnabled"))
        hud.features.add(Feature("Loadout Title", "loadoutTitleEnabled"))
        run {
            val f = Feature("Time Changer", "timeChangerEnabled")
            f.sub.add(DropdownSetting("Time", "", fishmod.features.TimeChanger.modes(),
                { FishSettings.timeChangerMode }, { v -> FishSettings.timeChangerMode = v }))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Arrow Hit Sound", "arrowHitSoundEnabled")
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.arrowHitSoundName }, { v -> FishSettings.arrowHitSoundName = v },
                { FishSettings.arrowHitSoundVolume }, { FishSettings.arrowHitSoundPitch }))
            f.sub.add(SliderIntSetting("Volume %", "Above 100 = louder (stacked plays)", "arrowHitSoundVolume", 0, 500, 10))
            f.sub.add(SliderDoubleSetting("Pitch", "", "arrowHitSoundPitch", 0.0, 2.0))
            f.sub.add(ToggleSetting("Suppress Vanilla Sound", "", "arrowHitSoundSuppress"))
            general.features.add(f)
        }
        run {
            val f = Feature("Mob Hit Sound (Melee)", "meleeHitSoundEnabled")
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.meleeHitSoundName }, { v -> FishSettings.meleeHitSoundName = v },
                { FishSettings.meleeHitSoundVolume }, { FishSettings.meleeHitSoundPitch }))
            f.sub.add(SliderIntSetting("Volume %", "Above 100 = louder (stacked plays)", "meleeHitSoundVolume", 0, 500, 10))
            f.sub.add(SliderDoubleSetting("Pitch", "", "meleeHitSoundPitch", 0.0, 2.0))
            f.sub.add(ToggleSetting("Suppress Vanilla Sound", "Mute the vanilla attack sounds", "meleeHitSoundSuppress"))
            general.features.add(f)
        }
        run {
            val f = Feature("Block Overlay", "blockOverlayEnabled")
            val blockOverlayModeOptions = arrayOf("Outline", "Fill", "Filled Outline")
            f.sub.add(DropdownSetting("Mode", "", blockOverlayModeOptions,
                { blockOverlayModeOptions.getOrElse(FishSettings.blockOverlayMode) { blockOverlayModeOptions[0] } },
                { v -> FishSettings.blockOverlayMode = blockOverlayModeOptions.indexOf(v).coerceAtLeast(0) }))
            f.sub.add(ColorPickerSetting("Fill Color", "", "blockOverlayFillColor"))
            f.sub.add(SliderIntSetting("Fill Opacity %", "", "blockOverlayOpacity", 0, 100))
            f.sub.add(ColorPickerSetting("Outline Color", "", "blockOverlayOutlineColor"))
            f.sub.add(SliderDoubleSetting("Outline Thickness", "", "blockOverlayOutlineThickness", 0.01, 0.1))
            f.sub.add(ToggleSetting("Phase (through walls)", "", "blockOverlayPhase"))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Item Rarity Background", VisualSettings::class.java, "rarityBackground")
            f.sub.add(SliderIntSetting("Opacity %", "", VisualSettings::class.java, "itemRarityOpacity", 0, 100))
            f.sub.add(ToggleSetting("Hypixel Colors", "Brighter, accurate per-rarity colours", VisualSettings::class.java, "itemRarityHypixelColors"))
            f.sub.add(ToggleSetting("Circular", "", VisualSettings::class.java, "rarityBgCircle"))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Dark Mode", VisualSettings::class.java, "darkModeEnabled")
            f.sub.add(SliderIntSetting("Opacity %", "", VisualSettings::class.java, "darkModeOpacity", 1, 80))
            f.sub.add(ToggleSetting("Tint HUD", "Also darken the hotbar/chat/etc, not just the world", VisualSettings::class.java, "darkModeTintHud"))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Item Tooltip", "itemTooltipPrices")
            f.sub.add(SubcategoryHeader("Extra lines on SkyBlock item tooltips"))
            f.sub.add(ToggleSetting("Prices", "Value = base + modifiers (enchants, HPB, recomb, gems, reforge…)", "itemTooltipPrices"))
            f.sub.add(ToggleSetting("NPC Sell Price", "", "itemTooltipNpcSell"))
            f.sub.add(ToggleSetting("Dungeon Quality", "Stat-boost % + floor", "itemQualityTooltip"))
            f.sub.add(ToggleSetting("Exotic Armor", "Tags crystal/fairy/exotic dyed armor + hex", "exoticTooltip"))
            f.sub.add(ToggleSetting("Original Name", "On items with a custom name, shows the original name", "originalNameTooltip"))
            f.sub.add(InputSetting("Original Name Text", "", "originalNameLabel").gatedBy { FishSettings.originalNameTooltip })
            f.sub.add(ColorPickerSetting("Original Name Color", "", "originalNameColor").gatedBy { FishSettings.originalNameTooltip })
            f.sub.add(ToggleSetting("Obtained Date", "When the item was obtained", fishmod.features.extras.ExtrasSettings::class.java, "obtainedDateTooltip"))
            f.sub.add(SubcategoryHeader("Scrollable Tooltips — scroll: move · shift: sideways · ctrl: scale"))
            f.sub.add(ToggleSetting("Scrollable Tooltips", "", "tooltipScrollEnabled"))
            f.sub.add(SliderIntSetting("Tooltip Scale %", "", "tooltipScrollScale", 30, 150, 5).gatedBy { FishSettings.tooltipScrollEnabled })
            f.sub.add(SliderIntSetting("Scroll Speed", "", "tooltipScrollSpeed", 1, 10).gatedBy { FishSettings.tooltipScrollEnabled })
            hud.features.add(f)
        }
        run {
            val f = Feature("Recipe Viewer", "recipeViewer")
            f.sub.add(SubcategoryHeader("Hold Shift on an item to open its NEU recipe"))
            f.sub.add(ToggleSetting("Enabled", "", "recipeViewer"))
            f.sub.add(SliderIntSetting("Hold Time (ms)", "", "recipeViewerHoldTime", 250, 5000, 50))
            hud.features.add(f)
        }
        run {
            val f = Feature("Item List", "skyItemList")
            f.sub.add(SubcategoryHeader("JEI-style Skyblock item panel beside inventories. Click an item for its recipe"))
            f.sub.add(ToggleSetting("Enabled", "", "skyItemList"))
            f.sub.add(SliderIntSetting("Columns", "", "skyItemListColumns", 2, 20))
            f.sub.add(ToggleSetting("Rarity Background", "Tint each item slot by its rarity", "skyItemListRarityBg"))
            f.sub.add(SliderIntSetting("Rarity Opacity %", "", "skyItemListRarityOpacity", 5, 100))
            f.sub.add(ToggleSetting("Rounded Rarity Background", "Round the corners of the rarity tint", "skyItemListRarityRound"))
            f.sub.add(SliderIntSetting("Background Opacity %", "0% invisible - 100% solid", "skyItemListOpacity", 0, 100))
            f.sub.add(ColorPickerSetting("Background Color", "", "skyItemListBgColor"))
            f.sub.add(ColorPickerSetting("Accent Color", "", "skyItemListAccentColor"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Auto BIN Price", "auctionPriceAutofillEnabled")
            f.sub.add(SubcategoryHeader("Prefills the AH \"Create Auction\" price field with item value minus a discount"))
            f.sub.add(SliderIntSetting("Discount %", "", "auctionAutofillPercent", 0, 50))
            hud.features.add(f)
        }
        run {
            val f = Feature("Render Optimizer", VisualSettings::class.java, "renderOptimizer")
            f.sub.add(ToggleSetting("Hide Nearby Players", "Hide other players within range", VisualSettings::class.java, "hideNearbyPlayers"))
            f.sub.add(SliderDoubleSetting("Player Range", "Blocks", VisualSettings::class.java, "hidePlayersRadius", 1.0, 12.0).gatedBy { VisualSettings.hideNearbyPlayers })
            f.sub.add(ToggleSetting("Hide Dead Entities", "Drop dying / 0-HP mobs from the render pass", VisualSettings::class.java, "hideDeadMobs"))
            f.sub.add(ToggleSetting("No Swing Animation", "Suppress the first-person hand swing", VisualSettings::class.java, "noSwingAnimation"))
            f.sub.add(ToggleSetting("Swing: Terminator Only", "Only suppress while holding a Terminator", VisualSettings::class.java, "noSwingTerminatorOnly").gatedBy { VisualSettings.noSwingAnimation })
            f.sub.add(ToggleSetting("Stop Shovel Flattening", "Cancel the shovel make-path interaction", VisualSettings::class.java, "noShovelPath"))
            f.sub.add(SubcategoryHeader("Clutter hiders"))
            f.sub.add(ToggleSetting("Hide Falling Blocks", "", VisualSettings::class.java, "roHideFallingBlocks"))
            f.sub.add(ToggleSetting("Hide Lightning", "", VisualSettings::class.java, "roHideLightning"))
            f.sub.add(ToggleSetting("Hide Experience Orbs", "", VisualSettings::class.java, "roHideExperienceOrbs"))
            f.sub.add(ToggleSetting("Hide Death Animation", "Hide mobs that are dying", VisualSettings::class.java, "roHideDeathAnimation"))
            f.sub.add(ToggleSetting("Hide Armor Stands", "Nametag stands on dying mobs (needs Hide Death Animation)", VisualSettings::class.java, "roHideDyingArmorStands").gatedBy { VisualSettings.roHideDeathAnimation })
            f.sub.add(ToggleSetting("Hide Explosion Particles", "", VisualSettings::class.java, "roHideExplosionParticles"))
            f.sub.add(ToggleSetting("Hide Archer Passive", "The archer passive's floating bone meal", VisualSettings::class.java, "roHideArcherPassive"))
            f.sub.add(ToggleSetting("Hide Healer Fairy", "The healer fairy held by some mobs", VisualSettings::class.java, "roHideHealerFairy"))
            f.sub.add(ToggleSetting("Hide Soul Weaver", "The soul weaver helmet worn by some mobs", VisualSettings::class.java, "roHideSoulWeaver"))
            f.sub.add(ToggleSetting("Hide Tentacle Head", "The tentacle head worn by some mobs", VisualSettings::class.java, "roHideTentacleHead"))
            f.sub.add(ToggleSetting("Hide Fire Overlay", "The first-person fire overlay", VisualSettings::class.java, "roHideFireOverlay"))
            f.sub.add(ToggleSetting("Hide Inventory Labels", "The \"Crafting\" text in your inventory", VisualSettings::class.java, "roHideInventoryLabels"))
            f.sub.add(ToggleSetting("Hide Objective", "The \"Objective:\" boss bar (e.g. Rift races)", VisualSettings::class.java, "roHideObjective"))
            // Mutually exclusive: turning one on turns the other off
            f.sub.add(ToggleSetting("Remove Damage Indicator", "Hide the floating damage numbers",
                { VisualSettings.roRemoveDamageIndicator }, { v -> VisualSettings.roRemoveDamageIndicator = v; if (v) VisualSettings.roFormatDamageIndicator = false }))
            f.sub.add(ToggleSetting("Format Damage Indicator", "Shorten damage numbers, e.g. 1,234,567 -> 1.23M",
                { VisualSettings.roFormatDamageIndicator }, { v -> VisualSettings.roFormatDamageIndicator = v; if (v) VisualSettings.roRemoveDamageIndicator = false }))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Gyro Helper", "gyroHelperEnabled")
            f.sub.add(ColorPickerSetting("Box Color", "", "gyroBoxColor"))
            f.sub.add(ColorPickerSetting("Ring Color", "", "gyroRingColor"))
            general.features.add(f)
        }
        run {
            val f = Feature("Mage Beam", "mageBeamEnabled")
            f.sub.add(ColorPickerSetting("Color", "", "mageBeamColor"))
            f.sub.add(SliderIntSetting("Duration (ticks)", "", "mageBeamDurationTicks", 1, 100))
            f.sub.add(ToggleSetting("Hide Particles", "", "mageBeamHideParticles"))
            f.sub.add(ToggleSetting("Depth Check", "", "mageBeamDepth"))
            dungeon.features.add(f)
        }
        run {
            val f = Feature("Spring Boots", "springBootsEnabled")
            f.sub.add(ToggleSetting("Show Blocks", "Blocks instead of charge %", "springBootsShowBlocks"))
            f.sub.add(ToggleSetting("Landing Box", "", "springBootsBox"))
            f.sub.add(ColorPickerSetting("Box Color", "", "springBootsBoxColor").gatedBy { FishSettings.springBootsBox })
            hud.features.add(f)
        }
        run {
            val f = Feature("Tac Timer", "tacTimerEnabled")
            f.sub.add(ToggleSetting("Reverse (count up)", "", "tacTimerReverse"))
            f.sub.add(ToggleSetting("\"Tac:\" Prefix", "", "tacTimerPrefix"))
            f.sub.add(ToggleSetting("\"s\" Suffix", "", "tacTimerSuffix"))
            f.sub.add(ToggleSetting("Start Waypoint", "", "tacTimerWaypoint"))
            f.sub.add(ColorPickerSetting("Waypoint Color", "", "tacTimerColor").gatedBy { FishSettings.tacTimerWaypoint })
            hud.features.add(f)
        }
        run {
            val sw = fishmod.features.other.Stopwatch
            val trig = fishmod.features.other.Stopwatch.Trigger.values()
            fun textGate(t: () -> fishmod.features.other.Stopwatch.Trigger) = { t().name.let { it == "CHAT" || it == "CHAT_REGEX" || it == "GUI_OPEN" } }
            val f = Feature("Stopwatch", sw.javaClass, "enabled")
            f.sub.add(SubcategoryHeader("Timer with custom start/stop/split/reset rules. Keybinds always work."))
            f.sub.add(DropdownSetting("Start On", "", trig, { sw.startTrigger }, { v -> sw.startTrigger = v }))
            f.sub.add(InputSetting("Start Text", "Text or regex for chat/GUI triggers", sw.javaClass, "startText").gatedBy(textGate { sw.startTrigger }))
            f.sub.add(DropdownSetting("Stop On", "", trig, { sw.stopTrigger }, { v -> sw.stopTrigger = v }))
            f.sub.add(InputSetting("Stop Text", "Text or regex for chat/GUI triggers", sw.javaClass, "stopText").gatedBy(textGate { sw.stopTrigger }))
            f.sub.add(DropdownSetting("Split On", "", trig, { sw.splitTrigger }, { v -> sw.splitTrigger = v }))
            f.sub.add(InputSetting("Split Text", "Text or regex for chat/GUI triggers", sw.javaClass, "splitText").gatedBy(textGate { sw.splitTrigger }))
            f.sub.add(DropdownSetting("Reset On", "", trig, { sw.resetTrigger }, { v -> sw.resetTrigger = v }))
            f.sub.add(InputSetting("Reset Text", "Text or regex for chat/GUI triggers", sw.javaClass, "resetText").gatedBy(textGate { sw.resetTrigger }))
            f.sub.add(ToggleSetting("Restart On Start Trigger", "Start trigger restarts a running timer", sw.javaClass, "restartOnStart"))
            f.sub.add(ToggleSetting("Dungeons Only", "Triggers only fire inside dungeons", sw.javaClass, "dungeonsOnly"))
            f.sub.add(KeybindSetting("Start/Stop Key", "") { sw.toggleKey })
            f.sub.add(KeybindSetting("Split Key", "") { sw.splitKey })
            f.sub.add(KeybindSetting("Reset Key", "") { sw.resetKey })
            f.sub.add(InputSetting("Prefix", "", sw.javaClass, "prefix"))
            f.sub.add(SliderIntSetting("Decimals", "", sw.javaClass, "decimals", 0, 3))
            f.sub.add(SliderIntSetting("History Shown", "Last stopped times", sw.javaClass, "historyCount", 0, 10))
            f.sub.add(ToggleSetting("Show Best", "", sw.javaClass, "showBest"))
            f.sub.add(ButtonSetting("Clear Best", "", { "Clear" }) { sw.resetBest() })
            f.sub.add(ToggleSetting("Chat On Stop", "", sw.javaClass, "chatOnStop"))
            f.sub.add(ToggleSetting("Hide When Idle", "", sw.javaClass, "hideWhenIdle"))
            f.sub.add(ColorPickerSetting("Prefix Color", "", sw.javaClass, "prefixColor"))
            f.sub.add(ColorPickerSetting("Running Color", "", sw.javaClass, "runningColor"))
            f.sub.add(ColorPickerSetting("Stopped Color", "", sw.javaClass, "stoppedColor"))
            f.sub.add(ColorPickerSetting("History Color", "", sw.javaClass, "historyColor"))
            f.sub.add(ColorPickerSetting("Best Color", "", sw.javaClass, "bestColor"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Visual Effects", "cameraTweaksEnabled")
            f.sub.add(ToggleSetting("Full Bright", "", "cameraFullBright"))
            f.sub.add(ToggleSetting("Disable Blindness", "", "cameraNoBlindness"))
            f.sub.add(ToggleSetting("Disable Nausea", "", "cameraNoNausea"))
            visuals.features.add(f)
        }
        run {
            val f = Feature("Explosive Shot", "explosiveShotEnabled")
            f.sub.add(ToggleSetting("Show Title", "", "explosiveShotShowTitle"))
            f.sub.add(ToggleSetting("Chat Message", "", "explosiveShotChatMessage"))
            f.sub.add(ToggleSetting("Announce to Party (Archer)", "", "explosiveShotAnnounceParty"))
            dungeon.features.add(f)
        }

        run {
            val f = Feature("Boss Tick Clocks", Floor7Settings::class.java, "enableTickTimers")
            f.sub.add(SubcategoryHeader("Maxor"))
            f.sub.add(ToggleSetting("Maxor", "", Floor7Settings::class.java, "maxorTicksHud"))
            f.sub.add(SubcategoryHeader("Storm"))
            f.sub.add(ToggleSetting("Storm", "", Floor7Settings::class.java, "stormTicksHud"))
            f.sub.add(ToggleSetting("Tick Down From 5", "", Floor7Settings::class.java, "stormTicksCountDown").gatedBy { Floor7Settings.stormTicksHud })
            f.sub.add(ColorPickerSetting("Storm Timer Color", "", Floor7Settings::class.java, "stormTicksColor").gatedBy { Floor7Settings.stormTicksHud })
            f.sub.add(ToggleSetting("Storm Kill Time", "", Floor7Settings::class.java, "stormKillTimeHud"))
            f.sub.add(ToggleSetting("LB Release Timer", "", Floor7Settings::class.java, "enableLbReleaseTimer"))
            f.sub.add(ColorPickerSetting("LB Release Timer Color", "", Floor7Settings::class.java, "lbReleaseTimerColor").gatedBy { Floor7Settings.enableLbReleaseTimer })
            f.sub.add(SliderIntSetting("LB Release Ping (ms)", "Fires the release cue this much earlier to offset latency", Floor7Settings::class.java, "lbReleaseTimerPingMs", 0, 500).gatedBy { Floor7Settings.enableLbReleaseTimer })
            f.sub.add(ToggleSetting("Py Tick Timer", "Counts down 5s to 29.5s, then tells you to stand on the crusher", Floor7Settings::class.java, "enablePyTimer"))
            f.sub.add(ColorPickerSetting("Py Timer Color", "", Floor7Settings::class.java, "pyTimerColor").gatedBy { Floor7Settings.enablePyTimer })
            f.sub.add(SliderIntSetting("Py Ping (ms)", "Ends the countdown this much earlier so high ping doesn't make you late", Floor7Settings::class.java, "pyTimerPingMs", 0, 500).gatedBy { Floor7Settings.enablePyTimer })
            f.sub.add(ToggleSetting("Storm Crush Banner", "", Floor7Settings::class.java, "stormCrushAlert"))
            f.sub.add(ToggleSetting("Pillar Blast Timer", "Counts down the second before the crushed pillar explodes", Floor7Settings::class.java, "pillarBlastTimer"))
            f.sub.add(SubcategoryHeader("Necron"))
            f.sub.add(ToggleSetting("Necron LB Timer", "Counts down to the LB shot 4s into Necron's phase, then says SHOOT LB", Floor7Settings::class.java, "enableNecronLbTimer"))
            f.sub.add(SliderIntSetting("Necron LB Ping (ms)", "Ends it this much earlier to offset latency", Floor7Settings::class.java, "necronLbPingMs", 0, 500).gatedBy { Floor7Settings.enableNecronLbTimer })
            f.sub.add(ColorPickerSetting("Necron LB Color", "", Floor7Settings::class.java, "necronLbColor").gatedBy { Floor7Settings.enableNecronLbTimer })
            f.sub.add(ToggleSetting("Necron Drop Prediction", "Red box where Necron's drag will drop you, from your velocity and the known drag time", Floor7Settings::class.java, "enableNecronDropPredict"))
            f.sub.add(DropdownSetting("Drop Marker Style", "", arrayOf("Box", "Beacon"),
                { Floor7Settings.necronDropStyle },
                { v -> Floor7Settings.necronDropStyle = v }).gatedBy { Floor7Settings.enableNecronDropPredict })
            f.sub.add(ColorPickerSetting("Drop Marker Color", "", Floor7Settings::class.java, "necronDropColor").gatedBy { Floor7Settings.enableNecronDropPredict })
            f.sub.add(ToggleSetting("Lava/Platform Popup", "Says if the predicted drop is over lava or a platform (move it in the HUD editor)", Floor7Settings::class.java, "necronDropLavaPopup").gatedBy { Floor7Settings.enableNecronDropPredict })
            f.sub.add(SubcategoryHeader("Goldor"))
            f.sub.add(ToggleSetting("Goldor", "", Floor7Settings::class.java, "goldorTicksHud"))
            f.sub.add(ToggleSetting("In 3s Increments", "", Floor7Settings::class.java, "goldorUseDeathTicks").gatedBy { Floor7Settings.goldorTicksHud })
            f.sub.add(ToggleSetting("Tick Up", "", Floor7Settings::class.java, "goldorTicksCountUp").gatedBy { Floor7Settings.goldorTicksHud })
            f.sub.add(ToggleSetting("Terminals Open In", "", Floor7Settings::class.java, "termOpenHud"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Crystal Respawn", Floor7Settings::class.java, "crystalSpawnHud")
            f.sub.add(ToggleSetting("Place Reminder", "", Floor7Settings::class.java, "crystalPlaceAlert"))
            f.sub.add(ToggleSetting("Instant Reminder", "", Floor7Settings::class.java, "crystalAlertInstant").gatedBy { Floor7Settings.crystalPlaceAlert })
            floor7.features.add(f)
        }
        run {
            val f = Feature("Section Count", Floor7Settings::class.java, "sectionProgressHud")
            f.sub.add(ToggleSetting("Color w/ Progress", "", Floor7Settings::class.java, "sectionProgressColored"))
            f.sub.add(ToggleSetting("Prev Objective", "", Floor7Settings::class.java, "sectionShowPrevious"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Hide Terminal Titles", Floor7Settings::class.java, "muteTerminalTitles")
            f.sub.add(SubcategoryHeader("Hides Hypixel titles and subtitles during P3 (terminal/device/gate spam). Death and revive titles still show."))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Terminal Tracker", TerminalTracker::class.java, "terminalTrackerEnabled")
            f.sub.add(SubcategoryHeader("Per-section list of who finished each terminal/device/lever and what is missing."))
            f.sub.add(ToggleSetting("Show All Sections", "Show earlier sections too", TerminalTracker::class.java, "showAllSections"))
            f.sub.add(ToggleSetting("Log Positions", "Print each completion with the player position to chat and the log", TerminalTracker::class.java, "logPositions"))
            f.sub.add(ColorPickerSetting("Done", "", TerminalTracker::class.java, "doneColor"))
            f.sub.add(ColorPickerSetting("Pending", "", TerminalTracker::class.java, "pendingColor"))
            f.sub.add(ColorPickerSetting("Header", "", TerminalTracker::class.java, "headerColor"))
            f.sub.add(ColorPickerSetting("Player Name", "", TerminalTracker::class.java, "nameColor"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Goldor Splits", GoldorSection::class.java, "goldorSplitsEnabled")
            f.sub.add(ToggleSetting("PB Colors", "Pink section time on a new PB", "splitPbColors"))
            f.sub.add(ColorPickerSetting("New PB", "", "splitPbColor").gatedBy { FishSettings.splitPbColors })
            GoldorSection.sectionSplits().forEachIndexed { i, s ->
                f.sub.add(ColorPickerSetting("S${i + 1} Name", "", { s.nameColor() }, { v -> RunSplit.setNameColor(s.name, v) }, s.defaultNameColor()))
            }
            f.sub.add(DropdownSetting("Show During", "",
                GoldorSection.SplitsShownIn.values(),
                { GoldorSection.goldorSplitsShownIn },
                { v -> GoldorSection.goldorSplitsShownIn = v }))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Gate Display", Floor7Settings::class.java, "gateDisplayEnabled")
            f.sub.add(SliderDoubleSetting("Text Scale", "",
                { Floor7Settings.gateDisplayScale.toDouble() },
                { v -> Floor7Settings.gateDisplayScale = v.toFloat() },
                1.0, 12.0))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Blood Solver", Floor7Settings::class.java, "bloodSolverEnabled")
            f.sub.add(ToggleSetting("Kill Mobs Title", "Title when the blood mobs are ready to kill — learn your own move times for accuracy", Floor7Settings::class.java, "bloodSolverKillTitle"))
            f.sub.add(ToggleSetting("Watcher Speed Alert", "Title + sound for fast / normal / slow Watcher", Floor7Settings::class.java, "bloodSolverSpeedAlert"))
            f.sub.add(ToggleSetting("Send Speed In Party", "Also /pc the Watcher speed", Floor7Settings::class.java, "bloodSolverSpeedAlertParty").gatedBy { Floor7Settings.bloodSolverSpeedAlert })
            f.sub.add(SliderIntSetting("Timer Decimals", "", Floor7Settings::class.java, "bloodSolverDecimals", 0, 2))
            f.sub.add(ColorPickerSetting("Box Color", "", Floor7Settings::class.java, "bloodSolverBoxColor"))
            f.sub.add(ColorPickerSetting("Line Color", "", Floor7Settings::class.java, "bloodSolverLineColor"))
            f.sub.add(ColorPickerSetting("Timer Color", "", Floor7Settings::class.java, "bloodSolverTimerColor"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Players Leaped", Floor7Settings::class.java, "playersLeapedEnabled")
            f.sub.add(SubcategoryHeader("Stand in your leap spot to see how many teammates are there: Mage HEE2 ?/4, Bers S1 ?/1, Healer EE3 ?/3, Mage Core ?/4, Healer Dragons ?/4"))
            f.sub.add(ToggleSetting("Any Class", "Show at every spot regardless of your class (for testing)", Floor7Settings::class.java, "playersLeapedAnyClass"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Terminal Solver", "terminalSolverEnabled")
            val terminalRenderModeOptions = arrayOf("Overlay", "Custom GUI")
            f.sub.add(DropdownSetting("Render Mode", "Custom GUI replaces the chest with a big rounded board",
                terminalRenderModeOptions,
                { terminalRenderModeOptions.getOrElse(FishSettings.terminalRenderMode) { terminalRenderModeOptions[0] } },
                { v -> FishSettings.terminalRenderMode = terminalRenderModeOptions.indexOf(v).coerceAtLeast(0) }))
            f.sub.add(SliderDoubleSetting("Custom Scale", "", "terminalCustomScale", 0.5, 3.0).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(SliderIntSetting("Custom Roundness", "", "terminalCustomRoundness", 0, 15).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(SliderIntSetting("Custom Gap", "", "terminalCustomGap", 0, 15).gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(ColorPickerSetting("Custom Background", "", "terminalCustomBg").gatedBy { FishSettings.terminalRenderMode == 1 })
            f.sub.add(ToggleSetting("Block Wrong Clicks", "", "terminalBlockWrongClicks"))
            f.sub.add(ToggleSetting("Middle Click GUI", "Send terminal clicks as a middle-click so items never touch the cursor (right stays right)", "terminalMiddleClickGui"))
            f.sub.add(ToggleSetting("Stop Tooltips", "Hide hover tooltips in terminals", "terminalStopTooltips"))
            f.sub.add(ToggleSetting("Hide Wrong Items", "Cover non-solution slots", "terminalHideWrong"))
            f.sub.add(ToggleSetting("Show Numbers", "Order # on Numbers/Rubix slots", "terminalShowNumbers"))
            f.sub.add(ToggleSetting("Stop Melody Solver", "", "terminalStopMelody"))
            f.sub.add(ToggleSetting("Solve Sound", "Ping when you finish a terminal", "terminalSolverSound"))
            f.sub.add(SliderIntSetting("First Click Protection (ms)","Block clicks for this long after a terminal opens (Odin: ~500 minus your ping; 0 = off)", "terminalFirstClickProtMs", 0, 800, 10))
            f.sub.add(SubcategoryHeader("Colors"))
            f.sub.add(ColorPickerSetting("Panes", "", "terminalHighlightColor"))
            f.sub.add(ColorPickerSetting("Starts With", "", "terminalStartsWithColor"))
            f.sub.add(ColorPickerSetting("Select", "", "terminalSelectColor"))
            f.sub.add(ColorPickerSetting("Order 1st", "", "terminalOrderColor1"))
            f.sub.add(ColorPickerSetting("Order 2nd", "", "terminalOrderColor2"))
            f.sub.add(ColorPickerSetting("Order 3rd", "", "terminalOrderColor3"))
            f.sub.add(ColorPickerSetting("Rubix +1", "", "terminalRubixColor"))
            f.sub.add(ColorPickerSetting("Rubix +2", "", "terminalRubixColor2"))
            f.sub.add(ColorPickerSetting("Rubix -1", "", "terminalRubixNeg1"))
            f.sub.add(ColorPickerSetting("Rubix -2", "", "terminalRubixNeg2"))
            f.sub.add(ColorPickerSetting("Melody", "", "terminalMelodyPointerColor"))
            f.sub.add(ColorPickerSetting("Melody Column", "Bar showing the column to click on", "terminalMelodyColor"))
            f.sub.add(ColorPickerSetting("Wrong-Item Cover", "", "terminalWrongCover"))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Arrow Align", "arrowAlignEnabled")
            f.sub.add(ToggleSetting("Block Wrong Clicks", "Cancels rotating a frame that isn't in the solution (hold sneak to override)", "arrowAlignBlockWrong"))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Simon Says Solver", "simonSolverEnabled")
            f.sub.add(ToggleSetting("Through Walls", "", "simonSolverDepth"))
            f.sub.add(ToggleSetting("Block Wrong Clicks", "Cancels clicks on the wrong button (hold sneak to override)", "simonSolverBlockWrong"))
            f.sub.add(ColorPickerSetting("Next", "", "simonSolverColor1"))
            f.sub.add(ColorPickerSetting("Second", "", "simonSolverColor2"))
            f.sub.add(ColorPickerSetting("Rest", "", "simonSolverColor3"))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Melody Message", "melodyMessageEnabled")
            f.sub.add(ToggleSetting("Announce on Open", "Party message when the melody terminal opens", "melodyMessageOnOpen"))
            f.sub.add(InputSetting("Open Message", "",
                { FishSettings.melodyMessageText }, { v -> FishSettings.melodyMessageText = v ?: "" })
                .gatedBy { FishSettings.melodyMessageOnOpen })
            f.sub.add(ToggleSetting("Progress Calls", "Party-message 25/50/75% as the marker drops", "melodyMessageProgress"))
            solvers.features.add(f)
        }
        run {
            val f = Feature("Arrows Device", "arrowsDeviceEnabled")
            f.sub.add(ToggleSetting("Through Walls", "", "arrowsDeviceDepth"))
            f.sub.add(ToggleSetting("Complete Alert", "Title when the device finishes", "arrowsDeviceCompleteAlert"))
            f.sub.add(ColorPickerSetting("Target", "Emerald block to shoot", "arrowsDeviceTargetColor"))
            f.sub.add(ColorPickerSetting("Hit", "Already-shot block", "arrowsDeviceMarkedColor"))
            f.sub.add(ToggleSetting("Show Aim Positions", "Optimal aim points for hitting adjacent blocks together", "arrowsDeviceShowAim"))
            f.sub.add(ColorPickerSetting("Aim 1st", "", "arrowsDeviceAim1Color").gatedBy { FishSettings.arrowsDeviceShowAim })
            f.sub.add(ColorPickerSetting("Aim 2nd", "", "arrowsDeviceAim2Color").gatedBy { FishSettings.arrowsDeviceShowAim })
            f.sub.add(ColorPickerSetting("Aim 3rd", "", "arrowsDeviceAim3Color").gatedBy { FishSettings.arrowsDeviceShowAim })
            solvers.features.add(f)
        }
        run {
            val f = Feature("Storm LB Waypoint", "stormLbWaypointEnabled")
            f.sub.add(ColorPickerSetting("Color", "", "stormLbWaypointColor"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Wither Highlight", "witherEspEnabled")
            f.sub.add(ColorPickerSetting("Maxor", "", "witherEspMaxorColor"))
            f.sub.add(ColorPickerSetting("Storm", "", "witherEspStormColor"))
            f.sub.add(ColorPickerSetting("Goldor", "", "witherEspGoldorColor"))
            f.sub.add(ColorPickerSetting("Necron", "", "witherEspNecronColor"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Storm Over Alert", "stormOverEnabled")
            f.sub.add(SubcategoryHeader("5s countdown + title at 26.75s once Storm has used both lightning procs — move it in the HUD editor"))
            f.sub.add(InputSetting("Text", "", "stormOverText"))
            f.sub.add(ColorPickerSetting("Color", "", "stormOverColor"))
            f.sub.add(ToggleSetting("Sound", "", "stormOverSound"))
            f.sub.add(ToggleSetting("5s Countdown", "Counts down the last 5s before Storm is over", "stormOverCountdown"))
            f.sub.add(ToggleSetting("Hide Server Countdown", "Hides the 5-4-3-2-1 titles Hypixel sends during Storm", "stormOverHideServerCountdown"))
            f.sub.add(SliderIntSetting("Duration (ms)", "", "stormOverDurationMs", 500, 6000, 250))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Veno Stack Count", "venoStackEnabled")
            f.sub.add(SubcategoryHeader("Counts your Mage beam hits on Storm while holding a Venomous weapon (x/40) with a 5s refresh timer — move it in the HUD editor"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("M7 Relics", Floor7Settings::class.java, "relicStartHud")
            f.sub.add(ToggleSetting("Cauldron Box", "Box + tracer the cauldron for the relic you hold", Floor7Settings::class.java, "relicHighlight"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Relic Times", Floor7Settings::class.java, "relicTimesEnabled")
            f.sub.add(SubcategoryHeader("Once all 5 relics are placed, lists each one's place time (from P5 start) and who placed it"))
            f.sub.add(ToggleSetting("Send To Party Chat", "Off = only you see it", Floor7Settings::class.java, "relicTimesParty"))
            floor7.features.add(f)
        }
        run {
            val f = Feature("Wither Dragons", "witherDragonsEnabled")
            f.sub.add(ToggleSetting("Spawn Timer (World)", "In-world countdown on each dragon's hitbox", "witherDragonsTimerWorld"))
            f.sub.add(ToggleSetting("Spawn Timer (HUD)", "On-screen countdown for the priority dragon — movable in the HUD editor", "witherDragonsTimerHud"))
            val timerStyleOptions = arrayOf("Milliseconds", "Seconds", "Ticks")
            f.sub.add(DropdownSetting("Timer Style", "", timerStyleOptions,
                { timerStyleOptions.getOrElse(FishSettings.witherDragonsTimerStyle) { timerStyleOptions[0] } },
                { v -> FishSettings.witherDragonsTimerStyle = timerStyleOptions.indexOf(v).coerceAtLeast(0) })
                .gatedBy { FishSettings.witherDragonsTimerWorld || FishSettings.witherDragonsTimerHud })
            f.sub.add(ToggleSetting("Spawn Alert (Title)", "Title with the priority dragon's colour when a wave starts spawning (NoammAddons)", "witherDragonsSpawnAlert"))
            f.sub.add(ToggleSetting("Spawn Alert Sound", "", "witherDragonsSpawnSound").gatedBy { FishSettings.witherDragonsSpawnAlert })
            f.sub.add(ToggleSetting("Spawn Alert → Party", "Call the priority dragon in party chat", "witherDragonsSpawnParty").gatedBy { FishSettings.witherDragonsSpawnAlert })
            f.sub.add(ToggleSetting("Dragon Health", "", "witherDragonsHealth"))
            f.sub.add(ToggleSetting("Skip Box", "", "witherDragonsSkipBox"))
            f.sub.add(ToggleSetting("Fill Skip Box", "", "witherDragonsBoxFill").gatedBy { FishSettings.witherDragonsSkipBox })
            f.sub.add(ToggleSetting("Priority Tracer", "Line to the highest-priority spawning dragon", "witherDragonsTracer"))
            f.sub.add(ToggleSetting("Aim Assist", "Box at the arrow-lead point for the ice spray", "witherDragonsAimAssist"))
            f.sub.add(ColorPickerSetting("Tracer / Aim Color", "", "witherDragonsAimColor")
                .gatedBy { FishSettings.witherDragonsTracer || FishSettings.witherDragonsAimAssist })
            f.sub.add(ToggleSetting("Send Kill Stats", "Time / arrows / spray to chat on each dragon death", "witherDragonsSendStats"))
            f.sub.add(SubcategoryHeader("Priority"))
            f.sub.add(ToggleSetting("Custom Priority", "Factor in blessing power + your class", "witherDragonsPriority"))
            f.sub.add(SliderDoubleSetting("Normal Power", "", "witherDragonsNormalPower", 0.0, 32.0).gatedBy { FishSettings.witherDragonsPriority })
            f.sub.add(SliderDoubleSetting("Easy Power", "", "witherDragonsEasyPower", 0.0, 32.0).gatedBy { FishSettings.witherDragonsPriority })
            val soloDebuffOptions = arrayOf("Tank", "Healer")
            f.sub.add(DropdownSetting("Purple Solo Debuff", "", soloDebuffOptions,
                { soloDebuffOptions.getOrElse(FishSettings.witherDragonsSoloDebuff) { soloDebuffOptions[0] } },
                { v -> FishSettings.witherDragonsSoloDebuff = soloDebuffOptions.indexOf(v).coerceAtLeast(0) })
                .gatedBy { FishSettings.witherDragonsPriority })
            f.sub.add(ToggleSetting("Solo Debuff on All Splits", "", "witherDragonsSoloDebuffAll").gatedBy { FishSettings.witherDragonsPriority })
            floor7.features.add(f)
        }

        for (et in FishModAddonApi.dungeonToggles) {
            dungeon.features.add(Feature(et.name(), { et.get().get() }, { v -> et.set().accept(v) }))
        }
        val cheats = Column("Cheats")
        for (et in FishModAddonApi.cheatToggles) {
            val f = Feature(et.name(), { et.get().get() }, { v -> et.set().accept(v) })
            for (make in et.subSettings) f.sub.add(make())
            cheats.features.add(f)
        }

        run {
            val f = Feature("Enable Map", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapEnabled")
            f.sub.add(ColorPickerSetting("Background Color", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapBackgroundColor"))
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
            f.sub.add(ToggleSetting("Center Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapTextCenter"))
            f.sub.add(ToggleSetting("Ugly Question Marks", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapUglyQuestionMarks"))
            f.sub.add(ToggleSetting("Show Room Secrets", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapShowRoomSecrets"))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Background Image", { fishmod.utils.config.values.DungeonMapSettings.mapImageSelection.isNotEmpty() },
                { v ->
                    val s = fishmod.utils.config.values.DungeonMapSettings
                    if (v) {
                        val restore = s.mapImageLastSelection.ifEmpty {
                            fishmod.features.dungeon.map.MapImageLoader.getImageNames()
                                .firstOrNull { it != fishmod.features.dungeon.map.MapImageLoader.NO_IMAGE } ?: ""
                        }
                        s.mapImageSelection = restore
                    } else {
                        if (s.mapImageSelection.isNotEmpty()) s.mapImageLastSelection = s.mapImageSelection
                        s.mapImageSelection = ""
                    }
                })
            f.sub.add(ButtonSetting("Open Images Folder", "") {
                try {
                    fishmod.features.dungeon.map.MapImageLoader.init()
                    fishmod.compat.GLFW.openUri(fishmod.features.dungeon.map.MapImageLoader.getImagesPath().toUri())
                } catch (e: Exception) {
                    FishDiag.fail("FishModScreen.5", "open map images folder failed", e)
                }
            })
            val imageNames: Array<String> = run {
                val names = fishmod.features.dungeon.map.MapImageLoader.getImageNames()
                (if (names.isEmpty()) listOf("No image") else names).toTypedArray()
            }
            f.sub.add(DropdownSetting("Image", "", imageNames,
                { if (fishmod.utils.config.values.DungeonMapSettings.mapImageSelection in imageNames) fishmod.utils.config.values.DungeonMapSettings.mapImageSelection else imageNames[0] },
                { v ->
                    fishmod.utils.config.values.DungeonMapSettings.mapImageSelection = if (v == fishmod.features.dungeon.map.MapImageLoader.NO_IMAGE) "" else v
                    if (v != fishmod.features.dungeon.map.MapImageLoader.NO_IMAGE) fishmod.utils.config.values.DungeonMapSettings.mapImageLastSelection = v
                }))
            f.sub.add(SliderIntSetting("Image Alpha", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapImageAlpha", 0, 255))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Info HUD", { fishmod.utils.config.values.DungeonMapSettings.mapInfoEnabled == true },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapInfoEnabled = v })
            f.sub.add(ToggleSetting("No Words", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoNoWords"))
            f.sub.add(ToggleSetting("Tied to Map", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoMapTied"))
            f.sub.add(ToggleSetting("Show Secrets", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowSecrets"))
            f.sub.add(ToggleSetting("Secrets Tail = Left (not total)", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowLeft")
                .gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapInfoShowSecrets })
            f.sub.add(ToggleSetting("Force Paul", "Always count Paul's +10 score (auto-detected when he's mayor or minister)", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoForcePaul"))
            f.sub.add(ToggleSetting("Show Score", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowScore"))
            f.sub.add(ToggleSetting("Show Deaths", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowDeaths"))
            f.sub.add(ToggleSetting("Show Mimic", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowMimic"))
            f.sub.add(ToggleSetting("Show Prince", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowPrince"))
            f.sub.add(ToggleSetting("Show Crypts", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoShowCrypts"))
            f.sub.add(ToggleSetting("Hide When Done", "Drop Mimic/Prince/Crypts once complete", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapInfoHideCompleted"))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Player Heads", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerHeadsEnabled")
            f.sub.add(ColorPickerSetting("Head Background", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerHeadBackground"))
            f.sub.add(ColorPickerSetting("Own Head Background", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerHeadOwnBackground"))
            f.sub.add(SliderIntSetting("Outline Size", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerHeadBackgroundSize", 0, 5))
            f.sub.add(ToggleSetting("Ugly Pointer (Own)", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerUglyPointer"))
            f.sub.add(ToggleSetting("Class Color Outline", "Border the head in the teammate's dungeon class color", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerHeadClassOutline"))
            f.sub.add(ToggleSetting("Draw Own Head Last", "Own head renders on top instead of z-ordered with the rest", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerHeadDrawOwnLast"))
            f.sub.add(SliderIntSetting("Player Name Scale %", "",
                { (fishmod.utils.config.values.DungeonMapSettings.mapPlayerNamesScaling * 100).toInt() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapPlayerNamesScaling = v / 100.0f },
                0, 150))
            f.sub.add(ColorPickerSetting("Name Color", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPlayerNameColor"))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Room Additions", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRoomAdditionsEnabled")
            f.sub.add(ToggleSetting("Prince Crown Icon", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRoomAdditionsPrince"))
            f.sub.add(ToggleSetting("Room Checkmarks", "Corner checkmark when a named room is cleared, recoloured when all secrets are done", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRoomAdditionsChecks"))
            f.sub.add(ColorPickerSetting("Cleared Check", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRoomCheckClearedColor"))
            f.sub.add(ColorPickerSetting("Secrets Done Check", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRoomCheckSecretsColor"))
            f.sub.add(SliderIntSetting("Darken Multiplier %", "",
                { (fishmod.utils.config.values.DungeonMapSettings.mapDarkenMultiplier * 100).toInt() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapDarkenMultiplier = v / 100.0f },
                0, 100))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Door Colors", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapDoorGay")
            f.sub.add(SliderDoubleSetting("Door Thickness", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapDoorThickness.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapDoorThickness = v.toFloat() },
                0.0, 10.0))
            f.sub.add(ColorPickerSetting("Unopened", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapUnopenedDoorColor"))
            f.sub.add(ColorPickerSetting("Blood", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapBloodDoorColor"))
            f.sub.add(ColorPickerSetting("Wither", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapWitherDoorColor"))
            f.sub.add(ColorPickerSetting("Normal", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapNormalDoorColor"))
            f.sub.add(ColorPickerSetting("Puzzle", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPuzzleDoorColor"))
            f.sub.add(ColorPickerSetting("Champion", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapChampionDoorColor"))
            f.sub.add(ColorPickerSetting("Trap", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapTrapDoorColor"))
            f.sub.add(ColorPickerSetting("Entrance", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapEntranceDoorColor"))
            f.sub.add(ColorPickerSetting("Fairy", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapFairyDoorColor"))
            f.sub.add(ColorPickerSetting("Rare", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRareDoorColor"))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Door Highlight", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapDoorHighlightEnabled")
            f.sub.add(SliderDoubleSetting("Width", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapDoorHighlightWidth.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapDoorHighlightWidth = v.toFloat() },
                1.0, 10.0))
            f.sub.add(ToggleSetting("Through Walls", "Every highlighted door, not just Wither",
                fishmod.utils.config.values.DungeonMapSettings::class.java, "mapDoorHighlightThroughWall"))
            f.sub.add(ToggleSetting("Full Box", "Highlight the whole frame, not just the near face",
                fishmod.utils.config.values.DungeonMapSettings::class.java, "mapDoorHighlightFullBox"))
            f.sub.add(ColorPickerSetting("Wither: No Key", "Wither-door box until the Wither Key is picked up (turns green once held)",
                fishmod.utils.config.values.DungeonMapSettings::class.java, "mapWitherHighlightMissingColor"))
            f.sub.add(ToggleSetting("Outline Only", "Outline every door in one colour, no fill or key colours",
                fishmod.utils.config.values.DungeonMapSettings::class.java, "mapDoorOutlineOnly"))
            f.sub.add(ColorPickerSetting("Outline Color", "Colour used by Outline Only",
                fishmod.utils.config.values.DungeonMapSettings::class.java, "mapDoorOutlineColor"))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Room Colors", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRoomColorsEnabled")
            f.sub.add(ColorPickerSetting("Unopened", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapUnopenedRoomColor"))
            f.sub.add(ColorPickerSetting("Blood", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapBloodRoomColor"))
            f.sub.add(ColorPickerSetting("Normal", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapNormalRoomColor"))
            f.sub.add(ColorPickerSetting("Puzzle", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapPuzzleRoomColor"))
            f.sub.add(ColorPickerSetting("Champion", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapChampionRoomColor"))
            f.sub.add(ColorPickerSetting("Trap", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapTrapRoomColor"))
            f.sub.add(ColorPickerSetting("Entrance", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapEntranceRoomColor"))
            f.sub.add(ColorPickerSetting("Fairy", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapFairyRoomColor"))
            f.sub.add(ColorPickerSetting("Rare", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapRareRoomColor"))
            dungeonMap.features.add(f)
        }
        run {
            val f = Feature("Score Messages", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScoreMessages")
            f.sub.add(ToggleSetting("Score Missing Msg (1min, to party)", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScoreMissingMsg"))
            f.sub.add(SubcategoryHeader("270 Score"))
            f.sub.add(ToggleSetting("Title", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore270Title"))
            val t270t = InputSetting("Title Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore270TitleText")
            t270t.hint = "& color codes, <time> ok"
            f.sub.add(t270t.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore270Title })
            f.sub.add(ToggleSetting("Party Chat", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore270MessageEnabled"))
            val t270c = InputSetting("Party Chat Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore270Message")
            t270c.hint = "<time> ok"
            f.sub.add(t270c.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore270MessageEnabled })
            f.sub.add(ToggleSetting("Client-only Msg", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore270ClientEnabled"))
            val t270cl = InputSetting("Client-only Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore270ClientMessage")
            t270cl.hint = "& color codes, <time> ok"
            f.sub.add(t270cl.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore270ClientEnabled })
            f.sub.add(SubcategoryHeader("300 Score"))
            f.sub.add(ToggleSetting("Title", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore300Title"))
            val t300t = InputSetting("Title Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore300TitleText")
            t300t.hint = "& color codes, <time> ok"
            f.sub.add(t300t.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore300Title })
            f.sub.add(ToggleSetting("Party Chat", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore300MessageEnabled"))
            val t300c = InputSetting("Party Chat Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore300Message")
            t300c.hint = "<time> ok"
            f.sub.add(t300c.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore300MessageEnabled })
            f.sub.add(ToggleSetting("Client-only Msg", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore300ClientEnabled"))
            val t300cl = InputSetting("Client-only Text", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScore300ClientMessage")
            t300cl.hint = "& color codes, <time> ok"
            f.sub.add(t300cl.gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScore300ClientEnabled })
            f.sub.add(SubcategoryHeader("Title Display"))
            f.sub.add(SliderDoubleSetting("Title Scale", "",
                { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale.toDouble() },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale = v.toFloat() },
                0.5, 4.0))
            f.sub.add(ToggleSetting("Title Sound", "", fishmod.utils.config.values.DungeonMapSettings::class.java, "mapScoreTitleSound"))
            f.sub.add(DropdownSetting("Sound", "", fishmod.features.dungeon.map.ScoreMessages.SOUND_OPTIONS,
                { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSoundId },
                { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSoundId = v })
                .gatedBy { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleSound })
            dungeonMap.features.add(f)
        }

        val slayer = Column("Slayer")
        run {
            val spawnAlert = Feature("Mini/Boss Spawn Alert", "slayerSpawnAlertEnabled")
            spawnAlert.sub.add(ToggleSetting("Mini-Boss Alerts", "Alert when a slayer miniboss spawns", "slayerMiniBossAlert"))
            spawnAlert.sub.add(ToggleSetting("Boss Alerts", "Alert when the main slayer boss spawns", "slayerBossAlert"))
            spawnAlert.sub.add(SliderIntSetting("Alert Duration (ms)", "On-screen time for spawn alerts", "slayerAlertDurationMs", 250, 8000, 250))
            slayer.features.add(spawnAlert)

            val cocoon = Feature("Cocoon Alert", "slayerCocoonAlertEnabled")
            cocoon.sub.add(SubcategoryHeader("Fires on \"YOU COCOONED YOUR SLAYER BOSS\""))
            cocoon.sub.add(SliderIntSetting("Alert Duration (ms)", "", "slayerCocoonAlertDurationMs", 250, 8000, 250))
            slayer.features.add(cocoon)

            val rngTitle = Feature("RNG Meter HUD", "slayerRngTitleEnabled")
            rngTitle.sub.add(SubcategoryHeader("Last \"RNG Meter - ...\" value, updates after every boss  ·  drag position with Edit HUD"))
            rngTitle.sub.add(ColorPickerSetting("Label Colour", "", { FishSettings.slayerRngTitleColor }, { v -> FishSettings.slayerRngTitleColor = v }, 0xFFFF55FF.toInt()))
            rngTitle.sub.add(SliderDoubleSetting("Scale", "", "slayerRngHudScale", 0.5, 3.0))
            slayer.features.add(rngTitle)

            val spawnHud = Feature("Spawn Progress HUD", "slayerSpawnHudEnabled")
            spawnHud.sub.add(SubcategoryHeader("Live spawn-bar %  ·  drag position with Edit HUD"))
            spawnHud.sub.add(SliderDoubleSetting("Scale", "", "slayerSpawnHudScale", 0.5, 3.0))
            slayer.features.add(spawnHud)

            val statsHud = Feature("Slayer Stats HUD", "slayerStatsHudEnabled")
            statsHud.sub.add(SubcategoryHeader("Total XP / kills per slayer  ·  drag position with Edit HUD"))
            statsHud.sub.add(ToggleSetting("Show XP", "", "slayerStatsShowXp"))
            statsHud.sub.add(ToggleSetting("Show Kills", "", "slayerStatsShowKills"))
            statsHud.sub.add(SliderDoubleSetting("Scale", "", "slayerStatsHudScale", 0.5, 3.0))
            statsHud.sub.add(ButtonSetting("Reset Session Stats", "Zero the XP / kills counters", Runnable { fishmod.features.slayers.SlayerStatsTracker.reset() }))
            slayer.features.add(statsHud)

            val carry = Feature("Carry Tracker HUD", "slayerCarryHudEnabled")
            carry.sub.add(SubcategoryHeader("/trackcarry <ign> [amount]  ·  /trackcarry remove <ign>  ·  /trackcarry clear"))
            carry.sub.add(SubcategoryHeader("Counts bosses \"Spawned by\" that player dying near you"))
            carry.sub.add(SliderDoubleSetting("Scale", "", "slayerCarryHudScale", 0.5, 3.0))
            slayer.features.add(carry)

            val profit = Feature("Profit Tracker", "slayerProfitEnabled")
            profit.sub.add(SubcategoryHeader("SkyHanni-style: prices real drops for coins/hr  ·  drag with Edit HUD"))
            profit.sub.add(SubcategoryHeader("With chat open: click the mode line to switch  ·  left-click a row to hide it  ·  right-click the title to reset"))
            profit.sub.add(DropdownSetting("Display", "Total = all-time (saved); This Session = since this launch", arrayOf("Total", "This Session"),
                { FishSettings.slayerProfitDisplayMode },
                { v -> FishSettings.slayerProfitDisplayMode = v }))
            profit.sub.add(SliderIntSetting("Drop Rows", "Max item rows shown (highest value first); the rest fold into one row", "slayerProfitLines", 3, 30, 1))
            profit.sub.add(SliderIntSetting("Hide Below (coins)", "Rows worth less than this fold into the \"N more items\" row (0 = show all)", "slayerProfitMinValue", 0, 1_000_000, 10_000))
            profit.sub.add(ToggleSetting("Count Mob Kill Coins", "Count small purse gains while grinding as a \"Mob Kill Coins\" drop row + profit", "slayerProfitCountKillCoins"))
            profit.sub.add(ToggleSetting("Always Show Hidden Rows", "Keep hidden rows on screen (dark + struck) even when chat is closed", "slayerProfitShowHidden"))
            profit.sub.add(SliderIntSetting("Idle Pause (s)", "No drop/kill this long → pause & rewind the clock by this much", "slayerProfitIdleSeconds", 15, 600, 15))
            profit.sub.add(SliderDoubleSetting("Scale", "", "slayerProfitHudScale", 0.5, 3.0))
            profit.sub.add(ButtonSetting("Reset This Mode", "Clear drops / bosses / time for the current Display mode, every slayer", Runnable { fishmod.features.slayers.SlayerProfitTracker.reset() }))
            slayer.features.add(profit)

            val timer = Feature("Boss Timer", "slayerTimerEnabled")
            timer.sub.add(SubcategoryHeader("Spawn-to-kill time + PB  ·  drag position with Edit HUD"))
            timer.sub.add(DropdownSetting("Start Mode", "When the clock starts", arrayOf("Spawned", "Fully Spawned"),
                { FishSettings.slayerTimerStartMode },
                { v -> FishSettings.slayerTimerStartMode = v }))
            timer.sub.add(ToggleSetting("Show Current Timer", "", "slayerTimerShowCurrent"))
            timer.sub.add(ToggleSetting("Show PB", "", "slayerTimerShowPb"))
            timer.sub.add(ToggleSetting("Show New PB", "", "slayerTimerShowNewPb"))
            timer.sub.add(ToggleSetting("Show Cycle", "Full kill-to-kill time (fight + loot + walk + refill) + a live 'since kill' counter", "slayerTimerShowCycle"))
            timer.sub.add(SliderDoubleSetting("Scale", "", "slayerTimerHudScale", 0.5, 3.0))
            slayer.features.add(timer)

            val phases = Feature("Boss Phases", "slayerPhaseEnabled")
            phases.sub.add(SubcategoryHeader("SkyHanni-style attack/phase cues on the boss — all 6 slayers"))
            phases.sub.add(SubcategoryHeader("Voidgloom laser/hits/beacon · Inferno shield+dagger/fire pillar/pits · Bloodfiend twinclaws/steak/mania · Rev BOOM · Sven PUPS · Tara hatchlings"))
            phases.sub.add(ToggleSetting("World Text", "Draw the cue as text above the boss", "slayerPhaseWorldText"))
            phases.sub.add(ToggleSetting("Title Warnings", "Big title for BOOM / PUPS / HATCHLINGS / FIRE PITS / TWINCLAWS / STEAK / BEACON", "slayerPhaseTitles"))
            phases.sub.add(ToggleSetting("Health Phase Split", "Show the 1/3 · 2/3 phase fraction (Voidgloom / Inferno)", "slayerPhaseHealthSplit"))
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
        columns.add(fishmod.features.diana.DianaMenu.column())
        columns.add(fishmod.features.fishing.FishingMenu.column())
        columns.add(fishmod.features.mining.MiningMenu.column())
        columns.add(fishmod.features.kuudra.KuudraMenu.column())
        columns.add(fishmod.features.rift.RiftMenu.column())
        columns.add(fishmod.features.extras.ExtrasMenu.column())
        columns.add(fishmod.features.garden.GardenMenu.column())
        columns.add(fishmod.features.achievements.AchMenu.column())
        columns.add(fishmod.features.gamble.GambleMenu.column())
        columns.add(visuals)
        columns.add(cosmetics)
        if (cheats.features.isNotEmpty()) columns.add(cheats)
    }

    private fun applySavedColumnOrder() {
        val saved = FishSettings.fmColumnOrder
        if (saved.isBlank()) return
        val byName = columns.associateBy { it.name }
        val used = HashSet<String>()
        val reordered = ArrayList<Column>(columns.size)
        for (slot in saved.split(",")) {
            // old merged slots ("1:A+B") split into separate columns
            val names = slot.substringAfter(':').split("+")
            for (n in names) { val c = byName[n]; if (c != null && used.add(n)) reordered.add(c) }
        }
        for (c in columns) if (used.add(c.name)) reordered.add(c)
        columns.clear()
        columns.addAll(reordered)
    }

    private fun loadFolders() {
        folders.clear()
        for (entry in FishSettings.fmSidebarFolders.split(";")) {
            val parts = entry.split("|")
            if (parts.size < 3 || parts[0].isBlank()) continue
            folders.add(Folder(parts[0], parts[1] == "1", LinkedHashSet(parts[2].split(",").filter { it.isNotBlank() })))
        }
        normalizeFolders()
    }

    private fun saveFolders() {
        FishSettings.fmSidebarFolders = folders.joinToString(";") { f ->
            f.name + "|" + (if (f.open) "1" else "0") + "|" + f.members.joinToString(",")
        }
    }

    private fun folderOf(c: Column): Folder? = folders.firstOrNull { c.name in it.members }

    // drop stale members and keep each folder's columns contiguous
    private fun normalizeFolders() {
        val top = columns.map { it.name }.toHashSet()
        for (f in folders) f.members.retainAll(top)
        val out = ArrayList<Column>(columns.size)
        val done = HashSet<Folder>()
        for (c in columns) {
            val f = folderOf(c)
            if (f == null) { out.add(c); continue }
            if (done.add(f)) columns.filterTo(out) { folderOf(it) === f }
        }
        columns.clear()
        columns.addAll(out)
    }

    private fun saveColumnOrder() {
        normalizeFolders()
        saveFolders()
        FishSettings.fmColumnOrder = columns.joinToString(",") { top ->
            top.name
        }
    }

    private fun loadFoldedColumns() {
        foldedColumns.clear()
        for (n in FishSettings.fmFoldedColumns.split(",")) if (n.isNotBlank()) foldedColumns.add(n)
    }

    private fun saveFoldedColumns() {
        FishSettings.fmFoldedColumns = foldedColumns.joinToString(",")
    }

    private fun isFolded(c: Column): Boolean = c.name in foldedColumns

    private fun toggleFold(c: Column) {
        val unfolding = isFolded(c)
        if (unfolding) foldedColumns.remove(c.name) else foldedColumns.add(c.name)
        saveFoldedColumns()
        frameVisibleColumns = null
        if (unfolding) {
            if (FishSettings.fmAnimations) unfoldStart[c.name] = System.currentTimeMillis() else unfoldStart.remove(c.name)
            val idx = visibleColumns().indexOf(c)
            if (idx >= 0) hScroll = idx * (columnWidth() + COLUMN_GUTTER)
        }
        hScroll = Mth.clamp(hScroll, 0, maxHScroll())
    }

    // unfold every column in the folder, or fold them all if none are folded
    private fun toggleFolderColumns(f: Folder) {
        val cols = columns.filter { folderOf(it) === f }
        if (cols.isEmpty()) return
        val unfolding = cols.any { isFolded(it) }
        for (c in cols) {
            if (unfolding && isFolded(c)) {
                foldedColumns.remove(c.name)
                if (FishSettings.fmAnimations) unfoldStart[c.name] = System.currentTimeMillis() else unfoldStart.remove(c.name)
            } else if (!unfolding) foldedColumns.add(c.name)
        }
        saveFoldedColumns()
        frameVisibleColumns = null
        if (unfolding) {
            val idx = visibleColumns().indexOf(cols[0])
            if (idx >= 0) hScroll = idx * (columnWidth() + COLUMN_GUTTER)
        }
        hScroll = Mth.clamp(hScroll, 0, maxHScroll())
    }

    private fun left(): Int = 0
    private fun top(): Int = 0
    private fun right(): Int = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
    private fun bottom(): Int = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()

    private fun vx(real: Number): Int = (real.toDouble() / fishmod.utils.rendering.UiScale.factor()).toInt()

    private fun cx0(): Int = left() + MARGIN + STACK_W + COLUMN_GUTTER
    private fun cx1(): Int = right() - MARGIN
    private fun cyTop(): Int = top() + TOP_BAR_H + MARGIN + HEADER_H
    private fun cyBot(): Int = bottom() - BOTTOM_RESERVE

    private fun refreshVisibleCacheIfStale() {
        val f = searchText.lowercase()
        if (f == visibleCacheSearch) return
        visibleCacheSearch = f
        visibleFeaturesCache.clear()
    }

    private fun settingMatches(ft: Feature, f: String): Boolean =
        ft.sub.any { it.name.lowercase().contains(f) || it.description.lowercase().contains(f) }

    private fun visibleFeatures(c: Column): List<Feature> {
        refreshVisibleCacheIfStale()
        return visibleFeaturesCache.getOrPut(c) {
            val f = visibleCacheSearch!!
            val out = ArrayList<Feature>()
            for (ft in c.features) {
                if (f.isEmpty() || ft.name.lowercase().contains(f)) out.add(ft)
                else if (settingMatches(ft, f)) out.add(ft)
            }
            out
        }
    }

    private fun visibleColumns(): List<Column> {
        if (frameCaching) frameVisibleColumns?.let { return it }
        return computeVisibleColumns().also { if (frameCaching) frameVisibleColumns = it }
    }

    private fun computeVisibleColumns(): List<Column> {
        refreshVisibleCacheIfStale()
        val out = ArrayList<Column>()
        for (c in columns) {
            val matches = visibleFeatures(c).isNotEmpty()
            if (visibleCacheSearch!!.isEmpty()) { if (!isFolded(c)) out.add(c) }
            else if (matches) out.add(c)
        }
        return out
    }

    private fun fmDropMs(): Int = if (FishSettings.fmAnimations) FishSettings.fmDropDurationMs else 0
    private fun fmStagMs(): Int = if (FishSettings.fmAnimations) FishSettings.fmStaggerDelayMs else 0

    private fun exitTotalDurationMs(): Long {
        val n = visibleColumns().size
        return fmDropMs().toLong() + fmStagMs().toLong() * Math.max(0, n - 1)
    }

    private fun requestClose() {
        if (closing) return
        closing = true
        closeStartTime = System.currentTimeMillis()
    }

    private fun openEase(index: Int): Float {
        if (!FishSettings.fmAnimations) return 1f
        val delay = index.toLong() * fmStagMs()
        val elapsed = System.currentTimeMillis() - screenOpenTime - delay
        if (elapsed <= 0L) return 0f
        val dur = fmDropMs().toFloat()
        val t = if (dur <= 0f) 1f else Mth.clamp(elapsed / dur, 0f, 1f)
        return Easing.easeOutBack(t)
    }

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

    private fun anyColumnAnimating(): Boolean {
        val n = visibleColumns().size
        for (i in 0 until n) {
            if (isColumnOpening(i) || isColumnClosing(i)) return true
        }
        return false
    }

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

    private fun unfoldYOffset(c: Column, restTop: Int): Float {
        val st = unfoldStart[c.name] ?: return 0f
        val dur = fmDropMs().toFloat()
        val elapsed = System.currentTimeMillis() - st
        if (dur <= 0f || elapsed >= dur || closing) { unfoldStart.remove(c.name); return 0f }
        val ease = Easing.easeOutBack(Mth.clamp(elapsed / dur, 0f, 1f))
        return -(restTop + (bottom() - restTop) * 0.25f) * (1f - ease)
    }

    private fun currentCardBg(): Int {
        val alpha = Mth.clamp(Math.round(FishSettings.fmBgAlpha * 2.55f), 0, 255)
        return (alpha shl 24) or cardBgRgb()
    }

    private fun cardBgRgb(): Int {
        return when (FishSettings.fmBgPreset) {
            "Deep Blue" -> 0x0F1E3D
            "Crimson" -> 0x3D0F14
            "Violet" -> 0x2A0F3D
            "Custom" -> FishSettings.fmBgCustomColor and 0xFFFFFF
            else -> 0x14181D
        }
    }

    private fun brighten(rgb: Int, amount: Int): Int {
        val a = (rgb ushr 24) and 0xFF
        val r = Mth.clamp(((rgb ushr 16) and 0xFF) + amount, 0, 255)
        val g = Mth.clamp(((rgb ushr 8) and 0xFF) + amount, 0, 255)
        val b = Mth.clamp((rgb and 0xFF) + amount, 0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun refreshButtonTheme() {
        val rgb = FishSettings.fmButtonColor and 0xFFFFFF
        val alpha = Mth.clamp(Math.round(FishSettings.fmButtonAlpha * 2.55f), 0, 255)
        ACCENT = (alpha shl 24) or rgb
        ACCENT_HOVER = brighten(ACCENT, 28)
        ScreenTheme.ACCENT = ACCENT; ScreenTheme.ACCENT_HOVER = ACCENT_HOVER

        val btnAlpha = Mth.clamp(Math.round(FishSettings.fmRowBgAlpha * 2.55f), 0, 255)
        ROW_BUTTON = (btnAlpha shl 24) or (brighten(cardBgRgb(), 10) and 0xFFFFFF)

        val rowRgb = FishSettings.fmRowColor and 0xFFFFFF
        val rowAlpha = Mth.clamp(Math.round(FishSettings.fmRowAlpha * FishSettings.fmRowBgAlpha * 0.0255f), 0, 255)
        ROW_ENABLED = (rowAlpha shl 24) or rowRgb
    }

    private fun columnWidth(): Int {
        val n = visibleColumns().size
        if (n == 0) return 0
        val avail = (cx1() - cx0()) - (n - 1) * COLUMN_GUTTER
        return (avail / n).coerceIn(MIN_COLUMN_W, MAX_COLUMN_W)
    }

    private fun columnX0(visibleIndex: Int): Int {
        return cx0() + visibleIndex * (columnWidth() + COLUMN_GUTTER) - Math.round(hScrollAnim).toInt()
    }

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

    private class RowLayout(
        val feature: Feature,
        val rowTop: Int, val rowBottom: Int,
        val subTop: Int, val subBottom: Int
    )

    private fun layoutColumn(c: Column, scrollOffset: Int, topY: Int = cyTop()): List<RowLayout> {
        if (!frameCaching) return computeLayout(c, scrollOffset, topY)
        return frameLayouts.getOrPut(Triple(c, scrollOffset, topY)) { computeLayout(c, scrollOffset, topY) }
    }

    private fun computeLayout(c: Column, scrollOffset: Int, topY: Int): List<RowLayout> {
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

    private fun clampAllScrolls() {
        val full = cyBot() - cyTop()
        for (c in visibleColumns()) clampScroll(c, full)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) { }
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) { }

    private var widgetRenderFailureLogged = false
    private var recorderSizeLogged = false

    private var frameCaching = false
    private var frameVisibleColumns: List<Column>? = null
    private val frameLayouts = HashMap<Triple<Column, Int, Int>, List<RowLayout>>()

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        frameVisibleColumns = null
        frameLayouts.clear()
        frameCaching = true
        try {
            extractRenderStateCached(ctx, mouseX, mouseY, delta)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.7", "FishModScreen render failed closing=$closing columns=${columns.size}", t)
        } finally {
            frameCaching = false
        }
    }

    private fun extractRenderStateCached(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        refreshButtonTheme()
        if (resetArmed && System.currentTimeMillis() - resetArmedAt > 3000) resetArmed = false
        clampAllScrolls()
        clampHScroll()

        if (closing && !closeFinalized && System.currentTimeMillis() - closeStartTime >= exitTotalDurationMs()) {
            closeFinalized = true
            try { ConfigRoot.manager.save() } catch (t: Throwable) { FishDiag.fail("FishModScreen.8", "Config save on /fm close failed", t) }
            try { FishConfig.manager.save() } catch (t: Throwable) { FishDiag.fail("FishModScreen.9", "FishConfig save on /fm close failed", t) }
            fishmod.features.achievements.AchUsage.onConfigSaved()
            FishDiag.guard("FishModScreen.ach", "achievement feature scan failed") { fishmod.features.achievements.AchUsage.onFeatureScan(columns.flatMap { it.features }); fishmod.features.achievements.AchUsage.onActivityScan(columns.map { it.name to it.features }) }
            super.onClose()
            return
        }

        UiRecorder.clear()

        extractBlurredBackground(ctx)
        ctx.fillGradient(0, 0, this.width, this.height, DIM_TOP, DIM_BOT)

        val vmx = vx(mouseX)
        val vmy = vx(mouseY)

        hoverDesc = null
        try {
            renderTopBar(ctx, vmx, vmy)
            renderContent(ctx, vmx, vmy)
            renderSearchBar(ctx, vmx, vmy)
            // hint is wider than the sidebar, so only show it while hovering the sidebar
            if (vmx in stackX0()..stackX0() + STACK_W) renderHint(ctx)
            renderHoverTooltip(ctx)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.10", "widget rendering failed search='$searchText' hScroll=$hScroll", t)
            if (!widgetRenderFailureLogged) {
                widgetRenderFailureLogged = true
                fishmod.utils.debug.FishDebug.LOGGER.error("[FishModScreen] widget rendering failed - screen will show blur only", t)
            }
        }

        if (!recorderSizeLogged) {
            recorderSizeLogged = true
            fishmod.utils.debug.FishDebug.LOGGER.debug("[UiRenderer] extractRenderState queued {} draw commands", UiRecorder.size())
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    // Edit HUD pill, right of the search bar
    private fun editHudRect(): IntArray {
        val bh = 28
        val x = (right() - 220) / 2 + 220 + 8
        val y = bottom() - BOTTOM_RESERVE + (BOTTOM_RESERVE - bh) / 2 - 8
        return intArrayOf(x, y, sw(this.font, "Edit HUD", 0.95f) + 28, bh)
    }

    private fun topBarButtonRects(): Array<IntArray> {
        val labels = arrayOf("Report", "Credits", if (resetArmed) "Confirm?" else "Reset", "Save & Close")
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

        val labels = arrayOf("Report", "Credits", if (resetArmed) "Confirm?" else "Reset", "Save & Close")
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
            sst(ctx, this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, 0xFFFFFFFF.toInt(), 0.85f)
        } else {
            roundedRectRing(ctx, x, y, w, h, h / 2 - 1, 1, if (hover) 0xFF20272E.toInt() else 0xFF171C21.toInt(), if (hover) ACCENT_HOVER else accent)
            // accent text is unreadable on dark accents; the ring already shows hover
            sst(ctx, this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, TEXT_COLOR, 0.85f)
        }
    }

    private fun renderSearchBar(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val bw = 220
        val bh = 28
        val bx = (right() - bw) / 2
        val by = bottom() - BOTTOM_RESERVE + (BOTTOM_RESERVE - bh) / 2 - 8
        roundedRectRing(ctx, bx, by, bw, bh, bh / 2, 1, 0xFF14181D.toInt(), if (searchFocused) ACCENT else 0xFF3A3F48.toInt())

        val gx = bx + 16
        val gy = by + bh / 2 - 1
        disc(ctx, gx, gy, 3, SUBTEXT_COLOR)
        UiRecorder.fillRect((gx + 2).toFloat(), (gy + 2).toFloat(), 4f, 1f, SUBTEXT_COLOR)

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
            sst(ctx, this.font, "Search…", bx + 30, by + (bh - 8) / 2, SUBTEXT_COLOR, 1.15f)
        } else {
            nvgTextFieldContent(field, bx + 30, by + 6, bw - 40, bh - 12, 9.5f)
        }
        val e = editHudRect()
        drawPillButton(ctx, e[0], e[1], e[2], e[3], "Edit HUD", false, ACCENT, mouseX >= e[0] && mouseX <= e[0] + e[2] && mouseY >= e[1] && mouseY <= e[1] + e[3])
    }

    private fun renderHint(ctx: GuiGraphicsExtractor) {
        // Keycap controls box, same style as the HUD editor's
        val rows = arrayOf("Click" to "Fold a column", "Drag" to "Move a column", "Drag onto folder" to "File it in the folder", "Click folder" to "Open its columns", "Right-click folder" to "Show/hide its tabs", "Shift+Right-click" to "Rename folder (empty = delete)")
        val ts = 10f
        val ks = 9.5f
        val rowH = 14.5f
        val capH = rowH - 2f
        val pad = 5f
        val capCol = rows.maxOf { UiRecorder.textWidth(it.first, ks) } + 6f
        val cw = pad * 2 + capCol + 5f + rows.maxOf { UiRecorder.textWidth(it.second, ts) }
        val ch = pad * 2 + rows.size * rowH - 2f
        val bx = (left() + MARGIN).toFloat()
        val by = bottom() - ch - 3f
        UiRecorder.roundedRectRing(bx, by, cw, ch, 5f, 1f, 0xE00E1115.toInt(), 0xFF2B333C.toInt())
        var y = by + pad
        for ((k, t) in rows) {
            UiRecorder.roundedRectRing(bx + pad, y, UiRecorder.textWidth(k, ks) + 6f, capH, 2f, 1f, 0xFF1B2027.toInt(), 0xFF3A3F48.toInt())
            UiRecorder.text(k, bx + pad + 3f, y + (capH - ks) / 2f, ks, TEXT_COLOR)
            UiRecorder.text(t, bx + pad + capCol + 5f, y + (capH - ts) / 2f, ts, 0xFFAEB8C2.toInt())
            y += rowH
        }
    }

    private fun renderColumnCard(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, headerTop: Int, cardBottom: Int) {
        val hy = headerTop
        val w = x1 - x0
        UiRecorder.dropShadow(x0.toFloat(), hy.toFloat(), w.toFloat(), (cardBottom - hy).toFloat(), CARD_RADIUS.toFloat(), 10f, 0x60000000)
        roundedRect(ctx, x0, hy, w, cardBottom - hy, CARD_RADIUS, currentCardBg())
        UiRecorder.fillRectTopRounded(x0.toFloat(), hy.toFloat(), w.toFloat(), HEADER_STRIP_H.toFloat(), CARD_RADIUS.toFloat(), ACCENT)
        val titleClip = w - 20
        sst(ctx, this.font, ellipsize(c.name, titleClip), x0 + 10, hy + HEADER_STRIP_H + 6, TEXT_COLOR, 1f)
        c.sub()?.let { sst(ctx, this.font, ellipsize(it, titleClip, 0.8f), x0 + 10, hy + HEADER_STRIP_H + 19, SUBTEXT_COLOR, 0.8f) }
    }

    private var sbCol: Column? = null
    private var sbGrab = 0

    private fun sbBar(c: Column): IntArray? {
        val top = cyTop()
        val vp = Math.min(columnContentHeight(c), cyBot() - top)
        val ms = maxScrollFor(c, vp)
        if (ms <= 0) return null
        val barH = Math.max(20, (vp.toLong() * vp / columnContentHeight(c)).toInt())
        val barY = top + ((vp - barH).toLong() * c.scroll / ms).toInt()
        return intArrayOf(top, vp, ms, barH, barY)
    }

    private fun sbSet(c: Column, my: Int) {
        val b = sbBar(c) ?: return
        val room = b[1] - b[3]
        if (room <= 0) return
        c.scroll = Mth.clamp(((my - sbGrab - b[0]).toLong() * b[2] / room).toInt(), 0, b[2])
    }

    private fun renderColumnScrollbar(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, top: Int, bot: Int) {
        val vp = bot - top
        val ms = maxScrollFor(c, vp)
        if (ms <= 0) return
        val trackX = x1 - 3
        val barH = Math.max(20, (vp.toLong() * vp / columnContentHeight(c)).toInt())
        val barY = top + ((vp - barH).toLong() * c.scroll / ms).toInt()
        UiRecorder.fillRect(trackX.toFloat(), top.toFloat(), 2f, (bot - top).toFloat(), 0xFF141A20.toInt())
        UiRecorder.fillRect(trackX.toFloat(), barY.toFloat(), 2f, barH.toFloat(), ACCENT)
    }

    private fun renderContent(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val cols = visibleColumns()
        val top = cyTop()
        val bot = cyBot()
        val colW = columnWidth()
        val dc = dragColumn

        renderStack(ctx, mouseX, mouseY)
        if (cols.isEmpty()) {
            val msg = "Click a tab on the left to open a column"
            sst(ctx, this.font, msg, cx0() + (cx1() - cx0() - sw(this.font, msg, 1f)) / 2, (top + bot) / 2, SUBTEXT_COLOR, 1f)
            return
        }
        UiRecorder.pushScissor((cx0() - 2).toFloat(), 0f, (right() - cx0() + 2).toFloat(), bottom().toFloat())
        try {
        for (i in cols.indices) {
            val c = cols[i]
            if (c === dc) continue
            val yOff = Math.round(columnYOffset(i, top - HEADER_H) + unfoldYOffset(c, top - HEADER_H))
            try {
                renderOneColumn(ctx, c, columnX0(i), colW, top, bot, if (dc != null) -10000 else mouseX, if (dc != null) -10000 else mouseY, yOff)
            } catch (t: Throwable) {
                FishDiag.fail("FishModScreen.23", "column '${c.name}' render failed", t)
            }
        }

        if (dc != null) {
            renderOneColumn(ctx, dc, dragMouseX - dragGrabDX, colW, top, bot, mouseX, mouseY)
        }
        } finally { UiRecorder.popScissor() }
    }

    private fun stackListTop(): Int = cyTop() - HEADER_H
    private fun stackListBot(): Int = cyBot()
    private fun stackPitch(): Int = STACK_TAB_H + ROW_GAP
    // sidebar row: folder header, column tab, or the "+ New Folder" row
    private class StackRow(val folder: Folder?, val col: Column?, val indent: Int) {
        fun isAdd(): Boolean = folder == null && col == null
    }
    private class StackDrop(val gap: Int, val into: Folder?)

    private fun stackRowList(skipCol: Column? = null, skipFolder: Folder? = null): List<StackRow> {
        val out = ArrayList<StackRow>()
        fun addCol(c: Column, ind: Int) {
            if (c === skipCol) return
            out.add(StackRow(null, c, ind))
        }
        val done = HashSet<Folder>()
        for (c in columns) {
            val f = folderOf(c)
            if (f == null) { addCol(c, 0); continue }
            if (!done.add(f) || f === skipFolder) continue
            out.add(StackRow(f, null, 0))
            if (f.open) for (m in columns) if (folderOf(m) === f) addCol(m, STACK_SUB_INDENT)
        }
        for (f in folders) if (f !in done && f !== skipFolder) out.add(StackRow(f, null, 0))
        out.add(StackRow(null, null, 0))
        return out
    }
    private fun stackMaxScroll(): Int = Math.max(0, stackRowList().size * stackPitch() - ROW_GAP - (stackListBot() - stackListTop()))
    private fun stackX0(): Int = left() + MARGIN
    private fun stackRowY(row: Int): Int = stackListTop() + row * stackPitch() - stackScroll
    private fun stackIdle(): Boolean = stackDragCol == null && stackDragFolder == null

    // where the dragged sidebar row lands: a gap in rows (dragged one excluded) or into a folder
    private fun stackDrop(rows: List<StackRow>): StackDrop {
        val pos = stackDragY - stackGrabDY + STACK_TAB_H / 2 - stackListTop() + stackScroll
        val h = Math.floorDiv(pos, stackPitch())
        val over = rows.getOrNull(h)
        if (stackDragCol != null && over?.folder != null && pos - h * stackPitch() in 6 until STACK_TAB_H - 6) return StackDrop(-1, over.folder)
        return StackDrop(Math.floorDiv(pos + stackPitch() / 2, stackPitch()).coerceIn(0, rows.size - 1), null)
    }

    private fun applyStackDrop() {
        val sc = stackDragCol
        val sf = stackDragFolder
        val rows = stackRowList(sc, sf)
        val d = stackDrop(rows)
        val into = d.into
        if (sc != null && into != null) {
            folderOf(sc)?.members?.remove(sc.name)
            if (into.members.isEmpty()) { columns.remove(sc); columns.add(sc) }
            into.members.add(sc.name)
            into.open = true
        } else {
            val anchor = rows.subList(d.gap, rows.size).firstOrNull()?.takeIf { !it.isAdd() }
            val block = if (sc != null) listOf(sc) else columns.filter { folderOf(it) === sf }
            if (sc != null) folderOf(sc)?.members?.remove(sc.name)
            columns.removeAll(block)
            val af = anchor?.folder ?: anchor?.col?.let { folderOf(it) }
            var idx = when {
                anchor == null -> columns.size
                af != null && (sf != null || anchor.folder != null) -> columns.indexOfFirst { folderOf(it) === af }
                else -> columns.indexOf(anchor.col)
            }
            if (idx < 0) idx = columns.size
            columns.addAll(idx, block)
            if (sc != null && anchor?.col != null) af?.members?.add(sc.name)
            if (sf != null && af != null && af !== sf) { folders.remove(sf); folders.add(folders.indexOf(af), sf) }
        }
        saveColumnOrder()
        frameVisibleColumns = null
    }

    private fun createFolder() {
        var n = folders.size + 1
        while (folders.any { it.name == "Folder $n" }) n++
        val f = Folder("Folder $n", true, LinkedHashSet())
        folders.add(f)
        saveFolders()
        startRename(f)
        stackScroll = stackMaxScroll()
    }

    private fun startRename(f: Folder) {
        commitRename()
        val tf = EditBox(this.font, 0, 0, 100, 14, Component.empty())
        tf.setMaxLength(32)
        tf.setBordered(false)
        tf.value = f.name
        tf.setFocused(true)
        renameField = tf
        renamingFolder = f
    }

    // empty name deletes the folder, its columns stay
    private fun commitRename() {
        val f = renamingFolder ?: return
        val name = renameField?.value?.filter { it !in ";|," }?.trim() ?: f.name
        if (name.isEmpty()) folders.remove(f) else f.name = name
        renamingFolder = null
        renameField = null
        saveColumnOrder()
    }

    // (row, row index)
    private fun stackHitAt(mx: Int, my: Int): Pair<StackRow, Int>? {
        if (mx < stackX0() || mx > stackX0() + STACK_W) return null
        if (my < stackListTop() || my > stackListBot()) return null
        val i = Math.floorDiv(my - stackListTop() + stackScroll, stackPitch())
        val r = stackRowList().getOrNull(i) ?: return null
        return if (my < stackRowY(i) + STACK_TAB_H && mx >= stackX0() + r.indent) r to i else null
    }

    private fun renderStack(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        stackScroll = Mth.clamp(stackScroll, 0, stackMaxScroll())
        val x0 = stackX0()
        val lt = stackListTop()
        val lb = stackListBot()
        val ms = stackMaxScroll()
        val tx0 = x0
        val tw = STACK_W - (if (ms > 0) 6 else 0)
        val dragCol = stackDragCol
        val dragFolder = stackDragFolder
        UiRecorder.pushScissor(x0.toFloat(), lt.toFloat(), STACK_W.toFloat(), (lb - lt).toFloat())
        try {
            val live = !stackIdle() && dragMoved
            val rows = if (live) stackRowList(dragCol, dragFolder) else stackRowList()
            val d = if (live) stackDrop(rows) else null
            var slot = 0
            var anchorY = 0
            fun ease(key: String, target: Double, from: Double = target): Int {
                val cur = stackAnimY[key] ?: from
                var ny = cur + (target - cur) * 0.35
                if (Math.abs(target - ny) < 0.5) ny = target
                stackAnimY[key] = ny
                return Math.round(ny).toInt()
            }
            for ((i, r) in rows.withIndex()) {
                if (d != null && d.gap == i) slot++
                val f = r.folder
                val c = r.col
                val key = when {
                    f != null -> "\u0001" + f.name
                    c == null -> "\u0002"
                    else -> c.name
                }
                val target = stackRowY(slot++).toDouble()
                val y = ease(key, target, if (r.indent > 0) anchorY.toDouble() else target)
                if (r.indent == 0) anchorY = y
                val x = tx0 + r.indent
                val w = tw - r.indent
                when {
                    f != null -> drawStackFolder(ctx, f, x, y, w, mouseX, mouseY, d?.into === f)
                    c == null -> drawStackAddRow(ctx, x, y, w, mouseX, mouseY)
                    else -> drawStackTab(ctx, c, x, y, w, mouseX, mouseY)
                }
            }
            if (live) {
                val dy = stackDragY - stackGrabDY
                if (dragCol != null) {
                    stackAnimY[dragCol.name] = dy.toDouble()
                    drawStackTab(ctx, dragCol, tx0, dy, tw, mouseX, mouseY)
                } else if (dragFolder != null) {
                    stackAnimY["\u0001" + dragFolder.name] = dy.toDouble()
                    drawStackFolder(ctx, dragFolder, tx0, dy, tw, mouseX, mouseY, false)
                }
            }
        } finally { UiRecorder.popScissor() }

        if (ms > 0) {
            val vp = lb - lt
            val barH = Math.max(20, (vp.toLong() * vp / (vp + ms)).toInt())
            val barY = lt + ((vp - barH).toLong() * stackScroll / ms).toInt()
            val trackX = x0 + STACK_W - 2
            UiRecorder.fillRect(trackX.toFloat(), lt.toFloat(), 2f, vp.toFloat(), 0xFF141A20.toInt())
            UiRecorder.fillRect(trackX.toFloat(), barY.toFloat(), 2f, barH.toFloat(), ACCENT)
        }
    }

    private fun drawStackTab(ctx: GuiGraphicsExtractor, c: Column, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int) {
        val open = !isFolded(c)
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), STACK_TAB_H.toFloat(), 4f, 6f, 0x50000000)
        roundedRect(ctx, x, y, w, STACK_TAB_H, 4, currentCardBg())
        roundedRect(ctx, x, y, w, STACK_TAB_H, 4, if (open) ROW_ENABLED else ROW_BUTTON)
        if (stackIdle() && mouseX in x..x + w && mouseY >= y && mouseY < y + STACK_TAB_H) roundedRect(ctx, x, y, w, STACK_TAB_H, 4, ROW_HOVER)
        val cy = y + STACK_TAB_H / 2
        if (open) disc(ctx, x + 9, cy, 3, ACCENT)
        else roundedRectRing(ctx, x + 6, cy - 3, 6, 6, 3, 1, 0, SUBTEXT_COLOR)
        sst(ctx, this.font, ellipsize(c.name, w - 38), x + 18, y + (STACK_TAB_H - 10) / 2, TEXT_COLOR, 1.1f)
        drawChevron(ctx, x + w - 12, cy, open, if (open) TEXT_COLOR else CHEVRON_COLOR)
    }

    private fun drawStackFolder(ctx: GuiGraphicsExtractor, f: Folder, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int, dropTarget: Boolean) {
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), STACK_TAB_H.toFloat(), 4f, 6f, 0x50000000)
        roundedRect(ctx, x, y, w, STACK_TAB_H, 4, currentCardBg())
        roundedRect(ctx, x, y, w, STACK_TAB_H, 4, ROW_BUTTON)
        if (dropTarget) roundedRectRing(ctx, x, y, w, STACK_TAB_H, 4, 1, 0, ACCENT)
        else if (stackIdle() && mouseX in x..x + w && mouseY >= y && mouseY < y + STACK_TAB_H) roundedRect(ctx, x, y, w, STACK_TAB_H, 4, ROW_HOVER)
        val cy = y + STACK_TAB_H / 2
        // folder glyph
        UiRecorder.fillRect((x + 5).toFloat(), (cy - 4).toFloat(), 4f, 2f, ACCENT)
        UiRecorder.fillRect((x + 5).toFloat(), (cy - 3).toFloat(), 9f, 7f, ACCENT)
        val tf = renameField
        if (renamingFolder === f && tf != null) {
            roundedRect(ctx, x + 16, y + 4, w - 34, STACK_TAB_H - 8, 3, 0xFF14181D.toInt())
            nvgTextFieldContent(tf, x + 16, y + 4, w - 34, STACK_TAB_H - 8)
        } else {
            sst(ctx, this.font, ellipsize(f.name + " (" + f.members.size + ")", w - 38), x + 18, y + (STACK_TAB_H - 10) / 2, TEXT_COLOR, 1.1f)
        }
        drawChevron(ctx, x + w - 12, cy, f.open, TEXT_COLOR)
    }

    private fun drawStackAddRow(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int) {
        val hover = stackIdle() && mouseX in x..x + w && mouseY >= y && mouseY < y + STACK_TAB_H
        roundedRectRing(ctx, x, y, w, STACK_TAB_H, 4, 1, 0, if (hover) ACCENT else 0xFF3A3F48.toInt())
        sst(ctx, this.font, "+ New Folder", x + 18, y + (STACK_TAB_H - 10) / 2, TEXT_COLOR, 1.0f)
    }

    private fun renderOneColumn(ctx: GuiGraphicsExtractor, c: Column, x0: Int, colW: Int, top: Int, bot: Int, mouseX: Int, mouseY: Int, yOffset: Int = 0) {
        renderColumnBlock(ctx, c, x0, x0 + colW, top - HEADER_H + yOffset, bot + yOffset, mouseX, mouseY)
    }

    private fun renderColumnBlock(ctx: GuiGraphicsExtractor, c: Column, x0: Int, x1: Int, headerTop: Int, bandBot: Int, mouseX: Int, mouseY: Int) {
        val bodyTop = headerTop + HEADER_H
        val colBottom = Math.min(bodyTop + columnContentHeight(c), bandBot)

        renderColumnCard(ctx, c, x0, x1, headerTop, colBottom)

        UiRecorder.pushScissor(x0.toFloat(), bodyTop.toFloat(), (x1 - x0).toFloat(), (colBottom - bodyTop).toFloat())
        for (rl in layoutColumn(c, c.scroll, bodyTop)) {
            if (rl.rowBottom > bodyTop && rl.rowTop < colBottom) {
                try {
                    renderRow(ctx, rl.feature, x0, x1, rl.rowTop, mouseX, mouseY)
                } catch (t: Throwable) {
                    FishDiag.fail("FishModScreen.21", "row '${rl.feature.name}' in '${c.name}' render failed", t)
                }
            }
            val animH = rl.subBottom - rl.subTop
            if (animH > 0 && rl.subBottom > bodyTop && rl.subTop < colBottom) {
                renderSubPanel(ctx, rl.feature, x0, x1, rl.subTop, animH, mouseX, mouseY)
            }
        }
        UiRecorder.popScissor()

        renderColumnScrollbar(ctx, c, x0, x1, bodyTop, colBottom)
    }

    private fun ellipsize(text: String, maxW: Int, scale: Float = 1f): String {
        if (sw(this.font, text, scale) <= maxW) return text
        val n = fishmod.utils.rendering.TextFit.prefixLength(text, "…", maxW.toFloat(), 1) { sw(this.font, it, scale).toFloat() }
        return text.substring(0, n) + "…"
    }

    private fun renderRow(ctx: GuiGraphicsExtractor, f: Feature, x0: Int, x1: Int, top: Int, mouseX: Int, mouseY: Int) {
        val on = f.hasMaster() && f.get!!()
        val inView = mouseY >= cyTop() && mouseY <= cyBot()
        val hover = inView && mouseX >= x0 && mouseX <= x1 && mouseY >= top && mouseY <= top + ROW_H

        val bx = (x0 + 2).toFloat(); val bw = (x1 - x0 - 4).toFloat()
        if ((ROW_BUTTON ushr 24) != 0) UiRecorder.fillRoundedRect(bx, top.toFloat(), bw, ROW_H.toFloat(), ROW_RADIUS, ROW_BUTTON)
        if (on) UiRecorder.fillRoundedRect(bx, top.toFloat(), bw, ROW_H.toFloat(), ROW_RADIUS, ROW_ENABLED)
        if (hover) UiRecorder.fillRoundedRect(bx, top.toFloat(), bw, ROW_H.toFloat(), ROW_RADIUS, ROW_HOVER)
        if (on) UiRecorder.fillPillBar((x0 + 2).toFloat(), (top + 3).toFloat(), 2f, (ROW_H - 6).toFloat(), ACCENT)

        var label = f.name
        val maxTextW = x1 - x0 - 20
        if (stw(this.font, label) > maxTextW) {
            while (label.length > 1 && stw(this.font, "$label…") > maxTextW) label = label.substring(0, label.length - 1)
            label = "$label…"
        }
        UiRecorder.text(label, (x0 + 10).toFloat(), (top + (ROW_H - 8) / 2).toFloat(), NVG_BASE_TEXT_SIZE, if (on) TEXT_COLOR else SUBTEXT_COLOR)
        if (hover) {
            val d = descFor(f.name)
            if (d.isNotEmpty()) { hoverDesc = d; hoverDescX = x1 + 8; hoverDescY = top }
        }

        if (f.sub.isNotEmpty()) {
            drawChevron(ctx, x1 - 14, top + ROW_H / 2 - 2, f.expanded(), if (f.expanded()) ACCENT else CHEVRON_COLOR)
        }
    }

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
        if (animating) UiRecorder.pushScissor(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), animatedH.toFloat())

        val subH = f.naturalSubHeight()
        UiRecorder.fillRect(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), subH.toFloat(), SUBROW_BG)
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
            try {
                s.render(ctx, leftX, rightX, sy, mouseX, mouseY, this.font)
            } catch (t: Throwable) {
                FishDiag.fail("FishModScreen.22", "setting '${s.name}' (${s.javaClass.simpleName}) in '${f.name}' render failed", t)
            }
            sy += sh
        }

        if (animating) UiRecorder.popScissor()
    }

    private fun hovBtn(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean =
        try {
            clickInner(click, bl)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.12", "click failed at ${click.x()},${click.y()} button=${fishmod.compat.GLFW.btn(click)} search='$searchText'", t)
            true
        }

    private fun clickInner(click: MouseButtonEvent, bl: Boolean): Boolean {
        if (anyColumnAnimating()) return true

        val mx = vx(click.x())
        val my = vx(click.y())
        val btn = fishmod.compat.GLFW.btn(click)

        val rnf = renamingFolder
        if (rnf != null) {
            if (stackHitAt(mx, my)?.first?.folder === rnf) return true
            commitRename()
        }

        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(InputConstants.Type.MOUSE.getOrCreate(click.button()))
            capturingKeybind = null
            return true
        }

        val prevInput = activeInput
        if (prevInput is InputSetting && prevInput.textField != null) prevInput.textField!!.setFocused(false)
        if (prevInput is ColorPickerSetting) prevInput.hexField?.setFocused(false)
        activeInput = null

        val swW = 220
        val swH = 28
        val sx = (right() - swW) / 2
        val sy0 = bottom() - BOTTOM_RESERVE + (BOTTOM_RESERVE - swH) / 2 - 8
        searchFocused = mx >= sx && mx <= sx + swW && my >= sy0 && my <= sy0 + swH
        searchField?.setFocused(searchFocused)
        if (searchFocused) return true

        val e = editHudRect()
        if (hovBtn(mx, my, e[0], e[1], e[2], e[3])) { Minecraft.getInstance().showScreen(FishHudEditor(this)); return true }
        val rects = topBarButtonRects()
        if (hovBtn(mx, my, rects[0][0], rects[0][1], rects[0][2], rects[0][3])) { Minecraft.getInstance().showScreen(BoardReportScreen(this)); return true }
        if (hovBtn(mx, my, rects[1][0], rects[1][1], rects[1][2], rects[1][3])) { Minecraft.getInstance().showScreen(CreditsScreen(this)); return true }
        if (hovBtn(mx, my, rects[2][0], rects[2][1], rects[2][2], rects[2][3])) {
            if (resetArmed) { resetAllColumns(); resetArmed = false }
            else { resetArmed = true; resetArmedAt = System.currentTimeMillis() }
            return true
        }
        if (hovBtn(mx, my, rects[3][0], rects[3][1], rects[3][2], rects[3][3])) { onClose(); return true }

        val headerTop = cyTop() - HEADER_H

        if (mx >= stackX0() && mx <= stackX0() + STACK_W && my >= headerTop && my <= cyBot()) {
            val hit = stackHitAt(mx, my) ?: return true
            val r = hit.first
            val f = r.folder
            val c = r.col
            fun startDrag() {
                stackGrabDY = my - stackRowY(hit.second)
                stackDragY = my
                pressX = mx; pressY = my; dragMoved = false
            }
            when {
                r.isAdd() -> if (btn == 0) createFolder()
                f != null && btn == 1 -> {
                    val shift = fishmod.compat.GLFW.keyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                        fishmod.compat.GLFW.keyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_RIGHT_SHIFT)
                    if (shift) startRename(f)
                    else { f.open = !f.open; saveFolders(); stackScroll = Mth.clamp(stackScroll, 0, stackMaxScroll()) }
                }
                f != null && btn == 0 -> { stackDragFolder = f; startDrag() }
                c != null && btn == 0 -> { stackDragCol = c; startDrag() }
            }
            return true
        }

        if (searchText.isEmpty() && my >= headerTop && my < cyTop()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val x0 = columnX0(ci)
                val x1 = x0 + colW
                if (mx < x0 || mx > x1) continue
                if (btn != 0) return true
                dragColumn = cols[ci]
                dragGrabDX = mx - x0
                dragMouseX = mx
                pressX = mx; pressY = my; dragMoved = false
                return true
            }
        }

        if (btn == 0 && my >= cyTop() && my <= cyBot()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val x1 = columnX0(ci) + colW
                if (mx < x1 - 7 || mx > x1) continue
                val b = sbBar(cols[ci]) ?: continue
                if (my > b[0] + b[1]) continue
                sbCol = cols[ci]
                sbGrab = if (my >= b[4] && my <= b[4] + b[3]) my - b[4] else b[3] / 2
                sbSet(cols[ci], my)
                return true
            }
        }

        if (my >= cyTop() && my <= cyBot()) {
            val cols = visibleColumns()
            val colW = columnWidth()
            for (ci in cols.indices) {
                val x0 = columnX0(ci)
                val x1 = x0 + colW
                if (mx < x0 || mx > x1) continue
                return handleRowClick(cols[ci], x0, x1, mx, my, btn, cyTop())
            }
            return true
        }
        return super.mouseClicked(click, bl)
    }

    private fun handleRowClick(col: Column, x0: Int, x1: Int, mx: Int, my: Int, btn: Int, topY: Int): Boolean {
        for (rl in layoutColumn(col, col.scroll, topY)) {
            val f = rl.feature
            if (my >= rl.rowTop && my <= rl.rowBottom) {
                if (f.hasMaster()) {
                    if (btn == 1 && f.sub.isNotEmpty()) f.toggleExpanded()
                    else try { f.set!!(!f.get!!()) } catch (t: Throwable) { FishDiag.fail("FishModScreen.13", "toggle '${f.name}' failed", t) }
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
                        if (s is InputSetting || s is InputIntSetting || s is InputDoubleSetting || s is ColorPickerSetting) {
                            activeInput = s
                        }
                    }
                    val clicked = try {
                        s.onClick(mx, my, leftX, rightX, ssy, btn)
                    } catch (t: Throwable) {
                        FishDiag.fail("FishModScreen.14", "setting '${s.name}' (${s.javaClass.simpleName}) in '${f.name}' click failed", t)
                        true
                    }
                    if (clicked) {
                        if (s is KeybindSetting && s.capturing) capturingKeybind = s
                        return true
                    }
                    if (s is SliderIntSetting || s is SliderDoubleSetting) {
                        val slx = leftX + 2
                        val slw = rightX - leftX - 4
                        val sly = ssy + SLIDER_CTRL_Y
                        if (mx >= slx && mx <= slx + slw && my >= sly - 4 && my <= sly + SLIDER_H + 4) {
                            activeSlider = s; activeSliderX = slx; activeSliderW = slw; s.onDrag(mx, slx, slw); return true
                        }
                    }
                    ssy += sh
                }
                return true
            }
        }
        return true
    }

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

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
        try {
            dragInner(click, deltaX, deltaY)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.15", "drag failed slider=${activeSlider?.name} column=${dragColumn?.name}", t)
            true
        }

    private fun dragInner(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val sb = sbCol
        if (sb != null) { sbSet(sb, vx(click.y())); return true }
        val slider = activeSlider
        if (slider != null) { slider.onDrag(vx(click.x()), activeSliderX, activeSliderW); return true }
        if (!dragMoved && (dragColumn != null || !stackIdle())) {
            if (Math.abs(vx(click.x()) - pressX) >= DRAG_THRESHOLD || Math.abs(vx(click.y()) - pressY) >= DRAG_THRESHOLD) dragMoved = true
        }
        if (!stackIdle()) {
            stackDragY = vx(click.y())
            return true
        }
        val dc = dragColumn
        if (dc != null) {
            dragMouseX = vx(click.x())
            if (dragMoved) updateDragReorder(dc)
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean =
        try {
            releaseInner(click)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.16", "release failed column=${dragColumn?.name}", t)
            dragColumn = null
            true
        }

    private fun releaseInner(click: MouseButtonEvent): Boolean {
        activeSlider = null
        if (sbCol != null) { sbCol = null; return true }

        val sc = stackDragCol
        val sf = stackDragFolder
        if (sc != null || sf != null) {
            if (dragMoved) applyStackDrop()
            else if (sc != null) toggleFold(sc)
            else if (sf != null) toggleFolderColumns(sf)
            stackDragCol = null
            stackDragFolder = null
        }

        val dc = dragColumn
        if (dc != null && !dragMoved) {
            // plain click on a header folds it
            toggleFold(dc)
            dragColumn = null
        }
        if (dc != null && dragColumn != null) {
            dragColumn = null
            saveColumnOrder()
        }
        dragMoved = false

        return super.mouseReleased(click)
    }

    private fun updateDragReorder(dc: Column) {
        val cols = visibleColumns()
        val colW = columnWidth()
        val floatCenter = (dragMouseX - dragGrabDX) + colW / 2
        val curIdx = columns.indexOf(dc)
        if (!FishDiag.check(curIdx >= 0, "FishModScreen.25") { "dragged column '${dc.name}' not in columns" }) return
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean =
        try {
            scrollInner(mouseX, mouseY, horizontalAmount, verticalAmount)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.17", "scroll failed at $mouseX,$mouseY", t)
            true
        }

    private fun scrollInner(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (anyColumnAnimating()) return true

        val shiftDown = fishmod.compat.GLFW.keyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
            fishmod.compat.GLFW.keyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_RIGHT_SHIFT)

        val mouseX = vx(mouseX).toDouble()
        val mouseY = vx(mouseY).toDouble()
        if (mouseX >= stackX0() && mouseX <= stackX0() + STACK_W && mouseY >= cyTop() - HEADER_H && mouseY <= cyBot()) {
            stackScroll = Mth.clamp((stackScroll - verticalAmount * 18).toInt(), 0, stackMaxScroll())
            return true
        }
        val cols = visibleColumns()
        val colW = columnWidth()
        if (horizontalAmount == 0.0 && !shiftDown && mouseY >= cyTop()) {
            val dir = if (verticalAmount > 0) -1 else 1
            for (i in cols.indices) {
                val x0 = columnX0(i)
                val x1 = x0 + colW
                if (mouseX < x0 || mouseX > x1) continue
                if (handleRowScroll(cols[i], x0, x1, mouseX.toInt(), mouseY.toInt(), dir, cyTop())) return true
            }
            for (i in cols.indices) {
                val x0 = columnX0(i)
                val x1 = x0 + colW
                if (mouseX < x0 || mouseX > x1) continue
                val slot = cols[i]
                val colBottom = Math.min(cyTop() + columnContentHeight(slot), cyBot())
                if (mouseY <= colBottom) {
                    if (maxScrollFor(slot, colBottom - cyTop()) <= 0) break
                    slot.scroll = Mth.clamp((slot.scroll - verticalAmount * 18).toInt(), 0, maxScrollFor(slot, colBottom - cyTop()))
                    return true
                }
            }
        }

        val amount = if (horizontalAmount != 0.0) horizontalAmount else verticalAmount
        hScroll = Mth.clamp((hScroll - amount * 24).toInt(), 0, maxHScroll())
        return true
    }

    private fun resetAllColumns() {
        for (t in columns) {
            for (f in t.features) {
                try {
                    if (f.hasMaster() && f.get!!()) f.set!!(false)
                } catch (e: Throwable) {
                    FishDiag.fail("FishModScreen.28", "reset '${f.name}' in '${t.name}' failed", e)
                }
            }
        }
    }

    override fun keyPressed(input: KeyEvent): Boolean =
        try {
            keyInner(input)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.18", "key ${input.key()} failed capturing=${capturingKeybind?.name} input=${activeInput?.name}", t)
            capturingKeybind = null
            true
        }

    private fun keyInner(input: KeyEvent): Boolean {
        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(
                if (input.key() == GLFW.GLFW_KEY_ESCAPE) InputConstants.UNKNOWN
                else InputConstants.getKey(input)
            )
            capturingKeybind = null
            return true
        }
        val rf = renameField
        if (renamingFolder != null && rf != null) {
            when (input.key()) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitRename()
                GLFW.GLFW_KEY_ESCAPE -> { renamingFolder = null; renameField = null }
                else -> rf.keyPressed(input)
            }
            return true
        }
        val ai = activeInput
        if (ai is InputSetting && ai.textField != null) { ai.textField!!.keyPressed(input); return true }
        if (ai is InputIntSetting && ai.textField != null) { ai.textField!!.keyPressed(input); return true }
        if (ai is InputDoubleSetting && ai.textField != null) { ai.textField!!.keyPressed(input); return true }
        if (ai is ColorPickerSetting && ai.hexField != null && ai.hexField!!.isFocused) { ai.hexField!!.keyPressed(input); return true }
        if (searchFocused && searchField != null) { searchField!!.keyPressed(input); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean =
        try {
            charInner(input)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.19", "char input failed for setting '${activeInput?.name}'", t)
            true
        }

    private fun charInner(input: CharacterEvent): Boolean {
        val rf = renameField
        if (renamingFolder != null && rf != null) { rf.charTyped(input); return true }
        val ai = activeInput
        if (ai is InputSetting && ai.textField != null) {
            ai.textField!!.charTyped(input); ai.setter(ai.textField!!.value); return true
        }
        if (ai is InputIntSetting && ai.textField != null) { ai.textField!!.charTyped(input); return true }
        if (ai is InputDoubleSetting && ai.textField != null) { ai.textField!!.charTyped(input); return true }
        if (ai is ColorPickerSetting && ai.hexField != null && ai.hexField!!.isFocused) { ai.hexField!!.charTyped(input); return true }
        if (searchFocused && searchField != null) {
            searchField!!.charTyped(input); searchText = searchField!!.value; for (c in columns) c.scroll = 0; return true
        }
        return super.charTyped(input)
    }

        private var paintCount = 0

    override fun paintUiOverlay() {
        paintCount++
        try {
            fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, fishmod.utils.rendering.UiScale.factor())
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.20", "UI overlay paint failed ${this.width}x${this.height} frame=$paintCount", t)
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        requestClose()
    }

    class Column(val name: String) {
        var subtitle: String? = null
        fun sub(): String? = subtitle ?: COLUMN_SUBTITLES[name]
        val features: MutableList<Feature> = ArrayList()
        var scroll = 0
    }

    // sidebar-only folder of top-level columns, by name
    class Folder(var name: String, var open: Boolean, val members: LinkedHashSet<String>)

    class Feature(val name: String, val get: (() -> Boolean)?, val set: ((Boolean) -> Unit)?) {
        constructor(name: String, key: String) : this(name, FishSettings::class.java, key)
        constructor(name: String, owner: Class<*>, key: String) : this(name, SettingRef(owner, key, false))
        constructor(name: String, r: SettingRef<Boolean>) : this(name, { r.get() }, { r.set(it) })

        val sub: MutableList<Setting> = ArrayList()
        val expandAnim = Easing.Anim(250)
        init { if (get != null) FeatureMasters.map.putIfAbsent(name, get) }
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
        var gate: (() -> Boolean)? = null
        fun hiddenByGate(): Boolean = gate?.let { !it() } ?: false
        fun gatedBy(pred: () -> Boolean): Setting { gate = pred; return this }

        abstract fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, settingY: Int, mouseX: Int, mouseY: Int, tr: Font)
        open fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, settingY: Int, button: Int): Boolean = false
        open fun onScroll(mx: Int, my: Int, leftX: Int, rightX: Int, settingY: Int, dir: Int): Boolean = false
        open fun onDrag(mx: Int, sx: Int, sliderW: Int) {}
        open fun getHeight(): Int = ITEM_HEIGHT

        protected fun fit(s: String, maxW: Int): String {
            fun w(t: String) = Math.ceil(UiRecorder.textWidth(t, NVG_BASE_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()
            if (maxW <= 4 || w(s) <= maxW) return s
            val n = fishmod.utils.rendering.TextFit.prefixLength(s, "…", maxW.toFloat(), 1) { w(it).toFloat() }
            return s.substring(0, n) + "…"
        }
    }

    class SubcategoryHeader(name: String) : Setting(name, "") {
        // drawn bold, so measure bold or long lines overflow the column
        private fun w(t: String) = Math.ceil(UiRecorder.textWidthBold(t, SUBCAT_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()

        private val minLines: Int = wrap(name, MIN_COLUMN_W - 38).size

        override fun getHeight(): Int = when {
            minLines <= 1 -> SUBCAT_HEIGHT
            else -> SUBCAT_HEIGHT_2 + (minLines - 2) * 9
        }

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val h = getHeight()
            roundRect(ctx, leftX, sy, rightX, sy + h, 3, 0xFF11131A.toInt())
            val maxW = rightX - leftX - 12
            val lines = wrap(name, maxW)
            if (lines.size <= 1) {
                stBold(ctx, tr, lines.getOrElse(0) { name }, leftX + 6, sy + (h - 8) / 2, ACCENT)
            } else {
                for ((i, l) in lines.withIndex()) stBold(ctx, tr, l, leftX + 6, sy + 4 + i * 9, ACCENT)
            }
        }

        private fun wrap(s: String, maxW: Int): List<String> {
            if (w(s) <= maxW) return listOf(s)
            val out = ArrayList<String>()
            var line = ""
            for (word in s.split(" ")) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (w(candidate) > maxW && line.isNotEmpty()) { out.add(line); line = word } else line = candidate
            }
            if (line.isNotEmpty()) out.add(line)
            return out
        }
    }

    class ToggleSetting(name: String, desc: String, val getter: () -> Boolean, val setter: (Boolean) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, key: String) : this(name, desc, FishSettings::class.java, key)
        constructor(name: String, desc: String, owner: Class<*>, key: String) : this(name, desc, SettingRef(owner, key, false))
        constructor(name: String, desc: String, r: SettingRef<Boolean>) : this(name, desc, { r.get() }, { r.set(it) })

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
        companion object { const val W = 26; const val H = 14 }
    }

    class SliderIntSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit, val min: Int, val max: Int, val step: Int = 1, val format: ((Int) -> String)? = null) : Setting(name, desc) {
        constructor(name: String, desc: String, key: String, min: Int, max: Int, step: Int = 1, format: ((Int) -> String)? = null) : this(name, desc, FishSettings::class.java, key, min, max, step, format)
        constructor(name: String, desc: String, owner: Class<*>, key: String, min: Int, max: Int, step: Int = 1, format: ((Int) -> String)? = null) : this(name, desc, SettingRef(owner, key, 0), min, max, step, format)
        constructor(name: String, desc: String, r: SettingRef<Int>, min: Int, max: Int, step: Int, format: ((Int) -> String)?) : this(name, desc, { r.get() }, { r.set(it) }, min, max, step, format)

        init {
            FishDiag.check(max > min, "FishModScreen.31") { "int slider '$name' has empty range $min..$max" }
        }

        private fun snap(v: Int): Int {
            if (step <= 1) return v.coerceIn(min, max)
            return (min + Math.round((v - min).toFloat() / step) * step).coerceIn(min, max)
        }

        override fun getHeight(): Int = SLIDER_ROW_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            val slx = leftX + 2
            val slw = rightX - leftX - 4
            val sly = sy + SLIDER_CTRL_Y
            val pct = if (max > min) Mth.clamp((getter() - min).toFloat() / (max - min), 0f, 1f) else 0f
            pill(ctx, slx, sly, slx + slw, sly + SLIDER_H, SLIDER_BG)
            val fillW = (slw * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            disc(ctx, slx + fillW.coerceIn(3, slw - 3), sly + SLIDER_H / 2, 4, 0xFFFFFFFF.toInt())
            val v = format?.invoke(getter()) ?: getter().toString()
            st(ctx, tr, v, slx + slw - stw(tr, v), sy + 2, SUBTEXT_COLOR)
        }
        override fun onDrag(mx: Int, sx: Int, sliderW: Int) {
            val pct = Mth.clamp((mx - sx).toFloat() / sliderW, 0f, 1f)
            setter(snap(min + (pct * (max - min)).toInt()))
        }
    }

    class SliderDoubleSetting(name: String, desc: String, val getter: () -> Double, val setter: (Double) -> Unit, val min: Double, val max: Double) : Setting(name, desc) {
        constructor(name: String, desc: String, key: String, min: Double, max: Double) : this(name, desc, FishSettings::class.java, key, min, max)
        constructor(name: String, desc: String, owner: Class<*>, key: String, min: Double, max: Double) : this(name, desc, SettingRef(owner, key, 0.0), min, max)
        constructor(name: String, desc: String, r: SettingRef<Double>, min: Double, max: Double) : this(name, desc, { r.get() }, { r.set(it) }, min, max)

        init {
            FishDiag.check(max > min, "FishModScreen.32") { "double slider '$name' has empty range $min..$max" }
        }

        override fun getHeight(): Int = SLIDER_ROW_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            val slx = leftX + 2
            val slw = rightX - leftX - 4
            val sly = sy + SLIDER_CTRL_Y
            val pct = if (max > min) Mth.clamp(((getter() - min) / (max - min)).toFloat(), 0f, 1f) else 0f
            pill(ctx, slx, sly, slx + slw, sly + SLIDER_H, SLIDER_BG)
            val fillW = (slw * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            disc(ctx, slx + fillW.coerceIn(3, slw - 3), sly + SLIDER_H / 2, 4, 0xFFFFFFFF.toInt())
            val v = String.format("%.1f", getter())
            st(ctx, tr, v, slx + slw - stw(tr, v), sy + 2, SUBTEXT_COLOR)
        }
        override fun onDrag(mx: Int, sx: Int, sliderW: Int) {
            val pct = Mth.clamp((mx - sx).toFloat() / sliderW, 0f, 1f)
            setter(min + pct * (max - min))
        }
    }

    class DropdownSetting<T>(name: String, desc: String, val values: Array<T>, val getter: () -> T, val setter: (T) -> Unit,
                             val label: (T) -> String = { it.toString() }, val swatch: ((T) -> Int)? = null) : Setting(name, desc) {
        private val expandAnim = Easing.Anim(200)
        private var expanded = false
        private var pillX = 0
        private var pillW = 0

        init {
            FishDiag.check(values.isNotEmpty(), "FishModScreen.30") { "dropdown '$name' has no options" }
        }

        // Stale stored value (old config) shows as the first option; stored value kept until the player picks
        private fun indexOfCurrent(): Int {
            val cur = getter()
            for (i in values.indices) if (values[i] === cur || values[i] == cur) return i
            return 0
        }

        override fun getHeight(): Int {
            return ITEM_HEIGHT + Math.round(values.size * OPTION_H * expandAnim.progress())
        }

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val currentValue = values.getOrElse(indexOfCurrent()) { getter() }
            val current = label(currentValue)
            val textW = stw(tr, current)
            val sw = swatch
            pillW = textW + 22 + (if (sw != null) 12 else 0)
            pillX = rightX - pillW - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, PILL_H / 2, 2, TRACK_OFF, if (hov) ACCENT_HOVER else ACCENT)
            if (sw != null) disc(ctx, pillX + 12, by + PILL_H / 2, 4, sw(currentValue))
            st(ctx, tr, current, pillX + (if (sw != null) 22 else 10), by + (PILL_H - 8) / 2 - 1, TEXT_COLOR)

            val animating = expandAnim.isAnimating()
            if (expanded || animating) {
                val animH = Math.round(values.size * OPTION_H * expandAnim.progress())
                val oy = sy + ITEM_HEIGHT
                if (animating) UiRecorder.pushScissor(leftX.toFloat(), oy.toFloat(), (rightX - leftX).toFloat(), animH.toFloat())
                roundedRect(ctx, leftX + 2, oy, rightX - leftX - 4, values.size * OPTION_H, 5, SUBROW_BG)
                val curIdx = indexOfCurrent()
                for (i in values.indices) {
                    val rowY = oy + i * OPTION_H
                    val selected = i == curIdx
                    val rowHov = mx >= leftX + 2 && mx <= rightX - 2 && my >= rowY && my <= rowY + OPTION_H
                    if (rowHov) roundedRect(ctx, leftX + 4, rowY + 1, rightX - leftX - 8, OPTION_H - 2, 4, ROW_HOVER)
                    if (sw != null) disc(ctx, leftX + 12, rowY + OPTION_H / 2, 4, sw(values[i]))
                    st(ctx, tr, label(values[i]), leftX + (if (sw != null) 22 else 10), rowY + (OPTION_H - 8) / 2,
                        if (selected) ACCENT_HOVER else (if (rowHov) TEXT_COLOR else SUBTEXT_COLOR))
                    if (selected) UiRecorder.fillRect((leftX + 2).toFloat(), (rowY + 3).toFloat(), 2f, (OPTION_H - 6).toFloat(), ACCENT)
                }
                if (animating) UiRecorder.popScissor()
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
        constructor(name: String, desc: String, key: String) : this(name, desc, FishSettings::class.java, key)
        constructor(name: String, desc: String, owner: Class<*>, key: String) : this(name, desc, SettingRef(owner, key, ""))
        constructor(name: String, desc: String, r: SettingRef<String>) : this(name, desc, { r.get() }, { r.set(it) })

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
        private var wrapW = 0
        // line start offsets for the current text, wrapped at the field width
        private fun wrapStarts(text: String): List<Int> {
            val starts = arrayListOf(0)
            val maxW = wrapW - 6f
            if (maxW <= 10f) return starts
            var start = 0
            while (start < text.length) {
                var end = start + 1
                while (end <= text.length && UiRecorder.textWidth(text.substring(start, end), INPUT_TEXT_SIZE) <= maxW) end++
                end--
                if (end >= text.length) break
                if (end <= start) end = start + 1
                val sp = text.lastIndexOf(' ', end - 1)
                if (sp > start) end = sp + 1
                starts.add(end); start = end
            }
            return starts
        }
        private fun extraH(): Int = (wrapStarts(textField?.value ?: getter()).size - 1) * WRAP_LINE_H
        override fun getHeight(): Int = (if (hint != null) 35 else 26) + extraH()
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            initField(tr)
            st(ctx, tr, name, leftX + 2, sy + 1, TEXT_COLOR)
            val ix = leftX + 2
            val iy = sy + 11
            val fieldW = rightX - leftX - 4
            wrapW = fieldW
            val tf = textField!!
            if (!tf.isFocused) {
                if (tf.value != getter()) tf.value = getter()
                tf.cursorPosition = 0; tf.setHighlightPos(0)
            }
            val text = tf.value
            val starts = wrapStarts(text)
            val h = INPUT_H + (starts.size - 1) * WRAP_LINE_H
            UiRecorder.roundedRectRing(ix.toFloat(), iy.toFloat(), fieldW.toFloat(), h.toFloat(), 3f, 1f, SUBROW_BG, if (tf.isFocused) ACCENT else TRACK_OFF)
            val ty0 = iy + (INPUT_H - INPUT_TEXT_SIZE) / 2f
            val cursor = Math.min(tf.cursorPosition, text.length)
            for (i in starts.indices) {
                val s = starts[i]
                val e = if (i + 1 < starts.size) starts[i + 1] else text.length
                val ly = ty0 + i * WRAP_LINE_H
                UiRecorder.text(text.substring(s, e), ix + 3f, ly, INPUT_TEXT_SIZE, TEXT_COLOR)
                val last = i == starts.size - 1
                if (tf.isFocused && cursor >= s && (cursor < e || last) && (System.currentTimeMillis() / 500) % 2 == 0L) {
                    val cx = ix + 3f + UiRecorder.textWidth(text.substring(s, cursor), INPUT_TEXT_SIZE)
                    UiRecorder.fillRect(cx, ly - 1.5f, 1f, INPUT_TEXT_SIZE + 3f, TEXT_COLOR)
                }
            }
            hint?.let { st(ctx, tr, it, leftX + 2, sy + 27 + (starts.size - 1) * WRAP_LINE_H, SUBTEXT_COLOR) }
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = leftX + 2
            val iy = sy + 11
            val fieldW = rightX - leftX - 4
            if (mx >= ix && mx <= ix + fieldW && my >= iy && my <= iy + INPUT_H + extraH()) {
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

    class SoundSearchSetting(
        name: String,
        desc: String,
        private val valueGetter: () -> String,
        private val valueSetter: (String) -> Unit,
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
            try {
                fishmod.utils.FishUtil.sendSound(fishmod.utils.sound.SoundManager.preset(valueGetter()), vol, pit)
            } catch (t: Throwable) {
                FishDiag.fail("FishModScreen.34", "sound preview failed for '${valueGetter()}'", t)
            }
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
                emptyList()
            } else {
                fishmod.utils.sound.SoundManager.allSoundIds.asSequence()
                    .filter { it.contains(q) }
                    .sortedWith(compareBy({ !it.substringAfter(':').startsWith(q) }, { it.length }, { it }))
                    .take(10).toList()
            }
            cacheKey = key; cached = out
            return out
        }

        private fun browseList(): List<String> {
            val q = query.trim().lowercase().replace(' ', '_')
            val all = fishmod.utils.sound.SoundManager.allSoundIds
            return if (q.isEmpty()) all.sorted()
            else all.filter { it.contains(q) }
                .sortedWith(compareBy({ !it.substringAfter(':').startsWith(q) }, { it.length }, { it }))
        }

        override fun onScroll(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, dir: Int): Boolean {
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
                return s.replace(HEX_CODE_RE, "").replace(FORMAT_CODE_RE, "").length
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

    open class ColorPickerSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit, val defaultColor: Int? = null) : Setting(name, desc) {
        constructor(name: String, desc: String, key: String) : this(name, desc, FishSettings::class.java, key)
        constructor(name: String, desc: String, owner: Class<*>, key: String) : this(name, desc, SettingRef(owner, key, 0))
        constructor(name: String, desc: String, r: SettingRef<Int>) : this(name, desc, { r.get() }, { r.set(it) }, defaultOf(r.owner, r.name))

        private fun presetCount(): Int = PRESET_ARGB.size + (if (defaultColor != null) 1 else 0)

        private val expandAnim = Easing.Anim(200)
        private var expanded = false
        private var pillX = 0
        private var pillW = 0
        private var activeTab = 0
        var hexField: EditBox? = null

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

        private fun initHexField(tr: Font) {
            if (hexField == null) {
                val tf = EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty())
                tf.setMaxLength(7)
                tf.value = ""
                hexField = tf
            }
        }

        private fun parseHex(s: String): Int? {
            val h = s.trim().removePrefix("#")
            if (h.length != 6 || h.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
            return try { (0xFF shl 24) or (h.toLong(16).toInt() and 0xFFFFFF) } catch (e: NumberFormatException) { FishDiag.fail("FishModScreen.33", "validated hex '$h' failed to parse", e); null }
        }

        private fun commitHex() {
            val tf = hexField ?: return
            val argb = parseHex(tf.value) ?: return
            UserColorStore.add(argb)
            setter(argb)
            tf.value = ""
        }

        private fun contentHeight(): Int {
            return if (activeTab == 0) TAB_BAR_H + presetCount() * OPTION_H
            else TAB_BAR_H + HEX_ROW_H + Math.max(UserColorStore.all().size, 1) * OPTION_H
        }

        override fun getHeight(): Int {
            return ITEM_HEIGHT + Math.round(contentHeight() * expandAnim.progress())
        }

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX, sy + (ITEM_HEIGHT - 8) / 2, TEXT_COLOR)
            val cur = getter() or 0xFF000000.toInt()
            val idx = PRESET_ARGB.indexOf(cur)
            val label = if (idx >= 0) PRESET_NAMES[idx] else "#" + Integer.toHexString(cur and 0xFFFFFF).padStart(6, '0').uppercase()
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
                val innerX0 = leftX + 2
                val innerX1 = rightX - 2
                val ch = contentHeight()
                roundedRect(ctx, innerX0, oy, innerX1 - innerX0, ch, 5, SUBROW_BG)

                val tabW = (innerX1 - innerX0) / 2
                val tab0Hov = mx >= innerX0 && mx < innerX0 + tabW && my >= oy && my <= oy + TAB_BAR_H
                val tab1Hov = mx >= innerX0 + tabW && mx <= innerX1 && my >= oy && my <= oy + TAB_BAR_H
                st(ctx, tr, "Presets", innerX0 + (tabW - stw(tr, "Presets")) / 2, oy + (TAB_BAR_H - 8) / 2,
                    if (activeTab == 0) ACCENT_HOVER else (if (tab0Hov) TEXT_COLOR else SUBTEXT_COLOR))
                st(ctx, tr, "Your Colors", innerX0 + tabW + (tabW - stw(tr, "Your Colors")) / 2, oy + (TAB_BAR_H - 8) / 2,
                    if (activeTab == 1) ACCENT_HOVER else (if (tab1Hov) TEXT_COLOR else SUBTEXT_COLOR))
                val underlineX = if (activeTab == 0) innerX0 else innerX0 + tabW
                UiRecorder.fillRect(underlineX.toFloat(), (oy + TAB_BAR_H - 2).toFloat(), tabW.toFloat(), 2f, ACCENT)

                var cy = oy + TAB_BAR_H
                if (activeTab == 0) {
                    if (defaultColor != null) {
                        val dc = defaultColor or 0xFF000000.toInt()
                        val selected = cur == dc
                        val rowHov = mx >= innerX0 && mx <= innerX1 && my >= cy && my <= cy + OPTION_H
                        if (rowHov) roundedRect(ctx, innerX0 + 2, cy + 1, innerX1 - innerX0 - 4, OPTION_H - 2, 4, ROW_HOVER)
                        disc(ctx, innerX0 + 10, cy + OPTION_H / 2, 4, dc)
                        st(ctx, tr, "Default", innerX0 + 20, cy + (OPTION_H - 8) / 2,
                            if (selected) ACCENT_HOVER else (if (rowHov) TEXT_COLOR else SUBTEXT_COLOR))
                        if (selected) UiRecorder.fillRect(innerX0.toFloat(), (cy + 3).toFloat(), 2f, (OPTION_H - 6).toFloat(), ACCENT)
                        cy += OPTION_H
                    }
                    for (i in PRESET_ARGB.indices) {
                        val rowY = cy + i * OPTION_H
                        val selected = i == idx && (defaultColor == null || cur != (defaultColor or 0xFF000000.toInt()))
                        val rowHov = mx >= innerX0 && mx <= innerX1 && my >= rowY && my <= rowY + OPTION_H
                        if (rowHov) roundedRect(ctx, innerX0 + 2, rowY + 1, innerX1 - innerX0 - 4, OPTION_H - 2, 4, ROW_HOVER)
                        disc(ctx, innerX0 + 10, rowY + OPTION_H / 2, 4, PRESET_ARGB[i])
                        st(ctx, tr, PRESET_NAMES[i], innerX0 + 20, rowY + (OPTION_H - 8) / 2,
                            if (selected) ACCENT_HOVER else (if (rowHov) TEXT_COLOR else SUBTEXT_COLOR))
                        if (selected) UiRecorder.fillRect(innerX0.toFloat(), (rowY + 3).toFloat(), 2f, (OPTION_H - 6).toFloat(), ACCENT)
                    }
                } else {
                    initHexField(tr)
                    val fieldW = innerX1 - innerX0 - 4 - ADD_BTN_W - 4
                    val fieldX = innerX0 + 2
                    val fieldY = cy + (HEX_ROW_H - INPUT_H) / 2
                    nvgTextField(hexField!!, fieldX, fieldY, fieldW, INPUT_H)
                    val addX = fieldX + fieldW + 4
                    val addHov = mx >= addX && mx <= addX + ADD_BTN_W && my >= fieldY - 2 && my <= fieldY + INPUT_H + 2
                    roundedRect(ctx, addX, fieldY - 2, ADD_BTN_W, INPUT_H + 4, 3, if (addHov) ACCENT_HOVER else ACCENT)
                    st(ctx, tr, "Add", addX + (ADD_BTN_W - stw(tr, "Add")) / 2, fieldY + (INPUT_H - 8) / 2, -0x1)

                    cy += HEX_ROW_H
                    val colors = UserColorStore.all()
                    if (colors.isEmpty()) {
                        st(ctx, tr, "No custom colors yet - type a hex code above", innerX0 + 4, cy + (OPTION_H - 8) / 2, SUBTEXT_COLOR)
                    }
                    for (i in colors.indices) {
                        val rowY = cy + i * OPTION_H
                        val c = colors[i]
                        val selected = (getter() or 0xFF000000.toInt()) == c
                        val rowHov = mx >= innerX0 && mx <= innerX1 && my >= rowY && my <= rowY + OPTION_H
                        if (rowHov) roundedRect(ctx, innerX0 + 2, rowY + 1, innerX1 - innerX0 - 4, OPTION_H - 2, 4, ROW_HOVER)
                        disc(ctx, innerX0 + 10, rowY + OPTION_H / 2, 4, c)
                        st(ctx, tr, "#" + Integer.toHexString(c and 0xFFFFFF).padStart(6, '0').uppercase(), innerX0 + 20, rowY + (OPTION_H - 8) / 2,
                            if (selected) ACCENT_HOVER else (if (rowHov) TEXT_COLOR else SUBTEXT_COLOR))
                        if (selected) UiRecorder.fillRect(innerX0.toFloat(), (rowY + 3).toFloat(), 2f, (OPTION_H - 6).toFloat(), ACCENT)
                    }
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
            if (!expanded) return false

            val oy = sy + ITEM_HEIGHT
            val innerX0 = leftX + 2
            val innerX1 = rightX - 2
            val tabW = (innerX1 - innerX0) / 2

            if (my >= oy && my <= oy + TAB_BAR_H) {
                if (mx >= innerX0 && mx < innerX0 + tabW) { activeTab = 0; return true }
                if (mx >= innerX0 + tabW && mx <= innerX1) { activeTab = 1; return true }
                return true
            }

            var cy = oy + TAB_BAR_H
            if (activeTab == 0) {
                if (defaultColor != null) {
                    if (mx >= innerX0 && mx <= innerX1 && my >= cy && my <= cy + OPTION_H) {
                        setter(defaultColor)
                        expanded = false
                        expandAnim.setTarget(false)
                        return true
                    }
                    cy += OPTION_H
                }
                for (i in PRESET_ARGB.indices) {
                    val rowY = cy + i * OPTION_H
                    if (mx >= innerX0 && mx <= innerX1 && my >= rowY && my <= rowY + OPTION_H) {
                        setter(PRESET_ARGB[i])
                        expanded = false
                        expandAnim.setTarget(false)
                        return true
                    }
                }
            } else {
                val fieldW = innerX1 - innerX0 - 4 - ADD_BTN_W - 4
                val fieldX = innerX0 + 2
                val fieldY = cy + (HEX_ROW_H - INPUT_H) / 2
                if (mx >= fieldX && mx <= fieldX + fieldW && my >= fieldY && my <= fieldY + INPUT_H) {
                    hexField?.let { tf ->
                        tf.setFocused(true)
                        val len = tf.value.length
                        tf.cursorPosition = len; tf.setHighlightPos(len)
                    }
                    return true
                }
                val addX = fieldX + fieldW + 4
                if (mx >= addX && mx <= addX + ADD_BTN_W && my >= fieldY - 2 && my <= fieldY + INPUT_H + 2) {
                    commitHex()
                    return true
                }
                cy += HEX_ROW_H
                val colors = UserColorStore.all()
                for (i in colors.indices) {
                    val rowY = cy + i * OPTION_H
                    if (mx >= innerX0 && mx <= innerX1 && my >= rowY && my <= rowY + OPTION_H) {
                        setter(colors[i])
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
            private const val TAB_BAR_H = 18

            fun defaultOf(owner: Class<*>, name: String): Int? {
                val cls = if (owner.simpleName == "Companion") owner.enclosingClass ?: owner else owner
                return ColorDefaults.of(cls, name) ?: ColorDefaults.of(name)
            }
            private const val HEX_ROW_H = 22
            private const val ADD_BTN_W = 32
        }
    }

    class ConditionalColorPickerSetting(
        name: String, desc: String, val visible: () -> Boolean,
        getter: () -> Int, setter: (Int) -> Unit, defaultColor: Int? = null
    ) : ColorPickerSetting(name, desc, getter, setter, defaultColor) {
        val shownName: String = name

        private fun syncName() { this.name = if (visible()) shownName else "" }

        override fun getHeight(): Int {
            syncName()
            if (!visible()) return 0
            return super.getHeight()
        }
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            syncName()
            if (!visible()) return
            super.render(ctx, leftX, rightX, sy, mx, my, tr)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            syncName()
            if (!visible()) return false
            return super.onClick(mx, my, leftX, rightX, sy, btn)
        }
    }

    class ButtonSetting(name: String, desc: String, val label: () -> String, val action: Runnable) : Setting(name, desc) {
        constructor(name: String, desc: String, label: String, action: Runnable) : this(name, desc, { label }, action)
        constructor(name: String, desc: String, action: Runnable) : this(name, desc, { "Open" }, action)

        private fun buttonWidth(tr: Font, text: String): Int = stw(tr, text) + 24

        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            val text = label()
            val bw = buttonWidth(tr, text)
            val bx = rightX - bw - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + PILL_H
            roundedRect(ctx, bx, by, bw, PILL_H, PILL_H / 2, if (hov) ACCENT_HOVER else ACCENT)
            st(ctx, tr, text, bx + (bw - stw(tr, text)) / 2, by + (PILL_H - 8) / 2 - 1, -0x1)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val tr = Minecraft.getInstance().font ?: return false
            val bw = buttonWidth(tr, label())
            val bx = rightX - bw - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + PILL_H) {
                action.run(); return true
            }
            return false
        }
    }

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
            try { Minecraft.getInstance().options.save() } catch (t: Throwable) { FishDiag.fail("FishModScreen.35", "options save after binding '$name' failed", t) }
            capturing = false
        }
    }

    class InputIntSetting(name: String, desc: String, val getter: () -> Int, val setter: (Int) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, key: String) : this(name, desc, FishSettings::class.java, key)
        constructor(name: String, desc: String, owner: Class<*>, key: String) : this(name, desc, SettingRef(owner, key, 0))
        constructor(name: String, desc: String, r: SettingRef<Int>) : this(name, desc, { r.get() }, { r.set(it) })

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
        override fun getHeight(): Int = INPUT_ROW_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            initField(tr)
            val ix = leftX + 2
            val iy = sy + INPUT_CTRL_Y
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = leftX + 2
            val iy = sy + INPUT_CTRL_Y
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                textField?.setFocused(true)
                return true
            }
            return false
        }
    }

    class InputDoubleSetting(name: String, desc: String, val getter: () -> Double, val setter: (Double) -> Unit) : Setting(name, desc) {
        constructor(name: String, desc: String, key: String) : this(name, desc, FishSettings::class.java, key)
        constructor(name: String, desc: String, owner: Class<*>, key: String) : this(name, desc, SettingRef(owner, key, 0.0))
        constructor(name: String, desc: String, r: SettingRef<Double>) : this(name, desc, { r.get() }, { r.set(it) })

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
        override fun getHeight(): Int = INPUT_ROW_H
        override fun render(ctx: GuiGraphicsExtractor, leftX: Int, rightX: Int, sy: Int, mx: Int, my: Int, tr: Font) {
            st(ctx, tr, name, leftX + 2, sy + 2, TEXT_COLOR)
            initField(tr)
            val ix = leftX + 2
            val iy = sy + INPUT_CTRL_Y
            nvgTextField(textField!!, ix, iy, INPUT_W, INPUT_H)
        }
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val ix = leftX + 2
            val iy = sy + INPUT_CTRL_Y
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
        private const val ROW_HOVER = 0x1EFFFFFF
        private var ROW_ENABLED: Int = 0x2624B6B0
        private var ROW_BUTTON: Int = 0xFF1E2227.toInt()
        private const val ROW_RADIUS = 3f
        private val SUBROW_BG = 0xFF0F1317.toInt()
        private val TRACK_OFF = 0xFF3A3F48.toInt()
        private val TEXT_COLOR = ScreenTheme.TEXT_COLOR
        private val SUBTEXT_COLOR = ScreenTheme.SUBTEXT_COLOR
        private val CHEVRON_COLOR = 0xFF6C7885.toInt()

        private const val TEXT_SCALE = ScreenTheme.TEXT_SCALE

        private val COMPACT get() = FishSettings.fmCompact
        private val MARGIN get() = if (COMPACT) 10 else 16
        private const val TOP_BAR_H = 26
        private const val BOTTOM_RESERVE = 46

        private val COLUMN_GUTTER get() = if (COMPACT) 4 else 6
        private const val CARD_RADIUS = 7
        private const val HEADER_H = 36
        private val COLUMN_SUBTITLES = mapOf(
            "General" to "Core mod options",
            "Dungeons" to "Run helpers & alerts",
            "Dungeon Solvers" to "Puzzle & room solvers",
            "Dungeon Trackers" to "Run stats & timers",
            "Dungeon Map" to "Map display & rooms",
            "Floor 7" to "Boss fight helpers",
            "Inventory & Storage" to "Items, chests & search",
            "Party & Social" to "Party, chat & friends",
            "Cosmetics" to "Looks & colour tweaks",
            "HUD & Overlays" to "On-screen elements",
            "Visuals & Rendering" to "ESP, boxes & effects",
            "Diana" to "Mythological event tools",
            "Fishing" to "Sea creatures, drops and fishing helpers",
            "Slayer" to "Slayer boss helpers",
            "Cheats" to "Risky automation",
            "Mining" to "Mining & powder tools",
            "Kuudra" to "Crimson Isle boss run tools",
            "Rift" to "Rift dimension helpers",
            "Extras" to "Misc & utilities",
            "Farming" to "Garden farming tools",
            "Achievements" to "Goals & progress",
            "Gamble Points" to "Playtime points & /gamble",
        )
        private const val HEADER_STRIP_H = 3
        private val MIN_COLUMN_W get() = if (COMPACT) 156 else 172
        private val MAX_COLUMN_W get() = if (COMPACT) 236 else 260
        private const val STACK_W = 176
        private val STACK_TAB_H get() = if (COMPACT) 20 else 24
        private const val STACK_SUB_INDENT = 10
        private const val DRAG_THRESHOLD = 4

        private val ROW_H get() = if (COMPACT) 18 else 22
        private val ROW_GAP get() = if (COMPACT) 1 else 3
        private val ITEM_HEIGHT get() = if (COMPACT) 18 else 22
        private val PILL_H get() = if (COMPACT) 15 else 18
        private const val OPTION_H = 16
        private val SLIDER_BG = 0xFF2C3138.toInt()
        private val SLIDER_FILL get() = ACCENT
        private const val SLIDER_W = 56
        private const val SLIDER_H = 5
        private const val INPUT_W = 62
        private const val INPUT_H = 14
        private const val SUBCAT_HEIGHT = 13
        private const val SUBCAT_HEIGHT_2 = 22
        private const val TWO_LINE_H = 36
        private const val SLIDER_ROW_H = 24
        private const val SLIDER_CTRL_Y = 13
        private const val TWO_LINE_CTRL_Y = 20
        // Label sits right above its own box with a clear gap before the next row
        private const val INPUT_CTRL_Y = 12
        private const val INPUT_ROW_H = 30

        private const val NVG_BASE_TEXT_SIZE = 9.5f
        private const val INPUT_TEXT_SIZE = 7f
        private const val WRAP_LINE_H = 9

        private const val SUBCAT_TEXT_SIZE = NVG_BASE_TEXT_SIZE * 1.15f

        fun roundedRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
            UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), color)
        }

        fun roundRect(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, color: Int) {
            roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, color)
        }

        fun roundedRectRing(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, strokeW: Int, fillColor: Int, ringColor: Int) {
            UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), strokeW.toFloat(), fillColor, ringColor)
        }

        fun pill(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
            val h = y2 - y1
            roundedRect(ctx, x1, y1, x2 - x1, h, h / 2, color)
        }

        fun disc(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
            UiRecorder.disc(cx.toFloat(), cy.toFloat(), r.toFloat(), color)
        }

        fun st(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int) {
            UiRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * TEXT_SCALE, color)
        }
        fun stw(tr: Font, s: String): Int = Math.ceil(UiRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * TEXT_SCALE).toDouble()).toInt()

        fun stBold(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int) {
            UiRecorder.textBold(s, x.toFloat(), y.toFloat(), SUBCAT_TEXT_SIZE * TEXT_SCALE, color)
        }

        fun sst(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int, scale: Float) {
            UiRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * scale, color)
        }
        fun sw(tr: Font, s: String, scale: Float): Int = Math.ceil(UiRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * scale).toDouble()).toInt()

        fun drawChevron(ctx: GuiGraphicsExtractor, gx: Int, cy: Int, open: Boolean, color: Int) {
            UiRecorder.chevron(gx.toFloat(), cy.toFloat(), open, color)
        }

        fun nvgTextField(tf: EditBox, x: Int, y: Int, w: Int, h: Int) {
            val focused = tf.isFocused
            UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 3f, 1f, SUBROW_BG, if (focused) ACCENT else TRACK_OFF)
            nvgTextFieldContent(tf, x, y, w, h)
        }

        fun nvgTextFieldContent(tf: EditBox, x: Int, y: Int, w: Int, h: Int, size: Float = INPUT_TEXT_SIZE) {
            val text = tf.value
            val cursor = Math.min(tf.cursorPosition, text.length)
            val cursorX = UiRecorder.textWidth(text.substring(0, cursor), size)
            val pad = 3f
            val visibleW = w - pad * 2f
            val scroll = Math.max(0f, cursorX - visibleW)
            UiRecorder.pushScissor((x + 1).toFloat(), (y + 1).toFloat(), (w - 2).toFloat(), (h - 2).toFloat())
            UiRecorder.text(text, x + pad - scroll, y + (h - size) / 2f, size, TEXT_COLOR)
            if (tf.isFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                UiRecorder.fillRect(x + pad + cursorX - scroll, (y + 2).toFloat(), 1f, (h - 4).toFloat(), TEXT_COLOR)
            }
            UiRecorder.popScissor()
        }

        private fun descFor(name: String): String {
            fishmod.features.diana.DianaMenu.describe(name)?.let { return it }
            fishmod.features.fishing.FishingMenu.describe(name)?.let { return it }
            fishmod.features.mining.MiningMenu.describe(name)?.let { return it }
            fishmod.features.kuudra.KuudraMenu.describe(name)?.let { return it }
            fishmod.features.rift.RiftMenu.describe(name)?.let { return it }
            fishmod.features.extras.ExtrasMenu.describe(name)?.let { return it }
            fishmod.features.garden.GardenMenu.describe(name)?.let { return it }
            fishmod.features.achievements.AchMenu.describe(name)?.let { return it }
            fishmod.features.gamble.GambleMenu.describe(name)?.let { return it }
            return when (name) {
                "Chat" -> "Smart Copy, Compact Chat, Infinite History, Search, Filter"
                "Mod Prefix" -> "Tag FishMod's chat output with a prefix"
                "GUI Settings" -> "Size of each FishMod screen"
                "Inventory Buttons" -> "Clickable command buttons in your inventory"
                "Smart Copy Chat" -> "Right-click a chat line to copy it"
                "Compact Tab" -> "Cleaner custom tab player list"
                "Chat Filter" -> "Hide selected chat spam + NoammAddons' list + custom regex"
                "Explosive Shot" -> "Title with per-enemy damage"
                "Puzzle Overlay" -> "Show solved puzzle names"
                "Auto Sprint" -> "Keep sprinting while holding forward"
                "Sound Manager" -> "Master toggle & volume for FishMod cues"
                "Boss Health Numbers" -> "Numeric HP on the M7 boss bar"
                "Blessing Display" -> "Active dungeon blessings from the tab footer"
                "Mask & Phoenix Timer" -> "Spirit / Bonzo / Phoenix proc + cooldown timers"
                "Secret Clicked" -> "Box + chime when you click a dungeon secret"
                "Puzzle Solvers" -> "In-world solutions for dungeon puzzles"
                "Arrow Align" -> "F7 P3 arrow device — clicks needed per frame"
                "Ragnarock" -> "Alerts when your Ragnarock Axe cast succeeds or is cancelled"
                "Arrows Device" -> "F7 P3 Sharp Shooter — boxes targets vs already-hit blocks"
                "Simon Says Solver" -> "F7 P3 Goldor device — boxes the buttons to press, in order"
                "Melody Message" -> "Party-announce the F7 melody terminal + its progress"
                "Item Rarity Background" -> "Rarity-tinted sprite behind every item"
                "Gyro Helper" -> "Gyrokinetic Wand landing box + sucking-range ring"
                "Wither Highlight" -> "Outline the F7 wither boss by phase"
                "M7 Relics" -> "P5 relic spawn timer + cauldron box"
                "Auto Requeue" -> "Re-queue the same floor when a run ends (leader only)"
                "Party Warp Timer" -> "Countdown until you can /warp again"
                "Death Message" -> "Announce deaths with a template"
                "Send Lag to Party" -> "Warn the party when your game lags"
                "Run Splits" -> "Phase split timers for runs"
                "PB Messages" -> "Chat PB alerts for splits, Goldor sections, terminals and relics"
                "Session Stats" -> "Per-session run statistics HUD"
                "Loot Tracker" -> "Manual drop & profit tracker (D Hub inv)"
                "Simon Says" -> "F7 Goldor device solver"
                "Class Colored Boots" -> "Dye boots by your dungeon class"
                "M7 Lever Waypoints" -> "See F7/M7 levers through walls"
                "Starred Mob Highlight" -> "Outline dungeon mobs that need to be killed to clear the floor"
                "Boss Tick Clocks" -> "Maxor/Storm/Goldor tick timers + related P2/terminal notifications"
                "Crystal Respawn" -> "Crystal spawn countdown + reminder"
                "Section Count" -> "Terminal section completed/total"
                "Goldor Splits" -> "S1-S4 terminal split timers + total time"
                "Name Color" -> "Recolor your username gradient"
                "Nametag" -> "Show your own above-head nametag"
                "Player Size" -> "Resize your model (render only)"
                "Party Commands" -> "Dot-commands usable in party chat"
                "Chat Channels" -> "Where dot-commands are allowed"
                "Cooldown Overlay" -> "Ability cooldowns on item slots"
                "Pet HUD" -> "Show your active pet & level"
                "Soulflow HUD" -> "Track your soulflow count"
                "Fire Freeze Timer" -> "Fire Freeze staff cooldown timer"
                "Party Finder Join Stats" -> "Whisper or PF-join prints their MP/PB/Cata/Gear to chat — also /pfs [name]"
                "Party Finder Menu" -> "Level req + missing classes on heads, stats in party-member tooltips"
                "Party Finder List" -> "Scrollable party summary beside the menu; hover a row to highlight its head"
                "Party Finder Auto Kick" -> "As leader, kick joiners who miss the S+ PB / secrets bar"
                "Container Value" -> "No-background value list + total beside the open container / storage overlay"
                "Anita Medal Profit" -> "In Anita's shop: coins per bronze medal for each item, best one highlighted"
                else -> descForExternal(name)
            }
        }

        private fun descForExternal(name: String): String {
            for (et in FishModAddonApi.dungeonToggles + FishModAddonApi.cheatToggles) {
                if (et.name() == name) return et.description()
            }
            return ""
        }

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

/** Feature name -> master toggle, so the HUD editor can tell "setting off" from "not showing right now". */
object FeatureMasters {
    @JvmField val map: java.util.concurrent.ConcurrentHashMap<String, () -> Boolean> = java.util.concurrent.ConcurrentHashMap()
}
