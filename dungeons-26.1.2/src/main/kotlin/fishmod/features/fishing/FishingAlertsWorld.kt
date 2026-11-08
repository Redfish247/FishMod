package fishmod.features.fishing

import fishmod.features.fishing.FishingAlertSettings as S
import fishmod.utils.events.FishEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.decoration.ArmorStand

// Armor-stand and hotbar scans for hotspot, deployable and bait alerts. Names and offsets are from Feesh and UNVERIFIED in game.
object FishingAlertsWorld {
    private const val TICK_EVERY = 10
    private var tick = 0

    // Hotspot found: last two ids so a close hotspot does not re-alert
    private val seenHotspots = ArrayDeque<Int>()
    // Hotspot gone: stand near the hook, and when it vanished
    private var trackedId = -1
    private var trackedPerk = ""
    private var trackedPos: net.minecraft.world.phys.Vec3? = null
    private var goneAt = 0L

    private val warnedDeployables = HashSet<Int>()
    private var lastBait = -1
    private var lastBaitName = ""
    private var baitWarned = false

    fun init() {
        FishEvents.worldChange.register { reset(); false }
        FishEvents.serverTick.register {
            if (S.fishingAlerts && ++tick % TICK_EVERY == 0 && anyOn() && Fishing.active()) scan()
            false
        }
    }

    private fun anyOn() = S.alertHotspotFound || S.alertHotspotGone || S.alertDeployable || S.alertBait || (S.shareEnabled && S.shareHotspot)

    private fun reset() {
        seenHotspots.clear(); trackedId = -1; trackedPos = null; goneAt = 0L
        warnedDeployables.clear(); lastBait = -1; baitWarned = false
    }

    private fun scan() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        val pos = player.position()
        val hook = player.fishing
        val wantHot = S.alertHotspotFound || S.alertHotspotGone || (S.shareEnabled && S.shareHotspot)

