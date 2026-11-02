package fishmod.utils.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.debug.FishDiag
import java.io.File

// Moves renamed setting keys to their new names; runs before the config managers load.
object ConfigKeyMigration {

    private val RENAMED: Map<String, String> = mapOf(
        "capitalizeHealthNumbers" to "bossHpCapitalized",
        "crystalPlaceReminder" to "crystalPlaceAlert",
        "disableTitlesAtPre4" to "muteTitlesPre4",
        "disableTitlesAtSS" to "muteTitlesSS",
        "enableCrystalSpawnTime" to "crystalSpawnHud",
        "enableGoldorTickTimer" to "goldorTicksHud",
        "enableMaxorTickTimer" to "maxorTicksHud",
        "enableRelicStartTimer" to "relicStartHud",
        "enableStormDeathTime" to "stormKillTimeHud",
        "enableStormTickTimer" to "stormTicksHud",
        "enableTermStartTimer" to "termOpenHud",
        "hideTerminalTitles" to "muteTerminalTitles",
        "inDeathTicks" to "goldorUseDeathTicks",
        "instantlyDisplayCrystalReminder" to "crystalAlertInstant",
        "makeGoldorTickUp" to "goldorTicksCountUp",
        "notifiyMelody" to "melodyAlert",
        "notifyPre4Completion" to "pre4DoneAlert",
        "notifySSCompletion" to "ssDoneAlert",
        "notifyStormCrush" to "stormCrushAlert",
        "renderRelicHighlight" to "relicHighlight",
        "sectionColorProgress" to "sectionProgressColored",
        "sectionCompletionNotification" to "sectionDoneAlert",
        "sectionPrevObjective" to "sectionShowPrevious",
        "showCurrentSection" to "currentSectionHud",
        "showSectionProgress" to "sectionProgressHud",
        "stormTickTimerColor" to "stormTicksColor",
        "terminalTimeStamps" to "termSplitTimestamps",
        "tickDownStormTickTimer" to "stormTicksCountDown",
        "timePillarExplosion" to "pillarBlastTimer",
        "InvincibilityDuration" to "invincibilityHud",
        "archerColor" to "roleColorArcher",
        "berserkColor" to "roleColorBerserk",
        "healerColor" to "roleColorHealer",
        "mageColor" to "roleColorMage",
        "tankColor" to "roleColorTank",
        "bossHealthNumbers" to "bossHpNumbers",
        "detectDuplicateClass" to "dupeClassAlert",
        "displayInvincibilityTimer" to "invincibilityTimerHud",
        "enableAutoRequeue" to "autoRequeueEnabled",
        "enableKeyNotifier" to "keyPickupAlert",
        "enableLeapMessages" to "leapAnnounce",
        "enableWarpCooldown" to "partyWarpTimer",
        "hideBlazeNameTag" to "hideBlazeTags",
        "ignoreDupeMage" to "dupeClassSkipMage",
        "renderClassName" to "roleNameTags",
        "useClassColors" to "roleColors",
        "useStatusColorForInvincibility" to "invincibilityStatusColor",
        "circularRarityBackground" to "rarityBgCircle",
        "disableGlowing" to "noEntityGlow",
        "fixWitherEssence" to "witherEssenceSkin",
        "hideCooldown" to "hideItemCooldown",
        "hideDeadEntities" to "hideDeadMobs",
        "hideEntityFire" to "hideMobFire",
        "hideFireInf5" to "hideBurningInf5",
        "hideStatusOverLay" to "hideEffectHud",
        "hideStuckArrows" to "hideArrowsInBody",
        "itemRarityBackground" to "rarityBackground",
        "oldPlayerHead" to "legacyPlayerHead",
        "stopShovelFlattening" to "noShovelPath",
        "copyChat" to "chatCopy",
        "copyLineOnly" to "chatCopySingleLine",
        "disableAbilityCooldownSound" to "muteAbilityCooldown",
        "disableRecipeBook" to "hideRecipeBook",
        "disableScrollHotbar" to "lockHotbarScroll",
        "removeColorCodes" to "chatCopyStripColors",
        "replaceColorChars" to "chatCopyAmpersand",
        "textPrefix" to "chatPrefix",
        "timerPrefixColor" to "timerLabelColor",
        "command1" to "invCmd1",
        "command2" to "invCmd2",
        "command3" to "invCmd3",
        "command4" to "invCmd4",
        "command5" to "invCmd5",
        "command6" to "invCmd6",
        "command7" to "invCmd7",
        "crystalSpawnTime" to "crystalRespawnBox",
        "stormDeathTime" to "stormKillBox",
        "termStartTimer" to "termOpenBox",
        "displayTerminalSplitsWhen" to "goldorSplitsShownIn",
        "enableTerminalSplits" to "goldorSplitsEnabled",
        "terminalSplits" to "goldorSplitsBox",
        "enableSplits" to "runSplitsEnabled",
        "includeTotalTime" to "runSplitsTotal",
        "sendSplitInChat" to "runSplitsToChat",
        "onlyShowActivatedSplits" to "runSplitsReachedOnly",
        "splitTimer" to "runSplitsBox",
        "realTimeColorInactive" to "splitRealIdle",
        "realTimeColorOngoing" to "splitRealLive",
        "realTimeColorComplete" to "splitRealDone",
        "serverTimeColorInactive" to "splitTickIdle",
        "serverTimeColorOngoing" to "splitTickLive",
        "serverTimeColorComplete" to "splitTickDone",
        "parenthesesColorInactive" to "splitBracketIdle",
        "parenthesesColorOngoing" to "splitBracketLive",
        "parenthesesColorComplete" to "splitBracketDone",
        "timerType" to "splitClock",
        "hidePlayerRange" to "hidePlayersRadius",
        "hidePlayersInRange" to "hideNearbyPlayers"
    )

