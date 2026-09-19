package fishmod.features.slayers

import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3

/**
 * Attack / phase indicators on the slayer boss, for all six slayers — the FishMod take on SkyHanni's
 * per-slayer damage-indicator phase text. Detection mirrors SkyHanni: it reads the armour-stand
 * nametags Hypixel floats near the boss, the boss's `vehicle` (mount) for the laser / mania phases,
 * and the boss health fraction for the phase split.
 *
 * | Slayer     | Cues                                                                    |
 * |------------|-------------------------------------------------------------------------|
 * | Revenant   | `BOOM!` (T5 explosion telegraph)                                        |
 * | Tarantula  | `KILL HATCHLINGS` invuln phase, egg-sac timer                           |
 * | Sven       | `PUPS!` (howl / summon telegraph)                                       |
 * | Voidgloom  | hit phase `N/max Hits`, laser countdown, beacon countdown, phase split  |
 * | Inferno    | Hellion shield + which dagger, Fire Pillar timer, Fire Pits, phase split|
 * | Bloodfiend | `TWINCLAWS`, `STEAK!` / HP-till-steak, Mania Circles countdown           |
 *
 * Rendered as billboarded world text just above the boss (`RenderUtils.gizmoText`), plus optional
 * title warnings for the big one-shot cues. The Bloodfiend path is self-contained (it scans The Rift
 * for the `Bloodfiend` NPC itself) since that slayer isn't in [SlayerType].
 */
object SlayerBossPhases {

    private const val LASER_SECONDS = 8.2
    private const val MANIA_SECONDS = 26.0
    private const val BEACON_SECONDS = 5.0
    private const val TITLE_COOLDOWN_MS = 4_000L

    private val HITS = Regex("(\\d+)\\s+[Hh]its?")
    private val FIRE_PILLAR = Regex("([\\d.]+)s\\b.*?hits?", RegexOption.IGNORE_CASE)
    private val EGG_SAC = Regex("^\\d+s \\d+/\\d+$")
    private val TWINCLAWS = Regex("TWINCLAWS.*?([\\d.]+)s", RegexOption.IGNORE_CASE)
    // "Voidgloom Seraph 4.2M❤" / "Bloodfiend 1,234❤" — Hypixel's boss health nametag (current HP only)
    private val NAMETAG_HP = Regex("([\\d,.]+)\\s*([kKmMbB])?\\s*❤")
    private const val HATCHLINGS_LINE = "You need to kill the Broodfather's hatchlings before it can be damaged again!"

    // computed each client tick, read by the GIZMO render pass
    @Volatile private var show = false
    @Volatile private var line1 = ""
    @Volatile private var line2 = ""
    @Volatile private var ax = 0.0
    @Volatile private var ay = 0.0
    @Volatile private var az = 0.0

    // tarantula invuln latch
    private var hatchlingsActive = false
    private var hatchlingsAnchor: Vec3? = null

    // voidgloom beacon sighting
    private var beaconSeenNanos = 0L
    private var beaconLastSeenMs = 0L

    // boss health, parsed from the ❤ nametag (the entity attribute is capped for Hypixel bosses).
    // max is the running peak since this boss id was first seen.
    private var hpBossId = 0
    private var hpMaxSeen = 0.0

    // blaze fire-pits edge trigger
    private var lastHpFrac = 1.0

    // one-shot title cooldowns, keyed by cue
    private val titleAt = HashMap<String, Long>()

