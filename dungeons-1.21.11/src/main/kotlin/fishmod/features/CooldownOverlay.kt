package fishmod.features

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.data.ScoreboardUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.min

/**
 * Per-item ability cooldown overlay. Detects cooldown start via the Hypixel
 * "ability cooldown" sound (Enderman teleport at pitch 0 / volume 8) and renders
 * a countdown bar + number on the held item's slot until the cooldown expires.
 *
 * Hotbar and inventory-GUI slots both render the overlay.
 */
object CooldownOverlay {

    /**
     * Known item-id -> cooldown duration (ms). Values pulled from the Hypixel Skyblock wiki:
     * https://hypixelskyblock.minecraft.wiki/
     */
    private val COOLDOWNS: MutableMap<String, Long> = HashMap()

    init {
        COOLDOWNS["HYPERION"] = 5_000L
        COOLDOWNS["SCYLLA"] = 5_000L
        COOLDOWNS["VALKYRIE"] = 5_000L
        COOLDOWNS["ASTRAEA"] = 5_000L
        COOLDOWNS["SHADOW_FURY"] = 15_000L
        COOLDOWNS["INFINITE_SUPERBOOM_TNT"] = 20_000L
        COOLDOWNS["GIANTS_SWORD"] = 30_000L // Giant's Slam
        COOLDOWNS["ATOMSPLIT_KATANA"] = 4_000L // Soulcry
        COOLDOWNS["ICE_SPRAY_WAND"] = 5_000L
        COOLDOWNS["FIRE_FREEZE_STAFF"] = 10_000L
        COOLDOWNS["GYROKINETIC_WAND"] = 30_000L // Gravity Storm
        COOLDOWNS["RAGNAROCK_AXE"] = 20_000L
        COOLDOWNS["TACTICAL_INSERTION"] = 20_000L
        COOLDOWNS["ROGUE_SWORD"] = 5_000L
        COOLDOWNS["WITHER_CLOAK"] = 10_000L
        COOLDOWNS["DEIFIC_SPADE"] = 1_000L
        COOLDOWNS["WIERDER_TUBA"] = 20_000L
        COOLDOWNS["WIERD_TUBA"] = 20_000L
        COOLDOWNS["FIRE_FREEZE_STAFF"] = 10_000L
    }

    /** itemId -> wall-clock millisecond at which the cooldown ends. */
    private val active: MutableMap<String, Long> = HashMap()

    @JvmField
    var debugDumpSound = false

    // Hypixel mana-cost chat line, e.g. "-300 Mana (Wither Impact)".
    private val MANA_LINE: Pattern = Pattern.compile("-\\s*[\\d,]+\\s*Mana\\s*\\(([^)]+)\\)")
    private val COLOR_STRIP: Pattern = Pattern.compile("§.")

    // Hypixel "[Mage] Cooldown Reduction 49% -> 74%" — exact live CDR from the game.
    private val MAGE_CDR_LINE: Pattern = Pattern.compile("\\[Mage\\] Cooldown Reduction \\d+% -> (\\d+)%")

    @Volatile
    private var liveMageCdrPercent = -1

    // Action-bar mana, e.g. "590/770✎". Used to confirm an ability actually fired (mana spent).
    private val MANA_BAR: Pattern = Pattern.compile("([\\d,]+)/[\\d,]+✎")

    @Volatile
    private var lastMana = -1

    @Volatile
    private var pendingId: String? = null // right-clicked ability awaiting mana-drop confirmation

    @Volatile
    private var pendingAt: Long = 0

    @Volatile
    private var pendingManaBefore = -1

