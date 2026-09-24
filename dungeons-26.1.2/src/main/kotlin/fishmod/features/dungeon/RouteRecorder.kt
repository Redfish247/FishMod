package fishmod.features.dungeon

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.features.dungeon.map.Scan
import fishmod.utils.FishMsg
import fishmod.utils.Location
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
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ambient.Bat
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

    enum class Type(val label: String, val argb: Int, val tolerance: Double) {
        ETHERWARP("Etherwarp", 0xFFB45CFF.toInt(), 2.5),
        PEARL("Pearl", 0xFF20C0A0.toInt(), 3.0),
        BREAK("Break", 0xFFFF5555.toInt(), 1.5),
        SUPERBOOM("Superboom", 0xFFFF2020.toInt(), 3.0),
        CHEST("Chest", 0xFFFFAA00.toInt(), 1.5),
        SECRET("Secret", 0xFF55FF55.toInt(), 1.5),
        ITEM("Item", 0xFF55FFFF.toInt(), 3.0),
        BAT("Bat", 0xFFFF55FF.toInt(), 5.0),
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

    private const val BAT_RANGE = 6.0
    private const val ITEM_RANGE = 4.0
    private const val LOOKAHEAD = 4

    private val steps = ArrayList<Step>()
    private var mode = Mode.IDLE
    private var progress = 0
    private var liveWorld = false

    private var lastPos: Vec3? = null
    private var tick = 0L
    private var pendingPearl: Step? = null
    private var pearlTick = 0L
    private val pendingBreaks = HashMap<BlockPos, Long>()

    private val batPos = HashMap<Int, Vec3>()
    private val batEngaged = HashSet<Int>()
    private val itemPos = HashMap<Int, Vec3>()
    private val pickedItemIds = ConcurrentLinkedQueue<Int>()
    private val removedIds = ConcurrentLinkedQueue<Int>()
    @Volatile private var selfId = -1
    @Volatile private var teleported = false

    private var anchorCache: Map<String, DungeonRoomAnchor.Anchor> = emptyMap()
    private var anchorTick = -100L

    private val ETHER_ITEMS = setOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END", "ETHERWARP_CONDUIT")
    private val BOOM_ITEMS = setOf("SUPERBOOM_TNT", "INFINITE_SUPERBOOM_TNT")
    private var lastBoomTick = -100L
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dir get() = FabricLoader.getInstance().configDir.resolve("FishMod").resolve("routes")

    @JvmStatic
    fun init() {
        UseBlockCallback.EVENT.register(UseBlockCallback { _, level, hand, hit ->
            if (hand == InteractionHand.MAIN_HAND && tracking() && Minecraft.getInstance().player?.let { isBoom(it.mainHandItem) } == true) {
                boom(hit.blockPos.immutable())
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
                    ?: BlockPos.containing(player.eyePosition.add(player.lookAngle.scale(3.0))))
            } else if (hand == InteractionHand.MAIN_HAND && tracking() && player.mainHandItem.item == Items.ENDER_PEARL) {
                val step = action(Type.PEARL, player.blockPosition())
                if (step != null) { pendingPearl = step; pearlTick = tick }
            }
            InteractionResult.PASS
        })

        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, _, hand, pos, _ ->
            if (hand == InteractionHand.MAIN_HAND && tracking() && ItemUtil.getId(player.mainHandItem) == "DUNGEONBREAKER") {
                pendingBreaks[pos.immutable()] = tick
            }
            InteractionResult.PASS
        })

        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundTakeItemEntityPacket -> if (packet.playerId == selfId) pickedItemIds.add(packet.itemId)
                is ClientboundRemoveEntitiesPacket -> packet.entityIds.forEach { removedIds.add(it) }
                is ClientboundPlayerPositionPacket -> teleported = true
            }
            false
        }

        Events.ON_WORLD_CHANGE.register {
            liveWorld = false
            lastPos = null; pendingPearl = null; pendingBreaks.clear()
            batPos.clear(); batEngaged.clear(); itemPos.clear(); pickedItemIds.clear(); removedIds.clear()
            anchorCache = emptyMap()
            if (mode == Mode.RECORDING) { mode = Mode.IDLE; msg("§eRecording stopped (world changed). §7${steps.size} steps kept.") }
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })

        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { ctx, m, vc -> render(m, vc, fill = false); lineToNext(ctx, m, vc) }
        RenderingEvents.GIZMO.register { _ -> labels() }
    }

    private fun isBoom(stack: net.minecraft.world.item.ItemStack) = ItemUtil.getId(stack) in BOOM_ITEMS

    // use-on-block and use-item can both fire for one click
    private fun boom(pos: BlockPos) {
        if (tick - lastBoomTick < 5) return
        lastBoomTick = tick
        action(Type.SUPERBOOM, pos)
    }

    private fun tracking() = mode != Mode.IDLE && Location.inDungeon()

    private fun onTick(mc: Minecraft) {
        tick++
        val player = mc.player
        val level = mc.level
        selfId = player?.id ?: -1
        if (player == null || level == null || !tracking()) {
            pickedItemIds.clear(); removedIds.clear(); teleported = false; lastPos = player?.position()
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

        val iter = pendingBreaks.entries.iterator()
        while (iter.hasNext()) {
            val (bpos, t) = iter.next()
            if (level.getBlockState(bpos).isAir) { iter.remove(); action(Type.BREAK, bpos) }
            else if (tick - t > 10) iter.remove()
        }

        val eye = player.eyePosition
        while (true) {
            val id = pickedItemIds.poll() ?: break
            val p = itemPos[id] ?: (level.getEntity(id) as? ItemEntity)?.takeIf { e -> e.isFloorSecret() }?.position() ?: continue
            if (p.distanceToSqr(eye) <= ITEM_RANGE * ITEM_RANGE) action(Type.ITEM, BlockPos.containing(p))
        }
        while (true) {
            val id = removedIds.poll() ?: break
            val engaged = batEngaged.remove(id)
            val p = batPos[id] ?: continue
            if (engaged && p.distanceToSqr(eye) <= BAT_RANGE * BAT_RANGE) action(Type.BAT, BlockPos.containing(p))
        }
        batPos.clear(); itemPos.clear()
        for (e in level.getEntities(player, player.boundingBox.inflate(BAT_RANGE + 2.0))) {
            when (e) {
                is Bat -> {
                    batPos[e.id] = e.position()
                    if (e.hurtTime > 0 || e.deathTime > 0) batEngaged.add(e.id)
                }
                is ItemEntity -> if (e.isFloorSecret()) itemPos[e.id] = e.position()
            }
        }
    }

    private fun ItemEntity.isFloorSecret(): Boolean {
        if (item.item == Items.ARROW || hasPickUpDelay() || age < 10) return false
        val d = deltaMovement
        return onGround() && d.horizontalDistanceSqr() < 0.003 && kotlin.math.abs(d.y) < 0.05
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
        liveWorld = true
        msg("§a+ §f#${steps.size} §7${type.label}" + (anchor?.let { " §8(${it.name})" } ?: " §8(no room anchor)"))
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
            msg(if (progress >= steps.size) "§aRoute complete!" else "§7Done #${i + 1} ${type.label} §8→ §fnext #${progress + 1} ${steps[progress].type.label}")
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
        if (mode == Mode.IDLE && steps.isEmpty()) return emptyList()
        if (!Location.inDungeon()) return emptyList()
        val from = if (mode == Mode.PLAYING) progress else 0
        val out = ArrayList<Pair<Int, BlockPos>>()
        for (i in from until steps.size) resolve(steps[i])?.let { out.add(i to it) }
        return out
    }

    private fun box(p: BlockPos) = AABB(p).inflate(0.002)

    private fun render(m: PoseStack, vc: VertexConsumer, fill: Boolean) {
        val vis = visible()
        if (vis.isEmpty()) return
        var prev: Vec3? = null
        for ((i, p) in vis) {
            val s = steps[i]
            val rgba = RenderUtils.toFloats(s.type.argb)
            val current = mode == Mode.PLAYING && i == progress
            if (fill) {
                RenderUtils.renderFilled(m, vc, box(p), floatArrayOf(rgba[0], rgba[1], rgba[2], if (current) 0.55f else 0.3f))
                val c = Vec3.atCenterOf(p)
                prev?.let { RenderUtils.renderThickLine(m, vc, it, c, 0.04, floatArrayOf(rgba[0], rgba[1], rgba[2], 0.85f)) }
                prev = c
                if (s.type == Type.PEARL) {
                    resolve(s.room, s.landLocal, s.landWorld)?.let { land ->
                        RenderUtils.renderThickLine(m, vc, c, Vec3.atCenterOf(land), 0.02, floatArrayOf(rgba[0], rgba[1], rgba[2], 0.5f))
                        RenderUtils.renderFilled(m, vc, AABB(land).deflate(0.3), floatArrayOf(rgba[0], rgba[1], rgba[2], 0.4f))
                    }
                }
            } else {
                RenderUtils.renderOutline(m, vc, box(p), floatArrayOf(rgba[0], rgba[1], rgba[2], 1f))
            }
        }
    }

    private fun lineToNext(ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext, m: PoseStack, vc: VertexConsumer) {
        if (mode != Mode.PLAYING) return
        val (i, p) = visible().firstOrNull() ?: return
        if (i != progress) return
        RenderUtils.renderLineTo(ctx, m, vc, Vec3.atCenterOf(p), steps[i].type.argb)
    }

    private fun labels() {
        for ((i, p) in visible()) {
            val s = steps[i]
            RenderUtils.gizmoText(Component.literal("${i + 1}. ${s.type.label}"), Vec3(p.x + 0.5, p.y + 1.4, p.z + 0.5), 1.0f, s.type.argb)
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
        steps.clear(); progress = 0; pendingPearl = null; pendingBreaks.clear()
        mode = Mode.RECORDING
        msg("§aRecording. §7Etherwarps, pearls, superbooms, dungeonbreaker, chests, secrets, items and bats are logged. §f/fm route stop §7when done.")
    }

    @JvmStatic
    fun stop() {
        mode = Mode.IDLE
        msg("§eStopped. §7${steps.size} steps. §f/fm route play §7to follow it, §f/fm route save <name> §7to keep it.")
    }

    @JvmStatic
    fun play() {
        if (steps.isEmpty()) { msg("§cNo route loaded."); return }
        progress = 0; mode = Mode.PLAYING
        msg("§aPlaying ${steps.size} steps. §7Completed steps disappear as you do them.")
    }

    @JvmStatic
    fun skip(n: Int) {
        if (mode != Mode.PLAYING) { msg("§cNot playing."); return }
        progress = (progress + n).coerceIn(0, steps.size)
        msg("§7Now at step §f${progress + 1}§7/${steps.size}")
    }

    @JvmStatic
    fun undo() {
        val s = steps.removeLastOrNull() ?: run { msg("§cNothing to undo."); return }
        progress = progress.coerceAtMost(steps.size)
        msg("§eRemoved #${steps.size + 1} ${s.type.label}")
    }

    @JvmStatic
    fun clear() {
        steps.clear(); progress = 0; mode = Mode.IDLE
        msg("§eCleared.")
    }

    @JvmStatic
    fun save(name: String) {
        if (steps.isEmpty()) { msg("§cNothing to save."); return }
        try {
            Files.createDirectories(dir)
            val f = dir.resolve(clean(name) + ".json")
            Files.writeString(f, gson.toJson(steps))
            msg("§aSaved ${steps.size} steps → §f${f.fileName}")
        } catch (t: Throwable) { msg("§cSave failed: ${t.message}") }
    }

    @JvmStatic
    fun load(name: String) {
        try {
            val f = dir.resolve(clean(name) + ".json")
            if (!Files.exists(f)) { msg("§cNo route named ${clean(name)}"); return }
            val list: List<Step> = gson.fromJson(Files.readString(f), object : TypeToken<List<Step>>() {}.type)
            steps.clear(); steps.addAll(list); progress = 0; mode = Mode.IDLE; liveWorld = false
            msg("§aLoaded ${steps.size} steps. §f/fm route play §7to follow.")
        } catch (t: Throwable) { msg("§cLoad failed: ${t.message}") }
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
