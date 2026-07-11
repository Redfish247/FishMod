package fishmod.features;

import fishmod.features.other.CommandAliases;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * /fm aliases — map a short command (e.g. "dh") to run a longer one (e.g. "warp dh").
 *
 * <p>Same editing convention as {@link CommandKeysScreen}: {@code aliases}/{@code commands} are
 * the source of truth, widgets are rebuilt from them on every add/remove/scroll.
 */
public class CommandAliasesScreen extends Screen {

    private static final int BG_PANEL   = 0xF20E1016;
    private static final int BG_SECTION = 0xFF171A22;
    private static final int BORDER     = 0xFF2A2D38;
    private static final int ACCENT     = 0xFF55FFFF;
    private static final int TEXT_HINT  = 0xFF8A8F9C;
    private static final int LIST_BG    = 0xFF14161D;

    private static final int ROW_H = 24;
    private static final int MAX_VISIBLE = 6;
    private static final int ALIAS_FIELD_W = 90;
    private static final int REMOVE_BTN_W = 20;

    private final List<String> aliases = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private int scroll = 0;

    private int panelX, panelY;
    private final int panelW = 420;
    private int panelH;
    private int listX, listY, listW, listH;
    private int cmdFieldX, cmdFieldW, removeBtnX;

    public CommandAliasesScreen() { super(Text.literal("Command Aliases")); }

    @Override
    protected void init() {
        for (CommandAliases.Entry e : CommandAliases.all()) {
            aliases.add(e.alias());
            commands.add(e.command());
        }

        listH = ROW_H * MAX_VISIBLE;
        panelH = 56 + listH + 46;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);

        listX = panelX + 14;
        listY = panelY + 50;
        listW = panelW - 28;
        cmdFieldX = listX + ALIAS_FIELD_W + 6;
        cmdFieldW = listW - ALIAS_FIELD_W - 6 - REMOVE_BTN_W - 6;
        removeBtnX = listX + listW - REMOVE_BTN_W;

        rebuildRows();
    }

    private void persist() {
        List<CommandAliases.Entry> list = new ArrayList<>();
        for (int i = 0; i < aliases.size(); i++) list.add(new CommandAliases.Entry(aliases.get(i), commands.get(i)));
        CommandAliases.replaceAll(list);
    }

    private void rebuildRows() {
        clearChildren();

        for (int i = 0; i < aliases.size(); i++) {
            int rowTop = listY + i * ROW_H - scroll;
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue;
            final int idx = i;

            TextFieldWidget aliasField = new TextFieldWidget(this.textRenderer, listX, rowTop + 3, ALIAS_FIELD_W, 18, Text.literal("Alias"));
            aliasField.setMaxLength(32);
            aliasField.setText(aliases.get(i));
            aliasField.setChangedListener(s -> { aliases.set(idx, s); persist(); });
            addDrawableChild(aliasField);

            TextFieldWidget cmdField = new TextFieldWidget(this.textRenderer, cmdFieldX, rowTop + 3, cmdFieldW, 18, Text.literal("Command"));
            cmdField.setMaxLength(256);
            cmdField.setText(commands.get(i));
            cmdField.setChangedListener(s -> { commands.set(idx, s); persist(); });
            addDrawableChild(cmdField);

            addDrawableChild(ButtonWidget.builder(Text.literal("§cX"), b -> {
                        aliases.remove(idx); commands.remove(idx);
                        persist(); rebuildRows();
                    })
                    .dimensions(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18).build());
        }

        int btnY = listY + listH + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Alias"), b -> {
                    aliases.add(""); commands.add("");
                    persist(); rebuildRows();
                })
                .dimensions(panelX + 14, btnY, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + panelW - 14 - 70, btnY, 70, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION);
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§b§lCommand Aliases", panelX + panelW / 2, panelY + 7, 0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,
                "§7Alias §f(no slash) §7→ Command it runs. New/edited aliases work right away;",
                panelX + 14, panelY + 30, TEXT_HINT);
        ctx.drawTextWithShadow(this.textRenderer,
                "§7removing/renaming one fully clears after you rejoin.",
                panelX + 14, panelY + 39, TEXT_HINT);
        ctx.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, LIST_BG);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, aliases.size() * ROW_H - listH);
        scroll = Math.max(0, Math.min(max, scroll - (int) (verticalAmount * ROW_H)));
        rebuildRows();
        return true;
    }
}
