/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

internal class InFlightRequestRegistry<K : Any, V> {
    private val requests = ConcurrentHashMap<K, Entry<V>>()

    fun share(key: K, correlationId: String, starter: Supplier<CompletableFuture<V>>): Shared<V> {
        var created = false
        val entry = requests.compute(key) { _, existing ->
            existing ?: run {
                created = true
                val source = try {
                    starter.get()
                } catch (error: RuntimeException) {
                    CompletableFuture.failedFuture(error)
                }
                Entry(correlationId, source)
            }
        }!!
        entry.source.whenComplete { _, _ -> requests.remove(key, entry) }
        return Shared(entry.source.thenApply { it }, entry.correlationId, !created)
    }

    fun size(): Int = requests.size

    @JvmRecord
    data class Shared<V>(val future: CompletableFuture<V>, val correlationId: String, val reused: Boolean)

    @JvmRecord
    private data class Entry<V>(val correlationId: String, val source: CompletableFuture<V>)
}
