package fishmod.features.kuudra

import com.google.gson.GsonBuilder
import fishmod.utils.debug.FishDiag
import java.nio.file.Files
import java.nio.file.Paths

// Per-item paid-chest drops: Session (this launch) and Total (saved)
object KuudraLoot {

    class Item { @JvmField var name = ""; @JvmField var count = 0L; @JvmField var value = 0.0 }
    class Book {
        @JvmField var chests = 0; @JvmField var value = 0.0; @JvmField var cost = 0.0
        @JvmField var items: MutableMap<String, Item> = LinkedHashMap()
        fun add(drops: List<Triple<String, Int, Double>>, v: Double, c: Double) {
            chests++; value += v; cost += c
            for ((n, q, iv) in drops) { val it = items.getOrPut(n) { Item().also { x -> x.name = n } }; it.count += q; it.value += iv }
        }
    }

    private val FILE = Paths.get("config/fishmod/kuudra_loot.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    val session = Book()
    val total: Book by lazy { load() }

    fun record(drops: List<Triple<String, Int, Double>>, v: Double, c: Double) {
        session.add(drops, v, c); total.add(drops, v, c); save()
    }

    fun reset() { session.chests = 0; session.value = 0.0; session.cost = 0.0; session.items.clear(); total.chests = 0; total.value = 0.0; total.cost = 0.0; total.items.clear(); save() }

    private fun load(): Book = try {
        if (Files.exists(FILE)) GSON.fromJson(Files.readString(FILE), Book::class.java) ?: Book() else Book()
    } catch (t: Throwable) { FishDiag.fail("KuudraLoot.1", "kuudra loot load failed", t); Book() }

    private fun save() {
        try { Files.createDirectories(FILE.parent); Files.writeString(FILE, GSON.toJson(total)) }
        catch (t: Throwable) { FishDiag.fail("KuudraLoot.2", "kuudra loot save failed", t) }
    }
}
