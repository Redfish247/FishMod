package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object DungeonMapSettings {

    @ConfigValue
    @JvmField
    var mapEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var mapLegitMode: Boolean = true

    @ConfigValue
    @JvmField
    var mapBackgroundColor: Int = 1174405120

    @ConfigValue
    @JvmField
    var mapBackgroundSize: Float = 5.0f

    @ConfigValue
    @JvmField
    var mapImageSelection: String = ""

    @ConfigValue
    @JvmField
    var mapImageLastSelection: String = ""

    @ConfigValue
    @JvmField
    var mapImageAlpha: Int = 255

    @ConfigValue
    @JvmField
    var mapInfoNoWords: Boolean = false

    @ConfigValue
    @JvmField
    var mapInfoMapTied: Boolean = false

    @ConfigValue
    @JvmField
    var mapInfoEnabled: Boolean? = null

    @ConfigValue
    @JvmField
    var mapScoreStandaloneHideInBoss: Boolean = true

    @ConfigValue
    @JvmField
    var mapScoreMaxBonusMissing: Boolean = false

    @ConfigValue
    @JvmField
    var mapScoreNeededInsteadOfMissing: Boolean = false

    @ConfigValue
    @JvmField
    var mapScorePaul: Int = 0

    @ConfigValue
    @JvmField
    var mapInfoShowSecrets: Boolean = true

    @ConfigValue
    @JvmField
    var mapInfoShowScore: Boolean = true

    @ConfigValue
    @JvmField
    var mapInfoShowDeaths: Boolean = true

    @ConfigValue
    @JvmField
    var mapInfoShowMimic: Boolean = true

    @ConfigValue
    @JvmField
    var mapInfoShowPrince: Boolean = true

    @ConfigValue
    @JvmField
    var mapInfoShowCrypts: Boolean = true

    @ConfigValue
    @JvmField
    var mapInfoHideCompleted: Boolean = false

    @ConfigValue
    @JvmField
    var mapInfoShowLeft: Boolean = false

    @ConfigValue
    @JvmField
    var mapScoreMissingMsg: Boolean = true

    @ConfigValue
    @JvmField
    var mapScoreMessages: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore270Message: String = "On pace for 270"

    @ConfigValue
    @JvmField
    var mapScore270MessageEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore270TitleText: String = "On pace for 270"

    @ConfigValue
    @JvmField
    var mapScore270Title: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore270ClientEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore270ClientMessage: String = "&fOn pace for 270 &b<time>"

    @ConfigValue
    @JvmField
    var mapScore300Message: String = "On pace for 300"

    @ConfigValue
    @JvmField
    var mapScore300MessageEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore300TitleText: String = "On pace for 300"

    @ConfigValue
    @JvmField
    var mapScore300Title: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore300ClientEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var mapScore300ClientMessage: String = "&fOn pace for 300 &b<time>"

    @ConfigValue
    @JvmField
    var mapScoreTitleSound: Boolean = false

    @ConfigValue
    @JvmField
    var mapScoreTitleSoundId: String = "minecraft:entity.player.levelup"

    @ConfigValue
    @JvmField
    var mapScoreTitleVolume: Float = 1.0f

    @ConfigValue
    @JvmField
    var mapScoreTitlePitch: Float = 1.0f

    @ConfigValue
    @JvmField
    var mapTextScaling: Float = 0.45f

    @ConfigValue
    @JvmField
    var mapTextCenter: Boolean = true

    @ConfigValue
    @JvmField
    var mapUglyQuestionMarks: Boolean = false

    @ConfigValue
    @JvmField
    var mapShowRoomSecrets: Boolean = false

    @ConfigValue
    @JvmField
    var mapPlayerHeadsEnabled: Boolean = true

    @ConfigValue
    @JvmField
    var mapPlayerHeadBackground: Int = -1308622848

    @ConfigValue
    @JvmField
    var mapPlayerHeadOwnBackground: Int = -1291911168

    @ConfigValue
    @JvmField
    var mapPlayerHeadBackgroundSize: Int = 1

    @ConfigValue
    @JvmField
    var mapPlayerHeadDrawOwnLast: Boolean = false

    @ConfigValue
    @JvmField
    var mapPlayerUglyPointer: Boolean = false

    @ConfigValue
    @JvmField
    var mapPlayerHeadClassOutline: Boolean = false

    @ConfigValue
    @JvmField
    var mapPlayerNamesScaling: Float = 0.75f

    @ConfigValue
    @JvmField
    var mapPlayerNameColor: Int = -12171706

    @ConfigValue
    @JvmField
    var mapRoomAdditionsEnabled: Boolean = true

    @ConfigValue
    @JvmField
    var mapRoomAdditionsPrince: Boolean = false

    @ConfigValue
    @JvmField
    var mapRoomAdditionsMimic: Boolean = true

    @ConfigValue
    @JvmField
    var mapDoorThickness: Float = 9.0f

    @ConfigValue
    @JvmField
    var mapDoorGay: Boolean = false

    @ConfigValue
    @JvmField
    var mapDarkenMultiplier: Float = 0.4f

    @ConfigValue
    @JvmField
    var mapUnopenedDoorColor: Int = -14803426

    @ConfigValue
    @JvmField
    var mapBloodDoorColor: Int = -65536

    @ConfigValue
    @JvmField
    var mapWitherDoorColor: Int = -16777216

    @ConfigValue
    @JvmField
    var mapNormalDoorColor: Int = -9749999

    @ConfigValue
    @JvmField
    var mapPuzzleDoorColor: Int = -9109371

    @ConfigValue
    @JvmField
    var mapChampionDoorColor: Int = -73984

    @ConfigValue
    @JvmField
    var mapTrapDoorColor: Int = -2588877

    @ConfigValue
    @JvmField
    var mapEntranceDoorColor: Int = -15432448

    @ConfigValue
    @JvmField
    var mapFairyDoorColor: Int = -781429

    @ConfigValue
    @JvmField
    var mapRareDoorColor: Int = -13479

    @ConfigValue
    @JvmField
    var mapRoomColorsEnabled: Boolean = true

    @ConfigValue
    @JvmField
    var mapUnopenedRoomColor: Int = -14803426

    @ConfigValue
    @JvmField
    var mapBloodRoomColor: Int = -65536

    @ConfigValue
    @JvmField
    var mapNormalRoomColor: Int = -9749999

    @ConfigValue
    @JvmField
    var mapPuzzleRoomColor: Int = -9109371

    @ConfigValue
    @JvmField
    var mapChampionRoomColor: Int = -73984

    @ConfigValue
    @JvmField
    var mapTrapRoomColor: Int = -2588877

    @ConfigValue
    @JvmField
    var mapEntranceRoomColor: Int = -15432448

    @ConfigValue
    @JvmField
    var mapFairyRoomColor: Int = -781429

    @ConfigValue
    @JvmField
    var mapRareRoomColor: Int = -13479

    @ConfigValue
    @JvmField
    var mapMimicRoomColor: Int = -4570572

    @ConfigValue
    @JvmField
    var mapDoorOpenableColor: Int = -16711936

    @ConfigValue
    @JvmField
    var mapDoorOpenableColorFilled: Int = 855703296

    @ConfigValue
    @JvmField
    var mapDoorFairyColor: Int = -781429

    @ConfigValue
    @JvmField
    var mapDoorFairyColorFilled: Int = 871633803

    @ConfigValue
    @JvmField
    var mapDoorHighlightEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var mapDoorHighlightWidth: Float = 3.0f

    @ConfigValue
    @JvmField
    var mapDoorHighlightThroughWall: Boolean = false

    @ConfigValue
    @JvmField
    var mapDoorHighlightFullBox: Boolean = false

    @ConfigValue
    @JvmField
    var mapWitherHighlightMissingColor: Int = 0xFFFF0000.toInt()

    @ConfigValue
    @JvmField
    var mapDoorOutlineOnly: Boolean = false

    @ConfigValue
    @JvmField
    var mapDoorOutlineColor: Int = 0xFF00E5FF.toInt()

    @ConfigValue
    @JvmField
    var mapX: Float = 100.0f

    @ConfigValue
    @JvmField
    var mapY: Float = 100.0f

    @ConfigValue
    @JvmField
    var mapScale: Float = 1.0f

    @ConfigValue
    @JvmField
    var mapInfoX: Float = 100.0f

    @ConfigValue
    @JvmField
    var mapInfoY: Float = 100.0f

    @ConfigValue
    @JvmField
    var mapInfoScale: Float = 1.0f

    @ConfigValue
    @JvmField
    var mapScoreTitleX: Float = -1.0f

    @ConfigValue
    @JvmField
    var mapScoreTitleY: Float = -1.0f

    @ConfigValue
    @JvmField
    var mapScoreTitleScale: Float = 1.5f
}
