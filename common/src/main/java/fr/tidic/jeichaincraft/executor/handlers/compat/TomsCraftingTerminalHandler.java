package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.executor.CraftHandler;
import fr.tidic.jeichaincraft.executor.handlers.JeiRecipeTransfer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft-compat handler for Tom's Simple Storage Mod crafting terminal.
 *
 * No compile dependency on Tom's — detection is by class name so the mod
 * loads fine whether Tom's is installed or not. Placement goes through JEI's
 * recipe transfer, for which Tom's registers its own handler that pulls the
 * ingredients from the storage network. Batching and output handling are
 * shared with the Refined Storage handler — see {@link NetworkCraftSupport}.
 *
 * If Tom's renames its menu class the detection will silently miss and
 * the player will see {@code error.no_handler}.
 */
public class TomsCraftingTerminalHandler implements CraftHandler {

    private static final int OUTPUT_SLOT = 0;

    private final NetworkCraftSupport support = new NetworkCraftSupport(TomsStorageReader::countInNetwork);

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        if (menu == null) return false;
        String name = menu.getClass().getName();
        if (!name.startsWith("com.tom.storagemod")) return false;
        return name.endsWith("CraftingTerminalMenu") || name.endsWith("CraftingTerminalContainer");
    }

    @Override
    public int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu, int remainingCrafts) {
        // Tom's refills the grid inside the result slot's onTake, and PICKUP
        // is not throttled on the server, so repeated takes each craft once.
        List<Integer> grid = new ArrayList<>();
        if (menu instanceof AbstractCraftingMenu crafting) {
            for (Slot slot : crafting.getInputGridSlots()) grid.add(slot.index);
        } else {
            for (int i = 1; i <= 9 && i < menu.slots.size(); i++) grid.add(i);
        }
        return support.planBatch(recipe, menu, grid, remainingCrafts);
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu, int crafts) {
        // One set only: the batch comes from repeated takes, each refilled
        // from the network by Tom's.
        JeiRecipeTransfer.place(recipe, menu, false);
    }

    @Override
    public void takeOutput(AbstractContainerMenu menu, ItemStack expectedOutput, int crafts) {
        NetworkCraftSupport.takeIntoNetwork(menu, OUTPUT_SLOT, expectedOutput, crafts);
    }

    @Override
    public int placeToTakeTicks() {
        return 2;
    }

    @Override
    public int outputWaitTimeoutTicks() {
        // Tom's network round-trip can run long on heavy servers; cap at
        // 4 seconds to keep stuck steps from blocking the whole plan forever.
        return 80;
    }
}
