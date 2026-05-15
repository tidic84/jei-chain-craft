package fr.tidic.jeichaincraft.executor;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Pluggable executor for a recipe type. Each handler knows how to drive one
 * kind of container menu (vanilla crafting, furnace, modded machine...).
 */
public interface CraftHandler {

    /** True if this handler can drive {@code menu}. */
    boolean canHandle(AbstractContainerMenu menu);

    /**
     * Begins one craft of {@code recipe} (with craftAll=true so the server
     * places as many ingredients as available). Non-blocking — the executor
     * waits a few ticks before calling {@link #takeOutput} to let the server
     * push the result back.
     */
    void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu);

    /** Shift-click the output slot, transferring all crafted items to the player inventory. */
    void takeOutput(AbstractContainerMenu menu);

    /** How many ticks to wait between placeIngredients() and takeOutput(). */
    default int placeToTakeTicks() {
        return 4;
    }

    /** How many ticks to wait after takeOutput() before the next step starts. */
    default int afterTakeTicks() {
        return 2;
    }
}
