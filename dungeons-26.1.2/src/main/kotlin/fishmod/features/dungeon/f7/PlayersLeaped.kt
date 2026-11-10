package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.DungeonRole
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.AABB
import fishmod.utils.debug.FishDiag

object PlayersLeaped {

    private class Zone(val label: String, val cls: DungeonRole, val expected: Int, val box: AABB)

    private fun box(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) =
        AABB(x1, y1, z1, x2, y2, z2).inflate(0.5)

    private val ZONES = listOf(
        Zone("HEE2", DungeonRole.MAGE, 4, box(56.5, 131.0, 140.5, 62.5, 134.0, 137.5)),
        Zone("S1", DungeonRole.BERSERK, 1, box(103.5, 115.0, 44.5, 97.5, 117.0, 50.5)),
        Zone("EE3", DungeonRole.HEALER, 3, box(3.5, 109.0, 108.5, 0.5, 111.0, 97.5)),
        Zone("Core", DungeonRole.MAGE, 4, box(56.5, 115.0, 53.5, 51.5, 117.0, 50.5)),
        Zone("Dragons", DungeonRole.HEALER, 4, box(59.5, 5.0, 83.5, 47.5, 7.0, 71.5)),
    )

    private const val DONE_SHOW_MS = 2000L
    private val filledAt = HashMap<String, Long>()

    @JvmStatic
    fun init() {
        fishmod.utils.events.FishEvents.locationChange.register { _ -> filledAt.clear(); false }
    }

    private fun hiddenForRun(zone: Zone): Boolean =
        filledAt[zone.label]?.let { System.currentTimeMillis() - it > DONE_SHOW_MS } == true

    private fun activeZone(): Zone? {
        if (!Floor7Settings.playersLeapedEnabled || !SkyblockLocation.inDungeon() || !BossPhase.isInFloor7() || !BossPhase.inBoss()) return null
        val player = Minecraft.getInstance().player ?: return null
        val cls = DungeonRole.currentClass
        return ZONES.firstOrNull {
            (Floor7Settings.playersLeapedAnyClass || it.cls == cls) && !hiddenForRun(it) && it.box.contains(player.position())
        }
    }

    private fun leapedCount(zone: Zone): Int {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return 0
        val me = mc.player ?: return 0
        val team = DungeonRole.getAll().keys
        // class map empty (tab not parsed yet): count any other player in the zone
        return level.players().count { p ->
            p !== me && p.isAlive && (team.isEmpty() || p.gameProfile.name in team) && zone.box.contains(p.position())
        }
    }

    @JvmStatic
    fun display(): Boolean = activeZone() != null

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        try { renderInner(component, context) } catch (e: Exception) { FishDiag.fail("PlayersLeaped.2", "players leaped render threw", e) }
    }

    private fun renderInner(component: HUDComponent, context: GuiGraphicsExtractor) {
        if (HudPreview.active) {
            FishDraw.drawPrefixedText(component, context, "Leaped (HEE2)", "§e2/4")
            return
        }
        val zone = activeZone() ?: return
        val n = leapedCount(zone)
        FishDiag.check(n <= 4, "PlayersLeaped.3") { "leaped count $n > 4 in ${zone.label}" }
        if (n >= zone.expected) filledAt.putIfAbsent(zone.label, System.currentTimeMillis())
        if (zone.label in filledAt) {
            val font = net.minecraft.client.Minecraft.getInstance().font
            context.text(font, "§a§lAll Players Leaped", component.scaledX, component.scaledY, -1, true)
            return
        }
        FishDraw.drawPrefixedText(component, context, "Leaped (${zone.label})", "§e$n/${zone.expected}")
    }
}
