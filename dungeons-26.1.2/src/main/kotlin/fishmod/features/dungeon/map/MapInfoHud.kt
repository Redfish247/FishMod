package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

/** Secondary HUD line(s): secrets/score/deaths/mimic/prince/crypts readout, optionally anchored under [MapHud]. */
object MapInfoHud {

    @JvmStatic
    fun enabled(): Boolean = DungeonMapSettings.mapInfoEnabled == true

    @JvmStatic
    fun register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_map_info_hud")) { g, _ ->
            if (enabled()) {
                val mc = Minecraft.getInstance()
                if (!mc.options.hideGui && DungeonState.isInDungeon()) {
                    val allowed = if (DungeonMapSettings.mapInfoMapTied) {
                        DungeonMapSettings.mapEnabled && (!DungeonState.isInBoss() || MapColors.peeking())
                    } else {
                        !(DungeonState.isInBoss() && DungeonMapSettings.mapScoreStandaloneHideInBoss && !MapColors.peeking())
                    }
                    if (allowed) render(g, mc, false)
                }
            }
        }
    }

    @JvmStatic
    fun renderForEdit(g: GuiGraphicsExtractor, mc: Minecraft) {
        render(g, mc, true)
    }

    private fun render(g: GuiGraphicsExtractor, mc: Minecraft, edit: Boolean) {
        val example = edit && Scan.rooms.isEmpty()
        val l1 = line1(example)
        val l2 = line2(example)
        if (l1.isEmpty() && l2.isEmpty()) return

        val s = DungeonMapSettings.mapInfoScale.coerceIn(0.5f, 20.0f)
        val lh = mc.font.lineHeight
        val pose = g.pose()

        if (DungeonMapSettings.mapInfoMapTied) {
            val a = MapHud.tiedAnchor()
            pose.pushMatrix()
            pose.translate(a[0], a[1])
            pose.scale(s, s)
            drawLines(g, mc, l1, l2, 0, lh)
            pose.popMatrix()
        } else {
            val blockW = maxOf(mc.font.width(l1), mc.font.width(l2))
            pose.pushMatrix()
            pose.translate(DungeonMapSettings.mapInfoX, DungeonMapSettings.mapInfoY)
            pose.scale(s, s)
            drawLines(g, mc, l1, l2, blockW / 2, lh)
            pose.popMatrix()
        }
    }

    private fun drawLines(g: GuiGraphicsExtractor, mc: Minecraft, l1: String, l2: String, cx: Int, lh: Int) {
        if (l1.isNotEmpty() && l2.isNotEmpty()) {
            g.centeredText(mc.font, l1, cx, 0, -1)
            g.centeredText(mc.font, l2, cx, lh + 1, -1)
        } else {
            g.centeredText(mc.font, l1.ifEmpty { l2 }, cx, 0, -1)
        }
    }

    private fun noWords(): Boolean = DungeonMapSettings.mapInfoNoWords

    private fun join(vararg parts: String): String {
        val sb = StringBuilder()
        for (p in parts) {
            if (p.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("   ")
                sb.append(p)
            }
        }
        return sb.toString()
    }

    private fun line1(example: Boolean): String = join(secretsPiece(example), scorePiece(example))

    private fun line2(example: Boolean): String =
        join(deathsPiece(example), mimicPiece(example), princePiece(example), cryptsPiece(example))

    private fun secretsPiece(example: Boolean): String {
        if (!DungeonMapSettings.mapInfoShowSecrets) return ""
        val s: String
        if (example) {
            s = "§b0§7-§e0§7-§c0"
        } else {
            val needed = DungeonScore.calculateMinimumSecrets(false, false)
            val tail = if (DungeonMapSettings.mapInfoShowLeft)
                Math.max(0, 300 - DungeonScore.projectedFullClearScore())
            else
                DungeonScore.calculateTotalSecrets()
            s = "§b${DungeonScore.secretsFound}§7-§e$needed§7-§c$tail"
        }
        return if (noWords()) s else "§fSecrets: $s"
    }

    private fun scorePiece(example: Boolean): String {
        if (!DungeonMapSettings.mapInfoShowScore) return ""
        val s: String
        if (example) {
            s = "§c123"
        } else {
            val sc = DungeonScore.score
            val color = if (sc < 270) "§c" else if (sc < 300) "§e" else "§a"
            s = "$color$sc"
        }
        val prefix = if (noWords()) "" else "§fScore: "
        return prefix + s
    }

    private fun deathsPiece(example: Boolean): String {
        if (!DungeonMapSettings.mapInfoShowDeaths) return ""
        val d = if (example) "§a0" else (if (DungeonScore.deaths > 0) "§c" else "§a") + DungeonScore.deaths
        val prefix = if (noWords()) "§7D: " else "§fDeaths: "
        return prefix + d
    }

    private fun mimicPiece(example: Boolean): String {
        if (!DungeonMapSettings.mapInfoShowMimic) return ""
        if (!example && DungeonMapSettings.mapInfoHideCompleted && DungeonScore.mimicKilled) return ""
        val m = if (example) "§c✖" else if (DungeonScore.mimicKilled) "§a✔" else "§c✖"
        val prefix = if (noWords()) "§7M: " else "§fM: "
        return prefix + m
    }

    private fun princePiece(example: Boolean): String {
        if (!DungeonMapSettings.mapInfoShowPrince) return ""
        if (!example && DungeonMapSettings.mapInfoHideCompleted && DungeonScore.princeKilled) return ""
        val p = if (example) "§c✖" else if (DungeonScore.princeKilled) "§a✔" else "§c✖"
        val prefix = if (noWords()) "§7P: " else "§fP: "
        return prefix + p
    }

    private fun cryptsPiece(example: Boolean): String {
        if (!DungeonMapSettings.mapInfoShowCrypts) return ""
        if (example) return if (noWords()) "§c0§7/§a5" else "§fCrypts: §c0"
        val cr = minOf(DungeonScore.crypts, 5)
        if (DungeonMapSettings.mapInfoHideCompleted && cr >= 5) return ""
        val cc = if (cr >= 5) "§a" else if (cr >= 3) "§e" else "§c"
        return if (noWords()) "$cc$cr§7/§a5" else "§fCrypts: $cc$cr"
    }

    @JvmStatic
    fun baseWidth(mc: Minecraft): Int {
        val example = Scan.rooms.isEmpty()
        return maxOf(mc.font.width(line1(example)), mc.font.width(line2(example)))
    }

    @JvmStatic
    fun baseHeight(mc: Minecraft): Int {
        val example = Scan.rooms.isEmpty()
        val twoLines = line1(example).isNotEmpty() && line2(example).isNotEmpty()
        return if (twoLines) mc.font.lineHeight * 2 + 1 else mc.font.lineHeight
    }
}
