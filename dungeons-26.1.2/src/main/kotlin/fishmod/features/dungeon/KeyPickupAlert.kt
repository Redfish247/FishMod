package fishmod.features.dungeon

import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.FishUtil
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.sound.SoundManager
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import java.util.regex.Pattern

object KeyPickupAlert {

    private val WITHER: Pattern = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Wither Key!")
    private val BLOOD: Pattern = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Blood Key!")

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text ->
            if (!DungeonSettings.keyPickupAlert || fishmod.compat.BladeCompat.active || !SkyblockLocation.inDungeon()) return@register false
            val s = TextStrip.plain(text.string).trim()
            try {
                when {
                    s == "A Wither Key was picked up!" || WITHER.matcher(s).matches() ->
                        notify("§8§lWITHER KEY", "§8Wither key picked up", 0.7f, "witherKey")
                    s == "A Blood Key was picked up!" || BLOOD.matcher(s).matches() ->
                        notify("§c§lBLOOD KEY", "§cBlood key picked up", 1.2f, "bloodKey")
                    s.endsWith("has obtained Wither Key!") || s.endsWith("has obtained Blood Key!") ->
                        FishDiag.fail("KeyPickupAlert.1", "key pickup line did not match pattern: '$s'")
                }
            } catch (e: Exception) {
                FishDiag.fail("KeyPickupAlert.2", "key notification failed", e)
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            try { scanKeys() } catch (e: Exception) { FishDiag.fail("KeyPickupAlert.3", "key scan threw", e) }
        }
        RenderHooks.GIZMO.register { _ ->
            try { renderKeys() } catch (e: Exception) { FishDiag.fail("KeyPickupAlert.4", "key highlight render threw", e) }
        }
    }

    private val keys = ArrayList<Pair<ArmorStand, Boolean>>()

    // Dropped keys are armor stands named "Wither Key" / "Blood Key" (Odin KeyHighlight).
    private fun scanKeys() {
        keys.clear()
        if (!FishSettings.keyHighlight || !SkyblockLocation.inDungeon()) return
        val level = Minecraft.getInstance().level ?: return
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            when (TextStrip.plain(e.name.string).trim()) {
                "Wither Key" -> keys += e to true
                "Blood Key" -> keys += e to false
            }
        }
    }

    private fun renderKeys() {
        if (!FishSettings.keyHighlight || keys.isEmpty()) return
        for ((e, wither) in keys) {
            if (e.isRemoved) continue
            val c = if (wither) FishSettings.keyHighlightWitherColor else FishSettings.keyHighlightBloodColor
            val p = e.position()
            val box = AABB(p.x - 0.5, p.y + 1.15, p.z - 0.5, p.x + 0.5, p.y + 2.15, p.z + 0.5)
            FishDraw.gizmoBox(box, (0x40 shl 24) or (c and 0xFFFFFF), c, true)
            if (FishSettings.keyHighlightTracer)
                FishDraw.gizmoLine(FishDraw.cameraLineStart(0.2), box.center, c, 2f, true)
        }
    }

    private fun notify(title: String, chat: String, pitch: Float, key: String) {
        if (FishSettings.keyNotifierTitle)
            FishUtil.forceTitle(Component.literal(title), Component.empty(), FishSettings.keyNotifierDurationMs)
        if (FishSettings.keyNotifierChat) FishUtil.addChatMessage(Component.literal(chat))
        if (FishSettings.keyNotifierSound) {
            SoundManager.play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, pitch, key, 500)
        }
    }
}
