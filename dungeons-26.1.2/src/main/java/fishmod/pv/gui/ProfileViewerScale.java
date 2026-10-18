/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

final class ProfileViewerScale {
	static final int BASE_GUI_SCALE = 2;

	private ProfileViewerScale() {
	}

	static Viewport viewport(
		int screenWidth,
		int screenHeight,
		int framebufferWidth,
		int framebufferHeight,
		int minecraftGuiScale,
		int selectedPercent,
		Bounds bounds
	) {
		int guiScale = Math.max(1, minecraftGuiScale);
		int virtualWidth = ceilDiv(Math.max(1, framebufferWidth), BASE_GUI_SCALE);
		int virtualHeight = ceilDiv(Math.max(1, framebufferHeight), BASE_GUI_SCALE);
		float baseScale = BASE_GUI_SCALE / (float) guiScale;
		float selectedScale = Math.max(0.75F, Math.min(1.5F, selectedPercent / 100.0F));
		float fitScale = bounds == null ? selectedScale : fitScale(virtualWidth, virtualHeight, bounds);
		float effectiveScale = Math.min(selectedScale, fitScale);
		float renderScale = baseScale * effectiveScale;
		float offsetX = (screenWidth - virtualWidth * renderScale) / 2.0F;
		float offsetY = (screenHeight - virtualHeight * renderScale) / 2.0F;
		return new Viewport(virtualWidth, virtualHeight, renderScale, offsetX, offsetY, selectedScale, effectiveScale);
	}

	private static float fitScale(int width, int height, Bounds bounds) {
		float cx = width / 2.0F;
		float cy = height / 2.0F;
		float horizontalExtent = Math.max(cx - bounds.left(), bounds.right() - cx);
		float verticalExtent = Math.max(cy - bounds.top(), bounds.bottom() - cy);
		float fitX = horizontalExtent <= 0.0F ? 1.5F : Math.max(1.0F, width / 2.0F - 2.0F) / horizontalExtent;
		float fitY = verticalExtent <= 0.0F ? 1.5F : Math.max(1.0F, height / 2.0F - 2.0F) / verticalExtent;
		return Math.max(0.25F, Math.min(1.5F, Math.min(fitX, fitY)));
	}

	private static int ceilDiv(int value, int divisor) {
		return (value + divisor - 1) / divisor;
	}

	static Point bottomLeftPosition(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight, int margin) {
		int x = Math.max(0, Math.min(margin, viewportWidth - contentWidth));
		int y = Math.max(0, viewportHeight - contentHeight - margin);
		return new Point(x, y);
	}

	record Bounds(float left, float top, float right, float bottom) {
	}

	record Point(int x, int y) {
	}

	record Viewport(
		int virtualWidth,
		int virtualHeight,
		float renderScale,
		float offsetX,
		float offsetY,
		float selectedScale,
		float effectiveScale
	) {
		double toVirtualX(double screenX) {
			return (screenX - this.offsetX) / this.renderScale;
		}

		double toVirtualY(double screenY) {
			return (screenY - this.offsetY) / this.renderScale;
		}

		float toScreenX(float virtualX) {
			return this.offsetX + virtualX * this.renderScale;
		}

		float toScreenY(float virtualY) {
			return this.offsetY + virtualY * this.renderScale;
		}
	}
}
