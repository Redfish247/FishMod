package fishmod.features.dungeon

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.DungeonMap
import fishmod.features.dungeon.map.Room
import fishmod.features.dungeon.map.Scan
import fishmod.utils.FishMsg
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.EntityUtil
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

// Temporary dev tool: records dungeon actions as an ordered route, then replays it.
object RouteRecorder {

    enum class Type(val label: String, val tolerance: Double) {
        ETHERWARP("Etherwarp", 2.5),
        PEARL("Pearl", 3.0),
        BREAK("Break", 1.5),
        SUPERBOOM("Superboom", 3.0),
        CHEST("Chest", 1.5),
        SECRET("Secret", 1.5),
        ITEM("Item", 3.0),
        BAT("Bat", 5.0),
    }

    class Step(
        val type: Type,
        val room: String?,
        val local: IntArray?,
        val world: IntArray,
        var landLocal: IntArray? = null,
        var landWorld: IntArray? = null,
    )

    private enum class Mode { IDLE, RECORDING, PLAYING }

    private const val BAT_RANGE = 10.0
    private const val ITEM_RANGE = 6.0
    private const val LOOKAHEAD = 4

    private val steps = ArrayList<Step>()
    private var mode = Mode.IDLE
    private var progress = 0
    private var liveWorld = false
    private var dirty = false
    private var enteredRoute = false
    private var outsideTicks = 0
    private var lastAutoRoom: String? = null
    private val doneRooms = HashSet<String>()

    private var lastPos: Vec3? = null
    private var tick = 0L
    private var pendingPearl: Step? = null
    private var pearlTick = 0L
    private val pendingBreaks = HashMap<BlockPos, Long>()

    private val batSounds = ConcurrentLinkedQueue<Vec3>()
    private var lastBat = 0L
    private val itemPos = HashMap<Int, Vec3>()
    private val pickedItemIds = ConcurrentLinkedQueue<Int>()
    @Volatile private var selfId = -1
    @Volatile private var teleported = false

    private var anchorCache: Map<String, DungeonRoomAnchor.Anchor> = emptyMap()
    private var anchorTick = -100L

    private val ETHER_ITEMS = setOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END", "ETHERWARP_CONDUIT")
    private val BOOM_ITEMS = setOf("SUPERBOOM_TNT", "INFINITE_SUPERBOOM_TNT")
    private var lastBoomTick = -100L
    private var boomHeldTick = -1000L
    private val boomSounds = ConcurrentLinkedQueue<Pair<String, Vec3>>()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dir get() = FabricLoader.getInstance().configDir.resolve("FishMod").resolve("routes")