    // saved HUD boxes keep their label in "info"
    private val HUD_LABELS: Map<String, String> = mapOf(
        "Tick Timer" to "Boss Tick Clock",
        "Crystal Spawn Time" to "Crystal Respawn",
        "Crystal Reminder" to "Crystal Not Placed",
        "Storm Death Time" to "Storm Kill Time",
        "Storm Crushed" to "Storm Crush Banner",
        "Pillar Explosion Timer" to "Pillar Blast Timer",
        "Term Start Timer" to "Terminals Open In",
        "Section Progress" to "Section Count",
        "Current Section" to "P3 Section",
        "Device Completed" to "Device Done",
        "Melody Warning" to "Melody Callout",
        "Section Completion" to "Section Cleared",
        "Splits" to "Run Splits",
        "Term splits" to "Goldor Splits"
    )

    private val CLOCK_VALUES: Map<String?, String> = mapOf("TICK_TIME" to "SERVER_TICKS", "DIFFRENCE" to "GAP")

    @JvmStatic
    fun run(path: String) {
        val file = File(path)
        if (!file.isFile) return
        try {
            val root = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) } as? JsonObject ?: return
            var moved = 0
            for ((old, new) in RENAMED) {
                val v = root.remove(old) ?: continue
                if (!root.has(new)) root.add(new, v)
                moved++
            }
            val clock = root.get("splitClock")?.takeIf { it.isJsonPrimitive }?.asString
            CLOCK_VALUES[clock]?.let { root.addProperty("splitClock", it); moved++ }
            for ((_, v) in root.entrySet()) {
                val hud = v as? JsonObject ?: continue
                val label = hud.get("info")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                HUD_LABELS[label]?.let { hud.addProperty("info", it); moved++ }
            }
            if (moved == 0) return
            file.writeText(GsonBuilder().create().toJson(root), Charsets.UTF_8)
        } catch (e: Exception) {
            FishDiag.fail("ConfigKeyMigration.1", "setting key migration failed for $path", e)
        }
    }
}
