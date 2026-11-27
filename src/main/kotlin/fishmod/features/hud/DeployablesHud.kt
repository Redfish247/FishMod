package fishmod.features.hud

import fishmod.features.FishHudEditor
import fishmod.features.HudPreview
import fishmod.features.fishing.FishingAlertText
import fishmod.features.fishing.NamedStands
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.decoration.ArmorStand

/**
 * HUD listing nearby deployables (flares and power/flux orbs) with the time they have left.
 * Orbs carry their timer in the stand name ("Overflux 29s"). Flares do not always, so a flare's
 * timer is read from a neighbouring stand when present, otherwise counted down from first sight.
 */
object DeployablesHud {

    private const val SCAN_EVERY = 5
    private const val FLARE_SECS = 180
    private val ORB_RE = Regex("""^(Radiant|Mana Flux|Overflux|Plasmaflux)(?: Orb)? (\d+[ms\d ]*)$""")
    private val FLARE_RE = Regex("""(Warning|Alert|SOS) Flare""", RegexOption.IGNORE_CASE)
    private val FLARE_TEXTURES = mapOf(
        "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b" to "Warning Flare",
        // SOS Flare (purple cube head): seen at ~10 blocks in both boss fights while Eli's flare was down (2026-11-24, unconfirmed).
        // 4ceb0ed8... was a wrong guess: that head is a pale stacked block, not a flare.
        "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b" to "SOS Flare",
    )

    private class Entry(val key: Int, val name: String, val color: String, var expireAt: Long, var dist: Double, val fromName: Boolean)