        var nearHot: ArmorStand? = null
        var nearHotD = Double.MAX_VALUE
        var hookHot: ArmorStand? = null
        var hookHotD = Double.MAX_VALUE
        val stands = ArrayList<ArmorStand>()
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand) continue
            val name = e.customName?.string ?: continue
            if (name.isEmpty()) continue
            stands.add(e)
            if (wantHot && FishingAlertText.isHotspotName(name)) {
                val d = e.position().distanceTo(pos)
                if (d < nearHotD) { nearHotD = d; nearHot = e }
                if (hook != null) {
                    val hd = e.position().distanceTo(hook.position())
                    if (hd < hookHotD) { hookHotD = hd; hookHot = e }
                }
            }
        }

        if (wantHot) {
            val nh = nearHot
            if (nh != null && nearHotD <= 10.0 && nh.id !in seenHotspots) {
                seenHotspots.addLast(nh.id)
                while (seenHotspots.size > 2) seenHotspots.removeFirst()
                val perk = perkBelow(nh, stands)
                if (S.alertHotspotFound) {
                    FishingAlerts.notify(FishingTitles.HOTSPOT, "§6§lHotspot found", "§f$perk", "You found $perk Hotspot.")
                }
                FishingShare.offerHotspot(perk, null)
            }
            trackGone(level, pos, hookHot, hookHotD, stands)
        }
        if (S.alertDeployable) scanDeployables(stands, pos, player.name.string)
        if (S.alertBait) scanBait(player)
    }

    // Perk is the stand directly under the HOTSPOT stand
    internal fun perkBelow(hot: ArmorStand, stands: List<ArmorStand>): String {
        var best: ArmorStand? = null
        var bestD = Double.MAX_VALUE
        for (s in stands) {
            if (s === hot) continue
            val dx = s.x - hot.x; val dz = s.z - hot.z; val dy = hot.y - s.y
            if (dx * dx + dz * dz > 1.0 || dy < 0.0 || dy > 2.0) continue
            if (dy < bestD) { bestD = dy; best = s }
        }
        return best?.customName?.string?.trim().orEmpty().ifEmpty { "Unknown" }
    }

    private fun trackGone(level: net.minecraft.world.level.Level, pos: net.minecraft.world.phys.Vec3,
                          hookHot: ArmorStand?, hookHotD: Double, stands: List<ArmorStand>) {
        if (!S.alertHotspotGone) return
        if (goneAt != 0L) {
            // Wait 150 ms+ so a world change wins over the stand despawn (worldChange resets goneAt)
            if (System.currentTimeMillis() - goneAt < 150) return
            goneAt = 0L
            val p = trackedPos
            if (p != null && p.distanceTo(pos) <= 30.0 && stands.none { it.id == trackedId }) {
                FishingAlerts.notify(FishingTitles.HOTSPOT, "§c§lHotspot is gone", "", "$trackedPerk Hotspot is gone, time to find another one!")
            }
            trackedId = -1; trackedPos = null
            return
        }
        if (trackedId != -1) {
            if (stands.none { it.id == trackedId }) goneAt = System.currentTimeMillis()
            return
        }
        if (hookHot != null && hookHotD <= 5.0) {
            trackedId = hookHot.id
            trackedPos = hookHot.position()
            trackedPerk = perkBelow(hookHot, stands)
        }
    }

    private fun scanDeployables(stands: List<ArmorStand>, pos: net.minecraft.world.phys.Vec3, own: String) {
        val limit = S.alertDeployableSecs.coerceAtLeast(1)
        for (s in stands) {
            val name = s.customName?.string ?: continue
            if (s.id in warnedDeployables) continue
            val t = FishingAlertText.timerDeployable(name)
            if (t != null && s.position().distanceTo(pos) <= 8.0 && t.second <= limit) {
                warnedDeployables.add(s.id)
                expiring(t.first)
                continue
            }
            // Totem: its timer stand is two ids after the "Totem of Corruption" stand and carries "Remaining: ..."
            if (name.startsWith("Owner: ") && name.endsWith(own)) {
                val level = s.level()
                val totemStand = level.getEntity(s.id - 2)?.customName?.string
                val timer = level.getEntity(s.id - 1)?.customName?.string
                if (totemStand == "Totem of Corruption" && timer != null && timer.contains("Remaining: ")) {
                    val secs = FishingAlertText.parseSeconds(timer.substringAfter("Remaining: ")) ?: continue
                    if (secs <= limit) { warnedDeployables.add(s.id); expiring("Totem of Corruption") }
                }
            } else if (name.contains("Spawned by:") && name.endsWith(own)) {
                val next = s.level().getEntity(s.id + 1)?.customName?.string ?: continue
                if (next.startsWith("Black Hole")) {
                    val secs = FishingAlertText.parseSeconds(next.removePrefix("Black Hole")) ?: continue
                    if (secs <= limit) { warnedDeployables.add(s.id); expiring("Black Hole") }
                }
            }
        }
    }

    private fun expiring(name: String) =
        FishingAlerts.notify(FishingTitles.DEPLOYABLE, "§e§l$name expires soon", "", "Your $name expires soon.")

    private fun scanBait(player: net.minecraft.client.player.LocalPlayer) {
        // Needs a rod in the water recently (Feesh: hook submerged within 5 min); approximated by a live hook
        if (player.fishing == null) return
        val stack = player.inventory.getItem(8)
        if (stack.isEmpty) return
        val name = stack.hoverName.string
        if (!name.contains("Bait") && !name.contains("Obfuscated")) return
        val lore = stack.get(DataComponents.LORE)?.lines() ?: return
        var remaining: Int? = null
        for (l in lore) { remaining = FishingAlertText.baitRemaining(l.string); if (remaining != null) break }
        val r = remaining ?: return
        val prevName = lastBaitName
        val prev = lastBait
        lastBait = r; lastBaitName = name
        if (prev == -1) return
        if (name != prevName) { FishingAlerts.notify(FishingTitles.BAIT, "§b§lBait changed", "§f$name", "Bait changed to $name."); baitWarned = false; return }
        // +1 is a Hypixel refund, more than +1 means bait was added
        if (r > prev) { baitWarned = false; return }
        if (r <= S.alertBaitLow && !baitWarned) {
            baitWarned = true
            FishingAlerts.notify(FishingTitles.BAIT, "§c§lOut of bait soon", "§f$r $name", "Out of bait soon ($r left).")
        }
    }
}
