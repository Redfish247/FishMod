package fishmod.features.achievements

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import fishmod.utils.IoExecutor
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

// FishMod usage: days opened, settings changed, features turned on, HUD saves, prerelease builds
object AchUsage {

    private val CONFIG = Paths.get("config/fishmod-settings.json")
    @Volatile private var snapshot: Map<String, String>? = null

    fun init() {
        IoExecutor.execute { snapshot = readConfig() }
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is fishmod.features.FishModScreen) {
                markDay()
                if (Minecraft.getInstance().player?.isInWater == true) AchStore.max("secret.waterGui", 1)
            }
        }
    }

    // Runs once a player's achievement file is loaded
    fun onBind() {
        markDay()
        val ver = FabricLoader.getInstance().getModContainer("fishmod-dungeons").map { it.metadata.version.friendlyString }.orElse("")
        if (Regex("(?i)(pre|beta|alpha|rc|dev)").containsMatchIn(ver.substringBefore('-' + "26."))) AchStore.max("fm.prerelease", 1)
        if (AchStore.data.verSeen != ver) { AchStore.data.verSeen = ver; AchStore.data.updateDay = LocalDate.now().toString(); AchStore.changed() }
    }

    private fun markDay() {
        if (AchStore.data.fmDays.add(LocalDate.now().toString())) AchStore.changed()
        AchStore.max("fm.days", AchStore.data.fmDays.size.toLong())
    }

    private fun flatten(prefix: String, e: JsonElement, out: MutableMap<String, String>) {
        when {
            e.isJsonObject -> for ((k, v) in e.asJsonObject.entrySet()) flatten(if (prefix.isEmpty()) k else "$prefix.$k", v, out)
            else -> out[prefix] = e.toString()
        }
    }

    private fun readConfig(): Map<String, String>? = try {
        if (!Files.exists(CONFIG)) null else HashMap<String, String>().also { flatten("", JsonParser.parseString(Files.readString(CONFIG)), it) }
    } catch (t: Throwable) { FishDiag.fail("AchUsage.1", "config snapshot read failed", t); null }

    // Called after /fm closes and saves the config
    @JvmStatic
    fun onConfigSaved() {
        IoExecutor.execute {
            val now = readConfig() ?: return@execute
            val old = snapshot
            snapshot = now
            if (old == null) return@execute
            val changed = now.filter { (k, v) -> old[k] != null && old[k] != v }
            if (changed.isEmpty()) return@execute
            val turnedOn = changed.filter { (k, v) -> v == "true" && old[k] == "false" }.keys
            Minecraft.getInstance().execute {
                AchStore.inc("fm.settingsChanged", changed.size.toLong())
                if (AchStore.data.featuresOn.addAll(turnedOn)) AchStore.changed()
                AchStore.max("fm.featuresUsed", AchStore.data.featuresOn.size.toLong())
            }
        }
    }

    @JvmStatic
    fun onHudSaved() {
        AchStore.inc("fm.hudSaves")
        if (AchStore.data.hudAreas.add(fishmod.utils.Location.getCurrentLocation().name)) AchStore.changed()
        AchStore.max("fm.hudAreas", AchStore.data.hudAreas.size.toLong())
    }

    // Master toggles seen on at /fm close; feeds Ultimate Tester and The Optimizer
    @JvmStatic
    fun onFeatureScan(features: List<fishmod.features.FishModScreen.Feature>) {
        val masters = features.filter { it.hasMaster() }
        if (masters.isEmpty()) return
        val on = masters.filter { it.get?.invoke() == true }.map { it.name }
        if (AchStore.data.mastersOn.addAll(on)) AchStore.changed()
        AchStore.max("fm.featuresPct", AchStore.data.mastersOn.size * 100L / masters.size)
        if (on.any { it == "Render Optimizer" || it == "Performance HUD" }) AchStore.max("fm.perfOn", 1)
    }
}
