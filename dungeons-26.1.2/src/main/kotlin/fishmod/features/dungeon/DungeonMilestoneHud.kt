package fishmod.features.dungeon

import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.features.dungeon.DungeonMilestoneSettings as S
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

// Last class milestone from "<Class> Milestone ❸: You have ..." chat
object DungeonMilestoneHud {

    private const val NAME = "Dungeon Milestone"
    private val LINE = Regex("^(\\w+) Milestone ([❶-❾]):")
    @Volatile private var cls: String? = null
    @Volatile private var mark: String? = null

    fun init() {
        FishHudEditor.register(
            NAME,
            { S.milestoneHudX }, { v -> S.milestoneHudX = v },
            { S.milestoneHudY }, { v -> S.milestoneHudY = v },
            90, 12,
            { S.milestoneHudScale }, { v -> S.milestoneHudScale = v }
        )
        HudPreview.register(NAME) {
            val l = label("Berserk", "❸")
            HudExample(Minecraft.getInstance().font.width(l), HudPreview.TEXT_H) { ctx -> ctx.text(Minecraft.getInstance().font, l, 0, 0, -1, true) }
        }
        FishEvents.chat.register { text ->
            if (S.milestoneHud) LINE.find(text.chatPlain.trim())?.let { cls = it.groupValues[1]; mark = it.groupValues[2] }
            false
        }
        FishEvents.worldChange.register { cls = null; mark = null; false }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_milestone")) { ctx, _ ->
            if (!FishHudEditor.isOpen()) render(ctx)
        }
    }

    private fun label(c: String, m: String): Component = Component.literal("$c Milestone ").withColor(S.milestoneColor and 0xFFFFFF)
        .append(Component.literal(m).withColor(0xFFFF55))

    private fun render(ctx: GuiGraphicsExtractor) {
        if (!S.milestoneHud || !fishmod.features.dungeon.map.DungeonState.isInDungeon()) return
        val c = cls ?: return
        val m = mark ?: return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        val sc = S.milestoneHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.milestoneHudX.toFloat(), S.milestoneHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, label(c, m), 0, 0, -1, true)
        } catch (e: Exception) {
            FishDiag.fail("DungeonMilestoneHud.1", "milestone hud render failed", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
