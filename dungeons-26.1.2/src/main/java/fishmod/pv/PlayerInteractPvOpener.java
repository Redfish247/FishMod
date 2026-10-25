/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv;

/**
 * Legacy shift+right-click hook — replaced by {@link HypixelProfileSpyButton}
 * on Hypixel's native Profile chest. Kept as a no-op registrar for call sites.
 */
public final class PlayerInteractPvOpener {
	private PlayerInteractPvOpener() {
	}

	public static void register() {
		// Intentionally empty: opening PV from shift+entity click was the wrong UX.
		// Use HypixelProfileSpyButton on the Hypixel "'s Profile" chest instead.
	}
}
