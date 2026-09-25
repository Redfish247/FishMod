package fishmod.features.dungeon.f7

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.FishHudEditor
import fishmod.features.dungeon.PbMessages
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.regex.Pattern

object M7Relics {

    private enum class Relic(val itemName: String, val cauldron: Vec3, val argb: Int, val code: String) {
        RED("Corrupted Red Relic", Vec3(51.5, 7.5, 42.5), 0xC0FF0000.toInt(), "§c"),
        ORANGE("Corrupted Orange Relic", Vec3(57.5, 7.5, 42.5), 0xC0FF7200.toInt(), "§6"),
        GREEN("Corrupted Green Relic", Vec3(49.5, 7.5, 44.5), 0xC000FF00.toInt(), "§a"),
        BLUE("Corrupted Blue Relic", Vec3(59.5, 7.5, 44.5), 0xC0008AFF.toInt(), "§b"),
        PURPLE("Corrupted Purple Relic", Vec3(54.5, 7.5, 41.5), 0xC081006F.toInt(), "§5");
    }

    private const val NAME = "Relic Spawn Timer"
    private const val SPAWN_TICKS = 42
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val P5_START = Pattern.compile("\\[BOSS] Necron: All this, for nothing\\.\\.\\.")

    @Volatile private var spawnEndMs = 0L
    private val PICKUP = Pattern.compile("^(\\w{3,16}) picked the Corrupted (\\w{3,6}) Relic!$")
    private var p5StartMs = 0L
    private var myRelic: Relic? = null
    private val pickers = HashMap<Relic, String>()
    private val placed = LinkedHashMap<Relic, Double>()

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.relicTimerHudX }, { v -> FishSettings.relicTimerHudX = v },
            { FishSettings.relicTimerHudY }, { v -> FishSettings.relicTimerHudY = v },
            60, 12,
            { FishSettings.relicTimerScale }, { v -> FishSettings.relicTimerScale = v }
        )

        Events.ON_GAME_MESSAGE.register { text ->
            val msg = COLOR.replace(text.string, "")
            if (P5_START.matcher(msg).find()) {
                p5StartMs = System.currentTimeMillis()
                myRelic = null
                pickers.clear(); placed.clear()
                if (Floor7.enableRelicStartTimer)
                    spawnEndMs = System.currentTimeMillis() + SPAWN_TICKS * 50L
            } else if (p5StartMs != 0L) {
                val m = PICKUP.matcher(msg)
                if (m.find()) {
                    val relic = Relic.entries.firstOrNull { it.itemName == "Corrupted ${m.group(2)} Relic" }
                    if (relic != null) pickers[relic] = m.group(1)
                    if (m.group(1) == Minecraft.getInstance().player?.gameProfile?.name) myRelic = relic
                }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { spawnEndMs = 0L; p5StartMs = 0L; myRelic = null; pickers.clear(); placed.clear(); false }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { checkPlaced(); checkAllPlaced() }

        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> renderBox(m, vc) }
    }

    // Relic armor stand sitting on its cauldron = placed (NoammAddons M7Relics).
    private fun checkPlaced() {
        val relic = myRelic ?: return
        val level = Minecraft.getInstance().level ?: return
        if (Phase.getFloor() != "M7") { myRelic = null; return }
        val cx = relic.cauldron.x + 0.5
        val cz = relic.cauldron.z + 0.5
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand) continue
            if (!e.getItemBySlot(EquipmentSlot.HEAD).hoverName.string.contains("Relic")) continue
            val dx = e.x - cx; val dz = e.z - cz
            if (dx * dx + dz * dz >= 1.5 * 1.5) continue
            myRelic = null
            val secs = (System.currentTimeMillis() - p5StartMs) / 1000.0
            val color = relic.name.lowercase().replaceFirstChar { it.uppercase() }
            PbMessages.announce(FishSettings.pbMessagesRelics, "relic:${relic.name}",
                Component.literal("${relic.code}$color Relic §aplaced in"), secs)
            return
        }
    }

    // Records every relic's place time; once all 5 are in, prints/sends one line per relic.
    private fun checkAllPlaced() {
        if (!Floor7.relicTimesEnabled || p5StartMs == 0L || placed.size == Relic.entries.size) return
        val level = Minecraft.getInstance().level ?: return
        if (Phase.getFloor() != "M7") return
        val now = System.currentTimeMillis()
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand) continue
            if (!e.getItemBySlot(EquipmentSlot.HEAD).hoverName.string.contains("Relic")) continue
            for (relic in Relic.entries) {
                if (relic in placed) continue
                val dx = e.x - (relic.cauldron.x + 0.5); val dz = e.z - (relic.cauldron.z + 0.5)
                if (dx * dx + dz * dz < 1.5 * 1.5) placed[relic] = (now - p5StartMs) / 1000.0
            }
        }
        if (placed.size < Relic.entries.size) return
        for ((relic, secs) in placed) {
            val color = relic.name.lowercase().replaceFirstChar { it.uppercase() }
            val who = pickers[relic] ?: "?"
            val time = PbMessages.fmt(secs)
            if (Floor7.relicTimesParty) fishmod.utils.ChatQueue.enqueue("pc $color Relic: $time ($who)")
            else fishmod.utils.Misc.addChatMessage(Component.literal("${relic.code}$color Relic§7: §e$time §7($who)"))
        }
    }

    private fun heldRelic(): Relic? {
        val p = Minecraft.getInstance().player ?: return null
        val name = COLOR.replace(p.mainHandItem.hoverName.string, "").trim()
        return Relic.entries.firstOrNull { name == it.itemName }
    }

    private fun renderBox(matrices: PoseStack, vc: VertexConsumer) {
        if (!Floor7.renderRelicHighlight || !Location.inDungeon() || !Phase.inP5()) return
        val relic = heldRelic() ?: return
        val c = relic.cauldron
        val box = AABB(c.x - 0.5, c.y - 0.5, c.z - 0.5, c.x + 0.5, c.y + 0.5, c.z + 0.5)
        RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats((0x66 shl 24) or (relic.argb and 0xFFFFFF)))
        RenderUtils.renderThickOutline(matrices, vc, box, RenderUtils.toFloats(relic.argb), 0.02)
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Floor7.enableRelicStartTimer) return
        val left = spawnEndMs - System.currentTimeMillis()
        if (left <= 0L) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val sc = FishSettings.relicTimerScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.relicTimerHudX.toFloat(), FishSettings.relicTimerHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "§dRelic §f" + String.format("%.2fs", left / 1000.0), 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
