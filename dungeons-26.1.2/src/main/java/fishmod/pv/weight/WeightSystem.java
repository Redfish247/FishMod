/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight;

public enum WeightSystem {
	SENITHER("Senither"),
	LILY("Lily");

	private final String display;

	WeightSystem(String display) {
		this.display = display;
	}

	public String display() {
		return this.display;
	}

	public WeightSystem other() {
		return this == SENITHER ? LILY : SENITHER;
	}
}
