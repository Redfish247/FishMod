package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/** Draws the dungeon room-grid map HUD: background, rooms, doors, room names/state icons, and teammate heads. */
object MapHud {

    @JvmStatic
    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(ClientLevelEvents.AfterClientLevelChange { _, _ -> DungeonMap.reset() })

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_map_hud")) { g, _ ->
            val mc = Minecraft.getInstance()
            if (DungeonMapSettings.mapEnabled && !mc.options.hideGui && !mc.options.keyPlayerList.isDown && DungeonState.isInDungeon() &&
                !fishmod.features.dungeon.LeapMenu.isOverlayOpen() &&
                (!DungeonState.isInBoss() || MapColors.peeking())
            ) {
                renderAt(g, mc, DungeonMapSettings.mapX, DungeonMapSettings.mapY, DungeonMapSettings.mapScale, false)
            }
        }
    }

    @JvmStatic
    fun renderForEdit(g: GuiGraphicsExtractor, mc: Minecraft) {
        renderAt(g, mc, DungeonMapSettings.mapX, DungeonMapSettings.mapY, DungeonMapSettings.mapScale, true)
    }

    /** Public so the Leap menu's map view can place the map at its own position/scale. */
    @JvmStatic
    fun renderAt(g: GuiGraphicsExtractor, mc: Minecraft, x: Float, y: Float, scale: Float, edit: Boolean) {
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)

        val bg = DungeonMapSettings.mapBackgroundSize
        val mapSize = DungeonMap.calculateMapSize()
        val roomsX = mapSize.x * 16 + (mapSize.x - 1) * 4
        val roomsZ = mapSize.z * 16 + (mapSize.z - 1) * 4
        val offset = bg.toInt() * 2
        val bgW = roomsX + offset
        val bgH = roomsZ + offset

        val imgSel = DungeonMapSettings.mapImageSelection
        val imgId = if (imgSel.isNotEmpty() && imgSel != "No image") MapImageLoader.getImageId(imgSel) else null
        if (imgId != null) {
            val alpha = DungeonMapSettings.mapImageAlpha.coerceIn(0, 255)
            val tint = (alpha shl 24) or 0xFFFFFF
            g.blit(RenderPipelines.GUI_TEXTURED, imgId, 0, 0, 0.0f, 0.0f, bgW, bgH, bgW, bgH, tint)
        } else {
            g.fill(0, 0, bgW, bgH, DungeonMapSettings.mapBackgroundColor)
        }

        pose.pushMatrix()
        pose.translate(bg, bg)

        if (edit && Scan.rooms.isEmpty()) {
            val label = "MAP"
            val font = mc.font
            g.centeredText(font, label, roomsX / 2, roomsZ / 2 - font.lineHeight / 2, -1)
        } else {
            val textFactor = 1.0f / DungeonMapSettings.mapTextScaling.coerceAtLeast(0.01f)
            val legit = MapColors.legit()

            for (room in ArrayList(Scan.rooms)) {
                room.render(g)
            }

            for (door in ArrayList(Scan.doors)) {
                if (!legit || door.seen) door.render(g)
            }

            pose.pushMatrix()
            for (room in ArrayList(Scan.rooms)) {
                if (room.type != Room.Type.ENTRANCE && room.type != Room.Type.BLOOD) {
                    room.renderName(g, textFactor)
                }
            }
            pose.popMatrix()

            if (!DungeonState.isInBoss()) {
                DungeonPlayers.render(g, DungeonPlayers.shouldRenderNames(mc))
            }
        }

        pose.popMatrix()
        pose.popMatrix()
    }

    @JvmStatic
    fun tiedAnchor(): FloatArray {
        val bg = DungeonMapSettings.mapBackgroundSize
        val mapSize = DungeonMap.calculateMapSize()
        val roomsX = mapSize.x * 16 + (mapSize.x - 1) * 4
        val roomsZ = mapSize.z * 16 + (mapSize.z - 1) * 4
        val bgW = roomsX + bg * 2.0f
        val bgH = roomsZ + bg * 2.0f
        val scale = DungeonMapSettings.mapScale
        val cx = DungeonMapSettings.mapX + bgW / 2.0f * scale
        val topY = DungeonMapSettings.mapY + (bgH + 3.0f) * scale
        return floatArrayOf(cx, topY)
    }

    @JvmStatic
    fun baseWidth(mc: Minecraft): Int = 116 + DungeonMapSettings.mapBackgroundSize.toInt() * 2

    @JvmStatic
    fun baseHeight(mc: Minecraft): Int = 116 + DungeonMapSettings.mapBackgroundSize.toInt() * 2
}