    @JvmStatic
    fun init() {
        // Primary trigger: cooldown sound (Enderman teleport, pitch=0, volume=8).
        Events.ON_SOUND.register { event, volume, pitch ->
            if (!FishSettings.cooldownOverlayEnabled) {
                false
            } else {
                if (pitch == 0.0f && volume == 8.0f && event === SoundEvents.ENTITY_ENDERMAN_TELEPORT) {
                    if (debugDumpSound) {
                        Misc.addChatMessage(Text.literal("§d[fmcd] cooldown sound detected"))
                    }
                    onAbilityFired()
                }
                false
            }
        }

        // Fallback trigger: mana-cost chat line. Some Hypixel abilities suppress the cooldown sound.
        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.cooldownOverlayEnabled) {
                false
            } else {
                val s = COLOR_STRIP.matcher(text!!.string).replaceAll("")
                if (debugDumpSound && s.lowercase().contains("mana")) {
                    Misc.addChatMessage(Text.literal("§d[fmcd] chat: §7$s"))
                }
                val cdrM = MAGE_CDR_LINE.matcher(s)
                if (cdrM.find()) {
                    try {
                        liveMageCdrPercent = cdrM.group(1).toInt()
                    } catch (ignored: NumberFormatException) {
                    }
                    return@register false
                }
                val m = MANA_LINE.matcher(s)
                if (m.find()) {
                    if (debugDumpSound) {
                        Misc.addChatMessage(Text.literal("§d[fmcd] mana line matched: " + m.group(1)))
                    }
                    onAbilityFired()
                }
                false
            }
        }

        // Action-bar mana tracker. A right-click only *arms* a pending ability; the cooldown is
        // registered only once the action-bar mana actually DROPS shortly after. If no mana was
        // spent (off cooldown but you didn't have/use mana, missed cast, etc.) it never registers.
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (!overlay || !FishSettings.cooldownOverlayEnabled) return@register
            val s = COLOR_STRIP.matcher(msg.string).replaceAll("")
            val m = MANA_BAR.matcher(s)
            if (!m.find()) return@register
            val mana: Int
            try {
                mana = m.group(1).replace(",", "").toInt()
            } catch (e: NumberFormatException) {
                return@register
            }
            val pid = pendingId
            if (pid != null && System.currentTimeMillis() - pendingAt < 2000
                && pendingManaBefore >= 0 && mana < pendingManaBefore
            ) {
                if (debugDumpSound) {
                    Misc.addChatMessage(Text.literal("§d[fmcd] mana $pendingManaBefore→$mana confirms $pid"))
                }
                pendingId = null
                onAbilityFired()
            }
            lastMana = mana
        }

        // Right-click trigger — ARMS the pending confirmation only. Cooldown is registered by the
        // mana tracker above when mana actually drops, so a no-mana right-click won't start it.
        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            if (!FishSettings.cooldownOverlayEnabled) return@UseItemCallback ActionResult.PASS
            if (hand != Hand.MAIN_HAND) return@UseItemCallback ActionResult.PASS
            val stack = player.getStackInHand(hand)
            if (stack != null && !stack.isEmpty) {
                val id = ItemUtil.getId(stack)
                if (id != null && COOLDOWNS.containsKey(id)) {
                    pendingId = id
                    pendingAt = System.currentTimeMillis()
                    pendingManaBefore = lastMana
                    if (debugDumpSound) {
                        Misc.addChatMessage(Text.literal("§d[fmcd] armed $id (mana=$lastMana)"))
                    }
                }
            }
            ActionResult.PASS
        })

        // Inventory-GUI slot overlay (chest GUIs, player inventory open, etc.)
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!FishSettings.cooldownOverlayEnabled) return@register
            drawOverlay(ctx, stack, x, y)
        }
    }

    private fun onAbilityFired() {
        val mc = MinecraftClient.getInstance()
        val p = mc.player
        if (p == null || mc.networkHandler == null) return

        val held = p.mainHandStack
        if (held == null || held.isEmpty) return

        val id = ItemUtil.getId(held) ?: return

        val baseCdRaw = COOLDOWNS[id] ?: return

        // --- DECLARE VARIABLES ---
        var mageLvl = 0
        var isMage = false
        val baseCd = baseCdRaw.toDouble()
        var finalCdResult: Double
        val inDungeon = Location.inDungeon()

        // --- TAB LIST SCANNING (Mage detection) ---
        for (entry in mc.networkHandler!!.playerList) {
            if (entry.displayName != null) {
                val line = entry.displayName!!.string

                if (line.contains(p.name.string) && line.contains("Mage")) {
                    isMage = true
                    try {
                        val roman = line.split("Mage ")[1].split(")")[0].trim()
                        mageLvl = decodeRoman(roman)
                    } catch (e: Exception) {
                        mageLvl = ScoreboardUtil.getCurrentClassLevel()
                    }
                }
            }
        }

        // --- HYPERION OVERRIDE ---
        // Hyperion ability cooldown is hardcoded to 5s by Hypixel regardless of CDR.
        finalCdResult = if (id == "HYPERION") {
            baseCd
        } else if (isMage && inDungeon) {
            // --- MAGE REDUCTION CALCULATION ---
            val classReduction: Double = if (liveMageCdrPercent > 0) {
                // Game told us exact CDR via "[Mage] Cooldown Reduction X% -> Y%" chat — use it.
                liveMageCdrPercent / 100.0
            } else {
                // Fallback: level-based estimate (25% -> 70% across Mage 1..50).
                val level = if (mageLvl > 0) min(mageLvl, 50) else 50
                0.25 + (level - 1) * (0.45 / 49.0)
            }
            val multiplier = 1.0 - min(classReduction, 0.75)
            baseCd * multiplier
        } else {
            baseCd
        }

        // --- RAGNAROCK AXE OUTSIDE BUFFER ---
        // If not in a dungeon and item is Ragnarock Axe, add 3000ms (3s)
        if (!inDungeon && id == "RAGNAROCK_AXE") {
            finalCdResult += 3000.0
        }
        // Non-Mage in dungeons: Ragnarock Axe needs a 3s buffer.
        if (inDungeon && !isMage && id == "RAGNAROCK_AXE") {
            finalCdResult += 3000
        }

        if (debugDumpSound) {
            Misc.addChatMessage(
                Text.literal(
                    "§b[fmcd] Final CD: " + String.format("%.1fs", finalCdResult / 1000.0) + (if (inDungeon) " (Dungeon)" else " (Outside)")
                )
            )
        }

        val finalCd = finalCdResult.toLong()
        val now = System.currentTimeMillis()
        val existing = active[id]

        if (existing != null && existing > now) return

        active[id] = now + finalCd
    }

    // Helper to extract level if ScoreboardUtil isn't doing it
    private fun parseLevelFromTab(line: String): Int {
        if (line.contains("XLIX")) return 49
        if (line.contains("L")) return 50
        // You could add a full Roman Numeral parser here if needed,
        // but checking the top levels is usually enough for testing.
        return 0
    }

    // (Ensure there is only ONE getLevelFromXp method below this)

    /**
     * Standard Skyblock Dungeon Level XP Requirements.
     * You can expand this array to include all 50 levels.
     */
    private fun getLevelFromXp(xp: Long): Int {
        val levelXp = longArrayOf(
            0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045,
            4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640,
            135640, 187640, 258640, 356640, 488640, 668640, 911640, 1239640, 1677640, 2262640,
            3037640, 4057640, 5407640, 7157640, 9457640, 12457640, 16357640, 21357640, 27857640, 36357640,
            47357640, 61357640, 79357640, 102357640, 131357640, 168357640, 215357640, 275357640, 351357640, 448357640, 569857640
        )

        for (i in levelXp.indices.reversed()) {
            if (xp >= levelXp[i]) return i
        }
        return 0
    }

    /** Hotbar render hook (called from FishModInit HudRenderCallback). */
    @JvmStatic
    fun renderHotbar(ctx: DrawContext, tickCounter: RenderTickCounter) {
        if (!FishSettings.cooldownOverlayEnabled) return
        val mc = MinecraftClient.getInstance()
        val p: ClientPlayerEntity = mc.player ?: return
        if (mc.world == null) return
        if (mc.options.hudHidden) return
        if (mc.debugHud != null && mc.debugHud.shouldShowDebugHud()) return

        // Sweep expired entries before drawing.
        pruneExpired()
        if (active.isEmpty()) return

        val sw = mc.window.scaledWidth
        val sh = mc.window.scaledHeight
        val hbX = (sw - 182) / 2
        val hbY = sh - 22

        val inv: PlayerInventory = p.inventory
        for (i in 0 until 9) {
            val stack = inv.getStack(i)
            if (stack == null || stack.isEmpty) continue
            val x = hbX + 3 + i * 20
            val y = hbY + 3
            drawOverlay(ctx, stack, x, y)
        }
    }

    /** Debug dump for /fmpet command. */
    @JvmStatic
    fun debugState(): String {
        val sb = StringBuilder()
        sb.append("cooldownOverlayEnabled=").append(FishSettings.cooldownOverlayEnabled).append(" | active=")
        if (active.isEmpty()) {
            sb.append("(none)")
        } else {
            val now = System.currentTimeMillis()
            for (e in active.entries) {
                sb.append(e.key).append("(").append(Math.max(0, e.value - now)).append("ms) ")
            }
        }
        val mc = MinecraftClient.getInstance()
        if (mc.player != null) {
            val held = mc.player!!.mainHandStack
            sb.append(" | heldId=").append(if (held == null || held.isEmpty) "none" else ItemUtil.getId(held))
        }
        return sb.toString()
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val it = active.entries.iterator()
        while (it.hasNext()) if (it.next().value <= now) it.remove()
    }

    private fun drawOverlay(ctx: DrawContext, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty) return
        val id = ItemUtil.getId(stack) ?: return
        val endAt = active[id] ?: return
        val now = System.currentTimeMillis()
        val remaining = endAt - now
        if (remaining <= 0) return
        val total = COOLDOWNS[id]
        if (total == null || total <= 0) return

        // "Only show when < 3s left" option — gate text + bar visibility.
        val inFocusWindow = remaining < 3_000L
        if (FishSettings.cooldownOnlyUnder3s && !inFocusWindow) return

        val secs = remaining / 1000.0
        val text = if (secs >= 10) ceil(secs).toInt().toString() else String.format("%.1f", secs)

        if (FishSettings.cooldownShowText) {
            val mc = MinecraftClient.getInstance()
            val tx = x + 16 - mc.textRenderer.getWidth(text)
            val ty = y + 8 - mc.textRenderer.fontHeight / 2 + 1
            // Draw text on top of items (use 200 z-offset to clear item shading).
            ctx.matrices.pushMatrix()
            ctx.matrices.translate(0f, 0f)
            ctx.drawText(mc.textRenderer, text, tx, ty, 0xFFFFFFFF.toInt(), true)
            ctx.matrices.popMatrix()
        }
    }

    private fun decodeRoman(roman: String?): Int {
        if (roman == null) return 0
        val r = roman.uppercase()
        if (r.equals("L", ignoreCase = true)) return 50
        if (r.equals("XLIX", ignoreCase = true)) return 49
        if (r.equals("XLVIII", ignoreCase = true)) return 48
        if (r.equals("XLVII", ignoreCase = true)) return 47
        if (r.equals("XLVI", ignoreCase = true)) return 46
        if (r.equals("XLV", ignoreCase = true)) return 45

        // Fallback for numeric strings if it's already a number
        return try {
            r.replace(Regex("[^0-9]"), "").toInt()
        } catch (e: Exception) {
            0
        }
    }
}
