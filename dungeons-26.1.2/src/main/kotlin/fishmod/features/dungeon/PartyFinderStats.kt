package fishmod.features.dungeon

import fishmod.utils.FishMsg
import fishmod.utils.HypixelApi
import net.minecraft.client.Minecraft

/**
 * Party Finder join-request helper: while FishSettings.pfStatsEnabled is on, any whisper you
 * receive (typically someone asking to join your party) triggers a local-only lookup of their
 * MP/PB/Cata/Gear, printed to your own chat so you can vet them before inviting. Nothing is ever
 * sent back to the sender.
 */
object PartyFinderStats {

    private val lastLookupAt: MutableMap<String, Long> = HashMap()
    private const val COOLDOWN_MS = 15_000L

    @JvmStatic
    fun onWhisper(sender: String?) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || sender == null) return
        if (sender.equals(mc.player!!.name.string, ignoreCase = true)) return

        val now = System.currentTimeMillis()
        val last = lastLookupAt[sender.lowercase()]
        if (last != null && now - last < COOLDOWN_MS) return
        lastLookupAt[sender.lowercase()] = now

        HypixelApi.getByNameSilent(sender) { data ->
            val mp = if (data.magicalPower >= 0) data.magicalPower.toString() else "N/A"
            val pb = if (data.masterPbs != null && data.masterPbs.size > 7 && data.masterPbs[7] != null)
                data.masterPbs[7] else "N/A"
            val cata = HypixelApi.formatLevel(data.cataXp)
            val armorStars = data.armorStars
            val gear = if (armorStars != null)
                String.format(
                    "H%d C%d L%d B%d", armorStars[0], armorStars[1],
                    armorStars[2], armorStars[3]
                )
            else "N/A"
            FishMsg.send(
                "$sender wants to join — MP: $mp | M7 PB: $pb | Cata: $cata | Gear: $gear"
            )
        }
    }
}
