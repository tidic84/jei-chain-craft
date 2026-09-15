package fr.tidic.jeichaincraft.executor;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Pluggable executor for a recipe type. Each handler knows how to drive one
 * kind of container menu (vanilla crafting, furnace, modded machine...).
 *
 * Crafts are executed in batches: the executor asks {@link #planBatch} how
 * many crafts it may attempt at once, places them, waits for the server to
 * stage them ({@link #stagedCrafts}), then takes them all in a single tick.
 * Handlers that cannot batch safely keep the defaults (batch of 1).
 */
public interface CraftHandler {

    /** True if this handler can drive {@code menu}. */
    boolean canHandle(AbstractContainerMenu menu);

    /**
     * Number of crafts to attempt in the next batch, between 1 and
     * {@code remainingCrafts}. Must never exceed what the handler can
     * guarantee will actually be crafted (or later observe via
     * {@link #stagedCrafts}) — progress is counted from it.
     */
    default int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu, int remainingCrafts) {
        return 1;
    }

    /**
     * Begins {@code crafts} crafts of {@code recipe}. Non-blocking — the
     * executor polls the output slot before calling {@link #takeOutput}.
     */
    void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu, int crafts);

    /**
     * How many of the {@code requested} crafts the client currently sees
     * staged and ready to take. The executor waits (up to
     * {@link #batchSettleTicks()}) for this to reach {@code requested}, then
     * takes whatever is staged. Default: trust the plan.
     */
    default int stagedCrafts(AbstractContainerMenu menu, int requested) {
        return requested;
    }

    /**
     * Takes {@code crafts} crafts from the output slot. {@code expectedOutput}
     * is the item the executor expects the craft to produce — handlers that
     * route the result somewhere else (Tom's terminal sending it back to the
     * network) use it to identify the slots they need to move from.
     */
    void takeOutput(AbstractContainerMenu menu, ItemStack expectedOutput, int crafts);

    /** How many ticks to wait between placeIngredients() and takeOutput(). */
    default int placeToTakeTicks() {
        return 4;
    }

    /**
     * Extra ticks, once the output is visible, to wait for
     * {@link #stagedCrafts} to reach the requested count before taking a
     * partial batch.
     */
    default int batchSettleTicks() {
        return 0;
    }

    /** How many ticks to wait after takeOutput() before the next step starts. */
    default int afterTakeTicks() {
        return 2;
    }

    /**
     * Slot index of the recipe result. Vanilla CraftingMenu/InventoryMenu and
     * Tom's CraftingTerminalMenu all expose slot 0 as the output. Override if
     * a modded handler diverges.
     */
    default int outputSlotIndex() {
        return 0;
    }

    /** Menu-aware variant for containers whose slot layout is not fixed. */
    default int outputSlotIndex(AbstractContainerMenu menu) {
        return outputSlotIndex();
    }

    /**
     * Maximum ticks to wait for the output slot to populate before giving up
     * on a craft. Tom's auto-refill round-trip is variable so generous
     * timeouts keep long batches reliable.
     */
    default int outputWaitTimeoutTicks() {
        return 40;
    }

    /**
     * True if the grid is automatically replenished by the container after a
     * take (e.g. Tom's Simple Storage crafting terminal pulls fresh
     * ingredients from the network). When true the executor skips
     * placeIngredients() for every iteration after the first — placing once
     * is enough, takes alone keep producing until the count is reached.
     */
    default boolean gridAutoRefills() {
        return false;
    }
}
