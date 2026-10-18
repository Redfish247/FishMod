/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.mixin;

import fishmod.pv.ProfileViewerOpener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla chat RunCommand clicks call {@code sendUnattendedCommand}, which only
 * reaches the server. BetterPV registers {@code /pv} as a Fabric client command,
 * so open the profile viewer here instead of forwarding to Hypixel.
 */
@Mixin(Screen.class)
public abstract class ScreenClickCommandMixin {
	@Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
	private static void betterpv$clientPv(
		LocalPlayer player,
		String command,
		Screen screen,
		CallbackInfo ci
	) {
		if (ProfileViewerOpener.tryHandleChatCommand(command)) {
			ci.cancel();
		}
	}
}
