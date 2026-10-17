package fishmod.features.kuudra

import fishmod.features.mining.Mining
import fishmod.utils.ChatQueue
import fishmod.utils.FishMsg
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.data.PartyUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import fishmod.features.kuudra.KuudraSettings as S
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

// Kuudra run state (phases from Elle's lines, entities scanned every 10 ticks); data/regexes follow Odin
object Kuudra {

    enum class Supply(val pick: BlockPos, val drop: BlockPos) {
        Triangle(BlockPos(-67, 77, -122), BlockPos(-94, 78, -106)),
        X(BlockPos(-142, 77, -151), BlockPos(-106, 78, -112)),
        Equals(BlockPos(-65, 76, -87), BlockPos(-98, 78, -99)),
        Slash(BlockPos(-113, 77, -68), BlockPos(-106, 78, -99)),
        Shop(BlockPos(-81, 76, -143), BlockPos(-98, 78, -112)),
        xCannon(BlockPos(-143, 76, -125), BlockPos(-110, 78, -106)),
        Square(BlockPos(-143, 76, -80), BlockPos.ZERO),
        None(BlockPos.ZERO, BlockPos.ZERO);
        @JvmField var active = true
    }

    val PHASES = arrayOf("Supplies", "Build", "Eat", "Kill")

    @JvmStatic var phase = 0; private set
    @JvmStatic var tier = 0; private set
    val giants = ArrayList<Giant>()
    val piles = ArrayList<ArmorStand>()
    val players = ArrayList<Player>()
    var elle: ArmorStand? = null; private set
    var boss: MagmaCube? = null; private set
    var builders = 0; private set
    var buildPct = 0; private set
    var supplies = 0; private set
    val supplyTimes = ArrayList<Pair<String, Long>>()
    val freshers = LinkedHashMap<String, Long>()
    // phaseStart[0] = run start (supplies), [4] = run end
    val phaseStart = LongArray(5)
    var preSpot = Supply.None; private set
    var missing = Supply.None; private set
    private var stunAlerted = false
    private var sideAlerted = false
    private var tick = 0

    private val SUPPLY = Regex("^(?:\\[[^]]*] )?(\\w{1,16}) recovered one of Elle's supplies! \\((\\d)/(\\d)\\)$")
    private val BUILD = Regex("Building Progress (\\d+)% \\((\\d+) Players Helping\\)")
    private val PROGRESS = Regex("PROGRESS: (\\d+)%")
    private val PARTY_FRESH = Regex("^Party > (?:\\[[^]]*?] )?(\\w{1,16}): FRESH.*$")
    private val PARTY_NOPRE = Regex("^Party > (?:\\[[^]]*?] )?(\\w{1,16}): No ?(Triangle|X|Equals|Slash|xCannon|Square|Shop)!$")
    private val TIER = Regex("Kuudra's Hollow \\(T(\\d)\\)")
    private const val OWN_FRESH = "Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!"
    private const val PRE = "[NPC] Elle: Head over to the main platform, I will join you when I get a bite!"
    private const val NOT_AGAIN = "[NPC] Elle: Not again!"
    private const val END = "[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!"

