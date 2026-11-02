/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import com.mojang.blaze3d.platform.NativeImage
import fishmod.pv.ProfileViewer
import fishmod.pv.neu.SkyBlockPackCache
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

/**
 * Registers Hypixel SkyBlock item PNGs as dynamic GUI textures
 * (paper items, drills, attuned daggers, animated icons, …).
 */
object SkyBlockItemIconCache {
	private val BY_MODEL = ConcurrentHashMap<String, IconState>()

	@JvmStatic
	fun clear() {
		val ids = BY_MODEL.values.mapNotNull { it.textureId }
		BY_MODEL.clear()
		val client: Minecraft? = Minecraft.getInstance()
		if (client != null && ids.isNotEmpty()) {
			// Free GPU textures; ids are deterministic so a re-request re-registers them.
			client.execute { ids.forEach { client.textureManager.release(it) } }
		}
	}

	/** Kick pack download; icons register as textures become available. */
	@JvmStatic
	fun ensurePack() {
		SkyBlockPackCache.start()
	}

	/**
	 * @param itemModel e.g. `hypixel_skyblock:item/uncategorized/magma_chunk`
	 * @return texture id when loaded, otherwise `null` (request may still be in flight)
	 */
	@JvmStatic
	fun getOrRequest(itemModel: String?): Identifier? {
		if (itemModel.isNullOrBlank()) {
			return null
		}
		val key = itemModel.trim().lowercase(Locale.ROOT)
		if (!key.startsWith("hypixel_skyblock:")) {
			return null
		}
		val state = BY_MODEL.computeIfAbsent(key) { IconState(it) }
		if (state.textureId != null && state.loaded) {
			return state.textureId
		}
		if (state.failed) {
			return null
		}
		tryLoad(state)
		return if (state.loaded) state.textureId else null
	}

	@JvmStatic
	fun textureSize(itemModel: String?): Int {
		if (itemModel == null) {
			return 16
		}
		val state = BY_MODEL[itemModel.trim().lowercase(Locale.ROOT)]
		return if (state != null && state.size > 0) state.size else 16
	}

	private fun tryLoad(state: IconState) {
		if (!state.requested.compareAndSet(false, true)) {
			return
		}
		SkyBlockPackCache.start()
		if (!SkyBlockPackCache.isReady()) {
			// Pack still downloading - allow another attempt later.
			state.requested.set(false)
			return
		}
		val layers = SkyBlockPackCache.resolveTextureLayers(state.model)
		if (layers.isEmpty()) {
			state.failed = true
			return
		}
		val image = try {
			composeLayers(layers)
		} catch (exception: IOException) {
			state.failed = true
			ProfileViewer.LOGGER.warn("[FishPV] Failed reading item icon {}: {}", state.model, exception.toString())
			return
		}
		if (image == null) {
			state.failed = true
			return
		}
		val client: Minecraft? = Minecraft.getInstance()
		if (client == null) {
			image.close()
			state.requested.set(false)
			return
		}
		client.execute {
			try {
				val size = maxOf(image.width, image.height)
				val id = ProfileViewer.id(
					"dynamic_items/" + UUID.nameUUIDFromBytes(state.model.toByteArray(StandardCharsets.UTF_8))
				)
				client.textureManager.register(id, DynamicTexture({ "PV item " + state.model }, image))
				state.size = size
				state.textureId = id
				state.loaded = true
			} catch (exception: Exception) {
				image.close()
				state.failed = true
				ProfileViewer.LOGGER.warn("[FishPV] Failed registering item icon {}: {}", state.model, exception.toString())
			}
		}
	}

	@Throws(IOException::class)
	private fun composeLayers(layers: List<Path>): NativeImage? {
		var composed: NativeImage? = null
		for (layer in layers) {
			val frame = Files.newInputStream(layer).use { firstFrame(NativeImage.read(it)) }
			val target = composed ?: NativeImage(frame.width, frame.height, true).also {
				it.fillRect(0, 0, it.width, it.height, 0)
				composed = it
			}
			blitLayer(target, frame)
			frame.close()
		}
		return composed
	}

	/** Animated item sheets are width×(N·width); keep the first frame for GUI icons. */
	private fun firstFrame(image: NativeImage): NativeImage {
		val width = image.width
		val height = image.height
		if (width <= 0 || height <= 0) {
			return image
		}
		if (height > width && height % width == 0) {
			val frame = NativeImage(width, width, true)
			image.copyRect(frame, 0, 0, 0, 0, width, width, false, false)
			image.close()
			return frame
		}
		if (width > height && width % height == 0) {
			val frame = NativeImage(height, height, true)
			image.copyRect(frame, 0, 0, 0, 0, height, height, false, false)
			image.close()
			return frame
		}
		return image
	}

	private fun blitLayer(target: NativeImage, source: NativeImage) {
		val w = minOf(target.width, source.width)
		val h = minOf(target.height, source.height)
		for (y in 0 until h) {
			for (x in 0 until w) {
				val pixel = source.getPixel(x, y)
				if ((pixel ushr 24) and 0xFF == 0) {
					continue
				}
				target.setPixel(x, y, pixel)
			}
		}
	}

	private class IconState(val model: String) {
		val requested = AtomicBoolean()

		@Volatile
		var textureId: Identifier? = null

		@Volatile
		var loaded = false

		@Volatile
		var failed = false

		@Volatile
		var size = 16
	}
}
