package fishmod.features

import fishmod.features.cosmetics.CrosshairImageLoader
import fishmod.features.cosmetics.CustomCrosshair
import fishmod.features.general.BridgeBot
import fishmod.features.general.ExperimentProfitTracker
import fishmod.features.general.Ragnarock
import fishmod.features.inventory.SlotBinds
import fishmod.features.hud.ChatCommandsScreen
import fishmod.features.party.PartyLootScreen
import fishmod.features.visuals.TimeChanger
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
import fishmod.features.dungeon.floor7.TerminalTracker
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
    private var hoverPreview: String? = null
    private var hoverDescX = 0
    private var hoverDescY = 0

    private val foldedColumns = HashSet<String>()

    private var closing = false
    private var closeFinalized = false

    init {
        try { buildCategories() } catch (t: Throwable) { FishDiag.fail("FishModScreen.2", "buildCategories failed after ${columns.size} columns", t) }
        try { applySavedColumnOrder() } catch (t: Throwable) { FishDiag.fail("FishModScreen.3", "applySavedColumnOrder failed for '${FishSettings.fmColumnOrder}'", t) }
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
            f.sub.add(DropdownSetting("Background Preset", "", arrayOf("Graphite", "Charcoal", "Obsidian", "Moss", "Wine", "Deep Sea", "Custom"),
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
            f.sub.add(SubcategoryHeader("Animation"))
            f.sub.add(ToggleSetting("Menu Animations", "Off = cards and panels just appear, no fades or slides", "fmUiAnimations"))
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
        general.features.add(Feature("FishMod Chat (/fc)", "fishChatEnabled"))
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
                Runnable { fishmod.features.general.InvShortcutButtons.openEditMode() }))
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
            for (i in 0 until fishmod.features.inventory.PetKeybinds.COUNT) {
                val idx = i
                f.sub.add(KeybindSetting(fishmod.features.inventory.PetKeybinds.PETS[idx], "", { fishmod.utils.FishKeys.petKeybinds?.getOrNull(idx) }))
            }
            general.features.add(f)
        }
        run {
            val f = Feature("Slot Binds", "slotBindsEnabled")
            f.sub.add(SubcategoryHeader("Hold the bind key + click a hotbar slot then an inv slot to link; shift-left-click to swap"))
            f.sub.add(KeybindSetting("Bind Key (hold)", "Default R", { fishmod.utils.FishKeys.slotBind }))
            f.sub.add(SubcategoryHeader("Profiles — separate bind sets you can switch between"))
            val slotBindProfiles: Array<String> = fishmod.features.inventory.SlotBinds.profileNames().toTypedArray()
            f.sub.add(DropdownSetting("Profile", "", slotBindProfiles,
                { if (FishSettings.slotBindsProfile in slotBindProfiles) FishSettings.slotBindsProfile else slotBindProfiles[0] },
                { v -> FishSettings.slotBindsProfile = v }))
            f.sub.add(ButtonSetting("New Profile", "Fresh empty set", Runnable { fishmod.features.inventory.SlotBinds.newProfile() }))
            f.sub.add(ButtonSetting("Delete Profile", "Remove the current set (Default is only cleared)", Runnable { fishmod.features.inventory.SlotBinds.deleteActiveProfile() }))
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
            f.sub.add(SubcategoryHeader("Chat Tabs"))
            f.sub.add(ToggleSetting("Chat Tabs", "Tab buttons above the chat input that filter chat to one channel", "chatTabs"))
            f.sub.add(ToggleSetting("  Party Tab", "", "chatTabParty").gatedBy { FishSettings.chatTabs })
            f.sub.add(ToggleSetting("  Guild Tab", "", "chatTabGuild").gatedBy { FishSettings.chatTabs })
            f.sub.add(ToggleSetting("  Officer Tab", "", "chatTabOfficer").gatedBy { FishSettings.chatTabs })
            f.sub.add(ToggleSetting("  Co-op Tab", "", "chatTabCoop").gatedBy { FishSettings.chatTabs })
            f.sub.add(ToggleSetting("Switch Channel On Click", "Clicking a tab sends one /chat <channel>", "chatTabsSwitchChannel").gatedBy { FishSettings.chatTabs })
            f.sub.add(ColorPickerSetting("Tab Background", "", FishSettings::class.java, "chatTabsBgColor").gatedBy { FishSettings.chatTabs })
            f.sub.add(ColorPickerSetting("Tab Hover", "", FishSettings::class.java, "chatTabsHoverColor").gatedBy { FishSettings.chatTabs })
            f.sub.add(ColorPickerSetting("Selected Tab", "", FishSettings::class.java, "chatTabsSelectedColor").gatedBy { FishSettings.chatTabs })
            f.sub.add(ColorPickerSetting("Tab Text", "", FishSettings::class.java, "chatTabsTextColor").gatedBy { FishSettings.chatTabs })
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
            f.sub.add(ToggleSetting("Jacob Contest", "Hides Jacob's Anita's Artifact fortune line", fishmod.features.extras.ExtrasSettings::class.java, "cfJacobSpam"))
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
                { v -> FishSettings.bridgeBotName = v; fishmod.features.general.BridgeBot.rebuildPattern() }))
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
        dungeon.features.add(Feature("Death Counter", DungeonSettings::class.java, "deathCounterHud"))
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
            f.sub.add(SubcategoryHeader("Proc Sound"))
            f.sub.add(ToggleSetting("Sound On Proc", "Play a sound when a mask / Phoenix procs", "invincProcSoundOn"))
            f.sub.add(SoundSearchSetting("Sound", "Type to search every game sound",
                { FishSettings.invincProcSoundName }, { v -> FishSettings.invincProcSoundName = v },
                { FishSettings.invincProcVolume }, { FishSettings.invincProcPitch }).gatedBy { FishSettings.invincProcSoundOn })
            f.sub.add(SliderIntSetting("Volume %", "", "invincProcVolume", 0, 500, 10).gatedBy { FishSettings.invincProcSoundOn })
            f.sub.add(SliderDoubleSetting("Pitch", "", "invincProcPitch", 0.0, 2.0).gatedBy { FishSettings.invincProcSoundOn })
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
                { v -> FishSettings.trackerPriceModeEnum = v; fishmod.features.dungeon.trackers.CroesusPrices.applyPriceMode() }))
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
            f.sub.add(DropdownSetting("Preset", "", fishmod.features.cosmetics.CustomCrosshair.PRESETS,
                { if (FishSettings.crosshairPreset in fishmod.features.cosmetics.CustomCrosshair.PRESETS) FishSettings.crosshairPreset else fishmod.features.cosmetics.CustomCrosshair.PRESETS[0] },
                { v -> FishSettings.crosshairPreset = v })
                .gatedBy { FishSettings.crosshairMode == "Preset" })
            f.sub.add(ButtonSetting("Open Crosshairs Folder", "") {
                try {
                    fishmod.features.cosmetics.CrosshairImageLoader.init()
                    fishmod.compat.GLFW.openUri(fishmod.features.cosmetics.CrosshairImageLoader.getImagesPath().toUri())
                } catch (e: Exception) {
                    FishDiag.fail("FishModScreen.4", "open crosshairs folder failed", e)
                }
            }.gatedBy { FishSettings.crosshairMode == "Image" })
            val crosshairImageNames: Array<String> = run {
                val names = fishmod.features.cosmetics.CrosshairImageLoader.getImageNames()
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
            f.sub.add(ToggleSetting("Argument Hints in Chat", "Gray usage key after a .command while typing", "pcArgHints"))
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
            f.sub.add(ToggleSetting(".holding", "", "pcHolding"))
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
                Minecraft.getInstance().showScreen(fishmod.features.party.PartyLootScreen(fishmod.features.party.PartyLootScreen.Tab.WHITELIST, Minecraft.getInstance().curScreen))
            }))
            f.sub.add(ButtonSetting("Blacklist", "Edit who is always blocked", "Open", Runnable {
                Minecraft.getInstance().showScreen(fishmod.features.party.PartyLootScreen(fishmod.features.party.PartyLootScreen.Tab.BLACKLIST, Minecraft.getInstance().curScreen))
            }))
            party.features.add(f)
        }
        run {
            val f = Feature("Kick List", { FishSettings.pcKickListEnabled }, { v -> FishSettings.pcKickListEnabled = v })
            f.sub.add(SubcategoryHeader("Anyone on this list is auto-kicked from your party whenever you're leader"))
            f.sub.add(ButtonSetting("Manage List", "Add/remove names, or /fm kicklist", "Open", Runnable {
                Minecraft.getInstance().showScreen(fishmod.features.party.PartyLootScreen(fishmod.features.party.PartyLootScreen.Tab.KICK, Minecraft.getInstance().curScreen))
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
            f.sub.add(ButtonSetting("Reset", "Clear all tracked experiments", Runnable { fishmod.features.general.ExperimentProfitTracker.reset() }))
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
            val f = Feature("Spotify", "spotifyLyricsEnabled")
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
            f.sub.add(DropdownSetting("Time", "", fishmod.features.visuals.TimeChanger.modes(),
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
            f.sub.add(SliderIntSetting("Opacity %", "Inventory slots (and hotbar unless separate)", VisualSettings::class.java, "itemRarityOpacity", 0, 100))
            f.sub.add(ToggleSetting("Separate Hotbar Opacity", "Use a different opacity on the hotbar outside menus", VisualSettings::class.java, "itemRaritySeparateHotbar"))
            f.sub.add(SliderIntSetting("Hotbar Opacity %", "", VisualSettings::class.java, "itemRarityHotbarOpacity", 0, 100))
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
            f.sub.add(ToggleSetting("Focus Mode", "Hold Left Alt: tooltip shows only the item name", "focusMode"))
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
            f.sub.add(ToggleSetting("Click Key in Tooltip", "Yellow lines under the tooltip: left, right and shift-left click", "skyItemListClickKey"))
            f.sub.add(ToggleSetting("Rarity Background", "Tint each item slot by its rarity", "skyItemListRarityBg"))
            f.sub.add(SliderIntSetting("Rarity Opacity %", "", "skyItemListRarityOpacity", 5, 100))
            f.sub.add(ToggleSetting("Rounded Rarity Background", "Round the corners of the rarity tint", "skyItemListRarityRound"))
            f.sub.add(SliderIntSetting("Background Opacity %", "0% invisible - 100% solid", "skyItemListOpacity", 0, 100))
            f.sub.add(ColorPickerSetting("Background Color", "", "skyItemListBgColor"))
            f.sub.add(ColorPickerSetting("Accent Color", "", "skyItemListAccentColor"))
            f.sub.add(ToggleSetting("Highlight NPC After Warp", "After clicking [Click to warp] for an NPC, outline that NPC on arrival", "npcWarpHighlight"))
            f.sub.add(ColorPickerSetting("NPC Highlight Color", "", "npcWarpHighlightColor"))
            f.sub.add(ToggleSetting("NPC Highlight Beam", "Beacon-style beam above the NPC", "npcWarpHighlightBeam"))
            hud.features.add(f)
        }
        run {
            val f = Feature("Auto BIN Price", "auctionPriceAutofillEnabled")
            f.sub.add(SubcategoryHeader("Prefills the AH \"Create Auction\" price field with item value minus a discount"))
            f.sub.add(SliderIntSetting("Discount %", "", "auctionAutofillPercent", 0, 50))
            hud.features.add(f)
        }
        run {
            val f = Feature("Sign Calculator", "signCalculator")
            f.sub.add(SubcategoryHeader("Type math like 9*9, 1.5k/2 or (2+3)*64 in Hypixel input signs; shows \"= 81\" and sends the result"))
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
            val sw = fishmod.features.hud.Stopwatch
            val trig = fishmod.features.hud.Stopwatch.Trigger.values()
            fun textGate(t: () -> fishmod.features.hud.Stopwatch.Trigger) = { t().name.let { it == "CHAT" || it == "CHAT_REGEX" || it == "GUI_OPEN" } }
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
            val f = Feature("Relic Slot 9 Scroll Lock", Floor7Settings::class.java, "relicSlot9ScrollLock")
            f.sub.add(SubcategoryHeader("Blocks mouse-wheel hotbar scrolling while you hold slot 9 during the P5 relic phase"))
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
        val end = Column("The End")
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
            val endAlerts = Feature("Superior Dragon Alert", fishmod.features.slayer.EndAlertSettings::class.java, "superiorDragonAlert")
            endAlerts.sub.add(SubcategoryHeader("Title when a Superior Dragon spawns"))
            endAlerts.sub.add(SliderIntSetting("Alert Duration (ms)", "", { fishmod.features.slayer.EndAlertSettings.endAlertDurationMs }, { v -> fishmod.features.slayer.EndAlertSettings.endAlertDurationMs = v }, 250, 8000, 250))
            end.features.add(endAlerts)
            end.features.add(Feature("Endstone Protector Alert", fishmod.features.slayer.EndAlertSettings::class.java, "endstoneProtectorAlert"))
            end.features.add(Feature("Zealot / Dragon Counter", fishmod.features.slayer.EndAlertSettings::class.java, "zealotHud"))
            slayer.features.add(Feature("Cocoon Timer", fishmod.features.slayer.EndAlertSettings::class.java, "cocoonTimer"))
            slayer.features.add(Feature("Summons Recall Alert", fishmod.features.slayer.EndAlertSettings::class.java, "summonsRecallAlert"))
            val egg = Feature("Egg Sac Highlight", fishmod.features.slayer.EndAlertSettings::class.java, "eggSacHighlight")
            egg.sub.add(ColorPickerSetting("Colour", "", { fishmod.features.slayer.EndAlertSettings.eggSacColor }, { v -> fishmod.features.slayer.EndAlertSettings.eggSacColor = v }))
            slayer.features.add(egg)
            val yang = Feature("Yang Glyph Tracer", fishmod.features.slayer.EndAlertSettings::class.java, "yangGlyphTracer")
            yang.sub.add(ColorPickerSetting("Colour", "", { fishmod.features.slayer.EndAlertSettings.yangGlyphColor }, { v -> fishmod.features.slayer.EndAlertSettings.yangGlyphColor = v }))
            slayer.features.add(yang)
            slayer.features.add(Feature("Broodmother Countdown", fishmod.features.slayer.EndAlertSettings::class.java, "broodmotherHud"))
            slayer.features.add(Feature("No Slayer Quest Warning", fishmod.features.slayer.EndAlertSettings::class.java, "noQuestWarning"))
            val dm = Feature("Deathmite Highlight", fishmod.features.dungeon.DungeonExtraSettings::class.java, "deathmiteHighlight")
            dm.sub.add(ColorPickerSetting("Colour", "", { fishmod.features.dungeon.DungeonExtraSettings.deathmiteColor }, { v -> fishmod.features.dungeon.DungeonExtraSettings.deathmiteColor = v }))
            dungeon.features.add(dm)
            val sk = Feature("Skeleton Skull Highlight", fishmod.features.dungeon.DungeonExtraSettings::class.java, "skullHighlight")
            sk.sub.add(ToggleSetting("Hide Skulls", "Hide Skeleton Skull armor stands instead", { fishmod.features.dungeon.DungeonExtraSettings.skullHide }, { v -> fishmod.features.dungeon.DungeonExtraSettings.skullHide = v }))
            sk.sub.add(ColorPickerSetting("Colour", "", { fishmod.features.dungeon.DungeonExtraSettings.skullColor }, { v -> fishmod.features.dungeon.DungeonExtraSettings.skullColor = v }))
            dungeon.features.add(sk)
            val lh = Feature("Teammate Low HP Alert", fishmod.features.dungeon.DungeonExtraSettings::class.java, "lowHpAlert")
            lh.sub.add(SliderIntSetting("Cooldown (s)", "", { fishmod.features.dungeon.DungeonExtraSettings.lowHpCooldownSec }, { v -> fishmod.features.dungeon.DungeonExtraSettings.lowHpCooldownSec = v }, 1, 30, 1))
            dungeon.features.add(lh)
            dungeon.features.add(Feature("Party HP HUD", fishmod.features.dungeon.DungeonExtraSettings::class.java, "partyHpHud"))
            dungeon.features.add(Feature("Coloured Class Level In Tab", fishmod.features.dungeon.DungeonExtraSettings::class.java, "classTabColor"))
            dungeon.features.add(Feature("Kismet Count On Reroll", fishmod.features.dungeon.DungeonExtraSettings::class.java, "kismetCount"))
            val mini = Feature("Miniboss Highlight", fishmod.features.slayer.EndAlertSettings::class.java, "slayerMiniHighlight")
            mini.sub.add(ColorPickerSetting("Outline Colour", "", { fishmod.features.slayer.EndAlertSettings.slayerMiniHighlightColor }, { v -> fishmod.features.slayer.EndAlertSettings.slayerMiniHighlightColor = v }))
            slayer.features.add(mini)
            val gi = Feature("Items On Ground Names", fishmod.features.slayer.EndAlertSettings::class.java, "groundItemNames")
            gi.sub.add(SubcategoryHeader("Name and count above dropped items"))
            gi.sub.add(SliderIntSetting("Range (blocks)", "", { fishmod.features.slayer.EndAlertSettings.groundItemRange }, { v -> fishmod.features.slayer.EndAlertSettings.groundItemRange = v }, 4, 64, 1))
            slayer.features.add(gi)
            val ms = Feature("Dungeon Milestone HUD", fishmod.features.dungeon.DungeonMilestoneSettings::class.java, "milestoneHud")
            ms.sub.add(ColorPickerSetting("Label Colour", "", { fishmod.features.dungeon.DungeonMilestoneSettings.milestoneColor }, { v -> fishmod.features.dungeon.DungeonMilestoneSettings.milestoneColor = v }))
            dungeon.features.add(ms)

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
            statsHud.sub.add(ButtonSetting("Reset Session Stats", "Zero the XP / kills counters", Runnable { fishmod.features.slayer.SlayerStatsTracker.reset() }))
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
            profit.sub.add(ButtonSetting("Reset This Mode", "Clear drops / bosses / time for the current Display mode, every slayer", Runnable { fishmod.features.slayer.SlayerProfitTracker.reset() }))
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
            phases.sub.add(ToggleSetting("HP Label", "Big current/max HP over the boss, coloured by %", "slayerPhaseHpLabel"))
            phases.sub.add(ToggleSetting("Time Label", "Time since the boss spawned, under the HP label", "slayerPhaseTimeLabel"))
            phases.sub.add(ToggleSetting("Hide Boss Nametag", "Hide the vanilla HP nametag while the HP label shows", "slayerPhaseHideNametag"))
            slayer.features.add(phases)

            val bt = fishmod.features.slayer.BossToolsSettings::class.java
            val bossInd = Feature("Boss Damage Indicator", bt, "bossIndicator")
            bossInd.sub.add(SubcategoryHeader("Boss Phases-style HP label on non-slayer bosses"))
            bossInd.sub.add(ToggleSetting("Dungeon Floor Bosses", "Bonzo, Scarf, Professor, Thorn, Livid, Sadan + giants", bt, "bossIndicatorDungeon"))
            bossInd.sub.add(ToggleSetting("Crimson Isle Bosses", "Bladesoul, Magma Boss, Ashfang, Duke, Outlaw, Vanquisher", bt, "bossIndicatorNether"))
            bossInd.sub.add(ToggleSetting("Other Bosses", "Endstone Protector, Horseman, Diana, Jawbus/Thunder, Arachne, Rift bosses", bt, "bossIndicatorOther"))
            bossInd.sub.add(ToggleSetting("Time Label", "Time since the boss was first seen", bt, "bossIndicatorTime"))
            slayer.features.add(bossInd)
        }

        val extrasCol = fishmod.features.extras.ExtrasMenu.column()
        val farmCol = fishmod.features.garden.GardenMenu.column()
        val chatCol = Column("Chat")
        val eventsCol = Column("Events")
        val alertsCol = Column("Alerts & Reminders")
        val visitorsCol = Column("Visitors & Pests")
        val huntCol = fishmod.features.fishing.FishingMenu.huntingColumn()
        val itemsCol = Column("Items & Tooltips")
        val petsCol = Column("Pets")
        fun take(to: Column, names: Set<String>, vararg from: Column) { for (c in from) { val m = c.features.filter { it.name in names }; c.features.removeAll(m); to.features.addAll(m) } }
        take(chatCol, setOf("Chat","Chat Filter","FishMod Chat (/fc)","Messages from RedFish","Mod Prefix","Guild Bridge Bot","Twitch Bridge","Chat Coord Waypoints","Command Tooltip in Chat","Compact Bestiary","SkyBlock XP in Chat","Hide Sky Mall / Lottery Messages","Command Tab Complete","Wiki Lookup"), general, extrasCol)
        take(invStorage, setOf("Inventory Buttons","Inventory Equipment","Hide Inventory Potion Effects","Wardrobe/Loadout Hotkeys","Pet Keybinds","Slot Binds","Slot Locking","Inventory Search","Backpack Preview","Fix Double Clicks","Middle Click Fix","Accessory Bag Upgrade List","Personal Compactor Contents","Stacking Enchant Progress","Item Level Overlay"), general, extrasCol)
        take(eventsCol, setOf("Calendar Real Times","SkyBlock Date HUD","Hoppity Hunt Summary","Hoppity Egg Waypoints","Stray Rabbit Alert","Trick or Treat Chest Alert","Gift Tracker HUD","Hidden Jerry Highlight","Reindrake Alert + Warp Link","Carnival Zombie Shootout Outlines","Fairy Soul Waypoints"), extrasCol)
        take(alertsCol, setOf("Limbo Alert","Server Restart Alert","Booster Cookie Warning","No Bits Available Warning","Kick Cooldown Timer","Order Filled Alert","Auction Outbid Alert","Party Join Sound","Vanquisher Party Share","Quiver Warning"), extrasCol)
        take(visitorsCol, setOf("Visitor Timer","Visitor Arrival Alert","Visitor Shopping List","Visitor Menu Info","Visitor Rare Reward Warning","Visitor Stats","Help Carrolyn","Pest Spawn Title","Pest Finder","Pest Timer HUD"), farmCol)
        take(huntCol, setOf("HotM / HotF Helper","Moonglade Beacon Solver"), extrasCol)
        take(itemsCol, setOf("Item Tooltip","Recipe Viewer","Item List","Auto BIN Price","Sign Calculator","Anita Medal Profit"), hud)
        take(petsCol, setOf("Pet HUD","Pet Swap Title","Hide Pet Nametag Level","Soulflow HUD","Pet Keybinds"), hud, invStorage)
        take(dungeon, setOf("Catacombs Overflow Levels","Fire Freeze Timer"), hud)
        columns.add(general)
        columns.add(chatCol)
        columns.add(invStorage)
        columns.add(party)
        columns.add(dungeon)
        columns.add(dungeonTrackers)
        columns.add(dungeonMap)
        columns.add(solvers)
        columns.add(floor7)
        columns.add(hud)
        columns.add(itemsCol)
        columns.add(petsCol)
        columns.add(slayer)
        columns.add(end)
        columns.add(huntCol)
        columns.add(fishmod.features.diana.DianaMenu.column())
        columns.add(fishmod.features.fishing.FishingMenu.column())
        columns.add(fishmod.features.mining.MiningMenu.column())
        columns.add(fishmod.features.kuudra.KuudraMenu.column())
        columns.add(fishmod.features.rift.RiftMenu.column())
        columns.add(extrasCol)
        columns.add(alertsCol)
        columns.add(eventsCol)
        columns.add(farmCol)
        columns.add(visitorsCol)
        columns.add(fishmod.features.achievements.AchMenu.column())
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
            val names = slot.substringAfter(':').split("+")
            for (n in names) { val c = byName[n]; if (c != null && used.add(n)) reordered.add(c) }
        }
        for (c in columns) if (used.add(c.name)) reordered.add(c)
        columns.clear()
        columns.addAll(reordered)
    }

    private fun saveColumnOrder() {
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

    // Explorer: one column open at a time
    private fun toggleFold(c: Column, push: Boolean = true) {
        for (o in columns) if (o !== c) foldedColumns.add(o.name)
        foldedColumns.remove(c.name)
        saveFoldedColumns()
        frameVisibleColumns = null
        allMode = false
        selected = null; gridScroll = 0; detScroll = 0
        openedAt = System.currentTimeMillis()
        if (tabs.isEmpty()) tabs.add(c.name) else tabs[ti] = c.name
        if (push) { while (hist.size > hi + 1) hist.removeAt(hist.size - 1); hist.add(c.name); hi = hist.size - 1 }
    }

    // Tabs, back/forward history and card animation state
    private val tabs = ArrayList<String>()
    private var ti = 0
    private val hist = ArrayList<String>()
    private var hi = -1
    private var openedAt = System.currentTimeMillis()
    private var selAt = 0L
    private fun colByName(n: String): Column? = columns.firstOrNull { it.name == n }
    private fun anim(start: Long, delayMs: Int = 0, durMs: Int = 260): Float {
        if (!FishSettings.fmUiAnimations) return 1f
        val t = ((System.currentTimeMillis() - start - delayMs) / durMs.toFloat()).coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }
    private fun fa(c: Int, a: Float): Int = (((c ushr 24) * a).toInt() shl 24) or (c and 0xFFFFFF)
    private fun tabRects(): List<IntArray> {
        var x = left() + 8
        val out = ArrayList<IntArray>()
        for (t in tabs) { val w = Math.min(120, sw(this.font, t, 0.85f) + 30); out.add(intArrayOf(x, w)); x += w + 4 }
        out.add(intArrayOf(x, 20))
        return out
    }
    private fun renderTabs(mx: Int, my: Int) {
        if (tabs.isEmpty()) visibleColumns().firstOrNull()?.let { tabs.add(it.name); hist.add(it.name); hi = 0 }
        val r = tabRects()
        val y = TAB_Y
        for ((i, t) in tabs.withIndex()) {
            val x = r[i][0]; val w = r[i][1]
            val hov = mx in x..x + w && my in y..y + 20
            val a = hover("tab$i", hov)
            UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), 20f, 6f, if (i == ti) ScreenTheme.ink(0x33) else ScreenTheme.ink(0x14))
            if (i != ti && a > 0f) UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), 20f, 6f, fa(ROW_HOVER, a))
            val ts = 0.85f * NVG_BASE_TEXT_SIZE
            UiRecorder.text(ellipsize(t, w - 26, 0.85f), (x + 8).toFloat(), y + (20 - ts) / 2f, ts, if (i == ti) TEXT_COLOR else SUBTEXT_COLOR)
            if (tabs.size > 1) icon("x", x + w - 10f, y + 10f, if (hov && mx >= x + w - 16) TEXT_COLOR else SUBTEXT_COLOR)
        }
        val p = r[tabs.size]
        val ph = mx in p[0]..p[0] + 20 && my in y..y + 20
        UiRecorder.fillRoundedRect(p[0].toFloat(), y.toFloat(), 20f, 20f, 6f, ScreenTheme.ink(0x14))
        UiRecorder.fillRoundedRect(p[0].toFloat(), y.toFloat(), 20f, 20f, 6f, fa(ROW_HOVER, hover("tab+", ph)))
        icon("plus", p[0] + 10f, y + 10f, if (ph) TEXT_COLOR else SUBTEXT_COLOR)
    }
    private fun tabClick(mx: Int, my: Int): Boolean {
        val y = TAB_Y
        if (my !in y..y + 20) return false
        for ((i, r) in tabRects().withIndex()) {
            if (mx !in r[0]..r[0] + r[1]) continue
            when {
                i == tabs.size -> visibleColumns().firstOrNull()?.let { tabs.add(it.name); ti = tabs.size - 1 }
                tabs.size > 1 && mx >= r[0] + r[1] - 16 -> { tabs.removeAt(i); if (ti >= i && ti > 0) ti--; colByName(tabs[ti])?.let { toggleFold(it) } }
                else -> { ti = i; colByName(tabs[i])?.let { toggleFold(it) } }
            }
            return true
        }
        return false
    }


    // The menu is a centered, draggable window; double-click its top bar to maximize
    private var winDX = 0
    private var winDY = 0
    private var winMax = false
    private var winDrag: IntArray? = null
    private var lastBarClick = 0L
    private fun scrW(): Int = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
    private fun scrH(): Int = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
    private fun winW(): Int = if (winMax) scrW() else Math.min(860, (scrW() * 0.78f).toInt())
    private fun winH(): Int = if (winMax) scrH() else Math.min(520, (scrH() * 0.8f).toInt())
    private fun left(): Int = if (winMax) 0 else Mth.clamp((scrW() - winW()) / 2 + winDX, 0, scrW() - winW())
    private fun top(): Int = (if (winMax) 0 else Mth.clamp((scrH() - winH()) / 2 + winDY, 0, scrH() - winH())) + Math.round((1f - winP()) * 28f)
    private val winOpenAt = System.currentTimeMillis()
    private var closeAt = 0L
    private fun winP(): Float = anim(winOpenAt, 0, 220) * (if (closing) 1f - anim(closeAt, 0, 160) else 1f)

    // Smooth hover: each control eases its highlight in and out
    private val hv = HashMap<String, Float>()
    private var lastHoverNs = System.nanoTime()
    private var frameNs = System.nanoTime()
    private fun hover(key: String, on: Boolean): Float {
        val cur = hv[key] ?: 0f
        val dt = ((frameNs - lastHoverNs).coerceIn(0L, 100_000_000L)) / 1_000_000f
        val n = if (!FishSettings.fmUiAnimations) (if (on) 1f else 0f) else cur + ((if (on) 1f else 0f) - cur) * (1f - Math.exp(-dt / 60.0).toFloat())
        hv[key] = if (Math.abs(n - (if (on) 1f else 0f)) < 0.02f) (if (on) 1f else 0f) else n
        return hv[key]!!
    }
    private val knob = HashMap<String, Float>()
    private fun knobP(key: String, on: Boolean): Float {
        val t = if (on) 1f else 0f
        val k = 1f - Math.exp(-((frameNs - lastHoverNs).coerceIn(0L, 100_000_000L) / 1_000_000.0) / 50.0).toFloat()
        val n = if (!FishSettings.fmUiAnimations) t else (knob[key] ?: t).let { it + (t - it) * k }
        knob[key] = n
        return n
    }
    // Text centered in a box (measured, not guessed)
    private fun ctext(s: String, x: Int, y: Int, w: Int, h: Int, scale: Float, color: Int) {
        val size = NVG_BASE_TEXT_SIZE * scale
        UiRecorder.text(s, x + (w - UiRecorder.textWidth(s, size)) / 2f, y + (h - size) / 2f, size, color)
    }
    // Drawn icons so they sit dead-centre (font glyphs for arrows/x are off-centre)
    private fun icon(id: String, cx: Float, cy: Float, c: Int) {
        when (id) {
            "min" -> UiRecorder.fillRect(cx - 5f, cy, 10f, 1f, c)
            "max" -> UiRecorder.roundedRectRing(cx - 4.5f, cy - 4.5f, 9f, 9f, 1.5f, 1f, 0, c)
            "close", "x" -> { val r = if (id == "x") 3f else 4.5f; UiRecorder.line(cx - r, cy - r, cx + r, cy + r, 1.2f, c); UiRecorder.line(cx - r, cy + r, cx + r, cy - r, 1.2f, c) }
            "plus" -> { UiRecorder.fillRect(cx - 4f, cy - 0.5f, 8f, 1f, c); UiRecorder.fillRect(cx - 0.5f, cy - 4f, 1f, 8f, c) }
            "back", "fwd" -> { val d = if (id == "back") -1f else 1f; UiRecorder.line(cx - 5f * d, cy, cx + 5f * d, cy, 1.2f, c); UiRecorder.line(cx + 5f * d, cy, cx + 1.5f * d, cy - 3.5f, 1.2f, c); UiRecorder.line(cx + 5f * d, cy, cx + 1.5f * d, cy + 3.5f, 1.2f, c) }
            "up" -> { UiRecorder.line(cx, cy + 5f, cx, cy - 5f, 1.2f, c); UiRecorder.line(cx, cy - 5f, cx - 3.5f, cy - 1.5f, 1.2f, c); UiRecorder.line(cx, cy - 5f, cx + 3.5f, cy - 1.5f, 1.2f, c) }
        }
    }
    private fun right(): Int = left() + winW()
    private fun bottom(): Int = top() + winH()

    private fun vx(real: Number): Int = (real.toDouble() / fishmod.utils.rendering.UiScale.factor()).toInt()

    private fun cx0(): Int = left() + MARGIN + STACK_W + 18
    private fun cx1(): Int = right() - MARGIN
    private fun cyTop(): Int = top() + 92 + HEADER_H
    private fun cyBot(): Int = bottom() - STATUS_H - 6

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

    private fun requestClose() {
        if (closing) return
        closing = true
        closeAt = System.currentTimeMillis()
    }

    private fun currentCardBg(): Int {
        val alpha = Mth.clamp(Math.round(FishSettings.fmBgAlpha * 2.55f), 0, 255)
        return (alpha shl 24) or cardBgRgb()
    }

    private fun cardBgRgb(): Int = ScreenTheme.windowBg() and 0xFFFFFF

    private fun brighten(rgb: Int, amount: Int): Int {
        val a = (rgb ushr 24) and 0xFF
        val r = Mth.clamp(((rgb ushr 16) and 0xFF) + amount, 0, 255)
        val g = Mth.clamp(((rgb ushr 8) and 0xFF) + amount, 0, 255)
        val b = Mth.clamp((rgb and 0xFF) + amount, 0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun refreshButtonTheme() {
        if (!FishSettings.fmThemeV2) {
            FishSettings.fmThemeV2 = true
            FishSettings.fmButtonColor = 0xFFD6D6D6.toInt(); FishSettings.fmButtonAlpha = 100
            FishSettings.fmRowColor = 0xFFD6D6D6.toInt(); FishSettings.fmRowAlpha = 60; FishSettings.fmRowBgAlpha = 100
            FishSettings.fmBgPreset = "Graphite"
        }
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


    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) { }
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) { }

    private var widgetRenderFailureLogged = false
    private var recorderSizeLogged = false

    private var frameCaching = false
    private var frameVisibleColumns: List<Column>? = null

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        frameVisibleColumns = null
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

        if (closing && !closeFinalized && (!FishSettings.fmUiAnimations || System.currentTimeMillis() - closeAt >= 170)) {
            closeFinalized = true
            try { ConfigRoot.manager.save() } catch (t: Throwable) { FishDiag.fail("FishModScreen.8", "Config save on /fm close failed", t) }
            try { FishConfig.manager.save() } catch (t: Throwable) { FishDiag.fail("FishModScreen.9", "FishConfig save on /fm close failed", t) }
            fishmod.features.achievements.AchUsage.onConfigSaved()
            FishDiag.guard("FishModScreen.ach", "achievement feature scan failed") { fishmod.features.achievements.AchUsage.onFeatureScan(columns.flatMap { it.features }); fishmod.features.achievements.AchUsage.onActivityScan(columns.map { it.name to it.features }) }
            super.onClose()
            return
        }

        UiRecorder.clear()
        lastHoverNs = frameNs; frameNs = System.nanoTime()

        extractBlurredBackground(ctx)
        ctx.fillGradient(0, 0, this.width, this.height, DIM_TOP, DIM_BOT)
        val wr = if (winMax) 0f else 12f
        UiRecorder.dropShadow(left().toFloat(), top().toFloat(), winW().toFloat(), winH().toFloat(), wr, 18f, 0x80000000.toInt())
        UiRecorder.roundedRectRing(left().toFloat(), top().toFloat(), winW().toFloat(), winH().toFloat(), wr, 1f, fa(currentCardBg(), winP()), fa(ScreenTheme.ink(0x33), winP()))

        val vmx = vx(mouseX)
        val vmy = vx(mouseY)

        hoverDesc = null
        hoverPreview = null
        try {
            renderContent(ctx, vmx, vmy)
            renderChrome(ctx, vmx, vmy)
            statusHint = null
            renderHoverTooltip(ctx)
            renderShiftPreview(ctx)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.10", "widget rendering failed search='$searchText'", t)
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

    // Window chrome, laid out like docs/ui-mockups/fishmod-explorer.html:
    // title bar (tabs, – □ ✕), command bar, address bar (← → ↑, breadcrumb, search), status bar
    private val TAB_Y get() = top() + 5
    private val CMD_Y get() = top() + 32
    private val ADDR_Y get() = top() + 60
    private var listView = false
    private class Btn(val id: String, val label: String, val x: Int, val y: Int, val w: Int, val h: Int)
    private fun cmdButtons(): List<Btn> {
        val out = ArrayList<Btn>()
        var x = left() + 10
        for ((id, label) in listOf("hud" to "Edit HUD", "|" to "", "report" to "Report", "credits" to "Credits", "reset" to (if (resetArmed) "Confirm?" else "Reset"), "|" to "", "look" to "Appearance")) {
            if (id == "|") { x += 9; continue }
            val w = sw(this.font, label, 0.85f) + 18
            out.add(Btn(id, label, x, CMD_Y, w, 22)); x += w + 2
        }
        val gw = 40
        // segmented control: 3px padding on every side, flush with the 10px window margin
        val sx0 = right() - 10 - (gw * 2 + 6)
        out.add(Btn("grid", "Grid", sx0 + 3, CMD_Y + 3, gw, 16))
        out.add(Btn("list", "List", sx0 + 3 + gw, CMD_Y + 3, gw, 16))
        return out
    }
    private fun winButtons(): List<Btn> = listOf("min" to "–", "max" to "□", "close" to "✕").mapIndexed { i, (id, l) -> Btn(id, l, right() - 36 * (3 - i), top(), 36, 28) }
    private fun navButtons(): List<Btn> = listOf("back" to "←", "fwd" to "→", "up" to "↑").mapIndexed { i, (id, l) -> Btn(id, l, left() + 10 + i * 26, ADDR_Y + 2, 24, 22) }
    private fun searchRect(): IntArray = intArrayOf(right() - 10 - 220, ADDR_Y + 2, 220, 22)
    private fun hov(b: Btn, mx: Int, my: Int) = mx in b.x..b.x + b.w && my in b.y..b.y + b.h

    private fun renderChrome(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        val l = left().toFloat(); val w = winW().toFloat()
        UiRecorder.fillRect(l, (top() + 30).toFloat(), w, 1f, ScreenTheme.ink(0x1F))
        UiRecorder.fillRect(l, (top() + 58).toFloat(), w, 1f, ScreenTheme.ink(0x1F))
        UiRecorder.fillRect(l, (top() + 88).toFloat(), w, 1f, ScreenTheme.ink(0x1F))
        UiRecorder.fillRect(l, (bottom() - STATUS_H).toFloat(), w, 1f, ScreenTheme.ink(0x1F))
        renderTabs(mx, my)
        for (b in winButtons()) {
            val h = hov(b, mx, my)
            val a = hover("w" + b.id, h)
            if (a > 0f) UiRecorder.fillRoundedRectCorners(b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat(), 0f, if (b.id == "close" && !winMax) 12f else 0f, 0f, 0f, fa(if (b.id == "close") 0xFFC42B1C.toInt() else 0x33808080, a))
            icon(b.id, b.x + b.w / 2f, b.y + b.h / 2f, if (h) 0xFFFFFFFF.toInt() else TEXT_COLOR)
        }
        for (b in cmdButtons()) {
            val on = (b.id == "grid" && !listView) || (b.id == "list" && listView)
            val h = hov(b, mx, my)
            val a = hover("c" + b.id, h)
            if (b.id == "grid" || b.id == "list") {
                if (b.id == "grid") UiRecorder.fillRoundedRect((b.x - 3).toFloat(), (b.y - 3).toFloat(), (b.w * 2 + 6).toFloat(), (b.h + 6).toFloat(), 6f, ScreenTheme.ink(0x14))
                UiRecorder.fillRoundedRect(b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat(), 5f, fa(ScreenTheme.ink(0x2E), hover("v" + b.id, on)))
                if (!on && a > 0f) UiRecorder.fillRoundedRect(b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat(), 5f, fa(ROW_HOVER, a))
            } else if (a > 0f) UiRecorder.fillRoundedRect(b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat(), 6f, fa(0x2E808080, a))
            val c = if (b.id == "reset" && resetArmed) 0xFFE05A5A.toInt() else TEXT_COLOR
            ctext(b.label, b.x, b.y, b.w, b.h, 0.85f, c)
        }
        var sx = left() + 10 + sw(this.font, "Edit HUD", 0.85f) + 22
        UiRecorder.fillRect(sx.toFloat(), (CMD_Y + 5).toFloat(), 1f, 12f, ScreenTheme.ink(0x33))
        sx = cmdButtons().first { it.id == "look" }.x - 6
        UiRecorder.fillRect(sx.toFloat(), (CMD_Y + 5).toFloat(), 1f, 12f, ScreenTheme.ink(0x33))
        for (b in navButtons()) {
            val can = when (b.id) { "back" -> hi > 0; "fwd" -> hi < hist.size - 1; else -> selected != null || searchText.isNotEmpty() }
            val a = hover("n" + b.id, can && hov(b, mx, my))
            if (a > 0f) UiRecorder.fillRoundedRect(b.x.toFloat(), b.y.toFloat(), b.w.toFloat(), b.h.toFloat(), 6f, fa(0x2E808080, a))
            icon(b.id, b.x + b.w / 2f, b.y + b.h / 2f, if (can) TEXT_COLOR else 0xFF555555.toInt())
        }
        val s = searchRect()
        val cx = left() + 92; val cw = s[0] - 8 - cx
        UiRecorder.roundedRectRing(cx.toFloat(), s[1].toFloat(), cw.toFloat(), s[3].toFloat(), 6f, 1f, 0, ScreenTheme.ink(0x2E))
        val col = visibleColumns().firstOrNull()
        val crumb = "FishMod  ›  " + (if (searchText.isNotEmpty()) "Search results" else if (allMode) "All features" else col?.name ?: "") + (selected?.let { "  ›  " + it.name } ?: "")
        UiRecorder.text(ellipsize(crumb, cw - 16, 0.85f), (cx + 8).toFloat(), s[1] + (s[3] - 0.85f * NVG_BASE_TEXT_SIZE) / 2f, 0.85f * NVG_BASE_TEXT_SIZE, TEXT_COLOR)
        UiRecorder.roundedRectRing(s[0].toFloat(), s[1].toFloat(), s[2].toFloat(), s[3].toFloat(), 6f, 1f, ScreenTheme.ink(0x0F), if (searchFocused) ACCENT else ScreenTheme.ink(0x2E))
        var field = searchField
        if (field == null) {
            field = EditBox(this.font, s[0] + 8, s[1] + 4, s[2] - 16, s[3] - 8, Component.empty())
            field.setMaxLength(48)
            field.setBordered(false)
            field.setResponder { t -> searchText = t; gridScroll = 0; openedAt = System.currentTimeMillis() }
            searchField = field
        } else { field.setX(s[0] + 8); field.setY(s[1] + 4); field.setWidth(s[2] - 16) }
        if (searchText.isEmpty() && !searchFocused) UiRecorder.text("Search features", (s[0] + 8).toFloat(), s[1] + (s[3] - 0.85f * NVG_BASE_TEXT_SIZE) / 2f, 0.85f * NVG_BASE_TEXT_SIZE, SUBTEXT_COLOR)
        else nvgTextFieldContent(field, s[0] + 8, s[1] + 4, s[2] - 16, s[3] - 8, 8f)
        val all = columns.sumOf { it.features.count { f -> f.hasMaster() } }
        val on = columns.sumOf { it.features.count { f -> f.hasMaster() && f.get!!() } }
        val sy = bottom() - STATUS_H + 7
        sst(ctx, this.font, statusHint ?: ("${exItems().size} items" + (if (selected != null) "    1 selected" else "")), left() + 12, sy, if (statusHint != null) TEXT_COLOR else SUBTEXT_COLOR, 0.8f)
        val st = "$on of $all features on"
        sst(ctx, this.font, st, right() - 12 - sw(this.font, st, 0.8f), sy, SUBTEXT_COLOR, 0.8f)
    }

    private fun chromeClick(mx: Int, my: Int): Boolean {
        val s = searchRect()
        searchFocused = mx in s[0]..s[0] + s[2] && my in s[1]..s[1] + s[3]
        searchField?.setFocused(searchFocused)
        if (searchFocused) return true
        for (b in winButtons()) if (hov(b, mx, my)) { if (b.id == "max") winMax = !winMax else onClose(); return true }
        if (tabClick(mx, my)) return true
        if (my in top()..top() + 30) {
            val now = System.currentTimeMillis()
            if (now - lastBarClick < 300) { winMax = !winMax; winDrag = null } else if (!winMax) winDrag = intArrayOf(mx - winDX, my - winDY)
            lastBarClick = now
            return true
        }
        for (b in cmdButtons()) if (hov(b, mx, my)) {
            when (b.id) {
                "hud" -> Minecraft.getInstance().showScreen(FishHudEditor(this))
                "report" -> Minecraft.getInstance().showScreen(BoardReportScreen(this))
                "credits" -> Minecraft.getInstance().showScreen(CreditsScreen(this))
                "reset" -> if (resetArmed) { resetAllColumns(); resetArmed = false } else { resetArmed = true; resetArmedAt = System.currentTimeMillis() }
                "look" -> columns.firstOrNull { c -> c.features.any { it.name == "UI Customization" } }?.let { c -> toggleFold(c); selected = c.features.first { it.name == "UI Customization" }; selAt = System.currentTimeMillis() }
                "grid" -> { listView = false; openedAt = System.currentTimeMillis() }
                "list" -> { listView = true; openedAt = System.currentTimeMillis() }
            }
            return true
        }
        for (b in navButtons()) if (hov(b, mx, my)) {
            when (b.id) {
                "back", "fwd" -> { val k = if (b.id == "back") -1 else 1; if (hi + k in hist.indices) { hi += k; colByName(hist[hi])?.let { toggleFold(it, false) } } }
                "up" -> if (selected != null) selected = null else if (searchText.isNotEmpty()) { searchField?.value = ""; searchText = "" }
            }
            return true
        }
        return my < top() + 88
    }

    // Sidebar like the mockup: PINNED + OTHER COLUMNS, tight rows; right-click pins/unpins
    private var allMode = false
    private var statusHint: String? = null
    private val pinned by lazy { LinkedHashSet(FishSettings.fmPinned.split(",").filter { it.isNotBlank() }) }
    private var navScroll = 0
    private fun navRows(): List<Pair<String, Column?>> {
        val out = ArrayList<Pair<String, Column?>>()
        out.add("All features" to null)
        val p = columns.filter { it.name in pinned }
        if (p.isNotEmpty()) { out.add("PINNED" to null); p.forEach { out.add(it.name to it) } }
        out.add((if (p.isEmpty()) "COLUMNS" else "OTHER COLUMNS") to null)
        columns.filter { it.name !in pinned }.forEach { out.add(it.name to it) }
        return out
    }
    private fun navTop(): Int = top() + 92
    private fun navBot(): Int = bottom() - STATUS_H
    private fun navMax(): Int = Math.max(0, navRows().size * NAV_ROW + 8 - (navBot() - navTop()))
    private fun renderNav(mx: Int, my: Int) {
        navScroll = Mth.clamp(navScroll, 0, navMax())
        val x = stackX0(); val w = STACK_W
        UiRecorder.pushScissor(left().toFloat(), navTop().toFloat(), (w + MARGIN).toFloat(), (navBot() - navTop()).toFloat())
        try {
            for ((i, r) in navRows().withIndex()) {
                val y = navTop() + 4 + i * NAV_ROW - navScroll
                if (y + NAV_ROW < navTop() || y > navBot()) continue
                val c = r.second
                if (c == null && r.first == "All features") {
                    val hvA = mx in x..x + w && my in y until y + NAV_ROW && my in navTop()..navBot()
                    val haA = hover("navAll", hvA)
                    if (allMode) UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), NAV_ROW.toFloat(), 4f, ScreenTheme.ink(0x24))
                    else if (haA > 0f) UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), NAV_ROW.toFloat(), 4f, fa(ROW_HOVER, haA))
                    val tsA = NVG_BASE_TEXT_SIZE
                    UiRecorder.text("All features", (x + 10).toFloat(), y + (NAV_ROW - tsA) / 2f, tsA, if (allMode) TEXT_COLOR else 0xFFC8C8C8.toInt())
                    val tot = columns.sumOf { it.features.size }.toString()
                    val ns2 = 0.82f * NVG_BASE_TEXT_SIZE
                    UiRecorder.text(tot, x + w - 10 - UiRecorder.textWidth(tot, ns2), y + (NAV_ROW - ns2) / 2f, ns2, SUBTEXT_COLOR)
                    continue
                }
                if (c == null) { UiRecorder.text(r.first, (x + 10).toFloat(), y + 6f, 7.4f, SUBTEXT_COLOR); continue }
                val open = !allMode && !isFolded(c)
                val hv = mx in x..x + w && my in y until y + NAV_ROW && my in navTop()..navBot()
                if (hv) statusHint = if (c.name in pinned) "Right-click to unpin ${c.name}" else "Right-click to pin ${c.name}"
                val ha = hover("nav" + c.name, hv)
                if (open) UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), NAV_ROW.toFloat(), 4f, ScreenTheme.ink(0x24))
                else if (ha > 0f) UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), NAV_ROW.toFloat(), 4f, fa(ROW_HOVER, ha))
                val n = "${c.features.count { it.hasMaster() && it.get!!() }}/${c.features.size}"
                val ns = 0.82f * NVG_BASE_TEXT_SIZE
                val nw = UiRecorder.textWidth(n, ns)
                val ts = 1.0f * NVG_BASE_TEXT_SIZE
                UiRecorder.text(ellipsize(c.name, (w - nw - 26).toInt(), 1.0f), (x + 10).toFloat(), y + (NAV_ROW - ts) / 2f, ts, if (open) TEXT_COLOR else 0xFFC8C8C8.toInt())
                UiRecorder.text(n, x + w - 10 - nw, y + (NAV_ROW - ns) / 2f, ns, SUBTEXT_COLOR)
            }
        } finally { UiRecorder.popScissor() }
    }
    private fun navClick(mx: Int, my: Int, btn: Int): Boolean {
        if (mx !in left()..stackX0() + STACK_W || my !in navTop()..navBot()) return false
        val i = (my - navTop() - 4 + navScroll) / NAV_ROW
        val row = navRows().getOrNull(i) ?: return true
        if (row.first == "All features" && row.second == null) { allMode = true; selected = null; gridScroll = 0; openedAt = System.currentTimeMillis(); return true }
        val c = row.second ?: return true
        if (btn == 1) { if (!pinned.remove(c.name)) pinned.add(c.name); FishSettings.fmPinned = pinned.joinToString(",") }
        else toggleFold(c)
        return true
    }

    // Explorer layout: open column as a card grid, selected feature's settings in a side pane
    private var selected: Feature? = null
    private var gridScroll = 0
    private var detScroll = 0
    private fun exItems(): List<Feature> =
        if (searchText.isNotEmpty()) visibleColumns().flatMap { visibleFeatures(it) }
        else if (allMode) columns.flatMap { visibleFeatures(it) }
        else visibleColumns().firstOrNull()?.let { visibleFeatures(it) } ?: emptyList()
    private fun gx1(): Int = if (selected != null) cx1() - DET_W - COLUMN_GUTTER else cx1()
    private fun cardH(): Int = if (listView) 26 else CARD_TALL
    private fun gridCols(): Int = if (listView) 1 else Math.max(1, (gx1() - cx0() + CARD_GAP) / (CARD_MIN_W + CARD_GAP))
    private fun cardW(): Int { val n = gridCols(); return (gx1() - cx0() - (n - 1) * CARD_GAP) / n }
    private fun cardX(i: Int): Int = cx0() + (i % gridCols()) * (cardW() + CARD_GAP)
    private fun cardY(i: Int): Int = cyTop() + (i / gridCols()) * (cardH() + CARD_GAP) - gridScroll
    private fun gridMax(n: Int): Int = Math.max(0, ((n + gridCols() - 1) / gridCols()) * (cardH() + CARD_GAP) - (cyBot() - cyTop()))
    private fun detTop(): Int = cyTop() - HEADER_H + 40
    private fun detMax(f: Feature): Int = Math.max(0, f.naturalSubHeight() - (cyBot() - detTop()))

    private fun renderContent(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sbx = stackX0() + STACK_W + 8
        UiRecorder.fillRect(left().toFloat(), (top() + 89).toFloat(), (sbx - left()).toFloat(), (bottom() - STATUS_H - top() - 89).toFloat(), 0x0D000000)
        UiRecorder.fillRect(sbx.toFloat(), (top() + 89).toFloat(), 1f, (bottom() - STATUS_H - top() - 89).toFloat(), ScreenTheme.ink(0x1F))
        renderNav(mouseX, mouseY)
        val items = exItems()
        val col = visibleColumns().firstOrNull()
        val ht = cyTop() - HEADER_H
        val title = if (searchText.isNotEmpty()) "Search  ·  ${items.size} results" else if (allMode) "All features" else col?.name ?: "Click a tab on the left to open a column"
        sst(ctx, this.font, ellipsize(title, gx1() - cx0() - 80, 1.2f), cx0(), ht + 6, TEXT_COLOR, 1.2f)
        if (col != null || searchText.isNotEmpty() || allMode) sst(ctx, this.font, "${items.size} features", cx0() + Math.min(sw(this.font, title, 1.2f), gx1() - cx0() - 80) + 8, ht + 9, SUBTEXT_COLOR, 0.8f)
        if (searchText.isEmpty() && !allMode) col?.sub()?.let { sst(ctx, this.font, ellipsize(it, gx1() - cx0(), 0.8f), cx0(), ht + 22, SUBTEXT_COLOR, 0.8f) }
        gridScroll = Mth.clamp(gridScroll, 0, gridMax(items.size))
        val cw = cardW()
        UiRecorder.pushScissor(cx0().toFloat(), cyTop().toFloat(), (gx1() - cx0()).toFloat(), (cyBot() - cyTop()).toFloat())
        try {
            for ((i, f) in items.withIndex()) {
                val p = if (searchText.isEmpty()) anim(openedAt, Math.min(i, 24) * 25) else 1f
                val x = cardX(i); val y = cardY(i) + Math.round((1f - p) * 10f)
                if (y + cardH() < cyTop() || y > cyBot()) continue
                val on = f.hasMaster() && f.get!!()
                val hover = mouseY in cyTop()..cyBot() && mouseX in x..x + cw && mouseY in y..y + cardH()
                val xf = x.toFloat(); val yf = y.toFloat()
                UiRecorder.fillRoundedRect(xf, yf, cw.toFloat(), cardH().toFloat(), 6f, fa(if (f === selected) ScreenTheme.ink(0x24) else ScreenTheme.ink(0x10), p))
                val ha = hover("card" + f.name, hover)
                if (ha > 0f && f !== selected) UiRecorder.fillRoundedRect(xf, yf, cw.toFloat(), cardH().toFloat(), 6f, fa(ScreenTheme.ink(0x10), ha))
                if (f === selected) UiRecorder.roundedRectRing(xf, yf, cw.toFloat(), cardH().toFloat(), 6f, 1f, 0, ACCENT)
                val d = descFor(f.name)
                if (listView) {
                    sst(ctx, this.font, ellipsize(f.name, 180), x + 10, y + (cardH() - 10) / 2, fa(if (on) TEXT_COLOR else SUBTEXT_COLOR, p), 1f)
                    if (d.isNotEmpty()) sst(ctx, this.font, ellipsize(d, cw - 250, 0.8f), x + 200, y + (cardH() - 8) / 2, fa(SUBTEXT_COLOR, p), 0.8f)
                } else {
                    sst(ctx, this.font, ellipsize(f.name, cw - 44), x + 8, y + 7, fa(if (on) TEXT_COLOR else SUBTEXT_COLOR, p), 1f)
                    if (d.isNotEmpty()) sst(ctx, this.font, ellipsize(d, cw - 20, 0.8f), x + 8, y + 24, fa(SUBTEXT_COLOR, p), 0.8f)
                }
                if (p < 0.6f) continue
                if (f.hasMaster()) drawTogglePill(ctx, x + cw - 32, y + (if (listView) (cardH() - 13) / 2 else 6), 24, 13, on, knobP(f.name, on), hover && mouseX >= x + cw - 38)
                else if (f.sub.isNotEmpty()) drawChevron(ctx, x + cw - 16, y + 12, false, CHEVRON_COLOR)
                if (hover) statusHint = "Left-click: settings   ·   Right-click: turn " + (if (on) "off" else "on")
                if (hover && (HudPreview.namesFor(f.name).isNotEmpty() || WorldPreview.has(f.name))) hoverPreview = f.name
            }
        } finally { UiRecorder.popScissor() }
        if (items.isEmpty() && col != null) sst(ctx, this.font, "No features match", cx0(), cyTop() + 8, SUBTEXT_COLOR, 1f)
        val f = selected ?: return
        val dx0 = cx1() - DET_W + Math.round((1f - anim(selAt, 0, 220)) * 24f)
        UiRecorder.fillRoundedRect(dx0.toFloat(), ht.toFloat(), DET_W.toFloat(), (cyBot() - ht).toFloat(), CARD_RADIUS.toFloat(), ScreenTheme.ink(0x0F))
        // same type sizes and baselines as the column header on the left
        sst(ctx, this.font, ellipsize(f.name, DET_W - 40, 1.2f), dx0 + 12, ht + 6, TEXT_COLOR, 1.2f)
        descFor(f.name).takeIf { it.isNotEmpty() }?.let { sst(ctx, this.font, ellipsize(it, DET_W - 24, 0.8f), dx0 + 12, ht + 22, SUBTEXT_COLOR, 0.8f) }
        val xh = mouseX >= cx1() - 20 && mouseY in ht..ht + 20
        icon("x", cx1() - 12f, ht + 11f, if (xh) TEXT_COLOR else SUBTEXT_COLOR)
        detScroll = Mth.clamp(detScroll, 0, detMax(f))
        if (f.sub.isEmpty()) { sst(ctx, this.font, "No settings", dx0 + 12, detTop() + 6, SUBTEXT_COLOR, 0.9f); return }
        UiRecorder.pushScissor(dx0.toFloat(), detTop().toFloat(), DET_W.toFloat(), (cyBot() - detTop()).toFloat())
        try { renderSubPanel(ctx, f, dx0, cx1(), detTop() - detScroll, f.naturalSubHeight(), mouseX, if (mouseY >= detTop()) mouseY else -10000) }
        finally { UiRecorder.popScissor() }
    }

    private fun subClick(f: Feature, x0: Int, x1: Int, top: Int, mx: Int, my: Int, btn: Int) {
        val leftX = x0 + 14
        val rightX = x1 - 12
        var ssy = top + 6
        for (s in f.sub) {
            if (s.hiddenByGate()) continue
            val sh = s.getHeight()
            if (my >= ssy && my <= ssy + sh && (s is InputSetting || s is InputIntSetting || s is InputDoubleSetting || s is ColorPickerSetting)) activeInput = s
            val clicked = try { s.onClick(mx, my, leftX, rightX, ssy, btn) } catch (t: Throwable) {
                FishDiag.fail("FishModScreen.14", "setting '${s.name}' (${s.javaClass.simpleName}) in '${f.name}' click failed", t); true
            }
            if (clicked) { if (s is KeybindSetting && s.capturing) capturingKeybind = s; if (s is SliderIntSetting || s is SliderDoubleSetting) activeInput = s; return }
            if (s is SliderIntSetting || s is SliderDoubleSetting) {
                val slx = leftX + 2
                val slw = rightX - leftX - 4 - SLIDER_BOX
                val sly = ssy + SLIDER_CTRL_Y
                if (mx >= slx && mx <= slx + slw && my >= sly - 4 && my <= sly + SLIDER_H + 4) {
                    activeSlider = s; activeSliderX = slx; activeSliderW = slw; s.onDrag(mx, slx, slw); return
                }
            }
            ssy += sh
        }
    }

    private fun exClick(mx: Int, my: Int, btn: Int): Boolean {
        if (my < cyTop() && mx < gx1()) { selected = null; return true }
        val f = selected
        if (f != null && mx >= cx1() - DET_W) {
            if (mx >= cx1() - 20 && my <= cyTop() - HEADER_H + 20) { selected = null; return true }
            if (my >= detTop()) subClick(f, cx1() - DET_W, cx1(), detTop() - detScroll, mx, my, btn)
            return true
        }
        if (my < cyTop() || mx > gx1()) return true
        val cw = cardW()
        for ((i, ft) in exItems().withIndex()) {
            val x = cardX(i); val y = cardY(i)
            if (mx !in x..x + cw || my !in y..y + cardH()) continue
            // left click opens settings, right click toggles
            if (btn == 1) { if (ft.hasMaster()) try { ft.set!!(!ft.get!!()) } catch (t: Throwable) { FishDiag.fail("FishModScreen.13", "toggle '${ft.name}' failed", t) } }
            else if (btn == 0) { if (selected == null) selAt = System.currentTimeMillis(); selected = ft; detScroll = 0 }
            return true
        }
        return true
    }

    private fun exScroll(f: Feature, mx: Int, my: Int, dir: Int): Boolean {
        var ssy = detTop() - detScroll + 6
        for (s in f.sub) {
            if (s.hiddenByGate()) continue
            val sh = s.getHeight()
            if (my >= ssy && my <= ssy + sh && s.onScroll(mx, my, cx1() - DET_W + 14, cx1() - 12, ssy, dir)) return true
            ssy += sh
        }
        return false
    }


    private fun stackX0(): Int = left() + MARGIN
    private fun ellipsize(text: String, maxW: Int, scale: Float = 1f): String {
        if (sw(this.font, text, scale) <= maxW) return text
        val n = fishmod.utils.rendering.TextFit.prefixLength(text, "…", maxW.toFloat(), 1) { sw(this.font, it, scale).toFloat() }
        return text.substring(0, n) + "…"
    }

    // Shift over a HUD feature row: hide the panels and show its HUD example (current settings) where it sits in game.
    private fun renderShiftPreview(ctx: GuiGraphicsExtractor) {
        val name = hoverPreview ?: return
        val w = Minecraft.getInstance().window
        if (!(fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT))) return
        var drawn = false
        for (n in HudPreview.namesFor(name)) if (FishHudEditor.drawPreview(ctx, n)) drawn = true
        if (WorldPreview.draw(ctx, name, this.width, this.height)) drawn = true
        if (!drawn) return
        UiRecorder.clear()
        ctx.centeredText(this.font, "§7Preview: $name (release Shift to return)", this.width / 2, 6, 0xFFFFFFFF.toInt())
    }

    private fun renderHoverTooltip(ctx: GuiGraphicsExtractor) {
        val desc = hoverDesc ?: return
        val tw = stw(this.font, desc)
        val bw = tw + 16
        val bh = 18
        val bx = Math.min(hoverDescX, right() - bw - 4)
        val by = hoverDescY
        roundedRectRing(ctx, bx, by, bw, bh, 5, 1, 0xFF171717.toInt(), ACCENT)
        st(ctx, this.font, desc, bx + 8, by + 5, TEXT_COLOR)
    }

    private fun renderSubPanel(ctx: GuiGraphicsExtractor, f: Feature, x0: Int, x1: Int, top: Int, animatedH: Int, mouseX: Int, mouseY: Int) {
        val animating = f.expandAnim.isAnimating()
        if (animating) UiRecorder.pushScissor(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), animatedH.toFloat())

        val subH = f.naturalSubHeight()
        if (selected !== f) UiRecorder.fillRect(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), subH.toFloat(), SUBROW_BG)
        val leftX = x0 + 14
        val rightX = x1 - 12
        var sy = top + 6
        for (s in f.sub) {
            if (s.hiddenByGate()) continue
            val sh = s.getHeight()
            if (s !is SubcategoryHeader && s !is LabelSetting && s !is InputSetting && s !is SliderIntSetting && s !is SliderDoubleSetting &&
                s !is InputIntSetting && s !is InputDoubleSetting && s !is ColorPickerSetting) {
                val labelH = if (s is DropdownSetting<*>) ITEM_HEIGHT else sh
                st(ctx, this.font, s.name, leftX + 2, sy + (labelH - 7) / 2, TEXT_COLOR)
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
        val mx = vx(click.x())
        val my = vx(click.y())
        val btn = fishmod.compat.GLFW.btn(click)

        val cap = capturingKeybind
        if (cap != null) {
            cap.applyKey(InputConstants.Type.MOUSE.getOrCreate(click.button()))
            capturingKeybind = null
            return true
        }

        val prevInput = activeInput
        if (prevInput is SliderIntSetting) prevInput.commitTyping()
        if (prevInput is SliderDoubleSetting) prevInput.commitTyping()
        if (prevInput is InputSetting && prevInput.textField != null) prevInput.textField!!.setFocused(false)
        if (prevInput is ColorPickerSetting) prevInput.hexField?.setFocused(false)
        activeInput = null

        if (chromeClick(mx, my)) return true

        val headerTop = cyTop() - HEADER_H

        if (navClick(mx, my, btn)) return true

        if (my >= headerTop && my <= cyBot()) return exClick(mx, my, btn)
        return super.mouseClicked(click, bl)
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
        try {
            dragInner(click, deltaX, deltaY)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.15", "drag failed slider=${activeSlider?.name}", t)
            true
        }

    private fun dragInner(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        winDrag?.let { winDX = vx(click.x()) - it[0]; winDY = vx(click.y()) - it[1]; return true }
        val slider = activeSlider
        if (slider != null) { slider.onDrag(vx(click.x()), activeSliderX, activeSliderW); return true }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean =
        try {
            releaseInner(click)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.16", "release failed", t)
            true
        }

    private fun releaseInner(click: MouseButtonEvent): Boolean {
        if (winDrag != null) { winDrag = null; return true }
        activeSlider = null


        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean =
        try {
            scrollInner(mouseX, mouseY, horizontalAmount, verticalAmount)
        } catch (t: Throwable) {
            FishDiag.fail("FishModScreen.17", "scroll failed at $mouseX,$mouseY", t)
            true
        }

    private fun scrollInner(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mouseX = vx(mouseX).toDouble()
        val mouseY = vx(mouseY).toDouble()
        if (mouseX <= stackX0() + STACK_W && mouseY >= navTop()) {
            navScroll = Mth.clamp((navScroll - verticalAmount * NAV_ROW).toInt(), 0, navMax())
            return true
        }
        val f = selected
        val dir = if (verticalAmount > 0) -1 else 1
        if (f != null && mouseX >= cx1() - DET_W) {
            if (!exScroll(f, mouseX.toInt(), mouseY.toInt(), dir)) detScroll = Mth.clamp((detScroll - verticalAmount * 18).toInt(), 0, detMax(f))
            return true
        }
        gridScroll = Mth.clamp((gridScroll - verticalAmount * 18).toInt(), 0, gridMax(exItems().size))
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
        val ai = activeInput
        if ((ai is SliderIntSetting && ai.typing != null) || (ai is SliderDoubleSetting && ai.typing != null)) {
            val k = input.key()
            fun t(f: (String) -> String) { if (ai is SliderIntSetting) ai.typing = f(ai.typing!!) else if (ai is SliderDoubleSetting) ai.typing = f(ai.typing!!) }
            when (k) {
                GLFW.GLFW_KEY_BACKSPACE -> t { it.dropLast(1) }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { if (ai is SliderIntSetting) ai.commitTyping() else if (ai is SliderDoubleSetting) ai.commitTyping(); activeInput = null }
                GLFW.GLFW_KEY_ESCAPE -> { if (ai is SliderIntSetting) ai.typing = null else if (ai is SliderDoubleSetting) ai.typing = null; activeInput = null }
            }
            return true
        }
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
        val ai = activeInput
        val ch = input.codepointAsString()
        if (ai is SliderIntSetting && ai.typing != null) { if (ch.all { it.isDigit() || it == '-' } && ai.typing!!.length < 9) ai.typing += ch; return true }
        if (ai is SliderDoubleSetting && ai.typing != null) { if (ch.all { it.isDigit() || it == '-' || it == '.' } && ai.typing!!.length < 9) ai.typing += ch; return true }
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
            val maxW = rightX - leftX - 12
            val lines = wrap(name, maxW)
            if (lines.size <= 1) {
                UiRecorder.text(lines.getOrElse(0) { name }.uppercase(), (leftX + 2).toFloat(), sy + (h - 6.6f) / 2f + 1f, 6.6f, SUBTEXT_COLOR)
            } else {
                for ((i, l) in lines.withIndex()) UiRecorder.text(l.uppercase(), (leftX + 2).toFloat(), (sy + 4 + i * 9).toFloat(), 6.6f, SUBTEXT_COLOR)
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
            val slw = rightX - leftX - 4 - SLIDER_BOX
            val sly = sy + SLIDER_CTRL_Y
            val pct = if (max > min) Mth.clamp((getter() - min).toFloat() / (max - min), 0f, 1f) else 0f
            pill(ctx, slx, sly, slx + slw, sly + SLIDER_H, SLIDER_BG)
            val fillW = (slw * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            disc(ctx, slx + fillW.coerceIn(4, slw - 4), sly + SLIDER_H / 2, 4, 0xFFFFFFFF.toInt())
            val bx = slx + slw + 6
            val by = sly + SLIDER_H / 2 - 6
            roundedRectRing(ctx, bx, by, SLIDER_BOX - 6, 12, 3, 1, 0x14FFFFFF, if (typing != null) ACCENT else 0x26FFFFFF)
            val v = typing ?: getter().toString()
            val vs = NVG_BASE_TEXT_SIZE * 0.72f
            UiRecorder.text(v + (if (typing != null && (System.currentTimeMillis() / 500) % 2 == 0L) "|" else ""), bx + (SLIDER_BOX - 6 - UiRecorder.textWidth(v, vs)) / 2f, by + (12 - vs) / 2f, vs, TEXT_COLOR)
        }
        var typing: String? = null
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val bx = rightX - 2 - SLIDER_BOX + 6; val by = sy + SLIDER_CTRL_Y + SLIDER_H / 2 - 6
            if (mx in bx..bx + SLIDER_BOX - 6 && my in by..by + 12) { typing = ""; return true }
            return false
        }
        fun commitTyping() { typing?.toIntOrNull()?.let { setter(snap(it)) }; typing = null }
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
            val slw = rightX - leftX - 4 - SLIDER_BOX
            val sly = sy + SLIDER_CTRL_Y
            val pct = if (max > min) Mth.clamp(((getter() - min) / (max - min)).toFloat(), 0f, 1f) else 0f
            pill(ctx, slx, sly, slx + slw, sly + SLIDER_H, SLIDER_BG)
            val fillW = (slw * pct).toInt()
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL)
            disc(ctx, slx + fillW.coerceIn(4, slw - 4), sly + SLIDER_H / 2, 4, 0xFFFFFFFF.toInt())
            val bx = slx + slw + 6
            val by = sly + SLIDER_H / 2 - 6
            roundedRectRing(ctx, bx, by, SLIDER_BOX - 6, 12, 3, 1, 0x14FFFFFF, if (typing != null) ACCENT else 0x26FFFFFF)
            val v = typing ?: String.format("%.1f", getter())
            val vs = NVG_BASE_TEXT_SIZE * 0.72f
            UiRecorder.text(v + (if (typing != null && (System.currentTimeMillis() / 500) % 2 == 0L) "|" else ""), bx + (SLIDER_BOX - 6 - UiRecorder.textWidth(v, vs)) / 2f, by + (12 - vs) / 2f, vs, TEXT_COLOR)
        }
        var typing: String? = null
        override fun onClick(mx: Int, my: Int, leftX: Int, rightX: Int, sy: Int, btn: Int): Boolean {
            val bx = rightX - 2 - SLIDER_BOX + 6; val by = sy + SLIDER_CTRL_Y + SLIDER_H / 2 - 6
            if (mx in bx..bx + SLIDER_BOX - 6 && my in by..by + 12) { typing = ""; return true }
            return false
        }
        fun commitTyping() { typing?.toDoubleOrNull()?.let { setter(it.coerceIn(min, max)) }; typing = null }
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
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, 6, 1, if (hov) ScreenTheme.ink(0x24) else ScreenTheme.ink(0x14), if (hov) ScreenTheme.ink(0x40) else ScreenTheme.ink(0x26))
            if (sw != null) disc(ctx, pillX + 12, by + PILL_H / 2, 4, sw(currentValue))
            st(ctx, tr, current, pillX + (if (sw != null) 22 else 10), by + (PILL_H - 7) / 2, TEXT_COLOR)

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
            st(ctx, tr, name, leftX + 2, sy + (ITEM_HEIGHT - 7) / 2, TEXT_COLOR)
            val cur = getter() or 0xFF000000.toInt()
            val idx = PRESET_ARGB.indexOf(cur)
            val label = if (idx >= 0) PRESET_NAMES[idx] else "#" + Integer.toHexString(cur and 0xFFFFFF).padStart(6, '0').uppercase()
            val textW = stw(tr, label)
            val swatchD = 8
            pillW = textW + swatchD + 26
            pillX = rightX - pillW - 2
            val by = sy + (ITEM_HEIGHT - PILL_H) / 2
            val hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, 6, 1, if (hov) ScreenTheme.ink(0x24) else ScreenTheme.ink(0x14), if (hov) ScreenTheme.ink(0x40) else ScreenTheme.ink(0x26))
            disc(ctx, pillX + 12, by + PILL_H / 2, swatchD / 2, getter() or 0xFF000000.toInt())
            st(ctx, tr, label, pillX + 22, by + (PILL_H - 7) / 2, TEXT_COLOR)

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
            roundedRectRing(ctx, bx, by, bw, PILL_H, 6, 1, if (hov) ScreenTheme.ink(0x24) else ScreenTheme.ink(0x14), if (hov) ScreenTheme.ink(0x40) else ScreenTheme.ink(0x26))
            st(ctx, tr, text, bx + (bw - stw(tr, text)) / 2, by + (PILL_H - 7) / 2, -0x1)
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
            val ring = if (capturing) ACCENT_HOVER else (if (hov) ACCENT else 0xFF4B4B4B.toInt())
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, 6, 1, ScreenTheme.ink(0x14), ring)
            st(ctx, tr, t, pillX + (pillW - textW) / 2, by + (PILL_H - 7) / 2, if (capturing) ACCENT_HOVER else TEXT_COLOR)
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
        private val ROW_HOVER get() = ScreenTheme.ink(0x1E)
        private var ROW_ENABLED: Int = 0x26D6D6D6
        private var ROW_BUTTON: Int = 0xFF212121.toInt()
        private const val ROW_RADIUS = 3f
        private val SUBROW_BG = 0xFF121212.toInt()
        private val TRACK_OFF = 0xFF3F3F3F.toInt()
        private val TEXT_COLOR get() = ScreenTheme.TEXT_COLOR
        private val SUBTEXT_COLOR get() = ScreenTheme.SUBTEXT_COLOR
        private val CHEVRON_COLOR = 0xFF767676.toInt()

        private const val TEXT_SCALE = ScreenTheme.TEXT_SCALE

        private val COMPACT get() = FishSettings.fmCompact
        private val MARGIN get() = if (COMPACT) 10 else 16
        private const val TOP_BAR_H = 26
        private const val BOTTOM_RESERVE = 46

        private val COLUMN_GUTTER get() = if (COMPACT) 4 else 6
        private const val CARD_RADIUS = 7
        private const val HEADER_H = 36
        private const val STATUS_H = 22
        private const val CARD_MIN_W = 150
        private const val CARD_TALL = 44
        private const val CARD_GAP = 4
        private const val DET_W = 230
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
        )
        private val MIN_COLUMN_W get() = if (COMPACT) 156 else 172
        private const val STACK_W = 176
        private const val NAV_ROW = 18
        private val STACK_TAB_H get() = if (COMPACT) 20 else 24
        private const val STACK_SUB_INDENT = 10
        private const val DRAG_THRESHOLD = 4

        private val ROW_H get() = if (COMPACT) 18 else 22
        private val ROW_GAP get() = if (COMPACT) 1 else 3
        private val ITEM_HEIGHT get() = if (COMPACT) 18 else 22
        private val PILL_H get() = if (COMPACT) 15 else 18
        private const val OPTION_H = 16
        private val SLIDER_BG = ScreenTheme.ink(0x26)
        private val SLIDER_FILL = 0xFFD6D6D6.toInt()
        private const val SLIDER_W = 56
        private const val SLIDER_H = 3
        private const val SLIDER_BOX = 40
        private const val INPUT_W = 62
        private const val INPUT_H = 14
        private const val SUBCAT_HEIGHT = 13
        private const val SUBCAT_HEIGHT_2 = 22
        private const val TWO_LINE_H = 36
        private const val SLIDER_ROW_H = 24
        private const val SLIDER_CTRL_Y = 13
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
                "Death Counter" -> "Deaths this run (disconnects count); move in the HUD editor"
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

        fun lerpColor(a: Int, b: Int, t: Float): Int {
            fun ch(s: Int) = (((a ushr s) and 0xFF) + (((b ushr s) and 0xFF) - ((a ushr s) and 0xFF)) * t).toInt() and 0xFF
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        fun drawTogglePill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, on: Boolean, knobProgress: Float, hover: Boolean) {
            val off = if (hover) 0xFF6A6A6A.toInt() else 0xFF5A5A5A.toInt()
            val onC = if (hover) ACCENT_HOVER else ACCENT
            val track = lerpColor(off, onC, knobProgress)
            UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h / 2f, track)
            val r = h / 2f - 2f
            val kx = x + 2f + r + knobProgress * (w - 4f - 2f * r)
            UiRecorder.disc(kx, y + h / 2f, r, 0xFFFFFFFF.toInt())
        }
    }
}

object FeatureMasters {
    @JvmField val map: java.util.concurrent.ConcurrentHashMap<String, () -> Boolean> = java.util.concurrent.ConcurrentHashMap()
}
