/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonParseException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Expected failures while reading flaky Hypixel / repo JSON shapes.
 *
 * <p>{@link NullPointerException} is treated as soft on purpose: third-party JSON often
 * drops fields that callers then dereference unguarded. That should empty a tab, not
 * fail the whole future. Keep genuine internal bugs loud by avoiding bare
 * {@code SoftDataFailure} wraps around non-parse code.
 *
 * <p>{@link CompletionException} / {@link ExecutionException} unwrap to their cause so a
 * soft parse error wrapped by a future still counts as soft.
 */
public final class SoftDataFailure {
	private SoftDataFailure() {
	}

	public static boolean isSoft(Throwable throwable) {
		Throwable current = unwrap(throwable);
		if (current == null) {
			return false;
		}
		return current instanceof JsonParseException
			|| current instanceof NullPointerException
			|| current instanceof IllegalStateException
			|| current instanceof ClassCastException
			|| current instanceof NumberFormatException
			|| current instanceof UnsupportedOperationException
			|| current instanceof ArithmeticException;
	}

	public static Throwable unwrap(Throwable throwable) {
		Throwable current = throwable;
		while (current instanceof CompletionException || current instanceof ExecutionException) {
			Throwable cause = current.getCause();
			if (cause == null || cause == current) {
				break;
			}
			current = cause;
		}
		return current;
	}

	/**
	 * Runs {@code action}; on a soft data failure returns {@code fallback} after logging.
	 * Hard runtime failures rethrow.
	 */
	public static <T> T call(String context, T fallback, SoftCall<T> action) {
		try {
			return action.get();
		} catch (RuntimeException exception) {
			if (!isSoft(exception)) {
				throw exception;
			}
			fishmod.pv.BetterPV.LOGGER.warn("{} failed", context, exception);
			return fallback;
		}
	}

	@FunctionalInterface
	public interface SoftCall<T> {
		T get();
	}
}
