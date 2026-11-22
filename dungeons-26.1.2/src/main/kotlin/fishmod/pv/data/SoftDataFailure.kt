/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonParseException
import fishmod.pv.ProfileViewer
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * Expected failures while reading flaky Hypixel / repo JSON shapes.
 *
 * [NullPointerException] is treated as soft on purpose: third-party JSON often
 * drops fields that callers then dereference unguarded. That should empty a tab, not
 * fail the whole future. Keep genuine internal bugs loud by avoiding bare
 * `SoftDataFailure` wraps around non-parse code.
 *
 * [CompletionException] / [ExecutionException] unwrap to their cause so a
 * soft parse error wrapped by a future still counts as soft.
 */
object SoftDataFailure {
    @JvmStatic
    fun isSoft(throwable: Throwable?): Boolean = when (unwrap(throwable)) {
        is JsonParseException,
        is NullPointerException,
        is IllegalStateException,
        is ClassCastException,
        is NumberFormatException,
        is UnsupportedOperationException,
        is ArithmeticException -> true
        else -> false
    }

    @JvmStatic
    fun unwrap(throwable: Throwable?): Throwable? {
        var current = throwable
        while (current is CompletionException || current is ExecutionException) {
            val cause = current.cause
            if (cause == null || cause === current) break
            current = cause
        }
        return current
    }

    @JvmStatic
    fun <T> call(context: String, fallback: T, action: SoftCall<T>): T =
        try {
            action.get()
        } catch (exception: RuntimeException) {
            if (!isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("{} failed", context, exception)
            fallback
        }

    fun interface SoftCall<T> {
        fun get(): T
    }
}
