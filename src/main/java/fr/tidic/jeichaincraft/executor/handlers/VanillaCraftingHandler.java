package fr.tidic.jeichaincraft.executor.handlers;

import fr.tidic.jeichaincraft.executor.CraftHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Drives the vanilla CraftingMenu (3x3 table) or the player's InventoryMenu (2x2).
 *
 * Strategy: piggyback on the vanilla recipe-book placement packet. The server
 * does all the slot math for us when we call gameMode.handlePlaceRecipe with
 * craftAll=true — it places enough ingredients in the grid for as many crafts
 * as the player's inventory allows. Then we QUICK_MOVE (shift-click) the
 * output slot, transferring everything into the inventory in one server tick.
 *
 * Output slot is always 0 for both InventoryMenu and CraftingMenu in 1.21.1.
 */
public class VanillaCraftingHandler implements CraftHandler {

    private static final int OUTPUT_SLOT = 0;

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof InventoryMenu;
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        if (gm == null) return;
        // craftAll=false → places exactly one set of ingredients. The executor
        // loops this for the number of crafts the plan requested, so the user
        // gets exactly the quantity they asked for (not "as many as fit").
        gm.handlePlaceRecipe(menu.containerId, recipe, /* craftAll = */ false);
    }

    @Override
    public void takeOutput(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        Player player = mc.player;
        if (gm == null || player == null) return;
        gm.handleInventoryMouseClick(menu.containerId, OUTPUT_SLOT, 0, ClickType.QUICK_MOVE, player);
    }
}
