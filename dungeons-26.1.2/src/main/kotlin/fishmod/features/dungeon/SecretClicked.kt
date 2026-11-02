package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.utils.TextStrip
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
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
import fishmod.utils.debug.FishDiag

object SecretClicked {

    private class Secret(val box: AABB, val blockPos: BlockPos?, @JvmField var locked: Boolean = false) {
        val expiresAt = System.currentTimeMillis() + FishSettings.secretClickedTimeToStay.coerceIn(1, 20) * 1000L
    }

    private const val BAT_RANGE = 10.0
    private const val ITEM_RANGE = 6.0

    private val clicked = CopyOnWriteArrayList<Secret>()
    private var lastChime = 0L

    private val itemPos = HashMap<Int, Vec3>()
    private var lastBat = 0L

    private val pickedItemIds = ConcurrentLinkedQueue<Int>()
    private val batSounds = ConcurrentLinkedQueue<Vec3>()
    @Volatile private var selfId = -1

    @JvmStatic
    fun init() {
        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, hand, hit ->
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                try { onInteract(hit.blockPos) } catch (e: Exception) { FishDiag.fail("SecretClicked.1", "secret click handler threw at ${hit.blockPos}", e) }
            }
            InteractionResult.PASS
        })

        FishEvents.ON_PACKET.register { packet ->
            try {
                when (packet) {
                    is ClientboundTakeItemEntityPacket -> if (packet.playerId == selfId) pickedItemIds.add(packet.itemId)
                    is ClientboundSoundPacket -> SecretDrops.batSound(packet)?.let { batSounds.add(it) }
                }
            } catch (e: Exception) { FishDiag.fail("SecretClicked.2", "secret packet handler threw on ${packet.javaClass.simpleName}", e) }
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try { onTick(mc) } catch (e: Exception) { FishDiag.fail("SecretClicked.3", "secret clicked tick threw (clicked=${clicked.size})", e) }
        })

        FishEvents.ON_GAME_MESSAGE.register { text ->
            if (TextStrip.plain(text.string).trim() == "That chest is locked!") {
                clicked.lastOrNull()?.locked = true
            }
            false
        }
        FishEvents.ON_WORLD_CHANGE.register {
            clicked.clear(); itemPos.clear(); pendingSkull = null
            pickedItemIds.clear(); batSounds.clear()
            false
        }

        RenderHooks.GIZMO.register { _ ->
            if (!FishSettings.secretClickedDepthCheck) try { renderGizmo() } catch (e: Exception) { FishDiag.fail("SecretClicked.4", "secret clicked gizmo render threw", e) }
        }
        RenderHooks.NO_DEPTH_FILLED.register { _, m, vc ->
            if (FishSettings.secretClickedDepthCheck) try { render(m, vc, fill = true) } catch (e: Exception) { FishDiag.fail("SecretClicked.5", "secret clicked fill render threw", e) }
        }
        RenderHooks.NO_DEPTH_LINE.register { _, m, vc ->
            if (FishSettings.secretClickedDepthCheck) try { render(m, vc, fill = false) } catch (e: Exception) { FishDiag.fail("SecretClicked.6", "secret clicked line render threw", e) }
        }
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

    private var cachedBoxes: List<Triple<AABB, Int, Int>> = emptyList()
    private var cachedBoxesAt = 0L
    private var cachedBoxesCount = -1

    private fun boxes(): List<Triple<AABB, Int, Int>> {
        val now = System.currentTimeMillis()
        if (clicked.size == cachedBoxesCount && now - cachedBoxesAt < 20L) return cachedBoxes
        return computeBoxes().also {
            cachedBoxes = it
            cachedBoxesAt = now
            cachedBoxesCount = clicked.size
        }
    }

    private fun computeBoxes(): List<Triple<AABB, Int, Int>> {
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
            FishDraw.gizmoBox(box, fill, 0)
            FishDraw.gizmoThickOutline(box, stroke, width)
        }
    }

    private fun active(): Boolean =
        FishSettings.secretClickedEnabled && SkyblockLocation.inDungeon() &&
            (FishSettings.secretClickedInBoss || !BossPhase.inBoss())

    private fun onInteract(pos: BlockPos) {
        if (!active()) return
        val mc = net.minecraft.client.Minecraft.getInstance()
        val level = mc.level ?: return
        val block = level.getBlockState(pos).block
        val secret = block is ChestBlock || block is LeverBlock ||
            (block is AbstractSkullBlock && SecretDrops.isSecretSkull(level, pos))
        if (!secret) {
            // Unknown skull owner id: confirm via the action-bar secret count instead
            if (block is AbstractSkullBlock) { pendingSkull = pos.immutable(); pendingSkullAt = System.currentTimeMillis() }
            return
        }
        markClicked(pos)
    }

    private var pendingSkull: BlockPos? = null
    private var pendingSkullAt = 0L

    @JvmStatic
    fun onSecretCount(found: Int, prevFound: Int, prevFresh: Boolean) {
        val pos = pendingSkull ?: return
        if (System.currentTimeMillis() - pendingSkullAt > 1500) { pendingSkull = null; return }
        if (!prevFresh || found <= prevFound) return
        pendingSkull = null
        net.minecraft.client.Minecraft.getInstance().execute {
            try { if (active()) markClicked(pos) } catch (e: Exception) { FishDiag.fail("SecretClicked.7", "deferred skull mark threw at $pos", e) }
        }
    }

    private fun markClicked(pos: BlockPos) {
        chime()
        if (!FishSettings.secretClickedBoxes || clicked.any { it.blockPos == pos }) return
        val box = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        clicked.add(Secret(box, pos.immutable()))
    }

    private fun chime() {
        if (!FishSettings.secretClickedChime) return
        if (BossPhase.inBoss() && !FishSettings.secretClickedChimeInBoss) return
        val now = System.currentTimeMillis()
        if (now - lastChime <= 10) return
        lastChime = now
        FishDiag.guard("SecretClicked.8", "secret chime play failed (${FishSettings.secretClickedSoundName})") { SoundManager.play(
            SoundManager.preset(FishSettings.secretClickedSoundName),
            FishSettings.secretClickedVolume.coerceIn(0, 500) / 100f,
            FishSettings.secretClickedPitch.toFloat().coerceIn(0f, 2f),
            "secretChime", 0
        ) }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        for ((box, fillArgb, strokeArgb) in boxes()) {
            if (fill) {
                if ((fillArgb ushr 24) != 0) FishDraw.renderFilled(matrices, vc, box, FishDraw.toFloats(fillArgb))
            } else {
                if ((strokeArgb ushr 24) != 0) FishDraw.renderThickOutline(matrices, vc, box, FishDraw.toFloats(strokeArgb), lineWidth())
            }
        }
    }
}
