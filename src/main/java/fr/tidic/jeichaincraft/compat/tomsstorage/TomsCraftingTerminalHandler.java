package fr.tidic.jeichaincraft.compat.tomsstorage;

import com.tom.storagemod.menu.CraftingTerminalMenu;
import fr.tidic.jeichaincraft.executor.CraftHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Drives the Tom's Simple Storage crafting terminal.
 *
 * Slot layout (CraftingTerminalMenu): 0 = result, 1..9 = 3x3 grid,
 * 10..45 = player inventory, 46 = offhand. The storage grid itself is not
 * made of vanilla slots, so it doesn't shift these indices.
 *
 * Placement reuses the vanilla recipe-book packet: the menu extends
 * RecipeBookMenu and its handlePlacement pulls ingredients from the player
 * inventory AND the storage network. Taking the result shift-clicks slot 0,
 * which the menu routes to the player inventory.
 *
 * Intermediate outputs are stashed back into the network: quick-moving a
 * player-inventory slot in a terminal pushes it into storage, and later steps
 * pull it back out through the recipe placer.
 */
class TomsCraftingTerminalHandler implements CraftHandler {

    private static final int OUTPUT_SLOT = 0;
    private static final int PLAYER_SLOTS_START = 10;

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof CraftingTerminalMenu;
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu) {
        MultiPlayerGameMode gm = Minecraft.getInstance().gameMode;
        if (gm == null) return;
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

    @Override
    public void stashOutput(AbstractContainerMenu menu, ItemStack output, int amount) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        Player player = mc.player;
        if (gm == null || player == null) return;
        // Whole slots are pushed until the crafted amount is covered. If the
        // network is full the server returns the remainder to the slot —
        // vanilla behavior, nothing is lost.
        int moved = 0;
        for (int i = PLAYER_SLOTS_START; i < menu.slots.size() && moved < amount; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack in = slot.getItem();
            if (in.isEmpty() || !ItemStack.isSameItem(in, output)) continue;
            moved += in.getCount();
            gm.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player);
        }
    }

    @Override
    public int placeToTakeTicks() {
        // Ingredients may travel from the storage network, not just the
        // player inventory — give the server a little more slack than the
        // vanilla table before the first output check.
        return 6;
    }
}
