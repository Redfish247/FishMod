package fishmod.utils.data

import com.google.gson.JsonObject
import fishmod.pv.neu.NeuRepoCache

object NeuRepo {

    @Volatile private var fairySoulsCache: JsonObject? = null

    @JvmStatic val fairySouls: JsonObject?
        get() = fairySoulsCache ?: NeuRepoCache.fairySoulsJson()?.also { fairySoulsCache = it }

    @JvmStatic fun isLoaded(): Boolean = NeuRepoCache.isReady()

    @JvmStatic fun get(id: String?): JsonObject? = NeuRepoCache.get(id)

    @JvmStatic fun ids(): Set<String> = NeuRepoCache.ids()

    @JvmStatic fun ensureLoaded() = NeuRepoCache.start()
}
