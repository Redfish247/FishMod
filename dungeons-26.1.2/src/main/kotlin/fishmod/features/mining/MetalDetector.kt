package fishmod.features.mining

import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import fishmod.features.mining.MiningSettings as S

object MetalDetector {
    private val TREASURE = Regex("TREASURE: (\\d+\\.?\\d?)m")
    private val KEEPER = Regex("Keeper of (\\w+)")
    private val KEEPER_OFFSET = mapOf("Diamond" to Vec3i(33, 0, 3), "Lapis" to Vec3i(-33, 0, -3), "Emerald" to Vec3i(-3, 0, 33), "Gold" to Vec3i(3, 0, -33))
    private val CHESTS = intArrayOf(
        -38, -22, 26, 38, -22, -26, -40, -22, 18, -41, -20, 22, -5, -21, 16, 40, -22, -30, -42, -20, -28, -43, -22, -40,
        42, -19, -41, 43, -21, -16, -1, -22, -20, 6, -21, 28, 7, -21, 11, 7, -21, 22, -12, -21, -44, 12, -22, 31,
        12, -22, -22, 12, -21, 7, 12, -21, -43, -14, -21, 43, -14, -21, 22, -17, -21, 20, -20, -22, 0, 1, -21, 20,
        19, -22, 29, 20, -22, 0, 20, -21, -26, -23, -22, 40, 22, -21, -14, -24, -22, 12, 23, -22, 26, 23, -22, -39,
        24, -22, 27, 25, -22, 17, 29, -21, -44, -31, -21, -12, -31, -21, -40, 30, -21, -25, -32, -21, -40, -36, -20, 42,
        -37, -21, -14, -37, -21, -22)

    private var center: BlockPos? = null
    private var prevDist = -1.0
    private var prevPos: Vec3? = null
    private var fresh = true
    @Volatile private var spots: List<BlockPos> = emptyList()

    fun init() {
        ClientReceiveMessageEvents.GAME.register { text, overlay ->
            if (!overlay && S.miningMetalDetector && inDivan() && TextStrip.plain(text.string).startsWith("You found")) { fresh = true; spots = emptyList() }
        }
        fishmod.utils.events.FishEvents.worldChange.register { center = null; spots = emptyList(); fresh = true; prevDist = -1.0; prevPos = null; false }
        RenderHooks.GIZMO.register { _ ->
            val s = spots
            if (!S.miningMetalDetector || s.isEmpty() || s.size > 8 || !inDivan()) return@register
            FishDiag.guard("MetalDetector.2", "metal detector render threw") {
                val c = S.miningMetalDetectorColor or 0xFF000000.toInt()
                if (s.size == 1) {
                    val b = s[0].below()
                    FishDraw.gizmoBox(AABB(b), (c and 0x00FFFFFF) or 0x50000000, c, true)
                    FishDraw.gizmoLine(FishDraw.cameraLineStart(1.0), Vec3.atCenterOf(b), c, 2f, true)
                } else for (p in s) FishDraw.gizmoBox(AABB(p), 0, (c and 0x00FFFFFF) or 0xA0000000.toInt(), true)
            }
        }
    }

    private fun inDivan() = SkyblockLocation.`in`(SkyblockLocation.CRYSTAL_HOLLOWS) && Mining.area.contains("Mines of Divan")

    // called from GuiMixin.setOverlayMessage, which sees every action bar source
    @JvmStatic
    fun onActionBar(text: Component) { FishDiag.guard("MetalDetector.1", "metal detector action bar threw") { onOverlay(text) } }

    private fun onOverlay(text: Component) {
        if (!S.miningMetalDetector || !inDivan()) return
        val pl = Minecraft.getInstance().player ?: return
        val plain = TextStrip.plain(text.string)
        val dist = TREASURE.find(plain)?.groupValues?.get(1)?.toDoubleOrNull() ?: return
        if (center == null) findCenter()
        val pos = pl.position()
        val before = spots.size
        if (dist == prevDist && pos == prevPos) update(dist, pos)
        if (spots.size != before) FishUtil.addChatMessage(Component.literal(
            if (spots.size == 1) "§aMetal Detector: treasure found." else if (spots.isEmpty()) "§cMetal Detector: no match, move and retry." else "§eMetal Detector: " + spots.size + " possible spots"))
        prevDist = dist; prevPos = pos
    }

    private fun update(d: Double, p: Vec3) {
        if (fresh) {
            fresh = false
            val out = ArrayList<BlockPos>()
            val c = center
            if (c != null) {
                for (i in CHESTS.indices step 3) {
                    val b = c.offset(CHESTS[i], CHESTS[i + 1] + 1, CHESTS[i + 2])
                    if (abs(p.distanceTo(Vec3.atLowerCornerOf(b)) - d) < 0.25) out += b
                }
            } else {
                val r = d.toInt()
                for (x in -r..r) for (z in -r..r) {
                    val b = BlockPos(p.x.toInt() + x, p.y.toInt(), p.z.toInt() + z)
                    if (abs(p.distanceTo(Vec3.atLowerCornerOf(b)) - d) < 0.25) out += b
                }
            }
            spots = out
        } else spots = spots.filter { abs(p.distanceTo(Vec3.atLowerCornerOf(it)) - d) < 0.25 }
        if (spots.isEmpty()) fresh = true
    }

    private fun findCenter() {
        val mc = Minecraft.getInstance()
        val pl = mc.player ?: return
        val level = mc.level ?: return
        for (s in level.getEntitiesOfClass(ArmorStand::class.java, pl.boundingBox.inflate(500.0)) { it.hasCustomName() }) {
            val m = KEEPER.matchEntire(TextStrip.plain(s.name.string)) ?: continue
            val off = KEEPER_OFFSET[m.groupValues[1]] ?: continue
            center = s.blockPosition().offset(off)
            return
        }
    }
}
