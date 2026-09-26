package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

object SecretClicked {

    private class Secret(val box: AABB, val blockPos: BlockPos?, @JvmField var locked: Boolean = false) {
        val expiresAt = System.currentTimeMillis() + FishSettings.secretClickedTimeToStay.coerceIn(1, 20) * 1000L
    }

    private const val BAT_RANGE = 10.0
    private const val ITEM_RANGE = 6.0

    private val clicked = CopyOnWriteArrayList<Secret>()
    private var lastChime = 0L
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private val itemPos = HashMap<Int, Vec3>()
    private var lastBat = 0L

    private val pickedItemIds = ConcurrentLinkedQueue<Int>()
    private val batSounds = ConcurrentLinkedQueue<Vec3>()
    @Volatile private var selfId = -1

    @JvmStatic
    fun init() {
        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, hand, hit ->
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) onInteract(hit.blockPos)
            InteractionResult.PASS
        })

        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundTakeItemEntityPacket -> if (packet.playerId == selfId) pickedItemIds.add(packet.itemId)
                is ClientboundSoundPacket -> SecretDrops.batSound(packet)?.let { batSounds.add(it) }
            }
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })

        Events.ON_GAME_MESSAGE.register { text ->
            if (COLOR.replace(text.string, "").trim() == "That chest is locked!") {
                clicked.lastOrNull()?.locked = true
            }
            false
        }
        Events.ON_WORLD_CHANGE.register {
            clicked.clear(); itemPos.clear()
            pickedItemIds.clear(); batSounds.clear()
            false
        }

        RenderingEvents.GIZMO.register { _ -> if (!FishSettings.secretClickedDepthCheck) renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.secretClickedDepthCheck) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.secretClickedDepthCheck) render(m, vc, fill = false) }
    }

    private fun onTick(mc: net.minecraft.client.Minecraft) {
        if (clicked.isNotEmpty()) {
            val now = System.currentTimeMillis()
            clicked.removeIf { it.expiresAt <= now }
        }
        val player = mc.player
        selfId = player?.id ?: -1
        val level = mc.level
        if (player == null || level == null || !active()) {
            pickedItemIds.clear(); batSounds.clear()
            return
        }
        val eye = player.eyePosition

        while (true) {
            val id = pickedItemIds.poll() ?: break
            if (!FishSettings.secretClickedItems) continue
            val pos = itemPos[id]
                ?: (level.getEntity(id) as? ItemEntity)?.takeIf { SecretDrops.isSecretItem(it) }?.position()
                ?: continue
            if (pos.distanceToSqr(eye) <= ITEM_RANGE * ITEM_RANGE) addLooseSecret(pos)
        }

        while (true) {
            val pos = batSounds.poll() ?: break
            if (!FishSettings.secretClickedBats) continue
            val now = System.currentTimeMillis()
            if (now - lastBat < 500 || pos.distanceToSqr(eye) > BAT_RANGE * BAT_RANGE) continue
            lastBat = now
            addLooseSecret(pos)
        }

        itemPos.clear()
        for (e in level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(ITEM_RANGE + 2.0))) {
            if (SecretDrops.isSecretItem(e)) itemPos[e.id] = e.position()
        }
    }

    private fun addLooseSecret(pos: Vec3) {
        chime()
        if (!FishSettings.secretClickedBoxes) return
        if (clicked.any { it.blockPos == null && it.box.center.distanceToSqr(pos) < 0.25 }) return
        val box = AABB.ofSize(pos, 0.9, 0.9, 0.9)
        clicked.add(Secret(box, null))
    }

    private fun boxes(): List<Triple<AABB, Int, Int>> {
        if (!active() || !FishSettings.secretClickedBoxes || clicked.isEmpty()) return emptyList()
        val level = net.minecraft.client.Minecraft.getInstance().level
        val style = FishSettings.secretClickedStyle
        return clicked.map { s ->
            val argb = if (s.locked) FishSettings.secretClickedLockedColor else FishSettings.secretClickedColor
            val box = if (s.blockPos != null) {
                val state = level?.getBlockState(s.blockPos)
                val shape = state?.getShape(level, s.blockPos)
                val local = if (shape != null && !shape.isEmpty) shape.bounds() else AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                local.move(s.blockPos.x.toDouble(), s.blockPos.y.toDouble(), s.blockPos.z.toDouble()).inflate(0.002)
            } else s.box
            val fillA = minOf(argb ushr 24, 0x60)
            val fill = if (style != "Outline") (fillA shl 24) or (argb and 0xFFFFFF) else 0
            val stroke = if (style != "Filled") (0xFF shl 24) or (argb and 0xFFFFFF) else 0
            Triple(box, fill, stroke)
        }
    }

    private fun lineWidth(): Double = FishSettings.secretClickedLineWidth.coerceIn(0.5, 10.0) / 100.0

    private fun renderGizmo() {
        val width = lineWidth()
        for ((box, fill, stroke) in boxes()) {
            RenderUtils.gizmoBox(box, fill, 0)
            RenderUtils.gizmoThickOutline(box, stroke, width)
        }
    }

    private fun active(): Boolean =
        FishSettings.secretClickedEnabled && Location.inDungeon() &&
            (FishSettings.secretClickedInBoss || !Phase.inBoss())

    private fun onInteract(pos: BlockPos) {
        if (!active()) return
        val mc = net.minecraft.client.Minecraft.getInstance()
        val level = mc.level ?: return
        val block = level.getBlockState(pos).block
        val secret = block is ChestBlock || block is LeverBlock ||
            (block is AbstractSkullBlock && SecretDrops.isSecretSkull(level, pos))
        if (!secret) return
        chime()
        if (!FishSettings.secretClickedBoxes || clicked.any { it.blockPos == pos }) return
        val box = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        clicked.add(Secret(box, pos.immutable()))
    }

    private fun chime() {
        if (!FishSettings.secretClickedChime) return
        if (Phase.inBoss() && !FishSettings.secretClickedChimeInBoss) return
        val now = System.currentTimeMillis()
        if (now - lastChime <= 10) return
        lastChime = now
        SoundManager.play(
            SoundManager.preset(FishSettings.secretClickedSoundName),
            FishSettings.secretClickedVolume.coerceIn(0, 500) / 100f,
            FishSettings.secretClickedPitch.toFloat().coerceIn(0f, 2f),
            "secretChime", 0
        )
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        for ((box, fillArgb, strokeArgb) in boxes()) {
            if (fill) {
                if ((fillArgb ushr 24) != 0) RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(fillArgb))
            } else {
                if ((strokeArgb ushr 24) != 0) RenderUtils.renderThickOutline(matrices, vc, box, RenderUtils.toFloats(strokeArgb), lineWidth())
            }
        }
    }
}
