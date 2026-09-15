package fr.tidic.jeichaincraft.ui;

import fr.tidic.jeichaincraft.core.RecipeLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lists every recipe producing a given item and lets the user pick one to
 * remember as their preference. Shown when the user clicks the +N indicator
 * on an ambiguous tree node.
 *
 * Rendering per candidate row:
 *   [output icon] [recipe id]
 *                 [ing1] [ing2] ... [ingN]   ← horizontal ingredient strip
 *
 * Click anywhere on a row → callback (which saves the pref + rebuilds the tree)
 * and pops back to the parent screen.
 */
public class RecipePickerScreen extends Screen {
    private static final int ROW_HEIGHT = 40;
    private static final int ROW_PADDING = 4;

    private final Screen parent;
    private final ItemStack target;
    private final List<RecipeHolder<?>> candidates;
    private final Consumer<RecipeHolder<?>> onChosen;
    private int scroll;

    public RecipePickerScreen(Screen parent,
                              ItemStack target,
                              List<RecipeHolder<?>> candidates,
                              Consumer<RecipeHolder<?>> onChosen) {
        super(Component.translatable("jeichaincraft.screen.picker.title", target.getHoverName()));
        this.parent = parent;
        this.target = target;
        this.candidates = candidates;
        this.onChosen = onChosen;
    }

    @Override
    protected void init() {
        Button back = Button.builder(Component.translatable("gui.back"),
                        b -> Minecraft.getInstance().setScreen(parent))
                .bounds(8, this.height - 28, 80, 20).build();
        addRenderableWidget(back);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);

        g.drawCenteredString(font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        int listX = 16;
        int listY = 28 - scroll;
        int rowW = this.width - 32;

        for (RecipeHolder<?> holder : candidates) {
            if (listY > -ROW_HEIGHT && listY < this.height - 32) {
                drawRow(g, holder, listX, listY, rowW, mouseX, mouseY);
            }
            listY += ROW_HEIGHT + ROW_PADDING;
        }
    }

    private void drawRow(GuiGraphics g, RecipeHolder<?> holder, int x, int y, int w,
                         int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        int bg = hover ? 0xFF303860 : 0xFF202020;
        g.fill(x, y, x + w, y + ROW_HEIGHT, bg);

        // Output icon
        g.renderItem(target, x + 4, y + 4);
        g.renderItemDecorations(font, target, x + 4, y + 4);

        // Recipe id
        g.drawString(font, holder.id().toString(), x + 26, y + 6, 0xFFFFFFFF);

        // Ingredients strip
        int ingX = x + 26;
        int ingY = y + 20;
        for (ItemStack ing : RecipeLookup.ingredientsOf(holder)) {
            g.renderItem(ing, ingX, ingY);
            g.renderItemDecorations(font, ing, ingX, ingY);
            ingX += 18;
            if (ingX > x + w - 20) break;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int listY = 28 - scroll;
        int rowW = this.width - 32;
        for (RecipeHolder<?> holder : candidates) {
            if (mouseX >= 16 && mouseX <= 16 + rowW
                    && mouseY >= listY && mouseY <= listY + ROW_HEIGHT) {
                onChosen.accept(holder);
                Minecraft.getInstance().setScreen(parent);
                return true;
            }
            listY += ROW_HEIGHT + ROW_PADDING;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) (dy * (ROW_HEIGHT + ROW_PADDING)));
        int maxScroll = Math.max(0,
                candidates.size() * (ROW_HEIGHT + ROW_PADDING) - this.height + 60);
        scroll = Math.min(scroll, maxScroll);
        return true;
    }
}