    @JvmStatic
    fun init() {
        UseBlockCallback.EVENT.register(UseBlockCallback { _, level, hand, hit ->
            if (hand == InteractionHand.MAIN_HAND && tracking() && Minecraft.getInstance().player?.let { isBoom(it.mainHandItem) } == true) {
                boom(hit.blockPos.immutable(), "use-block")
            } else if (hand == InteractionHand.MAIN_HAND && tracking()) {
                val block = level.getBlockState(hit.blockPos).block
                val type = when (block) {
                    is ChestBlock -> Type.CHEST
                    is LeverBlock, is AbstractSkullBlock -> Type.SECRET
                    else -> null
                }
                if (type != null) action(type, hit.blockPos.immutable())
            }
            InteractionResult.PASS
        })

        UseItemCallback.EVENT.register(UseItemCallback { player, _, hand ->
            if (hand == InteractionHand.MAIN_HAND && tracking() && isBoom(player.mainHandItem)) {
                val hit = Minecraft.getInstance().hitResult as? net.minecraft.world.phys.BlockHitResult
                boom(hit?.takeIf { it.type == net.minecraft.world.phys.HitResult.Type.BLOCK }?.blockPos
                    ?: BlockPos.containing(player.eyePosition.add(player.lookAngle.scale(3.0))), "use-item")
            } else if (hand == InteractionHand.MAIN_HAND && tracking() && player.mainHandItem.item == Items.ENDER_PEARL) {
                val step = action(Type.PEARL, player.blockPosition())
                if (step != null) { pendingPearl = step; pearlTick = tick }
            }
            InteractionResult.PASS
        })

        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, _, hand, pos, _ ->
            if (hand == InteractionHand.MAIN_HAND && tracking() && isBoom(player.mainHandItem)) {
                boom(pos.immutable(), "attack")
            } else if (hand == InteractionHand.MAIN_HAND && tracking() && ItemUtil.getId(player.mainHandItem) == "DUNGEONBREAKER") {
                pendingBreaks[pos.immutable()] = tick
            }
            InteractionResult.PASS
        })

        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundTakeItemEntityPacket -> if (packet.playerId == selfId) pickedItemIds.add(packet.itemId)
                is ClientboundPlayerPositionPacket -> teleported = true
                is ClientboundSoundPacket -> {
                    SecretDrops.batSound(packet)?.let { batSounds.add(it) }
                    boomSounds.add(packet.sound.value().location.path to Vec3(packet.x, packet.y, packet.z))
                }
            }
            false
        }

        Events.ON_WORLD_CHANGE.register {
            liveWorld = false
            lastPos = null; pendingPearl = null; pendingBreaks.clear()
            batSounds.clear(); itemPos.clear(); pickedItemIds.clear()
            anchorCache = emptyMap()
            doneRooms.clear(); lastAutoRoom = null
            if (mode == Mode.RECORDING) mode = Mode.IDLE
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })

        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> renderNoDepth(m, vc) }
        RenderingEvents.GIZMO.register { _ -> renderGizmo(); labels() }
    }

    private fun isBoom(stack: net.minecraft.world.item.ItemStack) = ItemUtil.getId(stack) in BOOM_ITEMS

    // use-on-block and use-item can both fire for one click
    // clicks and the explosion itself can all report one superboom
    private fun boom(pos: BlockPos, via: String) {
        fishmod.utils.debug.Debug.LOGGER.info("[Route] superboom via $via at $pos")
        if (tick - lastBoomTick < 20) return
        lastBoomTick = tick
        action(Type.SUPERBOOM, pos)
    }

    private fun currentRoom(): String? = DungeonMap.roomPlayerIn()?.owner?.data?.name

    private fun routeRoom(): String? = steps.firstNotNullOfOrNull { it.room }

    // leaving the route's room saves (if changed) and unloads it, even mid-route or mid-recording
    private fun checkRoomLeave() {
        if (steps.isEmpty() || !Location.inDungeon()) { outsideTicks = 0; return }
        val here = currentRoom() ?: return
        val rooms = steps.mapNotNullTo(HashSet()) { it.room }
        if (rooms.isEmpty()) return
        if (here in rooms) { enteredRoute = true; outsideTicks = 0; return }
        if (!enteredRoute || ++outsideTicks < 10) return
        val name = routeRoom()!!
        if (dirty) save(name)
        steps.clear(); progress = 0; mode = Mode.IDLE; enteredRoute = false; outsideTicks = 0
        lastAutoRoom = null
    }

    // entering a room with a saved route loads and plays it
    private fun autoLoad() {
        if (!FishSettings.routeRecorderEnabled || !FishSettings.routeAutoLoad) return
        if (mode != Mode.IDLE || steps.isNotEmpty() || !Location.inDungeon()) return
        val here = currentRoom() ?: return
        if (here == lastAutoRoom) return
        lastAutoRoom = here
        // finished route or green-checked (all secrets) room: don't load again this run
        if (here in doneRooms || DungeonMap.roomPlayerIn()?.owner?.state == Room.State.GREEN) return
        if (!Files.exists(dir.resolve(clean(here) + ".json"))) return
        if (load(here, quiet = true)) { progress = 0; mode = Mode.PLAYING; enteredRoute = true }
    }

    private fun tracking() = FishSettings.routeRecorderEnabled && mode != Mode.IDLE && Location.inDungeon()

    private fun onTick(mc: Minecraft) {
        tick++
        checkRoomLeave()
        autoLoad()
        val player = mc.player
        val level = mc.level
        selfId = player?.id ?: -1
        if (player == null || level == null || !tracking()) {
            pickedItemIds.clear(); batSounds.clear(); boomSounds.clear(); teleported = false; lastPos = player?.position()
            return
        }
        val pos = player.position()
        val prev = lastPos
        lastPos = pos

        // server teleport = etherwarp (sneaking + ether item) or pearl landing
        val tp = teleported
        teleported = false
        if (tp && prev != null && prev.distanceToSqr(pos) > 2.25) {
            val pearl = pendingPearl
            if (pearl != null && tick - pearlTick <= 80) {
                landPearl(pearl, player.blockPosition())
                pendingPearl = null
            } else if (player.isShiftKeyDown && ItemUtil.getId(player.mainHandItem) in ETHER_ITEMS) {
                action(Type.ETHERWARP, player.blockPosition().below())
            }
        }
        if (pendingPearl != null && tick - pearlTick > 80) pendingPearl = null

        if (isBoom(player.mainHandItem)) boomHeldTick = tick
        while (true) {
            val (name, at) = boomSounds.poll() ?: break
            if (tick - boomHeldTick > 40 || at.distanceToSqr(pos) > 100.0) continue
            fishmod.utils.debug.Debug.LOGGER.info("[Route] sound near superboom: $name")
            if ("explode" in name || "explosion" in name) boom(BlockPos.containing(at), "sound:$name")
        }

        val iter = pendingBreaks.entries.iterator()
        while (iter.hasNext()) {
            val (bpos, t) = iter.next()
            if (level.getBlockState(bpos).isAir) { iter.remove(); action(Type.BREAK, bpos) }
            else if (tick - t > 10) iter.remove()
        }

        val eye = player.eyePosition
        while (true) {
            val id = pickedItemIds.poll() ?: break
            val p = itemPos[id] ?: (level.getEntity(id) as? ItemEntity)?.takeIf { e -> SecretDrops.isSecretItem(e) }?.position() ?: continue
            if (p.distanceToSqr(eye) <= ITEM_RANGE * ITEM_RANGE) action(Type.ITEM, BlockPos.containing(p))
        }
        while (true) {
            val p = batSounds.poll() ?: break
            val now = System.currentTimeMillis()
            if (now - lastBat < 500 || p.distanceToSqr(eye) > BAT_RANGE * BAT_RANGE) continue
            lastBat = now
            action(Type.BAT, BlockPos.containing(p))
        }
        itemPos.clear()
        for (e in level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(ITEM_RANGE + 2.0))) {
            if (SecretDrops.isSecretItem(e)) itemPos[e.id] = e.position()
        }
    }

    private fun action(type: Type, pos: BlockPos): Step? = when (mode) {
        Mode.RECORDING -> record(type, pos)
        Mode.PLAYING -> { complete(type, pos); null }
        Mode.IDLE -> null
    }

    private fun record(type: Type, pos: BlockPos): Step? {
        val last = steps.lastOrNull()
        if (last != null && last.type == type && type != Type.ETHERWARP && type != Type.PEARL &&
            last.world.contentEquals(arr(pos))) return null
        val anchor = DungeonRoomAnchor.current()
        val step = Step(type, anchor?.name, anchor?.let { arr(DungeonRoomAnchor.toLocal(it, pos)) }, arr(pos))
        steps.add(step)
        dirty = true
        liveWorld = true
        return step
    }

    private fun landPearl(step: Step, land: BlockPos) {
        step.landWorld = arr(land)
        val anchor = step.room?.let { anchors()[it] }
        if (anchor != null) step.landLocal = arr(DungeonRoomAnchor.toLocal(anchor, land))
    }

    private fun complete(type: Type, pos: BlockPos) {
        val at = Vec3.atCenterOf(pos)
        val end = minOf(steps.size, progress + LOOKAHEAD)
        for (i in progress until end) {
            val s = steps[i]
            if (s.type != type) continue
            val w = resolve(s) ?: continue
            if (Vec3.atCenterOf(w).distanceTo(at) > type.tolerance) continue
            progress = i + 1
            if (progress >= steps.size) routeRoom()?.let { doneRooms.add(it) }
            return
        }
    }

    // ---- positions ----

    private fun arr(p: BlockPos) = intArrayOf(p.x, p.y, p.z)
    private fun bp(a: IntArray) = BlockPos(a[0], a[1], a[2])

    private fun anchors(): Map<String, DungeonRoomAnchor.Anchor> {
        if (tick - anchorTick < 20) return anchorCache
        anchorTick = tick
        val out = HashMap<String, DungeonRoomAnchor.Anchor>()
        for (tile in Scan.roomsList) {
            val room = tile.owner ?: continue
            val name = room.data?.name ?: continue
            val clay = room.clayPos ?: continue
            if (room.rotation == Room.Rotation.NONE || name in out) continue
            out[name] = DungeonRoomAnchor.Anchor(name, room.rotation, clay)
        }
        anchorCache = out
        return out
    }

    private fun resolve(s: Step): BlockPos? = resolve(s.room, s.local, s.world)

    private fun resolve(room: String?, local: IntArray?, world: IntArray?): BlockPos? {
        if (room != null && local != null) anchors()[room]?.let { return DungeonRoomAnchor.toWorld(it, bp(local)) }
        return if (liveWorld && world != null) bp(world) else null
    }

    // ---- rendering ----

    private fun visible(): List<Pair<Int, BlockPos>> {
        if (!FishSettings.routeRecorderEnabled || steps.isEmpty()) return emptyList()
        if (!Location.inDungeon()) return emptyList()
        val from = if (mode == Mode.PLAYING) progress else 0
        val out = ArrayList<Pair<Int, BlockPos>>()
        for (i in from until steps.size) resolve(steps[i])?.let { out.add(i to it) }
        return out
    }

    private fun box(p: BlockPos) = AABB(p).inflate(0.002)

    private fun color(t: Type): Int = when (t) {
        Type.ETHERWARP -> FishSettings.routeColorEtherwarp
        Type.PEARL -> FishSettings.routeColorPearl
        Type.BREAK -> FishSettings.routeColorBreak
        Type.SUPERBOOM -> FishSettings.routeColorSuperboom
        Type.CHEST -> FishSettings.routeColorChest
        Type.SECRET -> FishSettings.routeColorSecret
        Type.ITEM -> FishSettings.routeColorItem
        Type.BAT -> FishSettings.routeColorBat
    }

    private fun withAlpha(argb: Int, pct: Int) = ((pct.coerceIn(0, 100) * 255 / 100) shl 24) or (argb and 0xFFFFFF)

    private fun isSecret(t: Type) = t == Type.CHEST || t == Type.SECRET || t == Type.ITEM || t == Type.BAT

    private fun throughWalls(t: Type) = if (isSecret(t)) FishSettings.routeSecretsThroughWalls else FishSettings.routeThroughWalls

    // each step (and the line leading into it) renders in the pass matching its through-walls setting
    private inline fun draw(noDepth: Boolean, box: (AABB, Int, Int) -> Unit, line: (Vec3, Vec3, Double, Int) -> Unit) {
        val vis = visible()
        if (vis.isEmpty()) return
        val style = FishSettings.routeBoxStyle
        val fillPct = if (style != "Outline") FishSettings.routeFillOpacity else 0
        val strokePct = if (style != "Filled") FishSettings.routeOutlineOpacity else 0
        val hw = FishSettings.routeLineWidth * 0.01
        var prev: Vec3? = null
        for ((i, p) in vis) {
            val s = steps[i]
            val c = color(s.type)
            val center = Vec3.atCenterOf(p)
            if (throughWalls(s.type) != noDepth) { prev = center; continue }
            val current = FishSettings.routeHighlightCurrent && mode == Mode.PLAYING && i == progress
            box(box(p), withAlpha(c, if (current) fillPct + 25 else fillPct), withAlpha(c, if (current) 100 else strokePct))
            if (FishSettings.routeShowLines) {
                prev?.let { line(it, center, hw, withAlpha(c, FishSettings.routeLineOpacity)) }
                if (s.type == Type.PEARL) resolve(s.room, s.landLocal, s.landWorld)?.let { land ->
                    line(center, Vec3.atCenterOf(land), hw / 2, withAlpha(c, FishSettings.routeLineOpacity * 6 / 10))
                    box(AABB(land).deflate(0.3), withAlpha(c, maxOf(fillPct, 30)), 0)
                }
            }
            prev = center
        }
        if (FishSettings.routeLineToNext && mode == Mode.PLAYING) {
            val (i, p) = vis.first()
            val player = Minecraft.getInstance().player
            if (i == progress && player != null && throughWalls(steps[i].type) == noDepth) {
                val eye = RenderUtils.cameraLineStart(1.0)
                line(eye, Vec3.atCenterOf(p), hw / 2, withAlpha(color(steps[i].type), FishSettings.routeLineOpacity))
            }
        }
    }

    private fun renderNoDepth(m: PoseStack, vc: VertexConsumer) {
        val ow = FishSettings.routeOutlineWidth * 0.01
        draw(true, { b, fill, stroke ->
            if ((fill ushr 24) != 0) RenderUtils.renderFilled(m, vc, b, RenderUtils.toFloats(fill))
            if ((stroke ushr 24) != 0) RenderUtils.renderThickOutline(m, vc, b, RenderUtils.toFloats(stroke), ow)
        }, { a, b, hw, argb -> RenderUtils.renderThickLine(m, vc, a, b, hw, RenderUtils.toFloats(argb)) })
    }

    private fun renderGizmo() {
        val ow = FishSettings.routeOutlineWidth * 0.01
        draw(false, { b, fill, stroke ->
            RenderUtils.gizmoBox(b, fill, 0)
            RenderUtils.gizmoThickOutline(b, stroke, ow)
        }, { a, b, hw, argb -> RenderUtils.gizmoThickLine(a, b, hw, argb) })
    }

    private fun labels() {
        if (!FishSettings.routeShowLabels) return
        val scale = FishSettings.routeLabelScale.toFloat()
        for ((i, p) in visible()) {
            val s = steps[i]
            RenderUtils.gizmoText(Component.literal("${i + 1}. ${s.type.label}"), Vec3(p.x + 0.5, p.y + 1.4, p.z + 0.5), scale, withAlpha(color(s.type), 100))
        }
    }

    // ---- commands ----

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> {
        fun run(f: () -> Unit) = Command<FabricClientCommandSource> { f(); 1 }
        fun named(lit: String, f: (String) -> Unit) = ClientCommands.literal(lit)
            .then(ClientCommands.argument("name", StringArgumentType.word()).executes { c -> f(StringArgumentType.getString(c, "name")); 1 })
        return ClientCommands.literal("route")
            .executes(run(::status))
            .then(ClientCommands.literal("record").executes(run(::record)))
            .then(ClientCommands.literal("stop").executes(run(::stop)))
            .then(ClientCommands.literal("play").executes(run(::play)))
            .then(ClientCommands.literal("skip").executes(run { skip(1) }))
            .then(ClientCommands.literal("back").executes(run { skip(-1) }))
            .then(ClientCommands.literal("undo").executes(run(::undo)))
            .then(ClientCommands.literal("clear").executes(run(::clear)))
            .then(ClientCommands.literal("list").executes(run(::list)))
            .then(named("save", ::save))
            .then(named("load", ::load))
    }

    private fun msg(s: String) = FishMsg.send("§d[Route] §r$s")

    @JvmStatic
    fun record() {
        steps.clear(); progress = 0; pendingPearl = null; pendingBreaks.clear(); enteredRoute = false
        mode = Mode.RECORDING
        msg("§aRecording. §7Etherwarps, pearls, superbooms, dungeonbreaker, chests, secrets, items and bats are logged. §f/fm route stop §7when done.")
    }

    @JvmStatic
    fun stop() {
        mode = Mode.IDLE
        val room = routeRoom()
        if (room != null && steps.isNotEmpty()) save(room)
        msg("§eStopped. §7${steps.size} steps" + (if (room != null) ", saved to §f$room§7. It loads whenever you enter that room." else ". §f/fm route save <name> §7to keep it."))
    }

    @JvmStatic
    fun play() {
        if (steps.isEmpty()) { msg("§cNo route loaded."); return }
        progress = 0; mode = Mode.PLAYING; enteredRoute = false
        msg("§aPlaying ${steps.size} steps. §7Completed steps disappear as you do them.")
    }

    @JvmStatic
    fun skip(n: Int) {
        if (mode != Mode.PLAYING) { msg("§cNot playing."); return }
        progress = (progress + n).coerceIn(0, steps.size)
        if (progress >= steps.size) routeRoom()?.let { doneRooms.add(it) }
        msg("§7Now at step §f${progress + 1}§7/${steps.size}")
    }

    @JvmStatic
    fun undo() {
        val s = steps.removeLastOrNull() ?: run { msg("§cNothing to undo."); return }
        progress = progress.coerceAtMost(steps.size)
        dirty = true
        msg("§eRemoved #${steps.size + 1} ${s.type.label}")
    }

    @JvmStatic
    fun clear() {
        steps.clear(); progress = 0; mode = Mode.IDLE; dirty = false
        msg("§eCleared.")
    }

    @JvmStatic
    fun save(name: String) {
        if (steps.isEmpty()) { msg("§cNothing to save."); return }
        try {
            Files.createDirectories(dir)
            val f = dir.resolve(clean(name) + ".json")
            Files.writeString(f, gson.toJson(steps))
            dirty = false
            msg("§aSaved ${steps.size} steps → §f${f.fileName}")
        } catch (t: Throwable) { msg("§cSave failed: ${t.message}") }
    }

    @JvmStatic
    fun load(name: String) { load(name, quiet = false) }

    private fun load(name: String, quiet: Boolean): Boolean {
        try {
            val f = dir.resolve(clean(name) + ".json")
            if (!Files.exists(f)) { msg("§cNo route named ${clean(name)}"); return false }
            val list: List<Step> = gson.fromJson(Files.readString(f), object : TypeToken<List<Step>>() {}.type)
            steps.clear(); steps.addAll(list); progress = 0; mode = Mode.IDLE; liveWorld = false; dirty = false; enteredRoute = false
            if (!quiet) msg("§aLoaded ${steps.size} steps. §f/fm route play §7to follow.")
            return true
        } catch (t: Throwable) { msg("§cLoad failed: ${t.message}"); return false }
    }

    @JvmStatic
    fun list() {
        val names = try {
            if (!Files.isDirectory(dir)) emptyList()
            else Files.list(dir).use { s -> s.map { it.fileName.toString() }.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.toList() }
        } catch (_: Throwable) { emptyList() }
        msg(if (names.isEmpty()) "§7No saved routes." else "§7Routes: §f" + names.joinToString(", "))
    }

    @JvmStatic
    fun status() {
        msg("§7Mode §f${mode.name.lowercase()} §7· ${steps.size} steps" + if (mode == Mode.PLAYING) " · at #${progress + 1}" else "")
    }

    private fun clean(name: String) = name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
}
