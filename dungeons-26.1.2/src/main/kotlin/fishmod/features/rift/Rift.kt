package fishmod.features.rift

import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import fishmod.features.rift.RiftSettings as S

// Shared Rift state + init of every Rift helper
object Rift {

    fun inRift(): Boolean = Location.`in`(Location.THE_RIFT)

    private val KNOWN = listOf(
        "Dreadfarm", "Living Cave", "Living Stillness", "West Village", "Infested House", "Wyld Woods", "Mirrorverse",
        "Black Lagoon", "Stillgore", "Oubliette", "Colosseum", "Mountaintop", "Continuum", "Trial Grounds", "Time-Torn Isles",
        "Wizardman Bureau", "Wizard Brawl", "Walk of Fame", "Time Chamber", "Taylor", "Barry Center", "Village Plaza",
        "Cake House", "Hemovibe", "Broken Cage", "Lonely Island", "Wizard Tower", "Dolphin", "Enigma",
    )
    @Volatile private var cachedArea = ""
    private var tick = 0

    // Sidebar line naming a known Rift area first; the tab widget only says "The Rift" there, so it is a last resort
    private fun pollArea() {
        val lines = fishmod.features.mining.Mining.sidebar
        val hit = lines.firstOrNull { l -> KNOWN.any { l.contains(it, ignoreCase = true) } }
        if (hit != null) { cachedArea = hit.dropWhile { !it.isLetter() }.trim(); return }
        val tab = fishmod.features.mining.Mining.tabLine("Area:")?.removePrefix("Area:")?.trim().orEmpty()
        cachedArea = if (tab.isNotEmpty() && !tab.equals("The Rift", true)) tab else fishmod.features.mining.Mining.area
    }

    // Current Rift area name (e.g. "Dreadfarm")
    fun area(): String = cachedArea

    fun heldId(): String? = Minecraft.getInstance().player?.mainHandItem?.let { fishmod.utils.data.ItemUtil.getId(it) }

    fun inArea(vararg names: String): Boolean = inRift() && names.any { area().contains(it, ignoreCase = true) }

    fun strip(s: String) = s.replace(Constants.STRIP_COLOR_REGEX, "")

    // Named armor stands within r blocks of the player
    fun namedStands(r: Double): List<Pair<ArmorStand, String>> {
        val mc = Minecraft.getInstance()
        val me = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        return level.getEntitiesOfClass(ArmorStand::class.java, AABB(me.blockPosition()).inflate(r))
            .mapNotNull { s -> s.customName?.string?.let { s to strip(it) } }
    }

    // Closest non-stand mob just below a nametag stand
    fun mobUnder(stand: Entity): Entity? {
        val level = Minecraft.getInstance().level ?: return null
        return level.getEntities(stand, stand.boundingBox.inflate(1.0, 3.0, 1.0)) { it !is ArmorStand && it.isAlive }
            .minByOrNull { it.distanceToSqr(stand) }
    }

    private fun say(t: String) = fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal(t))

    // /fm rift debug: local-only state dump
    private fun debugDump() {
        val mc = Minecraft.getInstance(); val me = mc.player ?: return
        say("§6[Rift debug] inRift=${inRift()} area='${area()}'")
        say("§7tab Area: '${fishmod.features.mining.Mining.tabLine("Area:")}' sbArea='${fishmod.features.mining.Mining.area}'")
        say("§7sidebar: " + fishmod.features.mining.Mining.sidebar.joinToString(" | ") { it.take(40) })
        say("§7held id=${heldId()} wand=${RiftDreadfarm.wandHeld()} berberis=${S.riftBerberis} tracked=${RiftDreadfarm.berberisCount()} fireworks(5s)=${RiftDreadfarm.recentParticles()} total=${RiftDreadfarm.particleSeen}")
        val hit = mc.hitResult as? net.minecraft.world.phys.BlockHitResult
        say("§7target block=" + (hit?.let { "${it.blockPos.toShortString()} ${mc.level?.getBlockState(it.blockPos)?.block?.descriptionId}" } ?: "none"))
        val ents = mc.level?.getEntities(me, me.boundingBox.inflate(16.0)) { true }.orEmpty()
        val names = ents.mapNotNull { e -> (e.customName ?: if (e is net.minecraft.world.entity.decoration.ArmorStand) null else e.name)?.string?.let { strip(it) } }
            .filter { it.isNotBlank() }.distinct().take(25)
        say("§7nearby (${names.size}): " + names.joinToString(" | "))
        say("§7" + RiftSnake.debug())
        say("§7bacte cand: " + RiftMobs.bacteDebug.joinToString(" | ").ifEmpty { "none" })
        say("§7crux matched: " + RiftMobs.lastCruxNames.joinToString(" | ").ifEmpty { "none" })
    }

    @JvmStatic
    fun init() {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("fm").then(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("rift").then(
                    net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("debug").executes { debugDump(); 1 })))
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player != null && tick++ % 20 == 0) { if (inRift()) pollArea() else cachedArea = "" }
        }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { cachedArea = ""; false }
        FishDiag.guard("Rift.1", "RiftNametag init failed") { RiftNametag.init() }
        FishDiag.guard("Rift.2", "RiftDreadfarm init failed") { RiftDreadfarm.init() }
        FishDiag.guard("Rift.3", "RiftMobs init failed") { RiftMobs.init() }
        FishDiag.guard("Rift.4", "RiftSnake init failed") { RiftSnake.init() }
        FishDiag.guard("Rift.5", "RiftVampire init failed") { RiftVampire.init() }
        FishDiag.guard("Rift.guide", "RiftGuide init failed") { RiftGuide.init() }
        FishDiag.guard("Rift.6", "RiftParkour init failed") { RiftParkour.init() }
        FishDiag.guard("Rift.H", "RiftHay init failed") { RiftHay.init() }
        FishDiag.guard("Rift.L", "RiftLeech init failed") { RiftLeech.init() }
        FishDiag.guard("Rift.B", "RiftBurger init failed") { RiftBurger.init() }
        FishDiag.guard("Rift.7","RiftCaducousStem init failed") { RiftCaducousStem.init() }    }
}
