package fishmod.features

import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.data.ScoreboardUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.min

object CooldownOverlay {

    private val COOLDOWNS: MutableMap<String, Long> = HashMap()

    init {
        COOLDOWNS["HYPERION"] = 5_000L
        COOLDOWNS["SCYLLA"] = 5_000L
        COOLDOWNS["VALKYRIE"] = 5_000L
        COOLDOWNS["ASTRAEA"] = 5_000L
        COOLDOWNS["SHADOW_FURY"] = 15_000L
        COOLDOWNS["INFINITE_SUPERBOOM_TNT"] = 20_000L
        COOLDOWNS["GIANTS_SWORD"] = 30_000L
        COOLDOWNS["ATOMSPLIT_KATANA"] = 4_000L
        COOLDOWNS["ICE_SPRAY_WAND"] = 5_000L
        COOLDOWNS["FIRE_FREEZE_STAFF"] = 10_000L
        COOLDOWNS["GYROKINETIC_WAND"] = 30_000L
        COOLDOWNS["RAGNAROCK_AXE"] = 20_000L
        COOLDOWNS["TACTICAL_INSERTION"] = 20_000L
        COOLDOWNS["ROGUE_SWORD"] = 5_000L
        COOLDOWNS["WITHER_CLOAK"] = 10_000L
        COOLDOWNS["DEIFIC_SPADE"] = 1_000L
        COOLDOWNS["WEIRDER_TUBA"] = 20_000L
        COOLDOWNS["WEIRD_TUBA"] = 20_000L
        COOLDOWNS["INFINITE_SPIRIT_LEAP"] = 2_000L
        COOLDOWNS["EXTREMELY_REAL_SHURIKEN"] = 1_000L
        COOLDOWNS["HOTSPLOT_RADAR"] = 2_000L
    }

    private val active: MutableMap<String, Long> = HashMap()

    @JvmField
    var debugDumpSound = false

    private val MANA_LINE: Pattern = Pattern.compile("-\\s*[\\d,]+\\s*Mana\\s*\\(([^)]+)\\)")

    private val MAGE_CDR_LINE: Pattern = Pattern.compile("\\[Mage\\] Cooldown Reduction \\d+% -> (\\d+)%")

    @Volatile
    private var liveMageCdrPercent = -1

    private val MANA_BAR: Pattern = Pattern.compile("([\\d,]+)\\s*/\\s*[\\d,]+\\s*✎")

    @Volatile
    private var lastMana = -1

    @Volatile
    private var pendingId: String? = null

    @Volatile
    private var pendingAt: Long = 0

    @Volatile
    private var pendingManaBefore = -1

