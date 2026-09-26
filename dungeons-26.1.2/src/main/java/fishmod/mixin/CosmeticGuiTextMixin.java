package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

@Mixin(GuiGraphicsExtractor.class)
public abstract class CosmeticGuiTextMixin {

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds1(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds2(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V", at = @At("HEAD"), argsOnly = true)
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
        if (!NickState.isActive() && fishmod.cosmetic.RemoteNicks.isEmpty()) return text;
        Component out = text;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        return fishmod.cosmetic.RemoteNicks.applyResolvedOnly(out);
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
