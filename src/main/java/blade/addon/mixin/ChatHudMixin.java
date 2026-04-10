package blade.addon.mixin;

import blade.addon.features.dungeon.PartyCommandHandler;
import blade.addon.features.dungeon.PartyInviteAccepter;
import blade.addon.utils.config.values.FishSettings;
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

    private static final Pattern LAG_PATTERN   = Pattern.compile("([\\d.]+)s lost to lag");
    // Group 1 = typer IGN, Group 2 = command, Group 3 = optional arg
    private static final Pattern PARTY_CMD     = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )*(\\w+): [.!](rtca|cata|pb|fps|tps|ping|[fm][1-7])(?:\\s+(\\w+))?\\s*$");

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        // Strip §x color codes — blade/Hypixel embed them as literal chars
        String plain = message.getString().replaceAll("§.", "");

        // Lag message → party chat
        if (FishSettings.sendLagToParty) {
            Matcher lagM = LAG_PATTERN.matcher(plain);
            if (lagM.find()) {
                String time = lagM.group(1);
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.getNetworkHandler() != null)
                    mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc " + time + "s lost to lag."));
            }
        }

        // Party invite accepter (backup — also caught via ON_GAME_MESSAGE)
        PartyInviteAccepter.onMessage(plain);

        // Party commands (.rtca / .cata / .pb from any party member)
        Matcher cmdM = PARTY_CMD.matcher(plain);
        if (cmdM.find()) {
            String typer  = cmdM.group(1);
            String cmd    = cmdM.group(2);
            // If a name was provided (.rtca OtherPlayer) use that, otherwise use the typer's name
            String target = cmdM.group(3) != null ? cmdM.group(3) : typer;
            PartyCommandHandler.onPartyCommand(target, cmd);
        }
    }
}
