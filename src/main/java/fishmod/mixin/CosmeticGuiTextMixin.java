package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** Swaps the real IGN for the cosmetic name in on-screen text draws (scoreboard, tab list, tooltips, etc.). */
@Mixin(DrawContext.class)
public abstract class CosmeticGuiTextMixin {

    @ModifyVariable(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", at = @At("HEAD"), argsOnly = true)
    private Text fishmod$ds1(Text text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private Text fishmod$ds2(Text text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "drawTextWithBackground(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)V", at = @At("HEAD"), argsOnly = true)
    private Text fishmod$ds3(Text text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V", at = @At("HEAD"), argsOnly = true)
    private List<Text> fishmod$tt1(List<Text> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), argsOnly = true)
    private List<Text> fishmod$tt2(List<Text> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V", at = @At("HEAD"), argsOnly = true)
    private List<Text> fishmod$tt3(List<Text> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), argsOnly = true)
    private List<Text> fishmod$tt4(List<Text> lines) {
        return fishmod$swapList(lines);
    }

    private static Text fishmod$swap(Text text) {
        if (text == null) return text;
        Text out = text;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        return fishmod.cosmetic.RemoteNicks.apply(out);
    }

    private static List<Text> fishmod$swapList(List<Text> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        List<Text> out = null;
        for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            if (line == null) continue;
            Text swapped = fishmod$swap(line);
            if (swapped != line) {
                if (out == null) out = new ArrayList<>(lines);
                out.set(i, swapped);
            }
        }
        return out != null ? out : lines;
    }
}
