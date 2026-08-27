package fishmod.features

import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.regex.Pattern

/**
 * Revives blade-addons' `enableWarpCooldown` (logic ported from Odin's WarpCooldown): Hypixel gates
 * re-entering a dungeon for ~30s after the party enters one. The clock starts on the
 * "<player> entered <floor> Catacombs, Floor <n>!" chat line (not on `/warp`, which was wrong), and
 * the HUD counts it down. Optionally announces to party chat if you get kicked mid-join.
 */
object WarpCooldown {

    private const val NAME = "Warp Cooldown"
    private val ENTERED: Pattern =
        Pattern.compile("\\b(\\w{1,16}) entered (?:MM )?\\w+ Catacombs, Floor \\w+!")
    private val KICKED: Pattern =
        Pattern.compile("^(?:You were kicked while joining that server!|You are no longer allowed to access this instance!)$")
    private val COLOR = Regex("§.")

    @Volatile private var enteredAt = 0L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.warpCooldownHudX }, { v -> FishSettings.warpCooldownHudX = v },
            { FishSettings.warpCooldownHudY }, { v -> FishSettings.warpCooldownHudY = v },
            80, 12,
            { FishSettings.warpCooldownScale }, { v -> FishSettings.warpCooldownScale = v }
        )

        Events.ON_GAME_MESSAGE.register { text ->
            val s = COLOR.replace(text.string, "")
            if (ENTERED.matcher(s).find()) {
                enteredAt = System.currentTimeMillis()
            } else if (Dungeons.enableWarpCooldown && FishSettings.warpAnnounceKick && KICKED.matcher(s).matches()) {
                val mc = Minecraft.getInstance()
                mc.execute { mc.connection?.sendCommand("pc ${FishSettings.warpKickText}") }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { false }
    }

    private fun remainingMs(): Long {
        if (enteredAt == 0L) return 0
        val total = FishSettings.warpCooldownSeconds.coerceIn(1, 120) * 1000L
        return (total - (System.currentTimeMillis() - enteredAt)).coerceAtLeast(0)
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Dungeons.enableWarpCooldown) return
        val rem = remainingMs()
        if (rem <= 0L) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        val label = net.minecraft.network.chat.Component.literal("§eWarp: ")
            .append(net.minecraft.network.chat.Component.literal(String.format("%.1fs", rem / 1000.0))
                .withColor(FishSettings.warpCooldownColor and 0xFFFFFF))
        val sc = FishSettings.warpCooldownScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.warpCooldownHudX.toFloat(), FishSettings.warpCooldownHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, label, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
