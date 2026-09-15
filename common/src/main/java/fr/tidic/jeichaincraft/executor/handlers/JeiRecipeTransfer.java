package fr.tidic.jeichaincraft.executor.handlers;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.core.RecipeLookup;
import fr.tidic.jeichaincraft.jei.JEIChainCraftPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Optional;

/**
 * Places a crafting recipe in the open container through JEI's recipe
 * transfer — the same action as JEI's "+" button.
 *
 * Since 1.21.2 the vanilla placement packet takes a recipe-book display id
 * the client cannot derive from a recipe, so placement goes through JEI. It
 * also covers storage terminals for free: Tom's Simple Storage and Refined
 * Storage register their own JEI transfer handlers for their crafting grids.
 */
public final class JeiRecipeTransfer {
    private JeiRecipeTransfer() {}

    /**
     * @param maxTransfer false places one set; true fills the grid as far as
     *                    the available ingredients allow (like shift-clicking "+").
     * @return true if JEI accepted the transfer.
     */
    @SuppressWarnings({"unchecked", "removal"})
    public static boolean place(RecipeHolder<?> recipe, AbstractContainerMenu menu, boolean maxTransfer) {
        IJeiRuntime runtime = JEIChainCraftPlugin.runtime();
        Player player = Minecraft.getInstance().player;
        if (runtime == null || player == null) return false;

        IRecipeManager recipeManager = runtime.getRecipeManager();
        IRecipeCategory<RecipeHolder<CraftingRecipe>> category = recipeManager.getRecipeCategory(RecipeTypes.CRAFTING);
        RecipeHolder<CraftingRecipe> holder = (RecipeHolder<CraftingRecipe>) recipe;

        Optional<IRecipeLayoutDrawable<RecipeHolder<CraftingRecipe>>> layout = recipeManager.createRecipeLayoutDrawable(
                category, holder, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        if (layout.isEmpty()) {
            JEIChainCraftMod.LOGGER.warn("JEI could not lay out recipe {}", RecipeLookup.idOf(recipe));
            return false;
        }

        Optional<IRecipeTransferHandler<AbstractContainerMenu, RecipeHolder<CraftingRecipe>>> handler =
                runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category);
        if (handler.isEmpty()) {
            JEIChainCraftMod.LOGGER.warn("No JEI transfer handler for menu {}", menu.getClass().getName());
            return false;
        }

        IRecipeTransferError error = handler.get().transferRecipe(
                menu, holder, layout.get().getRecipeSlotsView(), player, maxTransfer, true);
        if (error != null && !error.getType().allowsTransfer) {
            JEIChainCraftMod.LOGGER.warn("JEI refused transfer of {}: {}", RecipeLookup.idOf(recipe), error.getType());
            return false;
        }
        return true;
    }
}
