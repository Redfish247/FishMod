package fishmod.features.kuudra

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.FishMsg
import fishmod.utils.Misc
import fishmod.utils.data.PartyUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.features.kuudra.KuudraSettings as S
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

// Ability announcer, ichor pool circle, rend damage alert, backbone timer and stun waypoints; lines and numbers follow IQ
object KuudraExtras {

    enum class Pod(val label: String, val pos: Vec3) {
        Left("Left Pod", Vec3(-152.5, 27.0, -172.5)),
        Right("Right Pod", Vec3(-167.5, 28.0, -167.5)),
        Back("Back Pod", Vec3(-154.5, 29.0, -156.5))
    }

    private class Spell(val line: String, val name: String, val on: () -> Boolean)
    private class Ichor(val center: Vec3, val until: Long)

    private val SPELLS = listOf(
        Spell("Casting Spell: Spirit Spark!", "Spirit Spark") { S.kuudraAnnounceSpirit },
        Spell("Casting Spell: Hollowed Rush!", "Hollowed Rush") { S.kuudraAnnounceHollowed },
        Spell("Casting Spell: Raging Wind!", "Raging Wind") { S.kuudraAnnounceWind },
        Spell("Casting Spell: Ichor Pool!", "Ichor Pool") { S.kuudraAnnounceIchor }
    )
    private val ICHOR = Regex("(?i).*(?:\\bcasting(?:\\s+spell:)?\\s+ichor\\s+pool!?|\\bichor\\s+pool\\s+casted(?:!|\\b))\\s*(?:at|@)?\\s*\\(?\\s*(-?\\d+)\\s*[, ]+\\s*(-?\\d+)\\s*[, ]+\\s*(-?\\d+)\\s*\\)?.*")
    private val pools = ArrayList<Ichor>()

    private const val REND_MIN = 1666f
    private const val REND_SCALE = 9600f
    private const val HP_CAP = 25_000f
    private var lastHp = HP_CAP - 1f

    private const val BONE_TICKS = 22
    private const val BONE_COOLDOWN = 32
    private const val BONE_BASE_ADVANCE = 2
    private var boneLeft = 0
    private var boneStart = 0
    private var boneCooldown = 0
    private var rendShow = 0

    // Stun state: set by the Human Cannonball purchase, cleared when a pod dies
    private val ENTER = Vec3(-161.0, 49.0, -186.0)
    private var stunPhase = false
    private var eaten = false

    private fun now() = System.currentTimeMillis()

    private fun playerInStomach(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        if (p.y >= 55.0) return false
        val dx = p.x - ENTER.x; val dz = p.z - ENTER.z
        return dx * dx + dz * dz <= 1600.0
    }

