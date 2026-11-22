/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.mixin;

import fishmod.pv.ProfileViewerOpener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenClickMixin {
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void fishpv$styleClick(Style style, boolean insertionClick, CallbackInfoReturnable<Boolean> cir) {
		if (insertionClick) {
			return;
		}
		if (ProfileViewerOpener.tryHandleStyle(style)) {
			cir.setReturnValue(true);
		}
	}
}
