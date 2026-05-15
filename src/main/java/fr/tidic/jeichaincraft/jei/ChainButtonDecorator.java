package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.core.InventoryAnalyzer;
import fr.tidic.jeichaincraft.core.PreferenceManager;
import fr.tidic.jeichaincraft.ui.RecipeTreeScreen;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a small "Chain" button overlay on every supported recipe category.
 * Activation is via keypress (see {@link RecipeScreenClickHandler}) because
 * IRecipeCategoryDecorator does not currently expose a click hook.
 */
public class ChainButtonDecorator<T> implements IRecipeCategoryDecorator<T> {

    private static final InventoryAnalyzer INVENTORY = new InventoryAnalyzer();
    private static final PreferenceManager PREFS = new PreferenceManager();

    @Override
    public void draw(T recipe,
                     IRecipeCategory<T> category,
                     IRecipeSlotsView view,
                     GuiGraphics graphics,
                     double mouseX, double mouseY) {
        int x = category.getWidth() - 18;
        int y = 0;

        boolean hover = mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
        int color = hover ? 0xFFFFA040 : 0xFF606060;
        graphics.fill(x, y, x + 16, y + 16, color);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF202020);
        graphics.drawString(Minecraft.getInstance().font, "C", x + 5, y + 4, 0xFFFFFFFF, false);

        if (hover) {
            graphics.renderTooltip(Minecraft.getInstance().font,
                    Component.translatable("jeichaincraft.button.chain.tooltip"),
                    (int) mouseX, (int) mouseY);
        }
    }

    public static void openTreeFor(ItemStack output) {
        int qty = Math.max(1, output.getCount());
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(output, qty, INVENTORY, PREFS));
    }
}
