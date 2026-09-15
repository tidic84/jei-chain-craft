package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.client.KeyBindings;
import fr.tidic.jeichaincraft.core.ItemId;
import fr.tidic.jeichaincraft.ui.ChainScreens;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Handles the "open chain tree" hotkey from anywhere it can resolve a target
 * stack. Pickup order:
 *   1. Slot under the mouse on a vanilla container screen (inventory, chest,
 *      crafting table, etc.). Works without JEI being involved at all.
 *   2. The JEI recipe view.
 *   3. The JEI ingredient list overlay (right side panel).
 *   4. The JEI bookmark overlay.
 *
 * There is intentionally no recipe-view decorator here: the feature is
 * hotkey-only, so no "C" is drawn on top of JEI's recipe UI.
 */
public class ChainKeyHandler {

    private static IJeiRuntime jeiRuntime;

    public static void bindJeiRuntime(IJeiRuntime rt) {
        jeiRuntime = rt;
    }

    /**
     * Called by the loader entry points when a key is pressed in a screen.
     * Returns true when the key opened the chain tree and must be consumed.
     */
    public static boolean onKeyPressed(Screen screen, int keyCode, int scanCode) {
        if (!KeyBindings.matches(keyCode, scanCode)) return false;

        ItemStack hovered = pickFromScreen(screen);
        JEIChainCraftMod.LOGGER.info("C pressed; pickHovered -> {} (count={})",
                hovered.isEmpty() ? "EMPTY" : ItemId.of(hovered), hovered.getCount());
        if (hovered.isEmpty()) return false;

        ChainScreens.openTreeFor(hovered);
        return true;
    }

    /**
     * Vanilla keeps the hovered slot in a protected field; each loader exposes
     * it differently (NeoForge getSlotUnderMouse(), Fabric access widener), so
     * the entry points provide the lookup.
     */
    private static Function<AbstractContainerScreen<?>, Slot> hoveredSlotLookup = screen -> null;

    public static void setHoveredSlotLookup(Function<AbstractContainerScreen<?>, Slot> lookup) {
        hoveredSlotLookup = lookup;
    }

    private static ItemStack pickFromScreen(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = hoveredSlotLookup.apply(container);
            if (slot != null) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) return stack;
            }
        }
        return pickFromJei();
    }

    private static ItemStack pickFromJei() {
        if (jeiRuntime == null) return ItemStack.EMPTY;
        ItemStack recipes = jeiRuntime.getRecipesGui()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                .orElse(ItemStack.EMPTY);
        if (!recipes.isEmpty()) return recipes;

        ItemStack list = jeiRuntime.getIngredientListOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (list != null && !list.isEmpty()) return list;
        ItemStack book = jeiRuntime.getBookmarkOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        return book != null ? book : ItemStack.EMPTY;
    }
}
