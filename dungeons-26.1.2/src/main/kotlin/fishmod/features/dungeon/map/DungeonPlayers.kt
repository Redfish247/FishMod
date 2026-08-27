package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.saveddata.maps.MapDecoration
import java.util.regex.Pattern

object DungeonPlayers {

    private val TABLIST = Pattern.compile("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")
    private const val SMOOTH_MS = 350L
    private val teammates = ArrayList<DungeonPlayer>()

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun interpolateYaw(start: Float, target: Float, progress: Float): Float {
        var delta = (target - start) % 360.0f
        if (delta > 180.0f) delta -= 360.0f
        if (delta < -180.0f) delta += 360.0f
        return start + delta * progress
    }

    @JvmStatic
    fun reset() {
        teammates.clear()
    }

    @JvmStatic
    fun updateRoster(mc: Minecraft) {
        val conn = mc.connection ?: return
        val ordered = ArrayList(conn.onlinePlayers)
        ordered.sortWith(playerInfoOrder())

        for (info in ordered) {
            val disp = info.tabListDisplayName ?: continue
            val line = stripColors(disp.string)
            val m = TABLIST.matcher(line)
            if (m.find()) {
                val name = m.group(2)
                val clazz = m.group(3)
                var p = find(name)
                if (p == null) {
                    p = DungeonPlayer(name)
                    teammates.add(p)
                }

                p.dead = clazz == "DEAD"
                if (clazz != "DEAD") p.clazz = clazz

                if (p.skin == null) p.skin = info.skin

                p.entity = null
                val level = mc.level
                if (level != null) {
                    for (e in level.players()) {
                        if (e.name.string == name) {
                            p.entity = e
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stripColors(s: String): String = s.replace(Regex("(?i)[&§][0-9a-fk-or]"), "")

    private fun playerInfoOrder(): Comparator<PlayerInfo> =
        compareBy<PlayerInfo> { if (isSpectator(it)) 1 else 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { teamName(it) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.name }

    private fun isSpectator(info: PlayerInfo?): Boolean = info != null && info.gameMode == GameType.SPECTATOR

    private fun teamName(info: PlayerInfo?): String {
        val team = info?.let { Minecraft.getInstance().level?.scoreboard?.getPlayersTeam(it.profile.name) }
        return team?.name ?: ""
    }

    @JvmStatic
    fun updateDecorations(decorations: Map<String, MapDecoration>?) {
        if (decorations.isNullOrEmpty()) return
        val rs = DungeonMap.getRoomSize() ?: return
        val center = DungeonMap.getMapCenter() ?: return
        val mc = Minecraft.getInstance()
        val selfName = mc.player?.gameProfile?.name
        var self: DungeonPlayer? = null
        val living = ArrayList<DungeonPlayer>()

        for (p in teammates) {
            if (selfName != null && selfName == p.name) {
                self = p
            } else if (!p.isDead()) {
                living.add(p)
            }
        }

        val scale = 32.0 / ((rs + 4.0) * 2.0)
        var cursor = 0

        for (decor in decorations.values) {
            val frame = decor.getSpriteLocation().path.contains("frame")
            val target: DungeonPlayer? = if (frame) self else if (cursor < living.size) living[cursor++] else null

            if (target != null) {
                val worldX = (center.x + decor.x() * scale).toFloat()
                val worldZ = (center.z + decor.y() * scale).toFloat()
                val mx = ((worldX + 201.0f) / 1.6f)
                val mz = ((worldZ + 201.0f) / 1.6f)
                val markerYaw = decor.rot() * 360.0f / 16.0f
                target.setMapTarget(mx, mz, markerYaw)
            }
        }
    }

    @JvmStatic
    fun render(g: GuiGraphicsExtractor, renderNames: Boolean) {
        val mc = Minecraft.getInstance()
        val matrices = g.pose()
        val list = ArrayList(teammates)

        for (p in list) p.tickAnimation()

        val ownLast = DungeonMapSettings.mapPlayerHeadDrawOwnLast
        if (ownLast) {
            for (p in list) if (!isSelf(mc, p)) renderHead(g, matrices, mc, p, renderNames)
            for (p in list) if (isSelf(mc, p)) renderHead(g, matrices, mc, p, renderNames)
        } else {
            for (p in list) renderHead(g, matrices, mc, p, renderNames)
        }
    }

    private fun isSelf(mc: Minecraft, p: DungeonPlayer): Boolean = mc.player != null && p.entity === mc.player

    private fun renderHead(g: GuiGraphicsExtractor, matrices: org.joml.Matrix3x2fStack, mc: Minecraft, player: DungeonPlayer, renderNames: Boolean) {
        if (player.isDead()) return
        val pos = player.mapRenderPosition()
        matrices.pushMatrix()
        matrices.translate(pos[0] - 2.0f, pos[1] - 2.0f)
        if (renderNames) {
            matrices.pushMatrix()
            matrices.scale(DungeonMapSettings.mapPlayerNamesScaling)
            g.centeredText(mc.font, player.name, 0, 8, DungeonMapSettings.mapPlayerNameColor)
            matrices.popMatrix()
        }

        matrices.rotate(Math.toRadians(180.0 + player.mapRenderYaw().toDouble()).toFloat())
        val self = isSelf(mc, player)
        val bg = if (self) DungeonMapSettings.mapPlayerHeadOwnBackground else DungeonMapSettings.mapPlayerHeadBackground
        val bgAlpha = (bg ushr 24) and 255
        val uglyPointer = DungeonMapSettings.mapPlayerUglyPointer
        if (DungeonMapSettings.mapPlayerHeadBackgroundSize != 0 && bgAlpha != 0 && (!self || !uglyPointer)) {
            val size = 5 + DungeonMapSettings.mapPlayerHeadBackgroundSize
            g.fill(-size, -size, size, size, bg)
        }

        if (self && uglyPointer) {
            g.blit(RenderPipelines.GUI_TEXTURED, MapTextures.SELF_MARKER, -5, -5, 0.0f, 0.0f, 10, 10, 10, 10, -1)
        } else if (player.skin != null) {
            // No PlayerFaceRenderer in this MC version; blit the 8x8 face region directly off the
            // skin's body texture (standard 64x64 skin layout: face at u=8,v=8).
            g.blit(RenderPipelines.GUI_TEXTURED, player.skin!!.body().texturePath(), -4, -4, 8.0f, 8.0f, 8, 8, 64, 64, -1)
        }

        matrices.popMatrix()
    }

    private fun find(name: String): DungeonPlayer? = teammates.firstOrNull { it.name == name }

    /** Public lookup for the Leap Menu (class / skin / dead state by IGN, case-insensitive). */
    @JvmStatic
    fun get(name: String): DungeonPlayer? = teammates.firstOrNull { it.name.equals(name, ignoreCase = true) }

    @JvmStatic
    fun shouldRenderNames(mc: Minecraft): Boolean {
        if (mc.player == null) return false
        if (MapColors.peeking()) return true
        if (isHoldingLeap(mc)) return true
        val screen = mc.screen
        if (screen != null) {
            val title = screen.title.string
            return title == "Spirit Leap" || title == "Teleport to Player"
        }
        return false
    }

    private fun isHoldingLeap(mc: Minecraft): Boolean {
        val stack: ItemStack = mc.player!!.mainHandItem
        val cd = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        val tag: CompoundTag = cd.copyTag()
        var id = tag.getString("id").orElse(null)
        if (id == null) {
            id = tag.getCompound("ExtraAttributes").flatMap { it.getString("id") }.orElse("")
        }
        return id == "SPIRIT_LEAP" || id == "INFINITE_SPIRIT_LEAP"
    }

    class DungeonPlayer(val name: String) {
        var clazz: String = "Unknown"
        var skin: PlayerSkin? = null
        var entity: Player? = null
        var dead: Boolean = false
        private var mapX = 0.0f
        private var mapZ = 0.0f
        private var yaw = 0.0f
        private var startX = 0f
        private var startZ = 0f
        private var startYaw = 0f
        private var targetX = 0f
        private var targetZ = 0f
        private var targetYaw = 0f
        private var animStart = 0L
        private var hasTarget = false

        fun isDead() = dead

        fun setMapTarget(tx: Float, tz: Float, tyaw: Float) {
            if (!hasTarget) {
                mapX = tx; startX = tx; targetX = tx
                mapZ = tz; startZ = tz; targetZ = tz
                yaw = tyaw; startYaw = tyaw; targetYaw = tyaw
                animStart = System.currentTimeMillis()
                hasTarget = true
            } else if (targetX != tx || targetZ != tz || targetYaw != tyaw) {
                startX = mapX
                startZ = mapZ
                startYaw = yaw
                targetX = tx
                targetZ = tz
                targetYaw = tyaw
                animStart = System.currentTimeMillis()
            }
        }

        fun tickAnimation() {
            if (hasTarget) {
                val progress = Math.min(1.0f, (System.currentTimeMillis() - animStart).toFloat() / SMOOTH_MS)
                mapX = lerp(startX, targetX, progress)
                mapZ = lerp(startZ, targetZ, progress)
                yaw = interpolateYaw(startYaw, targetYaw, progress)
            }
        }

        fun mapRenderPosition(): FloatArray {
            val e = entity
            return if (e != null && e.isAlive) {
                val x = ((e.x + 201.0) / 1.6).toFloat()
                val z = ((e.z + 201.0) / 1.6).toFloat()
                floatArrayOf(x, z)
            } else {
                floatArrayOf(mapX, mapZ)
            }
        }

        fun mapRenderYaw(): Float {
            val e = entity
            return if (e != null && e.isAlive) e.yRot else yaw
        }
    }
}
