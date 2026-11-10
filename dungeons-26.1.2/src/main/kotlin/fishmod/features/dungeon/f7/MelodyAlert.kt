package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.EntityTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.DungeonRole
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

// Shows who is on the melody terminal, from their "n%" party callouts; clears when they finish a terminal.
object MelodyAlert {

    private val PERCENT = Regex("(\\d+)%")

    private class Player(val name: String, val mine: Boolean)

    private var current: Player? = null
    private var bestPercent = 0
    private val callers = HashSet<String>()

    private fun clear() {
        current = null
        bestPercent = 0
        callers.clear()
    }

    @JvmStatic
    fun init() {
        FishEvents.partyChat.register { sender, msg ->
            if (Floor7Settings.melodyAlert && SkyblockLocation.inDungeon() && BossPhase.inTerminals()) {
                PERCENT.find(msg)?.let { m ->
                    val pct = FishDiag.notNull(m.groupValues[1].toIntOrNull(), "MelodyAlert.1") { "melody % not an int: '$msg'" }
                    if (pct != null && pct > bestPercent) {
                        bestPercent = pct
                        current = Player(sender, EntityTools.isClientPlayer(sender))
                        callers += sender
                    }
                }
            }
            false
        }
        FishEvents.terminalStep.register { who, _, kind, _, _ ->
            if (kind == "terminal" && who in callers) clear()
            false
        }
        FishEvents.sectionChange.register { clear(); false }
        FishEvents.locationChange.register { clear(); false }
    }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.melodyAlert && current?.mine == false

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val preview = HudPreview.active
        val who = if (preview) null else current ?: return
        val percent = if (preview) 50 else bestPercent
        // 25/50/75 callouts mean 4 lanes, 33/67 mean 3
        val lanes = if (percent % 25 == 0) 4 else 3
        val lane = minOf(Math.round(percent * lanes / 100.0).toInt(), lanes - 1)
        val role = if (preview) DungeonRole.HEALER else DungeonRole.getClass(who?.name)
        val color = if (DungeonSettings.roleColors) (if (preview) role!!.color else DungeonRole.getColor(who!!.name)) else FishConstants.DARK_PURPLE
        val label = role?.name ?: who!!.name
        val text = Component.literal(label).setStyle(Style.EMPTY.withColor(color).withBold(true))
            .append(Component.literal(" §7» §dMelody §f$lane§7/§f$lanes"))
        FishDiag.guard("MelodyAlert.2", "melody hud threw") { FishDraw.drawCenteredText(context, component, text) }
    }
}
