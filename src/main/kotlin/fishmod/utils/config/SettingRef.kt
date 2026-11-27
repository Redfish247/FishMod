package fishmod.utils.config

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class SettingRef<T>(val owner: Class<*>, val name: String, private val fallback: T) {
    constructor(name: String, fallback: T) : this(FishSettings::class.java, name, fallback)

    private val field: Field? = resolve(owner, name)

    @Suppress("UNCHECKED_CAST")
    fun get(): T = if (field == null) fallback else field.get(null) as T

    fun set(v: T) { field?.set(null, v) }

    companion object {
        private val MISSING = Any()
        private val cache = ConcurrentHashMap<String, Any>()

        fun resolve(owner: Class<*>, name: String): Field? {
            val hit = cache.getOrPut(owner.name + "." + name) { find(owner, name) ?: MISSING }
            return hit as? Field
        }

        private fun find(owner: Class<*>, name: String): Field? {
            for (c in listOfNotNull(owner, owner.enclosingClass)) {
                val f = try { c.getDeclaredField(name) } catch (_: NoSuchFieldException) { continue }
                if (!Modifier.isStatic(f.modifiers)) continue
                f.isAccessible = true
                return f
            }
            FishDiag.fail("SettingRef.1", "no static setting field ${owner.simpleName}.$name")
            return null
        }
    }
}