    fun init() {
        Events.ON_GAME_MESSAGE.register { c -> if (Kuudra.inKuudra()) FishDiag.guard("KuudraExtras.1", "kuudra extras chat failed") { onChat(Mining.strip(c.string)) }; false }
        Events.ON_WORLD_CHANGE.register { reset(); false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !Kuudra.inKuudra()) return@register
            FishDiag.guard("KuudraExtras.2", "kuudra extras tick failed") { tick(mc) }
        }
        UseItemCallback.EVENT.register(UseItemCallback { player, _, hand ->
            if (hand == InteractionHand.MAIN_HAND && S.kuudraBossMaster && S.kuudraBackbone && Kuudra.inKuudra()) {
                val stack = player.getItemInHand(hand)
                if (!stack.isEmpty && stack.hoverName.string.lowercase().contains("bonemerang") && boneCooldown <= 0) {
                    boneCooldown = BONE_COOLDOWN
                    boneStart = maxOf(1, BONE_TICKS - (BONE_BASE_ADVANCE + S.kuudraBackboneAdvance))
                    boneLeft = boneStart
                }
            }
            InteractionResult.PASS
        })
        MiningHuds.reg("Kuudra Backbone", "kuudra_backbone", 100, 10,
            { S.kuudraBackboneX }, { S.kuudraBackboneX = it }, { S.kuudraBackboneY }, { S.kuudraBackboneY = it },
            { S.kuudraBackboneScale }, { S.kuudraBackboneScale = it }, { S.kuudraBossMaster && S.kuudraBackbone && Kuudra.inKuudra() && (boneLeft > 0 || rendShow > 0) }, { S.kuudraBossMaster }) {
            if (rendShow > 0) listOf("§a§lREND NOW!")
            else {
                val done = ((boneStart - boneLeft) * 20 / maxOf(1, boneStart)).coerceIn(0, 20)
                listOf("§6Backbone §c" + "|".repeat(done) + "§7" + "|".repeat(20 - done))
            }
        }
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (Kuudra.inKuudra()) FishDiag.guard("KuudraExtras.3", "kuudra extras fill render failed") { fills(ps, vc) }
        }
        RenderingEvents.NO_DEPTH_LINE.register { _, ps, vc ->
            if (Kuudra.inKuudra()) FishDiag.guard("KuudraExtras.4", "kuudra extras line render failed") { lines(ps, vc) }
        }
    }

    private fun reset() {
        pools.clear(); lastHp = HP_CAP - 1f; stunPhase = false; eaten = false
        boneLeft = 0; boneCooldown = 0; rendShow = 0
    }

    private fun onChat(msg: String) {
        if (!Kuudra.inKuudra()) return
        if (msg.contains("Sending to server") || (msg.contains("Starting in ") && msg.contains(" seconds"))) pools.clear()
        if (S.kuudraBossMaster && S.kuudraAnnounce) for (sp in SPELLS) if (sp.on() && msg == sp.line) {
            val p = Minecraft.getInstance().player?.blockPosition() ?: return
            val t = if (Kuudra.phase == 4 && Kuudra.phaseStart[3] > 0) " in %.2fs".format((Kuudra.now() - Kuudra.phaseStart[3]) / 1000.0) else ""
            if (PartyUtil.isInParty()) Kuudra.party("${sp.name} Casted at ${p.x}, ${p.y}, ${p.z}$t!")
            break
        }
        if (S.kuudraBossMaster && S.kuudraIchorArea) ICHOR.matchEntire(msg)?.let { m ->
            val v = m.groupValues
            pools.add(Ichor(Vec3(v[1].toDouble() + 0.5, v[2].toDouble() + 0.05, v[3].toDouble() + 0.5), now() + 20_000L))
        }
        if (msg.contains("You purchased Human Cannonball!")) stunPhase = true
        else if (msg.contains("destroyed one of Kuudra's pods!")) { stunPhase = false; eaten = false }
    }

    private fun tick(mc: Minecraft) {
        if (boneCooldown > 0) boneCooldown--
        if (rendShow > 0) rendShow--
        if (boneLeft > 0 && --boneLeft == 0) {
            rendShow = 20
            if (S.kuudraBackboneSound) Misc.sendSound2D(SoundEvents.NOTE_BLOCK_BELL.value(), 2f, 1f)
        }
        if (stunPhase && playerInStomach()) { stunPhase = false; eaten = true }
        rend(mc)
    }

    private fun rend(mc: Minecraft) {
        if (!(S.kuudraBossMaster && S.kuudraRendAlert) || Kuudra.phase != 4) { lastHp = HP_CAP - 1f; return }
        val b = Kuudra.boss
        if ((mc.player?.y ?: return) > 30.0 || b == null || !b.isAlive) return
        val hp = b.health
        val secs = if (Kuudra.phaseStart[3] > 0) (Kuudra.now() - Kuudra.phaseStart[3]) / 1000.0 else 0.0
        if (hp > HP_CAP) return
        if (secs < 1.2) { lastHp = hp; return }
        val diff = maxOf(0f, lastHp - hp)
        if (diff > REND_MIN) {
            val dmg = diff * REND_SCALE
            val col = when { diff <= 4166f -> "§c"; diff <= 7291f -> "§e"; else -> "§a" }
            val txt = when { dmg >= 1_000_000f -> "%.2fM".format(dmg / 1_000_000f); dmg >= 1000f -> "%.2fK".format(dmg / 1000f); else -> "%.0f".format(dmg) }
            FishMsg.send("§fSomeone pulled for $col$txt §fdamage at §a${"%.2f".format(secs)}s§f.")
        }
        lastHp = hp
    }

    private fun podBox(p: Vec3) = AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 1.0, p.z + 0.5)

    // Waypoint stays on the pod while eaten, shifts with you during the stun flight
    private fun stunPos(): Vec3? {
        if (!(S.kuudraBossMaster && S.kuudraStunWaypoints)) return null
        val ph = Kuudra.phase
        if (ph !in 2..4 && !eaten && !stunPhase) return null
        val sel = S.kuudraStunPod.let { n -> Pod.entries.firstOrNull { it.label == n } ?: Pod.Left }.pos
        val p = Minecraft.getInstance().player ?: return null
        return when {
            playerInStomach() || eaten -> sel
            stunPhase -> sel.add(p.position().subtract(ENTER))
            else -> sel
        }
    }

    private fun fills(ps: PoseStack, vc: VertexConsumer) {
        val sp = stunPos()
        if (sp != null && S.kuudraStunStyle != "Outline") RenderUtils.fillBox(ps, vc, podBox(sp), Mining.alpha(S.kuudraStunColor, 25))
    }

    private fun lines(ps: PoseStack, vc: VertexConsumer) {
        val t = now()
        pools.removeAll { it.until <= t }
        if (S.kuudraBossMaster && S.kuudraIchorArea) for (p in pools) {
            val n = 64
            for (i in 0 until n) {
                val a0 = 2 * Math.PI * i / n; val a1 = 2 * Math.PI * (i + 1) / n
                RenderUtils.screenLine(ps, vc, Vec3(p.center.x + 8 * cos(a0), p.center.y, p.center.z + 8 * sin(a0)),
                    Vec3(p.center.x + 8 * cos(a1), p.center.y, p.center.z + 8 * sin(a1)), S.kuudraIchorColor, 2f)
            }
        }
        val sp = stunPos()
        if (sp != null && S.kuudraStunStyle != "Filled") Mining.outline(ps, vc, podBox(sp), S.kuudraStunColor, 2f)
    }
}
