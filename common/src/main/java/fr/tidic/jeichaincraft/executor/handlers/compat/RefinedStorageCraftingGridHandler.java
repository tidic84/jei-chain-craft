package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.executor.CraftHandler;
import fr.tidic.jeichaincraft.executor.handlers.JeiRecipeTransfer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Soft-compat handler for the Refined Storage crafting grid.
 *
 * Placement goes through JEI's recipe transfer, for which RS registers its
 * own handler (network first, then player inventory). Taking the result makes
 * RS extract a replacement for every single-item matrix slot from the network
 * in the same server call, so batching works like Tom's terminal — see
 * {@link NetworkCraftSupport}.
 */
public class RefinedStorageCraftingGridHandler implements CraftHandler {

    private final NetworkCraftSupport support = new NetworkCraftSupport(RefinedStorageReader::countInNetwork);

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return RefinedStorageReader.isCraftingGrid(menu);
    }

    @Override
    public int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu, int remainingCrafts) {
        return support.planBatch(recipe, menu, RefinedStorageReader.matrixSlotIndices(menu), remainingCrafts);
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu, int crafts) {
        // One set only: the batch is produced by repeated takes, each one
        // refilled from the network by RS.
        JeiRecipeTransfer.place(recipe, menu, false);
    }

    @Override
    public void takeOutput(AbstractContainerMenu menu, ItemStack expectedOutput, int crafts) {
        NetworkCraftSupport.takeIntoNetwork(menu, RefinedStorageReader.resultSlotIndex(menu), expectedOutput, crafts);
    }

    @Override
    public int outputSlotIndex(AbstractContainerMenu menu) {
        return RefinedStorageReader.resultSlotIndex(menu);
    }

    @Override
    public int placeToTakeTicks() {
        return 2;
    }

    @Override
    public int outputWaitTimeoutTicks() {
        return 80;
    }
}
