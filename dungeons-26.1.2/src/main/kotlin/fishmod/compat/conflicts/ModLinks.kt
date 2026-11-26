package fishmod.compat.conflicts

import fishmod.compat.showScreen
import fishmod.utils.debug.FishDiag
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ModLinks {

    private val factories = ConcurrentHashMap<String, Any>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private fun factoryFor(modId: String): Any? {
        factories[modId]?.let { return it }
        if (modId in missing) return null
        var found: Any? = null
        try {
            @Suppress("UNCHECKED_CAST")
            val api = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi") as Class<Any>
            for (c in FabricLoader.getInstance().getEntrypointContainers("modmenu", api)) {
                if (c.provider.metadata.id != modId) continue
                val factory = api.getMethod("getModConfigScreenFactory").invoke(c.entrypoint) ?: continue
                found = factory
                break
            }
        } catch (_: ClassNotFoundException) {
        } catch (t: Throwable) {
            FishDiag.fail("ModLinks.1", "config screen lookup failed $modId", t)
        }
        if (found == null) missing.add(modId) else factories[modId] = found
        return found
    }

    fun hasConfigScreen(modId: String): Boolean = factoryFor(modId) != null

    fun openConfig(modId: String, parent: Screen): Boolean {
        val factory = factoryFor(modId) ?: return false
        return try {
            val m = factory.javaClass.methods.first { it.name == "create" && it.parameterCount == 1 }
            m.isAccessible = true
            val s = m.invoke(factory, parent) as? Screen ?: return false
            Minecraft.getInstance().execute { Minecraft.getInstance().showScreen(s) }
            true
        } catch (t: Throwable) {
            FishDiag.fail("ModLinks.2", "open config screen failed $modId", t)
            false
        }
    }

    fun restartGame(): Boolean {
        val windows = System.getProperty("os.name", "").lowercase().contains("win")
        if (windows) try {
            val info = ProcessHandle.current().info()
            val exe = info.command().orElse(null)
            val args = info.arguments().orElse(null)
            if (exe != null && args != null) {
                fun q(s: String) = "\"" + s.replace("\"", "\\\"") + "\""
                val joined = args.joinToString(" ") { q(it) }
                fun ps(s: String) = "'" + s.replace("'", "''") + "'"
                val script = File.createTempFile("fishmod-restart", ".ps1")
                script.writeText("Start-Sleep -Seconds 4\nStart-Process -FilePath ${ps(exe)} -ArgumentList ${ps(joined)} -WorkingDirectory ${ps(File(".").absoluteFile.normalize().path)}\nRemove-Item -LiteralPath ${ps(script.path)} -Force\n")
                ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.path).start()
            } else return false
        } catch (t: Throwable) {
            FishDiag.fail("ModLinks.3", "restart helper failed", t)
            return false
        }
        Minecraft.getInstance().execute { Minecraft.getInstance().stop() }
        return windows
    }
}
