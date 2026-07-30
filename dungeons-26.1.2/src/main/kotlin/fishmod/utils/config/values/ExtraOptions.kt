package fishmod.utils.config.values

import config.practical.data.SoundData
import config.practical.manager.ConfigValue
import fishmod.utils.Constants
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents

object ExtraOptions {
    @ConfigValue @JvmField var disableAbilityCooldownSound: Boolean = true

    @ConfigValue @JvmField var textPrefix: String = ""

    @ConfigValue @JvmField var highlightSelectedPet: Boolean = false

    @ConfigValue @JvmField var drawPetHUD: Boolean = false

    @ConfigValue @JvmField var includePetSprite: Boolean = true

    @ConfigValue @JvmField var showPbs: Boolean = true

    @ConfigValue @JvmField var disableScrollHotbar: Boolean = false

    @ConfigValue @JvmField var enableKickedTimer: Boolean = true

    @ConfigValue @JvmField var enableRagaxeDisplay: Boolean = false

    @ConfigValue @JvmField var autoSkip: Boolean = true

    @ConfigValue @JvmField var realisticDelay: Boolean = false

    @ConfigValue @JvmField var includeLuckyButton: Boolean = false

    @ConfigValue @JvmField var luckyButtonRng: Double = 0.1

    @ConfigValue @JvmField var luckyButtonColor: Int = 0xff4a4f4b.toInt()

    @ConfigValue @JvmField var practiceSSAnywhere: Boolean = false

    @ConfigValue @JvmField var blockUnluckyButtonClick: Boolean = false

    @ConfigValue @JvmField var startButton: BlockPos = BlockPos(2, 2, 2)

    @ConfigValue @JvmField var disableRecipeBook: Boolean = false

    @ConfigValue @JvmField var useCustomRagSound: Boolean = false

    @ConfigValue @JvmField var ragSound: SoundData = SoundData(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)

    @ConfigValue @JvmField var sendOnPetSound: Boolean = false

    @ConfigValue @JvmField var petSound: SoundData = SoundData(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)

    @ConfigValue @JvmField var copyChat: Boolean = false

    @ConfigValue @JvmField var removeColorCodes: Boolean = false

    @ConfigValue @JvmField var replaceColorChars: Boolean = false

    @ConfigValue @JvmField var copyLineOnly: Boolean = false

    @ConfigValue @JvmField var disableBonzoSound: Boolean = false

    @ConfigValue @JvmField var useOldRagSound: Boolean = false

    @ConfigValue @JvmField var ssSound: SoundData = SoundData(SoundEvents.NOTE_BLOCK_PLING.value(), 0f, 1f)

    @ConfigValue @JvmField var moveToolTip: Boolean = false

    @ConfigValue @JvmField var sendPetSwapNotification: Boolean = false

    @ConfigValue @JvmField var timerPrefixColor: Int = Constants.DARK_PURPLE

    @ConfigValue @JvmField var oldBonzoSound: Boolean = false

    @ConfigValue @JvmField var displayCurrentArrow: Boolean = false

    @ConfigValue @JvmField var arrowSwapNotification: Boolean = false

    @ConfigValue @JvmField var stunWaypoint: Boolean = false

    @ConfigValue @JvmField var petHighlightColor: Int = 0xffff0000.toInt()

    @ConfigValue @JvmField var displayPetLevel: Boolean = false

    @ConfigValue @JvmField var toggleableSearchBar: Boolean = false

    @ConfigValue @JvmField var copyChatFeedback: Boolean = false

    @ConfigValue @JvmField var oldTubaSound: Boolean = false

    @ConfigValue @JvmField var ignoreColorCodesFilter: Boolean = false

    @ConfigValue @JvmField var ignoreColorCodesNotification: Boolean = false

    @ConfigValue @JvmField var enableReaperDisplay: Boolean = false

    @ConfigValue @JvmField var disableAllFilters: Boolean = false
}