    fun inKuudra(): Boolean = Location.`in`(Location.KUUDRA)
    fun now() = System.currentTimeMillis()

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { c -> FishDiag.guard("Kuudra.1", "kuudra chat failed") { onChat(Mining.strip(c.string)) } ?: false }
        Events.ON_WORLD_CHANGE.register { reset(); false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !inKuudra() || tick++ % 10 != 0) return@register
            FishDiag.guard("Kuudra.2", "kuudra entity scan failed") { scan(mc) }
        }
        KuudraHuds.init()
        KuudraRender.init()
        KuudraChests.init()
    }

    private fun reset() {
        Supply.entries.forEach { it.active = true }
        giants.clear(); piles.clear(); players.clear(); freshers.clear(); supplyTimes.clear()
        boss = null; elle = null; phase = 0; tier = 0; builders = 0; buildPct = 0; supplies = 0
        phaseStart.fill(0); preSpot = Supply.None; missing = Supply.None; stunAlerted = false; sideAlerted = false
    }

    private fun scan(mc: Minecraft) {
        val level = mc.level ?: return
        if (tier == 0) Mining.sidebar.firstNotNullOfOrNull { TIER.find(it) }?.let { tier = it.groupValues[1].toInt() }
        giants.clear(); piles.clear(); players.clear()
        for (e in level.entitiesForRendering()) when (e) {
            is Giant -> if (e.mainHandItem.hoverName.string.endsWith("Head")) giants.add(e)
            is MagmaCube -> if (e.size == 30 && e.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0) boss = e
            is ArmorStand -> {
                val n = e.name.string
                if (PROGRESS.containsMatchIn(n)) piles.add(e)
                if (n == "Elle") elle = e
                if (phase == 2) BUILD.find(n)?.let { buildPct = it.groupValues[1].toInt(); builders = it.groupValues[2].toInt() }
                if (phase == 1 && n == "✓ SUPPLIES RECEIVED ✓") dropDone(e.x.toInt(), e.z.toInt())
            }
            is Player -> if (e !== mc.player && e.uuid.version() == 4) players.add(e)
        }
        if (phase == 2 && S.kuudraAlerts && S.kuudraAlertStunPct in 1..100 && tier >= 3 && !stunAlerted && buildPct >= S.kuudraAlertStunPct) {
            stunAlerted = true; alert("§3§lGo to stun", "")
        }
        val b = boss
        if (phase == 4 && b != null && S.kuudraAlerts && S.kuudraAlertSide && !sideAlerted) {
            val side = when { b.x < -128 -> "RIGHT"; b.z > -84 -> "FRONT"; b.x > -72 -> "LEFT"; b.z < -132 -> "BACK"; else -> null }
            if (side != null) { sideAlerted = true; alert("§c§l$side!", "§7Kuudra spawn side") }
        }
    }

    private fun dropDone(x: Int, z: Int) {
        val s = when {
            x == -98 && z == -112 -> Supply.Shop; x == -98 && z == -99 -> Supply.Equals
            x == -110 && z == -106 -> Supply.xCannon; x == -106 && z == -112 -> Supply.X
            x == -94 && z == -106 -> Supply.Triangle; x == -106 && z == -99 -> Supply.Slash
            else -> return
        }
        s.active = false
    }

    private fun onChat(msg: String): Boolean {
        if (!inKuudra()) return false
        when (msg) {
            "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> setPhase(1)
            "[NPC] Elle: OMG! Great work collecting my supplies!" -> setPhase(2)
            "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> setPhase(3)
            "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!" -> setPhase(4)
            END -> { if (phase in 1..4) endRun() }
            PRE -> onPre()
            NOT_AGAIN -> checkPre()
            OWN_FRESH -> {
                freshers[Minecraft.getInstance().player?.name?.string ?: "You"] = now()
                if ((S.kuudraBuildMaster && S.kuudraFreshParty) && S.kuudraFreshMessage.isNotBlank()) party(S.kuudraFreshMessage)
            }
        }
        PARTY_FRESH.find(msg)?.let { freshers[it.groupValues[1]] = now() }
        PARTY_NOPRE.find(msg)?.let { onMissing(Supply.valueOf(it.groupValues[2])) }
        SUPPLY.find(msg)?.let { m ->
            supplies = m.groupValues[2].toInt()
            val t = if (phaseStart[1] > 0) now() - phaseStart[1] else 0L
            supplyTimes.add(m.groupValues[1] to t)
            if (m.groupValues[1] == Minecraft.getInstance().player?.name?.string) fishmod.features.achievements.AchBus.post(fishmod.features.achievements.AchEvent.SupplyDelivered)
            if (S.kuudraAlerts && S.kuudraAlertSupplies && supplies == m.groupValues[3].toInt()) alert("§a§lSupplies ${supplies}/${supplies}", "")
            if ((S.kuudraSupplyMaster && S.kuudraSupplyTimes) && phase == 1) {
                FishMsg.send("§6${m.groupValues[1]} §a§lrecovered a supply in ${fmt(t)}! §r§8(${m.groupValues[2]}/${m.groupValues[3]})")
                return S.kuudraSupplyHideOriginal
            }
        }
        return false
    }

    private fun setPhase(p: Int) {
        if (p <= phase) return
        val t = now()
        if (phase == 0) phaseStart[0] = t
        phaseStart[p - 1] = t
        if (p > 1 && phaseStart[p - 2] > 0) KuudraHuds.onSplit(p - 2, t - phaseStart[p - 2])
        phase = p
        if (p == 3) fishmod.features.achievements.AchBus.post(fishmod.features.achievements.AchEvent.BallistaBuilt)
        if (S.kuudraAlerts && S.kuudraAlertPhase) when (p) {
            2 -> alert("§e§lBuild started", "")
            3 -> alert("§a§lBallista built", "§7Eat / stun phase")
            4 -> alert("§c§lKill phase", "")
        }
    }

    private fun endRun() {
        val t = now()
        phaseStart[4] = t
        KuudraHuds.onSplit(3, t - phaseStart[3])
        KuudraHuds.onEnd(t - phaseStart[0])
        phase = 5
        KuudraChests.onRunEnd()
        fishmod.features.achievements.AchBus.post(fishmod.features.achievements.AchEvent.KuudraEnded(tier, t - phaseStart[0]))
    }

    private fun onPre() {
        val p = Minecraft.getInstance().player?.blockPosition() ?: return
        preSpot = when {
            Supply.Triangle.pick.closerThan(p, 15.0) -> Supply.Triangle
            Supply.X.pick.closerThan(p, 30.0) -> Supply.X
            Supply.Equals.pick.closerThan(p, 15.0) -> Supply.Equals
            Supply.Slash.pick.closerThan(p, 15.0) -> Supply.Slash
            else -> Supply.None
        }
        if (S.kuudraNoPre) FishMsg.send(if (preSpot == Supply.None) "§cPre-spot not registered (not there in time)" else "§aPre-spot: ${preSpot.name}")
    }

    private fun checkPre() {
        if (!S.kuudraNoPre || preSpot == Supply.None) return
        var pre = false; var second = false
        for (g in giants) {
            val v = Vec3(g.x, 76.0, g.z)
            when {
                preSpot.pick.closerToCenterThan(v, 18.0) -> pre = true
                preSpot == Supply.Triangle && Supply.Shop.pick.closerToCenterThan(v, 18.0) -> second = true
                preSpot == Supply.X && Supply.xCannon.pick.closerToCenterThan(v, 16.0) -> second = true
                preSpot == Supply.Slash && Supply.Square.pick.closerToCenterThan(v, 20.0) -> second = true
            }
        }
        val msg = when {
            !pre -> "No ${preSpot.name}!"
            !second -> when (preSpot) { Supply.Triangle -> "No Shop!"; Supply.X -> "No xCannon!"; Supply.Slash -> "No Square!"; else -> return }
            else -> return
        }
        FishMsg.send("§c$msg")
        if (S.kuudraNoPreParty) party(msg)
    }

    private fun onMissing(s: Supply) {
        missing = s
        if (S.kuudraAlerts && S.kuudraAlertNoPre) alert("§c§lNo ${s.name}!", "")
        if (!S.kuudraCratePriority) return
        val pr = KuudraPriority.of(s, preSpot, S.kuudraCratePriorityAdvanced)
        if (pr.isNotEmpty()) { FishMsg.send("§eCrate Priority: §f$pr"); Misc.forceTitle(Component.literal("§e$pr"), Component.empty(), 2500) }
    }

    fun party(msg: String) { if (PartyUtil.isInParty()) ChatQueue.enqueue("pc $msg") }

    fun alert(title: String, sub: String) {
        Misc.forceTitle(Component.literal(title), Component.literal(sub), 2000)
        if (S.kuudraAlertSound) Misc.sendSound2D(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f)
    }

    fun fmt(ms: Long): String = "%.2fs".format(ms / 1000.0)
}
