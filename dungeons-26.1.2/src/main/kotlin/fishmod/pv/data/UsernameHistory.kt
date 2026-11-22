/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

class UsernameHistory private constructor(state: State?, entries: List<Entry>?, error: String?) {
    class Entry(username: String?, changedAt: String?) {
        @get:JvmName("username") val username: String = username ?: ""
        @get:JvmName("changedAt") val changedAt: String = changedAt ?: ""
    }

    enum class State {
        IDLE,
        LOADING,
        READY,
        ERROR,
    }

    @get:JvmName("state") val state: State = state ?: State.IDLE
    @get:JvmName("entries") val entries: List<Entry> = entries?.toList() ?: emptyList()
    @get:JvmName("error") val error: String = error ?: ""

    fun loaded(): Boolean = state == State.READY || state == State.ERROR

    companion object {
        @JvmStatic
        fun idle(): UsernameHistory = UsernameHistory(State.IDLE, emptyList(), "")

        @JvmStatic
        fun loading(): UsernameHistory = UsernameHistory(State.LOADING, emptyList(), "")

        @JvmStatic
        fun ready(entries: List<Entry>?): UsernameHistory = UsernameHistory(State.READY, entries, "")

        @JvmStatic
        fun error(message: String?): UsernameHistory = UsernameHistory(State.ERROR, emptyList(), message)
    }
}
