package fr.tidic.jeichaincraft.executor.handlers;

import fr.tidic.jeichaincraft.core.RecipeLookup;
import fr.tidic.jeichaincraft.executor.CraftHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Drives the vanilla CraftingMenu (3x3 table) or the player's InventoryMenu (2x2).
 *
 * Strategy: piggyback on the vanilla recipe-book placement packet. With
 * craftAll=false, ServerPlaceRecipe adds exactly one more set on top of a
 * grid that already matches the recipe, so N packets sent in the same tick
 * stage exactly N sets (or fewer if ingredients run out). A single QUICK_MOVE
 * on the output then crafts every staged set in one server tick.
 *
 * The staged count is read back from the synced grid (every ingredient slot
 * holds one item per set) so progress reflects what the server really placed.
 *
 * Output slot is always 0 for both InventoryMenu and CraftingMenu in 1.21.1.
 */
public class VanillaCraftingHandler implements CraftHandler {

    private static final int OUTPUT_SLOT = 0;
    /** Ingredient stacks cap at 64 per grid slot anyway. */
    private static final int MAX_BATCH = 64;

    @Override
    public boolean canHandle(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof InventoryMenu;
    }

    @Override
    public int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu, int remainingCrafts) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 1;
        int per = RecipeLookup.outputCount(recipe);
        ItemStack result = recipe.value().getResultItem(player.level().registryAccess());
        // A shift-click stops crafting once the inventory is full, leaving
        // sets in the grid that we would wrongly count — cap by output room.
        int room = outputRoom(menu, player.getInventory(), result);
        int byRoom = room / Math.max(1, per);
        return Math.max(1, Math.min(Math.min(remainingCrafts, MAX_BATCH), byRoom));
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu, int crafts) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        if (gm == null) return;
        // craftAll=false → one extra set per packet. craftAll=true would pack
        // the grid to the max and overshoot the planned count.
        for (int i = 0; i < crafts; i++) {
            gm.handlePlaceRecipe(menu.containerId, recipe, /* craftAll = */ false);
        }
    }

    @Override
    public int stagedCrafts(AbstractContainerMenu menu, int requested) {
        if (!(menu instanceof RecipeBookMenu<?, ?> rb)) return requested;
        int gridSlots = rb.getGridWidth() * rb.getGridHeight() + 1;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < gridSlots && i < menu.slots.size(); i++) {
            if (i == rb.getResultSlotIndex()) continue;
            ItemStack s = menu.slots.get(i).getItem();
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
        gm.handleInventoryMouseClick(menu.containerId, OUTPUT_SLOT, 0, ClickType.QUICK_MOVE, player);
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
