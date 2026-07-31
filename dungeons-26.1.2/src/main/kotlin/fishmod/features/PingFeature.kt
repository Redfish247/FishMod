package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** MOBA-style location ping (default middle mouse); optionally shared to other FishMod users via the worker. */
object PingFeature {

    private const val REACH = 160.0
    private const val POLL_TICKS = 40 // ~2s between shared-ping polls

    private class Ping(val pos: Vec3, val startMs: Long, val name: String?, val srcTs: Long)

    private var pingKey: KeyMapping? = null
    private var self: Ping? = null
    private var chatPing: Ping? = null // latest coords parsed out of chat
    private val remote: MutableMap<String, Ping> = ConcurrentHashMap() // uuid -> their ping
    private var pollTick = 0
    private var lastSeenTs = 0L // newest source ts pulled, for the `since` filter

    // Requires labelled coords ("x: 12 y: 34 z: -56") so it won't grab random numbers from chat.
    private val COORD_PAT: Pattern = Pattern.compile(
            "(?i)x[:=]?\\s*(-?\\d{1,6})[ ,]+y[:=]?\\s*(-?\\d{1,4})[ ,]+z[:=]?\\s*(-?\\d{1,6})")
    private val NAME_PAT: Pattern = Pattern.compile("([A-Za-z0-9_]{2,16}):")

