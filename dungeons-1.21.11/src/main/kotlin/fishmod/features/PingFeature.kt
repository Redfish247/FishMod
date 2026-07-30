package fishmod.features

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Location ping — press the ping key (default middle mouse) to drop a through-walls waypoint where you're looking, like a MOBA ping. */
object PingFeature {

    private const val REACH = 160.0    // how far the ping ray travels before landing in air
    private const val POLL_TICKS = 40  // ~2s between shared-ping polls

    /** One ping marker — your own or a remote user's. */
    private class Ping(val pos: Vec3d, val startMs: Long, val name: String?, val srcTs: Long)

    private var pingKey: KeyBinding? = null
    private var self: Ping? = null
    private var chatPing: Ping? = null            // latest coords parsed out of chat
    private val remote: MutableMap<String, Ping> = ConcurrentHashMap() // uuid → their ping
    private var pollTick = 0
    private var lastSeenTs = 0L                   // newest source ts we've pulled, for the `since` filter

    // "x: 12 y: 34 z: -56", "x12 y34 z-56", "x=12, y=34, z=-56" — labelled so it won't grab random numbers.
    private val COORD_PAT: Pattern = Pattern.compile(
            "(?i)x[:=]?\\s*(-?\\d{1,6})[ ,]+y[:=]?\\s*(-?\\d{1,4})[ ,]+z[:=]?\\s*(-?\\d{1,6})")
    private val NAME_PAT: Pattern = Pattern.compile("([A-Za-z0-9_]{2,16}):")

    @JvmStatic
    fun init() {
        // Reuse the shared FishMod keybind category (created in Keybinds.init, which runs first) so we
        // don't double-register the category Identifier.
        var category = fishmod.utils.Keybinds.category
        if (category == null) category = KeyBinding.Category.create(Identifier.of(fishmod.utils.Constants.NAMESPACE))
        pingKey = KeyBindingHelper.registerKeyBinding(KeyBinding(
                "FishMod - Ping location",
                InputUtil.Type.MOUSE,
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

    /** Detect "x: N y: N z: N" in a chat line and drop a waypoint there, labelled with the speaker. */
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
        if (y < -64 || y > 320) return // implausible Y → almost certainly not a location
        // Best-effort speaker name: the last "Name:" token before the message body.
        var label = "ping"
        val nm = NAME_PAT.matcher(plain.substring(0, m.start()))
        while (nm.find()) label = nm.group(1)
        chatPing = Ping(Vec3d(x + 0.5, y.toDouble(), z + 0.5), System.currentTimeMillis(), label, 0)
    }

    private fun onTick(mc: MinecraftClient) {
        val key = pingKey ?: return

        var fired = false
        while (key.wasPressed()) fired = true // drain queued presses
        if (fired && FishSettings.pingEnabled && mc.player != null && mc.world != null
                && mc.currentScreen == null && Location.inSkyblock()) {
            placePing(mc)
        }

        // Poll for other users' shared pings.
        if (FishSettings.pingEnabled && FishSettings.pingShareEnabled) {
            if (++pollTick >= POLL_TICKS) { pollTick = 0; pollRemote(mc) }
        } else if (remote.isNotEmpty()) {
            remote.clear()
        }
    }

    private fun placePing(mc: MinecraftClient) {
        val p: ClientPlayerEntity = mc.player!!
        val delta = mc.renderTickCounter.getTickProgress(false)
        val eye = p.getCameraPosVec(delta)
        val look = p.getRotationVec(delta)
        val end = eye.add(look.multiply(REACH))

        val hit: BlockHitResult? = mc.world!!.raycast(RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p))

        val target: Vec3d = if (hit != null && hit.type != HitResult.Type.MISS) {
            val bp: BlockPos = hit.blockPos
            Vec3d(bp.x + 0.5, bp.y.toDouble(), bp.z + 0.5)
        } else {
            end // landed in air — ping the point you're aiming at
        }

        self = Ping(target, System.currentTimeMillis(), null, 0)

        if (FishSettings.pingSound) p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.7f, 1.6f)

        if (FishSettings.pingAnnounceParty && mc.networkHandler != null) {
            mc.networkHandler!!.sendChatCommand("pc x: " + Math.floor(target.x).toInt()
                    + ", y: " + Math.floor(target.y).toInt() + ", z: " + Math.floor(target.z).toInt() + " (ping)")
        }

        if (FishSettings.pingShareEnabled) {
            val uuid = p.uuid.toString().replace("-", "")
            val dim = mc.world!!.registryKey.value.toString()
            fishmod.utils.HypixelApi.uploadPing(uuid, p.gameProfile.name, target.x, target.y, target.z, dim)
        }
    }

    private fun pollRemote(mc: MinecraftClient) {
        if (mc.networkHandler == null || mc.player == null) return
        val selfUuid = mc.player!!.uuid.toString().replace("-", "")
        val uuids = HashSet<String>()
        for (entry in mc.networkHandler!!.playerList) {
            val gp = entry.profile
            if (gp?.id == null) continue
            val u = gp.id.toString().replace("-", "")
            if (u != selfUuid) uuids.add(u)
        }
        if (uuids.isEmpty()) return

        fishmod.utils.HypixelApi.fetchPings(uuids, lastSeenTs) { list ->
            mc.execute {
                val now = System.currentTimeMillis()
                for (pd in list) {
                    val existing = remote[pd.uuid()]
                    if (existing != null && existing.srcTs == pd.ts()) continue // same ping, keep its local clock
                    remote[pd.uuid()] = Ping(Vec3d(pd.x(), pd.y(), pd.z()), now, pd.name(), pd.ts())
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

    private fun render(ctx: net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext,
                        matrices: net.minecraft.client.util.math.MatrixStack,
                        vc: net.minecraft.client.render.VertexConsumer) {
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

    private fun drawPing(ctx: net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext,
                          matrices: net.minecraft.client.util.math.MatrixStack,
                          vc: net.minecraft.client.render.VertexConsumer, ping: Ping) {
        val frac = remainingFraction(ping)
        if (frac <= 0) return

        val base = FishSettings.pingColor
        val r = (base shr 16 and 0xFF) / 255f
        val g = (base shr 8 and 0xFF) / 255f
        val b = (base and 0xFF) / 255f
        val a = (minOf(1.0, frac) * 0.55).toFloat()   // fade out
        val rgba = floatArrayOf(r, g, b, a)

        val x = ping.pos.x; val y = ping.pos.y; val z = ping.pos.z
        // Base cube on the block + a tall thin beam so it's spottable from across the room.
        RenderUtils.renderFilled(matrices, vc, Box(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5), rgba)
        RenderUtils.renderFilled(matrices, vc, Box(x - 0.15, y, z - 0.15, x + 0.15, y + 6, z + 0.15), rgba)

        val me = MinecraftClient.getInstance().player
        if (me != null) {
            val dist = Math.round(Math.sqrt(me.squaredDistanceTo(ping.pos))).toInt()
            val who = if (ping.name.isNullOrBlank()) "Ping" else ping.name
            val label: Text = Text.literal("§b⚑ §f$who §7• §e${dist}m")
            RenderUtils.renderText(ctx, matrices, label, x, y + 6.4, z, 1.1f)
        }
    }
}
