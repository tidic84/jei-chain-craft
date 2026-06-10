package fr.tidic.jeichaincraft.executor;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Pluggable executor for a recipe type. Each handler knows how to drive one
 * kind of container menu (vanilla crafting, storage mod terminal...).
 */
public interface CraftHandler {

    /** True if this handler can drive {@code menu}. */
    boolean canHandle(AbstractContainerMenu menu);

    /**
     * Places exactly one set of ingredients for {@code recipe} in the grid.
     * Non-blocking — the executor polls {@link #peekOutput} until the server
     * pushes the result back, then calls {@link #takeOutput}.
     */
    void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu);

    /** Current content of the output slot — lets the executor verify the craft actually happened. */
    default ItemStack peekOutput(AbstractContainerMenu menu) {
        return menu.slots.isEmpty() ? ItemStack.EMPTY : menu.getSlot(0).getItem();
    }

    /** Shift-click the output slot, transferring the crafted items to the player inventory. */
    void takeOutput(AbstractContainerMenu menu);

    /**
     * Called when an intermediate step finishes (never for the final target):
     * handlers backed by a storage network move roughly {@code amount} of
     * {@code output} from the player inventory into storage. Default: no-op.
     */
    default void stashOutput(AbstractContainerMenu menu, ItemStack output, int amount) {}

    /** How many ticks to wait between placeIngredients() and the first output check. */
    default int placeToTakeTicks() {
        return 4;
    }

    /** How many ticks to wait after takeOutput() before the next step starts. */
    default int afterTakeTicks() {
        return 2;
    }
}