    @JvmStatic
    fun init() {
        // Reuses the shared category from Keybinds.init (which runs first) to avoid double-registering it.
        val category = fishmod.utils.Keybinds.category()
        pingKey = KeyMappingHelper.registerKeyMapping(KeyMapping(
                "FishMod: Ping location",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                category))

        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, matrices, vc -> render(ctx, matrices, vc) }
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.pingEnabled && FishSettings.pingFromChat && text != null)
                parseChatCoords(text.string.replace(Regex("§."), ""))
            false
        }
    }

    private fun parseChatCoords(plain: String?) {
        if (plain.isNullOrEmpty()) return
        val m = COORD_PAT.matcher(plain)
        if (!m.find()) return
        val x: Int; val y: Int; val z: Int
        try {
            x = m.group(1).toInt()
            y = m.group(2).toInt()
            z = m.group(3).toInt()
        } catch (e: NumberFormatException) { return }
        if (y < -64 || y > 320) return // implausible Y, almost certainly not a location
        var label = "ping" // best-effort: the last "Name:" token before the message body
        val nm = NAME_PAT.matcher(plain.substring(0, m.start()))
        while (nm.find()) label = nm.group(1)
        chatPing = Ping(Vec3(x + 0.5, y.toDouble(), z + 0.5), System.currentTimeMillis(), label, 0)
    }

    private fun onTick(mc: Minecraft) {
        val key = pingKey ?: return

        var fired = false
        while (key.consumeClick()) fired = true // drain queued presses
        if (fired && FishSettings.pingEnabled && mc.player != null && mc.level != null
                && mc.screen == null && Location.inSkyblock()) {
            placePing(mc)
        }

        if (FishSettings.pingEnabled && FishSettings.pingShareEnabled) {
            if (++pollTick >= POLL_TICKS) { pollTick = 0; pollRemote(mc) }
        } else if (remote.isNotEmpty()) {
            remote.clear()
        }
    }

    private fun placePing(mc: Minecraft) {
        val p: LocalPlayer = mc.player!!
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = p.getEyePosition(delta)
        val look = p.getViewVector(delta)
        val end = eye.add(look.scale(REACH))

        val hit: BlockHitResult? = mc.level!!.clip(ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p))

        val target: Vec3 = if (hit != null && hit.type != HitResult.Type.MISS) {
            val bp: BlockPos = hit.blockPos
            Vec3(bp.x + 0.5, bp.y.toDouble(), bp.z + 0.5)
        } else {
            end
        }

        self = Ping(target, System.currentTimeMillis(), null, 0)

        if (FishSettings.pingSound) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f)

        if (FishSettings.pingAnnounceParty && mc.connection != null) {
            mc.connection!!.sendCommand("pc x: " + Math.floor(target.x).toInt()
                    + ", y: " + Math.floor(target.y).toInt() + ", z: " + Math.floor(target.z).toInt() + " (ping)")
        }

        if (FishSettings.pingShareEnabled) {
            val uuid = p.uuid.toString().replace("-", "")
            val dim = mc.level!!.dimension().identifier().toString()
            fishmod.utils.HypixelApi.uploadPing(uuid, p.gameProfile.name(), target.x, target.y, target.z, dim)
        }
    }

    private fun pollRemote(mc: Minecraft) {
        if (mc.connection == null || mc.player == null) return
        val selfUuid = mc.player!!.uuid.toString().replace("-", "")
        val uuids = HashSet<String>()
        for (entry in mc.connection!!.onlinePlayers) {
            val gp = entry.profile
            if (gp == null || gp.id() == null) continue
            val u = gp.id().toString().replace("-", "")
            if (u != selfUuid) uuids.add(u)
        }
        if (uuids.isEmpty()) return

        fishmod.utils.HypixelApi.fetchPings(uuids, lastSeenTs) { list ->
            mc.execute {
                val now = System.currentTimeMillis()
                for (pd in list) {
                    val existing = remote[pd.uuid()]
                    if (existing != null && existing.srcTs == pd.ts()) continue // same ping, keep its local clock
                    remote[pd.uuid()] = Ping(Vec3(pd.x(), pd.y(), pd.z()), now, pd.name(), pd.ts())
                    if (pd.ts() > lastSeenTs) lastSeenTs = pd.ts()
                }
            }
        }
    }

    private fun remainingFraction(ping: Ping): Double {
        val dur = maxOf(1, FishSettings.pingDurationSeconds) * 1000L
        val elapsed = System.currentTimeMillis() - ping.startMs
        return if (elapsed >= dur) 0.0 else 1.0 - elapsed.toDouble() / dur
    }

    private fun render(ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext,
                        matrices: com.mojang.blaze3d.vertex.PoseStack,
                        vc: com.mojang.blaze3d.vertex.VertexConsumer) {
        if (!FishSettings.pingEnabled) return

        if (self != null && remainingFraction(self!!) <= 0) self = null
        self?.let { drawPing(ctx, matrices, vc, it) }

        if (chatPing != null && remainingFraction(chatPing!!) <= 0) chatPing = null
        chatPing?.let { drawPing(ctx, matrices, vc, it) }

        if (remote.isNotEmpty()) {
            remote.entries.removeIf { remainingFraction(it.value) <= 0 }
            for (ping in remote.values) drawPing(ctx, matrices, vc, ping)
        }
    }

    private fun drawPing(ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext,
                          matrices: com.mojang.blaze3d.vertex.PoseStack,
                          vc: com.mojang.blaze3d.vertex.VertexConsumer, ping: Ping) {
        val frac = remainingFraction(ping)
        if (frac <= 0) return

        val base = FishSettings.pingColor
        val r = (base shr 16 and 0xFF) / 255f
        val g = (base shr 8 and 0xFF) / 255f
        val b = (base and 0xFF) / 255f
        val a = (minOf(1.0, frac) * 0.55).toFloat()   // fade out
        val rgba = floatArrayOf(r, g, b, a)

        val x = ping.pos.x; val y = ping.pos.y; val z = ping.pos.z
        // Cube on the block plus a tall beam so it's spottable from across the room.
        RenderUtils.renderFilled(matrices, vc, AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5), rgba)
        RenderUtils.renderFilled(matrices, vc, AABB(x - 0.15, y, z - 0.15, x + 0.15, y + 6, z + 0.15), rgba)

        val me = Minecraft.getInstance().player
        if (me != null) {
            val dist = Math.round(Math.sqrt(me.distanceToSqr(ping.pos))).toInt()
            val who = if (ping.name.isNullOrBlank()) "Ping" else ping.name
            val label: Component = Component.literal("§b⚑ §f$who §7• §e${dist}m")
            RenderUtils.renderText(ctx, matrices, label, x, y + 6.4, z, 1.1f)
        }
    }
}
