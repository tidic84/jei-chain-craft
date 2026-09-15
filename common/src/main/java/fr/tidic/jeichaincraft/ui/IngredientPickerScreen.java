package fr.tidic.jeichaincraft.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the user pick which item of a tag-ingredient slot to plan around
 * (e.g. {@code #c:chests} → chest vs trapped_chest vs ender_chest). Choice
 * is stored per (recipe id, slot index) by the caller.
 */
public class IngredientPickerScreen extends Screen {
    private static final int ICON_SIZE = 18;
    private static final int CELL = 22;
    private static final int COLS = 9;

    private final Screen parent;
    private final List<ItemStack> options;
    private final Consumer<ItemStack> onChosen;

    public IngredientPickerScreen(Screen parent,
                                  List<ItemStack> options,
                                  Consumer<ItemStack> onChosen) {
        super(Component.translatable("jeichaincraft.screen.ingredient_picker.title"));
        this.parent = parent;
        this.options = options;
        this.onChosen = onChosen;
    }

    @Override
    protected void init() {
        Button back = Button.builder(Component.translatable("gui.back"),
                        b -> Minecraft.getInstance().setScreen(parent))
                .bounds(8, this.height - 28, 80, 20).build();
        addRenderableWidget(back);
    }

    private int gridLeft() {
        int gridW = Math.min(options.size(), COLS) * CELL;
        return (this.width - gridW) / 2;
    }

    private int gridTop() {
        return 40;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);

        g.drawCenteredString(font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        int left = gridLeft();
        int top = gridTop();
        ItemStack hovered = null;
        int hoveredMx = 0, hoveredMy = 0;
        for (int i = 0; i < options.size(); i++) {
            int cx = left + (i % COLS) * CELL;
            int cy = top + (i / COLS) * CELL;
            boolean hover = mouseX >= cx && mouseX < cx + ICON_SIZE
                    && mouseY >= cy && mouseY < cy + ICON_SIZE;
            if (hover) g.fill(cx - 1, cy - 1, cx + ICON_SIZE + 1, cy + ICON_SIZE + 1, 0xFF606080);
            else       g.fill(cx - 1, cy - 1, cx + ICON_SIZE + 1, cy + ICON_SIZE + 1, 0xFF202020);
            ItemStack opt = options.get(i);
            g.renderItem(opt, cx + 1, cy + 1);
            g.renderItemDecorations(font, opt, cx + 1, cy + 1);
            if (hover) {
                hovered = opt;
                hoveredMx = mouseX;
                hoveredMy = mouseY;
            }
        }
        if (hovered != null) {
            g.renderTooltip(font, hovered, hoveredMx, hoveredMy);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = gridLeft();
        int top = gridTop();
        for (int i = 0; i < options.size(); i++) {
            int cx = left + (i % COLS) * CELL;
            int cy = top + (i / COLS) * CELL;
            if (mouseX >= cx && mouseX < cx + ICON_SIZE
                    && mouseY >= cy && mouseY < cy + ICON_SIZE) {
                onChosen.accept(options.get(i));
                Minecraft.getInstance().setScreen(parent);
                return true;
            }
        }
        return false;
    }
}
