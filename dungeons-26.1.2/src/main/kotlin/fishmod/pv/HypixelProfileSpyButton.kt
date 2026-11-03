/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv

import java.util.Locale
import kotlin.math.abs
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Injects a command-block "Open Profile Viewer" control into Hypixel's right-click
 * player menu (empty slot under Co-op Request / next to Ender Chest).
 */
object HypixelProfileSpyButton {
	private val TITLE_PROFILE = Regex("^([A-Za-z0-9_]{3,16})'s Profile$")
	private val TITLE_NAME = Regex("^([A-Za-z0-9_]{3,16})$")
	private const val SLOT = 16
	// Built once on first draw instead of every frame.
	private var icon: ItemStack? = null

	private class Target(val player: String, val x: Int, val y: Int)

	@JvmStatic
	fun register() {
		ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
			if (screen !is AbstractContainerScreen<*>) {
				return@register
			}
			if (resolveTarget(screen) == null) {
				return@register
			}
			ScreenMouseEvents.allowMouseClick(screen).register { scr, click ->
				val target = resolveTarget(scr)
				if (target == null || click == null) {
					return@register true
				}
				val mx = click.x()
				val my = click.y()
				if (mx >= target.x && mx < target.x + SLOT && my >= target.y && my < target.y + SLOT) {
					ProfileViewerOpener.open(target.player)
					return@register false
				}
				true
			}
			ScreenEvents.afterExtract(screen).register { scr, graphics, mouseX, mouseY, _ ->
				val target = resolveTarget(scr) ?: return@register
				drawSlot(graphics, scr.font, target, mouseX, mouseY)
			}
		}
	}

	private fun resolveTarget(screen: Screen): Target? {
		if (screen !is AbstractContainerScreen<*>) {
			return null
		}
		var player = playerName(screen)
		val left = intField(screen, "leftPos", "field_2776") ?: return null
		val top = intField(screen, "topPos", "field_2800") ?: return null
		val menu = screen.menu ?: return null
		val slots: List<Slot> = menu.slots
		if (slots.isEmpty()) {
			return null
		}

		var ender: Slot? = null
		var coop: Slot? = null
		var headName: String? = null
		for (slot in slots) {
			if (slot == null || !slot.isActive) {
				continue
			}
			val stack = slot.item
			if (stack == null || stack.isEmpty) {
				continue
			}
			if (stack.`is`(Items.ENDER_CHEST)) {
				ender = slot
			}
			val name = stack.hoverName.string
			if (name.equals("Co-op Request", ignoreCase = true)) {
				coop = slot
			}
			if (headName == null && stack.`is`(Items.PLAYER_HEAD)) {
				TITLE_NAME.matchEntire(name.trim())?.let { headName = it.groupValues[1] }
			}
		}
		if (ender == null && coop == null) {
			return null
		}
		if (player == null) {
			player = headName
		}
		if (player == null) {
			return null
		}

		var empty: Slot? = null
		if (ender != null) {
			empty = findEmptyBeside(slots, ender, 1, 0)
		}
		if (empty == null && coop != null) {
			empty = findEmptyBeside(slots, coop, 0, 1)
		}
		if (empty == null) {
			return null
		}
		return Target(player, left + empty.x, top + empty.y)
	}

	private fun findEmptyBeside(slots: List<Slot?>, origin: Slot, dxSlots: Int, dySlots: Int): Slot? {
		val wantX = origin.x + dxSlots * 18
		val wantY = origin.y + dySlots * 18
		for (slot in slots) {
			if (slot == null || slot === origin || !slot.isActive) {
				continue
			}
			if (slot.x == wantX && slot.y == wantY && slot.item.isEmpty) {
				return slot
			}
		}
		// Fallback: nearest empty slot to the right/below within 2 slot steps.
		var best: Slot? = null
		var bestDist = Int.MAX_VALUE
		for (slot in slots) {
			if (slot == null || slot === origin || !slot.isActive) {
				continue
			}
			if (!slot.item.isEmpty) {
				continue
			}
			val dx = slot.x - origin.x
			val dy = slot.y - origin.y
			if (dxSlots > 0 && (dx <= 0 || dy != 0)) {
				continue
			}
			if (dySlots > 0 && (dy <= 0 || dx != 0)) {
				continue
			}
			val dist = abs(dx) + abs(dy)
			if (dist > 0 && dist < bestDist && dist <= 36) {
				bestDist = dist
				best = slot
			}
		}
		return best
	}

	private fun playerName(screen: Screen): String? {
		val plain = screen.title?.string
		if (plain.isNullOrBlank()) {
			return null
		}
		val trimmed = plain.trim()
		val m = TITLE_PROFILE.matchEntire(trimmed) ?: TITLE_NAME.matchEntire(trimmed) ?: return null
		val name = m.groupValues[1]
		if (name.equals("npc", ignoreCase = true) || name.lowercase(Locale.ROOT).startsWith("cit-")) {
			return null
		}
		return name
	}

	private fun intField(target: Any, vararg names: String): Int? {
		var c: Class<*>? = target.javaClass
		while (c != null && c != Any::class.java) {
			for (name in names) {
				try {
					val f = c.getDeclaredField(name)
					f.isAccessible = true
					val v = f.get(target)
					if (v is Number) {
						return v.toInt()
					}
				} catch (_: ReflectiveOperationException) {
				}
			}
			c = c.superclass
		}
		// Mojmap accessor fallbacks (getLeftPos / leftPos via method).
		for (methodName in arrayOf("getGuiLeft", "getLeftPos")) {
			try {
				val v = target.javaClass.getMethod(methodName).invoke(target)
				if (v is Number) {
					return v.toInt()
				}
			} catch (_: ReflectiveOperationException) {
			}
		}
		return null
	}

	private fun drawSlot(g: GuiGraphicsExtractor, font: Font, target: Target, mouseX: Int, mouseY: Int) {
		val x = target.x
		val y = target.y
		val hover = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT
		val stack = icon ?: ItemStack(Items.COMMAND_BLOCK).also {
			it.set(DataComponents.CUSTOM_NAME, Component.literal("Open Profile Viewer").withColor(0x55FF55))
			icon = it
		}
		g.item(stack, x, y)
		if (hover) {
			val tipX = x + SLOT + 8
			val tipY = y
			val title = "Open Profile Viewer"
			val body = "View " + target.player + "'s profile"
			val tw = maxOf(font.width(title), font.width(body))
			val pad = 3
			val boxW = tw + pad * 2
			val boxH = font.lineHeight * 2 + pad * 2 + 2
			g.fill(tipX - 1, tipY - 1, tipX + boxW + 1, tipY + boxH + 1, 0xFF000000.toInt())
			g.fill(tipX, tipY, tipX + boxW, tipY + boxH, 0xF0101018.toInt())
			g.text(font, Component.literal(title).withColor(0x55FF55), tipX + pad, tipY + pad, 0xFFFFFFFF.toInt(), false)
			g.text(font, Component.literal(body).withColor(0xFFFF55), tipX + pad, tipY + pad + font.lineHeight + 2, 0xFFFFFFFF.toInt(), false)
		}
	}
}