    @JvmStatic fun enabled(): Boolean = FishSettings.slayerPhaseEnabled

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick() })
        Events.ON_GAME_MESSAGE.register { text ->
            if (enabled()) onChat(text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim())
            false
        }
        Events.ON_WORLD_CHANGE.register { hardReset(); false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun hardReset() {
        show = false; line1 = ""; line2 = ""
        hatchlingsActive = false; hatchlingsAnchor = null
        beaconSeenNanos = 0L; beaconLastSeenMs = 0L
        lastHpFrac = 1.0
        hpBossId = 0; hpMaxSeen = 0.0
        titleAt.clear()
    }

    private fun onChat(s: String) {
        if (s == HATCHLINGS_LINE) {
            hatchlingsActive = true
            hatchlingsAnchor = SlayerManager.bossEntity?.position()
        }
    }

    private fun tick() {
        show = false
        var l1 = ""
        var l2 = ""
        if (!enabled()) { line1 = ""; line2 = ""; return }
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null || mc.options.hideGui || !Location.inSkyblock()) {
            line1 = ""; line2 = ""; return
        }

        val combatType = SlayerManager.type
        val combatBoss = SlayerManager.bossEntity
        var boss: LivingEntity? = null

        if (combatType != null && combatBoss != null && combatBoss.isAlive && SlayerManager.isActiveSlayer()) {
            boss = combatBoss
            val r = computeCombat(combatType, combatBoss, SlayerManager.tier)
            l1 = r.first; l2 = r.second
        } else if (Location.`in`(Location.THE_RIFT)) {
            val bf = findBloodfiend(mc)
            if (bf != null) {
                boss = bf
                val r = computeRift(bf)
                l1 = r.first; l2 = r.second
            }
        }

        if (boss != null && (l1.isNotEmpty() || l2.isNotEmpty())) {
            val bb = boss.boundingBox
            ax = (bb.minX + bb.maxX) / 2.0
            ay = bb.maxY + 0.5
            az = (bb.minZ + bb.maxZ) / 2.0
            line1 = l1; line2 = l2
            show = true
        } else {
            line1 = ""; line2 = ""
        }
    }

    // ---------------------------------------------------------------- the five combat slayers

    private fun computeCombat(type: SlayerType, boss: LivingEntity, tier: Int): Pair<String, String> {
        val mc = Minecraft.getInstance()
        val stands = nearbyStandNames(boss, 2.0, 4.0)
        val hp = trackHp(boss.id, stands)          // current HP from the ❤ nametag, 0 if unknown
        val maxHp = hpMaxSeen                       // running peak
        var l1 = ""
        var l2 = ""

        when (type) {
            SlayerType.REVENANT -> {
                if (stands.any { it.contains("Boom!", true) }) { l1 = "§c§lBOOM!"; title("boom", "§c§lBOOM!") }
            }

            SlayerType.SVEN -> {
                if (stands.any { it.contains("Calling the pups", true) }) { l1 = "§e§lPUPS!"; title("pups", "§e§lPUPS!") }
            }

            SlayerType.TARANTULA -> {
                if (hatchlingsActive) {
                    // SkyHanni clears the invuln latch once the boss starts moving again
                    val a = hatchlingsAnchor
                    if (a != null && boss.position().distanceTo(a) > 0.35) {
                        hatchlingsActive = false; hatchlingsAnchor = null
                    } else {
                        l1 = "§e§lKILL HATCHLINGS"; title("hatch", "§e§lKILL HATCHLINGS")
                    }
                }
                val egg = stands.firstOrNull { EGG_SAC.matches(it) }
                if (egg != null && stands.any { it.equals("SHOOT ME!", true) }) l2 = "§eEgg sac: §f$egg"
            }

            SlayerType.VOIDGLOOM -> {
                // laser (boss mounted on a stand): 8.2s minus the mount's age
                val v = boss.vehicle
                if (v != null) {
                    val remain = LASER_SECONDS - v.tickCount * 0.05
                    if (remain > -1.0) l1 = "§bLASER §f${fmt(remain)}s"
                }
                if (l1.isEmpty()) {
                    val hits = stands.firstNotNullOfOrNull { HITS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    if (hits != null) {
                        val max = when (tier) { 1 -> 15; 2 -> 30; 3 -> 60; else -> 100 }
                        l1 = "§d$hits§7/§d$max §7Hits"
                    }
                }
                if (l1.isEmpty()) {
                    val remain = beaconRemaining(mc)
                    if (remain != null) { l1 = "§4§lBEACON §f${fmt(remain.coerceAtLeast(0.0))}s"; title("beacon", "§4§lBEACON") }
                }
                l2 = healthSplit(hp, maxHp, if (tier >= 4) 6 else 3)
            }

            SlayerType.INFERNO -> {
                val pillar = stands.firstNotNullOfOrNull { FIRE_PILLAR.find(it)?.groupValues?.get(1) }
                if (pillar != null) l1 = "§cFIRE PILLAR §f${pillar}s"

                if (l1.isEmpty()) {
                    val shield = HELLION.entries.firstOrNull { sh -> stands.any { it.contains(sh.tag, true) } }
                    if (shield != null) {
                        val stand = stands.first { it.contains(shield.tag, true) }
                        val hits = Regex("\\u2668\\s*(\\d+)").find(stand)?.groupValues?.get(1)
                        l1 = "${shield.color}§l${shield.tag.uppercase()}" + (hits?.let { " §7♨$it" } ?: "")
                        l2 = "§7→ §f${shield.dagger} Dagger"
                    }
                }

                // Fire Pits: T3/T4 health crossing 33% downward
                if (maxHp > 0.0 && hp > 0.0 && tier >= 3) {
                    val frac = hp / maxHp
                    if (lastHpFrac > 0.33 && frac <= 0.33) title("firepits", "§cFIRE PITS!")
                    lastHpFrac = frac
                }
                if (l2.isEmpty()) l2 = healthSplit(hp, maxHp, if (tier >= 3) 3 else 2)
            }
        }
        return l1 to l2
    }

    private enum class HELLION(val tag: String, val color: String, val dagger: String) {
        AURIC("Auric", "§e", "Firedust"),
        ASHEN("Ashen", "§8", "Firedust"),
        SPIRIT("Spirit", "§f", "Twilight"),
        CRYSTAL("Crystal", "§b", "Twilight"),
    }

    // ---------------------------------------------------------------- Bloodfiend (Rift / vampire)

    private fun findBloodfiend(mc: Minecraft): LivingEntity? {
        val self = mc.player ?: return null
        var best: AbstractClientPlayer? = null
        var bestD = 20.0 * 20.0
        for (p in mc.level?.players() ?: emptyList()) {
            if (p === self) continue
            if (p.name.string.trim() != "Bloodfiend") continue
            val d = p.distanceToSqr(self)
            if (d < bestD) { bestD = d; best = p }
        }
        return best
    }

    private fun computeRift(bf: LivingEntity): Pair<String, String> {
        val stands = nearbyStandNames(bf, 3.0, 4.0)
        val hp = trackHp(bf.id, stands)
        val maxHp = hpMaxSeen
        var l1 = ""
        var l2 = ""

        if (stands.any { TWINCLAWS.containsMatchIn(it) }) { l1 = "§6§lTWINCLAWS"; title("twin", "§6§lTWINCLAWS", 5_000L) }

        val v = bf.vehicle
        if (l1.isEmpty() && v != null && v.tickCount > 40) {
            val remain = MANIA_SECONDS - v.tickCount / 20.0
            if (remain > 0.0) l1 = "§bMANIA §f${fmt(remain)}s"
        }

        if (l1.isEmpty() && hp > 0.0 && maxHp > 0.0) {
            val steakAt = maxHp * 0.2
            if (hp <= steakAt) { l1 = "§c§lSTEAK!"; title("steak", "§c§lSTEAK!", 2_000L) }
            else if (hp - steakAt < 300.0) l1 = "§cHP till Steak: §f${(hp - steakAt).toInt()}"
        }

        if (hp > 0.0 && maxHp > 0.0) l2 = "§7${(hp / maxHp * 100.0).toInt()}%"
        return l1 to l2
    }

    // ---------------------------------------------------------------- helpers

    /** Stripped, trimmed custom names of armour stands in a box around [e]. */
    private fun nearbyStandNames(e: LivingEntity, radius: Double, up: Double): List<String> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        val box = e.boundingBox.inflate(radius, up, radius)
        return level.getEntitiesOfClass(ArmorStand::class.java, box) { it.hasCustomName() }
            .mapNotNull { it.customName?.string?.replace(Constants.STRIP_COLOR_REGEX, "")?.trim() }
    }

    /** "1/3".."3/3" — 1 is the first (full-health) phase, [steps] is the last. */
    private fun healthSplit(hp: Double, maxHp: Double, steps: Int): String {
        if (!FishSettings.slayerPhaseHealthSplit || hp <= 0.0 || maxHp <= 0.0 || steps <= 1) return ""
        val step = maxHp / steps
        val phase = (steps - Math.ceil(hp / step).toInt() + 1).coerceIn(1, steps)
        return "§7Phase §e$phase/$steps"
    }

    /** Current HP from the boss's `❤` nametag among [stands]; also updates the running peak
     *  [hpMaxSeen], resetting it when the bound boss id changes. Returns 0 if no `❤` tag is visible. */
    private fun trackHp(bossId: Int, stands: List<String>): Double {
        if (bossId != hpBossId) { hpBossId = bossId; hpMaxSeen = 0.0; lastHpFrac = 1.0 }
        val cur = stands.firstNotNullOfOrNull { name ->
            NAMETAG_HP.find(name)?.let { m ->
                val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let null
                n * when (m.groupValues[2].lowercase()) { "k" -> 1_000.0; "m" -> 1_000_000.0; "b" -> 1_000_000_000.0; else -> 1.0 }
            }
        } ?: 0.0
        if (cur > hpMaxSeen) hpMaxSeen = cur
        return cur
    }

    private fun beaconRemaining(mc: Minecraft): Double? {
        val self = mc.player ?: return null
        val level = mc.level ?: return null
        val box = self.boundingBox.inflate(16.0, 8.0, 16.0)
        val hasBeacon = level.getEntitiesOfClass(ArmorStand::class.java, box) { true }.any { st ->
            val h = st.getItemBySlot(EquipmentSlot.HEAD)
            !h.isEmpty && h.hoverName.string.contains("Beacon", true)
        }
        val now = System.currentTimeMillis()
        if (hasBeacon) {
            if (beaconSeenNanos == 0L) beaconSeenNanos = System.nanoTime()
            beaconLastSeenMs = now
        } else if (beaconSeenNanos != 0L && now - beaconLastSeenMs > 1_500L) {
            beaconSeenNanos = 0L
        }
        if (beaconSeenNanos == 0L) return null
        return BEACON_SECONDS - (System.nanoTime() - beaconSeenNanos) / 1_000_000_000.0
    }

    private fun title(key: String, text: String, cooldownMs: Long = TITLE_COOLDOWN_MS) {
        if (!FishSettings.slayerPhaseTitles) return
        val now = System.currentTimeMillis()
        if (now - (titleAt[key] ?: 0L) < cooldownMs) return
        titleAt[key] = now
        Misc.forceTitle(Component.literal(text), Component.empty(), 1_500)
    }

    private fun fmt(v: Double): String = String.format("%.1f", v)

    // ---------------------------------------------------------------- render

    private fun render() {
        if (!show || !enabled() || !FishSettings.slayerPhaseWorldText) return
        if (line1.isNotEmpty()) RenderUtils.gizmoText(Component.literal(line1), Vec3(ax, ay, az), 1.4f, -0x1)
        if (line2.isNotEmpty()) RenderUtils.gizmoText(Component.literal(line2), Vec3(ax, ay + 0.36, az), 0.95f, -0x1)
    }
}
