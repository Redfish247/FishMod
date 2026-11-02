package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.EntityTools
import fishmod.utils.dungeon.DungeonRole
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import fishmod.utils.debug.FishDiag

object MelodyAlert {

    private val PATTERN: Pattern = Pattern.compile("(\\d+)%")

    private val names = CopyOnWriteArrayList<String>()

    private var melodyStarted = false
    private var ownUsername = false
    private var name: String? = null
    private var furthestProgress = 0

    @JvmStatic
    fun init() {
        FishEvents.ON_PARTY_MESSAGE.register { username, message ->
            if (!Floor7Settings.notifiyMelody) return@register false
            if (!SkyblockLocation.inDungeon() || !BossPhase.inTerminals()) return@register false

            val matcher = PATTERN.matcher(message)
            if (matcher.find()) {
                val progress = FishDiag.notNull(matcher.group(1).toIntOrNull(), "MelodyWarning.1") { "melody % not an int: '$message'" } ?: return@register false
                if (progress > furthestProgress) {
                    melodyStarted = true
                    name = username
                    furthestProgress = progress
                    ownUsername = EntityTools.isClientPlayer(username)
                    if (!names.contains(username)) {
                        names.add(username)
                    }
                }
            }
            false
        }

        FishEvents.ON_TERMINAL.register { formattedName, _, objective, _, _ ->
            if (names.contains(formattedName) && objective == "terminal") {
                reset()
            }
            false
        }

        FishEvents.ON_SECTION_CHANGE.register {
            reset()
            false
        }

        FishEvents.ON_LOCATION_CHANGE.register {
            reset()
            false
        }
    }

    private fun reset() {
        names.clear()
        furthestProgress = 0
        name = ""
        melodyStarted = false
        ownUsername = false
    }

    @JvmStatic
    fun display(): Boolean {
        return melodyStarted && Floor7Settings.notifiyMelody && !ownUsername
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.check(!name.isNullOrEmpty(), "MelodyWarning.3") { "melody warning shown with no player name" }
        val dungeonClass = DungeonRole.getClass(name)
        val lanes = if (furthestProgress % 25 == 0) 4 else 3 // alpha: 33/67%
        val num = minOf(Math.round(furthestProgress * lanes / 100.0).toInt(), lanes - 1)

        var color = FishConstants.DARK_PURPLE
        if (DungeonSettings.useClassColors) {
            color = DungeonRole.getColor(name)
        }

        val nameText = Component.literal(dungeonClass?.name ?: name ?: "Someone")
            .setStyle(Style.EMPTY.withColor(color).withBold(true))
        val infoText = Component.literal(" §r§dhas melody! $num/$lanes").setStyle(Style.EMPTY)

        val text = nameText.append(infoText)

        FishDiag.guard("MelodyWarning.2", "melody warning render threw") { FishDraw.drawCenteredText(context, component, text) }
    }
}