    private val entries = HashMap<Int, Entry>()
    private var tick = 0

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            "Deployables",
            { FishSettings.deployablesHudX }, { v -> FishSettings.deployablesHudX = v },
            { FishSettings.deployablesHudY }, { v -> FishSettings.deployablesHudY = v },
            120, 40,
            { FishSettings.deployablesHudScale }, { v -> FishSettings.deployablesHudScale = v },
            { FishSettings.deployablesHudEnabled }
        )
        HudPreview.register("Deployables") { HudPreview.text(sample()) }
        FishEvents.worldChange.register { entries.clear(); false }
        FishEvents.serverTick.register {
            if (FishSettings.deployablesHudEnabled && ++tick % SCAN_EVERY == 0) {
                FishDiag.guard("DeployablesHud.1", "deployables scan failed") { scan() }
            }
            false
        }
    }

    private fun sample() = listOf("§aRadiant §f25s", "§5Plasmaflux §f1m 10s", "§cSOS Flare §f2m 31s")

    private fun colorOf(name: String) = when (name) {
        "Radiant" -> "§a"; "Mana Flux" -> "§9"; "Overflux" -> "§5"; "Plasmaflux" -> "§d"
        "Warning Flare" -> "§e"; "Alert Flare" -> "§6"; "SOS Flare" -> "§c"
        else -> "§f"
    }

    private fun scan() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null || !SkyblockLocation.inSkyblock()) { entries.clear(); return }
        val now = System.currentTimeMillis()
        val range = FishSettings.deployablesHudRange.coerceIn(8, 128).toDouble()
        val pos = player.position()
        val seen = HashSet<Int>()

        for (s in NamedStands.of(level)) {
            val name = s.customName?.string?.trim() ?: continue
            if (name.isEmpty()) continue
            val d = s.position().distanceTo(pos)
            if (d > range) continue

            if (FishSettings.deployablesShowOrbs) {
                val m = ORB_RE.matchEntire(name)
                if (m != null) {
                    val secs = FishingAlertText.parseSeconds(m.groupValues[2]) ?: m.groupValues[2].trim().toIntOrNull() ?: continue
                    put(s.id, m.groupValues[1], now + secs * 1000L, d, true)
                    seen.add(s.id)
                    continue
                }
            }
            if (FishSettings.deployablesShowFlares) {
                val fm = FLARE_RE.find(name) ?: continue
                val flareName = fm.value
                val secs = neighbourTimer(s, level)
                val prev = entries[s.id]
                val expire = when {
                    secs != null -> now + secs * 1000L
                    prev != null -> prev.expireAt
                    else -> now + FLARE_SECS * 1000L
                }
                put(s.id, flareName, expire, d, secs != null)
                seen.add(s.id)
            }
        }

        if (FishSettings.deployablesShowFlares) scanFlareHeads(level, pos, range, now, seen)
        entries.keys.retainAll(seen)
        entries.values.removeAll { it.expireAt <= now }
    }

    private fun scanFlareHeads(level: net.minecraft.client.multiplayer.ClientLevel, pos: net.minecraft.world.phys.Vec3, range: Double, now: Long, seen: HashSet<Int>) {
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || e.id in seen) continue
            val d = e.position().distanceTo(pos)
            if (d > range) continue
            val tex = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .get(net.minecraft.core.component.DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: continue
            val json = runCatching { String(java.util.Base64.getDecoder().decode(tex)) }.getOrNull() ?: continue
            val flare = FLARE_TEXTURES.entries.firstOrNull { json.contains(it.key) }?.value
            if (flare == null) { if (d < 12.0) headDiag(e, json, d); continue }
            val prev = entries[e.id]
            val secs = neighbourTimer(e, level)
            val expire = when {
                secs != null -> now + secs * 1000L
                prev != null -> prev.expireAt
                else -> now + FLARE_SECS * 1000L
            }
            put(e.id, flare, expire, d, secs != null)
            seen.add(e.id)
        }
    }

    private val loggedHeads = HashSet<String>()
    // TEMP (2026-11-24): SOS flare not detected; log head textures of nearby stands so its texture can be added to FLARE_TEXTURES
    private fun headDiag(e: ArmorStand, json: String, d: Double) {
        val hash = Regex("texture/([0-9a-f]+)").find(json)?.groupValues?.get(1) ?: return
        if (loggedHeads.size < 60 && loggedHeads.add(hash)) fishmod.utils.debug.FishDebug.LOGGER.info("[FlareDiag] head texture=$hash stand='${e.customName?.string}' d=${"%.1f".format(d)}")
    }

    private fun put(id: Int, name: String, expireAt: Long, dist: Double, fromName: Boolean) {
        val e = entries[id]
        if (e == null) entries[id] = Entry(id, name, colorOf(name), expireAt, dist, fromName)
        else { e.expireAt = expireAt; e.dist = dist }
    }

    private fun neighbourTimer(s: ArmorStand, level: net.minecraft.client.multiplayer.ClientLevel): Int? {
        for (off in intArrayOf(-1, 1, -2, 2)) {
            val n = level.getEntity(s.id + off) as? ArmorStand ?: continue
            if (n.distanceToSqr(s) > 4.0) continue
            val t = n.customName?.string?.trim() ?: continue
            if (t.isEmpty() || t.length > 12) continue
            if (t.firstOrNull()?.isDigit() == true && t.last() == 's') FishingAlertText.parseSeconds(t)?.let { return it }
        }
        return null
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!FishSettings.deployablesHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null || !SkyblockLocation.inSkyblock()) return
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val out = entries.values.sortedBy { it.expireAt }.map { e ->
            val dist = if (FishSettings.deployablesShowDistance) " §7${e.dist.toInt()}m" else ""
            "${e.color}${e.name} §f${fmt(e.expireAt - now)}$dist"
        }
        if (out.isEmpty()) return
        val sc = FishSettings.deployablesHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(FishSettings.deployablesHudX.toFloat(), FishSettings.deployablesHudY.toFloat())
            ctx.pose().scale(sc, sc)
            HudPreview.drawText(ctx, out)
        } catch (e: Exception) {
            FishDiag.fail("DeployablesHud.2", "deployables hud render failed", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
