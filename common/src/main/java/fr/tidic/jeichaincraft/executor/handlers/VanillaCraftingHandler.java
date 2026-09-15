package fr.tidic.jeichaincraft.executor.handlers;

import fr.tidic.jeichaincraft.core.RecipeLookup;
import fr.tidic.jeichaincraft.executor.CraftHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Drives the vanilla CraftingMenu (3x3 table) or the player's InventoryMenu (2x2).
 *
 * Placement goes through JEI's recipe transfer (see {@link JeiRecipeTransfer}).
 * JEI either places one set or fills the grid to the max, so batches are
 * either a single craft or a full grid: a full grid holds at most
 * {@link #FULL_GRID} sets, so it is only requested when at least that many
 * crafts remain — the executor can then never overshoot.
 *
 * The staged count is read back from the synced grid (every ingredient slot
 * holds one item per set), and a single QUICK_MOVE on the output crafts every
 * staged set in one server tick.
 */
public class VanillaCraftingHandler implements CraftHandler {

    /** Ingredient stacks cap at 64 per grid slot. */
    private static final int FULL_GRID = 64;

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof InventoryMenu;
    }

    @Override
    public int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu, int remainingCrafts) {
        Player player = Minecraft.getInstance().player;
        if (player == null || remainingCrafts < FULL_GRID) return 1;
        // A shift-click stops crafting once the inventory is full, leaving
        // sets in the grid that we would wrongly count — require output room.
        ItemStack result = RecipeLookup.resultOf(recipe);
        int per = Math.max(1, result.getCount());
        return outputRoom(menu, player.getInventory(), result) >= FULL_GRID * per ? FULL_GRID : 1;
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu, int crafts) {
        JeiRecipeTransfer.place(recipe, menu, crafts > 1);
    }

    @Override
    public int stagedCrafts(AbstractContainerMenu menu, int requested) {
        if (!(menu instanceof AbstractCraftingMenu crafting)) return requested;
        int min = Integer.MAX_VALUE;
        for (Slot slot : crafting.getInputGridSlots()) {
            ItemStack s = slot.getItem();
            if (!s.isEmpty()) min = Math.min(min, s.getCount());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    @Override
    public int batchSettleTicks() {
        return 6;
    }

    @Override
    public void takeOutput(AbstractContainerMenu menu, ItemStack expectedOutput, int crafts) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        Player player = mc.player;
        if (gm == null || player == null) return;
        gm.handleContainerInput(menu.containerId, outputSlotIndex(menu), 0, ContainerInput.QUICK_MOVE, player);
    }

    @Override
    public int outputSlotIndex(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu crafting ? crafting.getResultSlot().index : 0;
    }

    /** Output items that fit in the main inventory + hotbar. */
    private static int outputRoom(AbstractContainerMenu menu, Inventory inv, ItemStack result) {
        if (result.isEmpty()) return 0;
        int max = result.getMaxStackSize();
        int room = 0;
        for (Slot slot : menu.slots) {
            if (slot.container != inv || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) continue;
            ItemStack s = slot.getItem();
            if (s.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(s, result)) room += Math.max(0, max - s.getCount());
        }
        return room;
    }
}
