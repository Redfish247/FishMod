/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Shares active work while keeping caller cancellation away from the shared source. */
final class InFlightRequestRegistry<K, V> {
	private final ConcurrentHashMap<K, Entry<V>> requests = new ConcurrentHashMap<>();

	Shared<V> share(K key, String correlationId, Supplier<CompletableFuture<V>> starter) {
		boolean[] created = {false};
		Entry<V> entry = this.requests.compute(key, (ignored, existing) -> {
			if (existing != null) {
				return existing;
			}
			created[0] = true;
			CompletableFuture<V> source;
			try {
				source = starter.get();
			} catch (RuntimeException error) {
				source = CompletableFuture.failedFuture(error);
			}
			return new Entry<>(correlationId, source);
		});
		entry.source().whenComplete((value, error) -> this.requests.remove(key, entry));
		return new Shared<>(entry.source().thenApply(value -> value), entry.correlationId(), !created[0]);
	}

	int size() {
		return this.requests.size();
	}

	record Shared<V>(CompletableFuture<V> future, String correlationId, boolean reused) {
	}

	private record Entry<V>(String correlationId, CompletableFuture<V> source) {
	}
}