    @JvmStatic
    fun init() {
        Events.ON_SOUND.register { event, volume, pitch ->
            if (!FishSettings.cooldownOverlayEnabled) {
                false
            } else {
                if (pitch <= 0.05f && volume >= 3f && event.location == SoundEvents.ENDERMAN_TELEPORT.location) {
                    if (debugDumpSound) {
                        Misc.addChatMessage(Component.literal("§d[fmcd] cooldown sound detected"))
                    }
                    onAbilityFired()
                }
                false
            }
        }

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.cooldownOverlayEnabled) {
                false
            } else {
                val s = HypixelApi.STRIP_COLOR.matcher(text!!.string).replaceAll("")
                if (debugDumpSound && s.lowercase().contains("mana")) {
                    Misc.addChatMessage(Component.literal("§d[fmcd] chat: §7$s"))
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
                        Misc.addChatMessage(Component.literal("§d[fmcd] mana line matched: " + m.group(1)))
                    }
                    onAbilityFired()
                }
                false
            }
        }

        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (!overlay || !FishSettings.cooldownOverlayEnabled) return@register
            val s = HypixelApi.STRIP_COLOR.matcher(msg.string).replaceAll("")
            val m = MANA_BAR.matcher(s)
            if (!m.find()) return@register
            val mana: Int
            try {
                mana = m.group(1).replace(",", "").toInt()
            } catch (e: NumberFormatException) {
                return@register
            }
            val pid = pendingId
            if (pid != null && System.currentTimeMillis() - pendingAt < 2500 &&
                pendingManaBefore >= 0 && mana < pendingManaBefore
            ) {
                if (debugDumpSound) {
                    Misc.addChatMessage(Component.literal("§d[fmcd] mana $pendingManaBefore→$mana confirms $pid"))
                }
                pendingId = null
                onAbilityFired()
            }
            lastMana = mana
        }

        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            if (!FishSettings.cooldownOverlayEnabled) return@UseItemCallback InteractionResult.PASS
            if (hand != InteractionHand.MAIN_HAND) return@UseItemCallback InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (stack != null && !stack.isEmpty) {
                val id = ItemUtil.getId(stack)
                if (id != null && COOLDOWNS.containsKey(id)) {
                    pendingId = id
                    pendingAt = System.currentTimeMillis()
                    pendingManaBefore = lastMana
                    if (debugDumpSound) {
                        Misc.addChatMessage(Component.literal("§d[fmcd] armed $id (mana=$lastMana)"))
                    }
                    if (lastMana < 0) {
                        pendingId = null
                        onAbilityFired()
                    }
                }
            }
            InteractionResult.PASS
        })

        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!FishSettings.cooldownOverlayEnabled) return@register
            drawOverlay(ctx, stack, x, y)
        }
    }

    private fun onAbilityFired() {
        val mc = Minecraft.getInstance()
        val p = mc.player
        if (p == null || mc.connection == null) return

        val held = p.mainHandItem
        if (held == null || held.isEmpty) return

        val id = ItemUtil.getId(held) ?: return

        val baseCdRaw = COOLDOWNS[id] ?: return

        var mageLvl = 0
        var isMage = false
        val baseCd = baseCdRaw.toDouble()
        var finalCdResult: Double
        val inDungeon = Location.inDungeon()

        for (entry in mc.connection!!.onlinePlayers) {
            if (entry.tabListDisplayName != null) {
                val line = entry.tabListDisplayName!!.string

                if (line.contains(p.name.string) && line.contains("Mage")) {
                    isMage = true
                    try {
                        val roman = line.split("Mage ")[1].split(")")[0].trim()
                        mageLvl = fishmod.utils.data.Roman.toInt(roman)
                    } catch (e: Exception) {
                        mageLvl = ScoreboardUtil.getCurrentClassLevel()
                    }
                }
            }
        }

        finalCdResult = if (id == "HYPERION") {
            baseCd
        } else if (isMage && inDungeon) {
            val classReduction: Double = if (liveMageCdrPercent > 0) {
                liveMageCdrPercent / 100.0
            } else {
                val level = if (mageLvl > 0) min(mageLvl, 50) else 50
                0.25 + (level - 1) * (0.45 / 49.0)
            }
            val multiplier = 1.0 - min(classReduction, 0.75)
            baseCd * multiplier
        } else {
            baseCd
        }

        if (!inDungeon && id == "RAGNAROCK_AXE") {
            finalCdResult += 3000.0
        }
        if (inDungeon && !isMage && id == "RAGNAROCK_AXE") {
            finalCdResult += 3000
        }

        if (debugDumpSound) {
            Misc.addChatMessage(
                Component.literal(
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

    @JvmStatic
    fun renderHotbar(ctx: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!FishSettings.cooldownOverlayEnabled) return
        val mc = Minecraft.getInstance()
        val p: LocalPlayer = mc.player ?: return
        if (mc.level == null) return
        if (mc.options.hideGui) return
        if (mc.debugOverlay != null && mc.debugOverlay.showDebugScreen()) return

        pruneExpired()
        if (active.isEmpty()) return

        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val hbX = (sw - 182) / 2
        val hbY = sh - 22

        val inv: Inventory = p.inventory
        for (i in 0 until 9) {
            val stack = inv.getItem(i)
            if (stack == null || stack.isEmpty) continue
            val x = hbX + 3 + i * 20
            val y = hbY + 3
            drawOverlay(ctx, stack, x, y)
        }
    }

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
        val mc = Minecraft.getInstance()
        if (mc.player != null) {
            val held = mc.player!!.mainHandItem
            sb.append(" | heldId=").append(if (held == null || held.isEmpty) "none" else ItemUtil.getId(held))
        }
        return sb.toString()
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val it = active.entries.iterator()
        while (it.hasNext()) if (it.next().value <= now) it.remove()
    }

    private fun drawOverlay(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty) return
        val id = ItemUtil.getId(stack) ?: return
        val endAt = active[id] ?: return
        val now = System.currentTimeMillis()
        val remaining = endAt - now
        if (remaining <= 0) return
        val total = COOLDOWNS[id]
        if (total == null || total <= 0) return

        val inFocusWindow = remaining < 3_000L
        if (FishSettings.cooldownOnlyUnder3s && !inFocusWindow) return

        val secs = remaining / 1000.0
        val text = if (secs >= 10) ceil(secs).toInt().toString() else String.format("%.1f", secs)

        if (FishSettings.cooldownShowText) {
            val mc = Minecraft.getInstance()
            val tx = x + 16 - mc.font.width(text)
            val ty = y + 8 - mc.font.lineHeight / 2 + 1
            ctx.text(mc.font, text, tx, ty, 0xFFFFFFFF.toInt(), true)
        }
    }
}
