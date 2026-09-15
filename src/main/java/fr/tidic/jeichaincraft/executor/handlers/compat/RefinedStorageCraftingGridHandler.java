package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.core.ItemId;
import fr.tidic.jeichaincraft.core.PreferenceManager;
import fr.tidic.jeichaincraft.executor.CraftHandler;
import fr.tidic.jeichaincraft.ui.ChainScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Soft-compat handler for the Refined Storage 2 crafting grid.
 *
 * Placement goes through RS's own recipe transfer (the grid is not a
 * RecipeBookMenu): the server clears the matrix and puts one item per slot,
 * taken from the network first, then from the player inventory. Taking the
 * result makes RS extract a replacement for every single-item matrix slot
 * from the network in the same server call, so batching works exactly like
 * Tom's terminal — see {@link NetworkCraftSupport}.
 */
public class RefinedStorageCraftingGridHandler implements CraftHandler {

    private static final int GRID_SIZE = 3;

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
        RefinedStorageReader.transferRecipe(menu, matrixFor(recipe));
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

    /**
     * 3x3 matrix of accepted items. Shaped recipes keep their layout; others
     * fill slots in order. A tag slot with a user preference only offers the
     * chosen item, otherwise RS picks whichever alternative it has most of.
     */
    private static List<List<ItemStack>> matrixFor(RecipeHolder<?> recipe) {
        List<List<ItemStack>> matrix = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) matrix.add(List.of());

        List<Ingredient> ingredients = recipe.value().getIngredients();
        int width = recipe.value() instanceof ShapedRecipe shaped ? shaped.getWidth() : GRID_SIZE;
        PreferenceManager prefs = ChainScreens.prefs();

        for (int idx = 0; idx < ingredients.size(); idx++) {
            int row = idx / width;
            int col = idx % width;
            if (row >= GRID_SIZE || col >= GRID_SIZE) continue;
            Ingredient ing = ingredients.get(idx);
            if (ing.isEmpty()) continue;

            List<ItemStack> options = Arrays.asList(ing.getItems());
            ResourceLocation pref = prefs == null ? null : prefs.ingredientPref(recipe.id(), idx);
            if (pref != null) {
                for (ItemStack opt : options) {
                    if (ItemId.of(opt).equals(pref)) {
                        options = List.of(opt);
                        break;
                    }
                }
            }
            matrix.set(row * GRID_SIZE + col, options);
        }
        return matrix;
    }
}
