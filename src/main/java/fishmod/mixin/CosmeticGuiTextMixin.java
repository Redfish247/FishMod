package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Swaps the real IGN for the cosmetic name in on-screen text draws (scoreboard, tab list, tooltips, etc.). */
@Mixin(GuiGraphics.class)
public abstract class CosmeticGuiTextMixin {

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds1(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds2(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds3(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt1(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt2(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt3(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt4(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    private static Component fishmod$swap(Component text) {
        if (text == null) return text;
        Component out = text;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        // In a container menu (friends list, player menus, AH, etc.) discover unknown names so
        // off-server players get nick-rewritten — those names appear nowhere else (not in tab/chat).
        // On the bare HUD (scoreboard/tab, redrawn every frame) use the lookup-free path to avoid
        // per-frame request spam; on-server players there are already covered by the bulk poll.
        boolean inMenu = fishmod$inMenu();
        out = inMenu
            ? fishmod.cosmetic.RemoteNicks.apply(out)
            : fishmod.cosmetic.RemoteNicks.applyResolvedOnly(out);
        // Tag flagged (shitter-list) players with a red ✘ on the bare HUD (tab list / scoreboard).
        if (!inMenu) out = fishmod.features.Reputation.decorateTab(out);
        // Streamer Mode: §k-scramble player names in Party Finder menus (and the lobby tab if enabled).
        out = fishmod.features.StreamerMode.forGui(out, inMenu);
        return out;
    }

    /** True when a server-driven container GUI (chest menu) is open — where off-server names show up. */
    private static boolean fishmod$inMenu() {
        return net.minecraft.client.Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
    }

    private static List<Component> fishmod$swapList(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        List<Component> out = null;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line == null) continue;
            Component swapped = fishmod$swap(line);
            if (swapped != line) {
                if (out == null) out = new ArrayList<>(lines);
                out.set(i, swapped);
            }
        }
        return out != null ? out : lines;
    }
}
