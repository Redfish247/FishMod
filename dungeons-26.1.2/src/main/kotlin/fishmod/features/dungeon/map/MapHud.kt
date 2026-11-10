package fishmod.features.dungeon.map
import fishmod.compat.hudHidden

import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object MapHud {

    @JvmStatic
    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(ClientLevelEvents.AfterClientLevelChange { _, _ -> DungeonMap.reset() })

        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_map_hud")) { g, _ ->
            val mc = Minecraft.getInstance()
            if (!fishmod.features.FishHudEditor.isOpen() && DungeonMapSettings.mapEnabled && !mc.hudHidden && !mc.options.keyPlayerList.isDown && DungeonState.isInDungeon() &&
                !fishmod.features.dungeon.LeapMenu.isOverlayOpen() &&
                !DungeonState.isInBoss()
            ) {
                try {
                    renderAt(g, mc, DungeonMapSettings.mapX, DungeonMapSettings.mapY, DungeonMapSettings.mapScale, false)
                } catch (e: Exception) {
                    FishDiag.fail("MapHud.1", "dungeon map HUD render failed rooms=${Scan.rooms.size} doors=${Scan.doors.size} size=${DungeonMap.getMapSize()}", e)
                }
            }
        }
    }

    @JvmStatic
    fun renderAt(g: GuiGraphicsExtractor, mc: Minecraft, x: Float, y: Float, scale: Float, edit: Boolean) {
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        drawMap(g, mc, null, edit)
        pose.popMatrix()
    }

    private class ExampleMap(val rooms: List<Room>, val doors: List<Door>, val size: MapVec2i)

    // Example: a fake 6x6 dungeon (real room names) drawn by the real room/door/background code
    private fun exampleMap(): ExampleMap {
        class R(val type: Room.Type, val shape: Room.Shape, val name: String, val secrets: Int, val state: Room.State, val cells: List<Pair<Int, Int>>)
        val specs = listOf(
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Andesite", 2, Room.State.GREEN, listOf(0 to 0)),
            R(Room.Type.PUZZLE, Room.Shape.S1x1, "Boulder", 0, Room.State.DISCOVERED, listOf(1 to 0)),
            R(Room.Type.NORMAL, Room.Shape.S2x1, "Balcony", 4, Room.State.CLEARED, listOf(2 to 0, 3 to 0)),
            R(Room.Type.RARE, Room.Shape.S1x1, "Lava Pit", 3, Room.State.DISCOVERED, listOf(4 to 0)),
            R(Room.Type.BLOOD, Room.Shape.S1x1, "Blood", 0, Room.State.DISCOVERED, listOf(5 to 0)),
            R(Room.Type.NORMAL, Room.Shape.S2x2, "Atlas", 6, Room.State.CLEARED, listOf(0 to 1, 1 to 1, 0 to 2, 1 to 2)),
            R(Room.Type.TRAP, Room.Shape.S1x1, "New Trap", 3, Room.State.DISCOVERED, listOf(2 to 1)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Basement", 1, Room.State.GREEN, listOf(3 to 1)),
            R(Room.Type.CHAMPION, Room.Shape.S1x1, "Dragon", 0, Room.State.CLEARED, listOf(4 to 1)),
            R(Room.Type.NORMAL, Room.Shape.S2x1, "Crypt", 5, Room.State.CLEARED, listOf(5 to 1, 5 to 2)),
            R(Room.Type.FAIRY, Room.Shape.S1x1, "Fairy", 0, Room.State.DISCOVERED, listOf(2 to 2)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Banners", 1, Room.State.DISCOVERED, listOf(3 to 2)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Beams", 2, Room.State.CLEARED, listOf(4 to 2)),
            R(Room.Type.NORMAL, Room.Shape.S3x1, "Slime", 5, Room.State.GREEN, listOf(0 to 3, 0 to 4, 0 to 5)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Admin", 0, Room.State.CLEARED, listOf(1 to 3)),
            R(Room.Type.NORMAL, Room.Shape.S2x1, "Doors", 5, Room.State.DISCOVERED, listOf(2 to 3, 3 to 3)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Cage", 1, Room.State.CLEARED, listOf(4 to 3)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Chambers", 2, Room.State.DISCOVERED, listOf(5 to 3)),
            R(Room.Type.NORMAL, Room.Shape.S4x1, "Hallway", 3, Room.State.CLEARED, listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4)),
            R(Room.Type.NORMAL, Room.Shape.S1x1, "Dome", 1, Room.State.DISCOVERED, listOf(5 to 4)),
            R(Room.Type.ENTRANCE, Room.Shape.S1x1, "Entrance", 0, Room.State.DISCOVERED, listOf(1 to 5)),
            R(Room.Type.NORMAL, Room.Shape.S2x1, "Gold", 1, Room.State.CLEARED, listOf(2 to 5, 3 to 5)),
            R(Room.Type.NORMAL, Room.Shape.S2x1, "Lava", 2, Room.State.DISCOVERED, listOf(4 to 5, 5 to 5)),
        )
        val owner = HashMap<Pair<Int, Int>, Room.Tile>()
        val rooms = specs.map { s ->
            val room = Room(s.type, s.shape, RoomData.example(s.name, s.type, s.shape, s.secrets), 0)
            room.state = s.state
            room.secretsFound = if (s.state == Room.State.GREEN) s.secrets else s.secrets / 2
            for (c in s.cells) {
                val t = Room.Tile(room, MapVec2i(c.first * 32 - 185, c.second * 32 - 185))
                room.tiles.add(t)
                owner[c] = t
            }
            room
        }
        val doors = ArrayList<Door>()
        for (c in 0 until 6) for (r in 0 until 6) {
            val a = owner[c to r] ?: continue
            for ((dc, dr) in listOf(1 to 0, 0 to 1)) {
                val b = owner[c + dc to r + dr] ?: continue
                if (a.owner === b.owner || (c * 7 + r * 3 + dc) % 4 == 0) continue
                val pos = MapVec2i((2 * c + dc) * 16 - 185, (2 * r + dr) * 16 - 185)
                val type = if (a.owner?.type == Room.Type.BLOOD || b.owner?.type == Room.Type.BLOOD) Door.Type.BLOOD
                    else if (c == 3 && r == 1 && dc == 0) Door.Type.WITHER else Door.Type.NORMAL
                doors.add(Door(pos, type, mutableListOf(a, b)))
            }
        }
        return ExampleMap(rooms, doors, MapVec2i(6, 6))
    }

    fun registerPreview() {
        fishmod.features.HudPreview.register("Dungeon Map") {
            val mc = Minecraft.getInstance()
            val ex = exampleMap()
            fishmod.features.HudExample(baseWidth(mc), baseHeight(mc)) { drawMap(it, mc, ex, false) }
        }
    }

    private fun drawMap(g: GuiGraphicsExtractor, mc: Minecraft, example: ExampleMap?, edit: Boolean) {
        val pose = g.pose()
        val bg = DungeonMapSettings.mapBackgroundSize
        val mapSize = example?.size ?: DungeonMap.calculateMapSize()
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

        if (edit && example == null && Scan.rooms.isEmpty()) {
            val label = "MAP"
            val font = mc.font
            g.centeredText(font, label, roomsX / 2, roomsZ / 2 - font.lineHeight / 2, -1)
        } else {
            val textFactor = 1.0f / DungeonMapSettings.mapTextScaling.coerceAtLeast(0.01f)
            val legit = MapColors.legit()

            val rooms: List<Room> = example?.rooms ?: ArrayList(Scan.rooms)
            val doors: List<Door> = example?.doors ?: ArrayList(Scan.doors)

            for (room in rooms) {
                try { room.render(g) } catch (e: Exception) { FishDiag.fail("MapHud.2", "room render failed '${room.data?.name}' type=${room.type} shape=${room.shape} tiles=${room.tiles.size}", e) }
            }

            for (door in doors) {
                try { if (!legit || door.seen) door.render(g) } catch (e: Exception) { FishDiag.fail("MapHud.3", "door render failed at ${door.pos} type=${door.type}", e) }
            }

            pose.pushMatrix()
            for (room in rooms) {
                if (room.type != Room.Type.ENTRANCE && room.type != Room.Type.BLOOD) {
                    try { room.renderName(g, textFactor) } catch (e: Exception) { FishDiag.fail("MapHud.4", "room name render failed '${room.data?.name}' state=${room.state}", e) }
                }
            }
            pose.popMatrix()

            if (example != null) {
                DungeonPlayers.renderExample(g)
            } else if (!DungeonState.isInBoss()) {
                DungeonPlayers.render(g, DungeonPlayers.shouldRenderNames(mc))
            }
        }

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
