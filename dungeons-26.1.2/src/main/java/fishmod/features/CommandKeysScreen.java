package fishmod.features;

import com.mojang.blaze3d.platform.InputConstants;
import fishmod.features.other.CommandKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * /fm commandkeys — bind arbitrary keys/mouse buttons to slash commands.
 *
 * <p>Entries are kept as plain {@code keys}/{@code commands} lists (the source of truth) and
 * widgets are rebuilt from them on every add/remove/scroll/rebind — simplest way to support a
 * variable-length, scrollable row list without hand-rolled widget recycling.
 *
 * <p>Key capture mirrors {@link FishModScreen}'s existing keybind-rebind convention: click a key
 * box to arm capture, then the next key or mouse click is bound; Escape unbinds instead.
 */
public class CommandKeysScreen extends Screen {

    private static final int BG_PANEL   = 0xF20E1016;
    private static final int BG_SECTION = 0xFF171A22;
    private static final int BORDER     = 0xFF2A2D38;
    private static final int ACCENT     = 0xFF55FFFF;
    private static final int TEXT_HINT  = 0xFF8A8F9C;
    private static final int LIST_BG    = 0xFF14161D;

    private static final int ROW_H = 24;
    private static final int MAX_VISIBLE = 6;
    private static final int KEY_BTN_W = 120;
    private static final int REMOVE_BTN_W = 20;

    private final List<InputConstants.Key> keys = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private Integer capturingIndex = null;
    private int scroll = 0;

    private int panelX, panelY;
    private final int panelW = 400;
    private int panelH;
    private int listX, listY, listW, listH;
    private int cmdFieldX, cmdFieldW, removeBtnX;

    public CommandKeysScreen() { super(Component.literal("Command Keys")); }

    @Override
    protected void init() {
        for (CommandKeys.Entry e : CommandKeys.all()) {
            keys.add(e.key());
            commands.add(e.command());
        }

        listH = ROW_H * MAX_VISIBLE;
        panelH = 50 + listH + 46;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);

        listX = panelX + 14;
        listY = panelY + 44;
        listW = panelW - 28;
        cmdFieldX = listX + KEY_BTN_W + 6;
        cmdFieldW = listW - KEY_BTN_W - 6 - REMOVE_BTN_W - 6;
        removeBtnX = listX + listW - REMOVE_BTN_W;

        rebuildRows();
    }

    private void persist() {
        List<CommandKeys.Entry> list = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) list.add(new CommandKeys.Entry(keys.get(i), commands.get(i)));
        CommandKeys.replaceAll(list);
    }

    private void rebuildRows() {
        clearWidgets();

        for (int i = 0; i < keys.size(); i++) {
            int rowTop = listY + i * ROW_H - scroll;
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue;
            final int idx = i;

            InputConstants.Key k = keys.get(i);
            String label = (capturingIndex != null && capturingIndex == i)
                    ? "> Press a key <"
                    : (k.equals(InputConstants.UNKNOWN) ? "Unbound" : k.getDisplayName().getString());
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                        capturingIndex = idx;
                        rebuildRows();
                    })
                    .bounds(listX, rowTop + 3, KEY_BTN_W, 18).build());

            EditBox cmdField = new EditBox(this.font, cmdFieldX, rowTop + 3, cmdFieldW, 18, Component.literal("Command"));
            cmdField.setMaxLength(256);
            cmdField.setValue(commands.get(i));
            cmdField.setResponder(s -> { commands.set(idx, s); persist(); });
            addRenderableWidget(cmdField);

            addRenderableWidget(Button.builder(Component.literal("§cX"), b -> {
                        keys.remove(idx); commands.remove(idx);
                        persist(); rebuildRows();
                    })
                    .bounds(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18).build());
        }

        int btnY = listY + listH + 8;
        addRenderableWidget(Button.builder(Component.literal("+ Add Command Key"), b -> {
                    keys.add(InputConstants.UNKNOWN); commands.add("");
                    persist(); rebuildRows();
                })
                .bounds(panelX + 14, btnY, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + panelW - 14 - 70, btnY, 70, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION);
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT);
        ctx.centeredText(this.font, "§b§lCommand Keys", panelX + panelW / 2, panelY + 7, 0xFFFFFF);
        ctx.text(this.font,
                "§7Click a key box, then press a key or click a mouse button §8(Esc to unbind)",
                panelX + 14, panelY + 30, TEXT_HINT);
        ctx.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, LIST_BG);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (capturingIndex != null) {
            keys.set(capturingIndex, InputConstants.Type.MOUSE.getOrCreate(click.button()));
            capturingIndex = null;
            persist();
            rebuildRows();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (capturingIndex != null) {
            keys.set(capturingIndex, input.key() == GLFW.GLFW_KEY_ESCAPE
                    ? InputConstants.UNKNOWN
                    : InputConstants.getKey(input));
            capturingIndex = null;
            persist();
            rebuildRows();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, keys.size() * ROW_H - listH);
        scroll = Math.max(0, Math.min(max, scroll - (int) (verticalAmount * ROW_H)));
        rebuildRows();
        return true;
    }
}
