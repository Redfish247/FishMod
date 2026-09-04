package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

/** Warns when a teammate has melody active on the pre-4th device. */
object MelodyWarning {

    private val PATTERN: Pattern = Pattern.compile("(\\d+)%")

    private val names = CopyOnWriteArrayList<String>()

    private var melodyStarted = false
    private var ownUsername = false
    private var name: String? = null
    private var furthestProgress = 0

    @JvmStatic
    fun init() {
        Events.ON_PARTY_MESSAGE.register { username, message ->
            if (!Floor7.notifiyMelody) return@register false
            if (!Location.inDungeon() || !Phase.inTerminals()) return@register false

            val matcher = PATTERN.matcher(message)
            if (matcher.find()) {
                val progress = matcher.group(1).toInt()
                if (progress > furthestProgress) {
                    melodyStarted = true
                    name = username
                    furthestProgress = progress
                    ownUsername = EntityUtil.isClientPlayer(username)
                    if (!names.contains(username)) {
                        names.add(username)
                    }
                }
            }
            false
        }

        Events.ON_TERMINAL.register { formattedName, _, objective, _, _ ->
            if (names.contains(formattedName) && objective == "terminal") {
                reset()
            }
            false
        }

        Events.ON_SECTION_CHANGE.register {
            reset()
            false
        }

        Events.ON_LOCATION_CHANGE.register {
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
        return melodyStarted && Floor7.notifiyMelody && !ownUsername
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val dungeonClass = DungeonClass.getClass(name)
        val num = minOf(furthestProgress / 25, 3)

        var color = Constants.DARK_PURPLE
        if (Dungeons.useClassColors) {
            color = DungeonClass.getColor(name)
        }

        val nameText = Component.literal(dungeonClass?.name ?: name ?: "Someone")
            .setStyle(Style.EMPTY.withColor(color).withBold(true))
        val infoText = Component.literal(" §r§dhas melody! $num/4").setStyle(Style.EMPTY)

        val text = nameText.append(infoText)

        RenderUtils.drawCenteredText(context, component, text)
    }
}
