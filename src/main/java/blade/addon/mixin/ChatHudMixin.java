package blade.addon.mixin;

import blade.addon.features.dungeon.PartyCommandHandler;
import blade.addon.features.dungeon.PartyInviteAccepter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    // Group 1 = typer IGN, Group 2 = command, Group 3 = optional arg
    private static final Pattern PARTY_CMD = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )*(\\w+): [.!](rtca|cata|pb|fps|tps|ping|ai|allinv|e|[fm][1-7]|t[1-5])(?:\\s+(\\w+))?\\s*$");

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        String plain = message.getString().replaceAll("§.", "");

        PartyInviteAccepter.onMessage(plain);

        Matcher cmdM = PARTY_CMD.matcher(plain);
        if (cmdM.find()) {
            String typer  = cmdM.group(1);
            String cmd    = cmdM.group(2);
            String target = cmdM.group(3) != null ? cmdM.group(3) : typer;
            PartyCommandHandler.onPartyCommand(typer, target, cmd);
        }
    }
}
