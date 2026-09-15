package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.core.RecipeLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongBiFunction;

/**
 * Shared batching logic for storage-network crafting terminals (Tom's
 * crafting terminal, Refined Storage crafting grid).
 *
 * Both refill the crafting grid synchronously inside the result slot's
 * onTake: the server pulls one of each consumed ingredient from the network
 * in the same call. So N PICKUP clicks sent in one tick craft N times —
 * provided the grid holds one set and the network holds N-1 more. PICKUP is
 * used instead of QUICK_MOVE because both mods' shift-click crafts a whole
 * stack in one go, overshooting the planned count.
 */
public final class NetworkCraftSupport {

    private final ToLongBiFunction<AbstractContainerMenu, Item> networkCount;

    /**
     * Our own running estimate of network stock per ingredient for the
     * current recipe. The client's network view lags a tick or two behind
     * the server; since we are the ones consuming, min(ledger, live view)
     * never over-estimates what the network can supply.
     */
    private final Map<Item, Long> ledger = new HashMap<>();
    private Identifier ledgerRecipe;
    private int ledgerContainer = -1;

    public NetworkCraftSupport(ToLongBiFunction<AbstractContainerMenu, Item> networkCount) {
        this.networkCount = networkCount;
    }

    public int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu,
                         List<Integer> gridSlotIndices, int remainingCrafts) {
        if (!RecipeLookup.idOf(recipe).equals(ledgerRecipe) || menu.containerId != ledgerContainer) {
            ledger.clear();
            ledgerRecipe = RecipeLookup.idOf(recipe);
            ledgerContainer = menu.containerId;
            // First craft of each step stays single: it stages the grid and
            // gives the network view time to catch up with earlier steps.
            return 1;
        }

        Map<Item, Integer> slotsPerItem = new HashMap<>();
        for (int i : gridSlotIndices) {
            if (i < 0 || i >= menu.slots.size()) continue;
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty()) slotsPerItem.merge(s.getItem(), 1, Integer::sum);
        }
        if (slotsPerItem.isEmpty()) return 1;

        Map<Item, Long> known = new HashMap<>();
        long extraSets = Long.MAX_VALUE;
        for (Map.Entry<Item, Integer> e : slotsPerItem.entrySet()) {
            long live = networkCount.applyAsLong(menu, e.getKey());
            long k = Math.min(live, ledger.getOrDefault(e.getKey(), Long.MAX_VALUE));
            known.put(e.getKey(), k);
            extraSets = Math.min(extraSets, k / e.getValue());
        }

        ItemStack result = RecipeLookup.resultOf(recipe);
        int per = Math.max(1, result.getCount());
        int maxStack = result.isEmpty() ? 64 : result.getMaxStackSize();
        // All outputs of a batch pile up on the cursor before being deposited.
        int byCursor = Math.max(1, maxStack / Math.max(1, per));

        int batch = (int) Math.max(1, Math.min(Math.min(remainingCrafts, byCursor),
                Math.min(Integer.MAX_VALUE, 1 + extraSets)));

        // Each click refills one set from the network, the last one included.
        for (Map.Entry<Item, Integer> e : slotsPerItem.entrySet()) {
            ledger.put(e.getKey(), Math.max(0, known.get(e.getKey()) - (long) batch * e.getValue()));
        }
        return batch;
    }

    /**
     * Takes {@code crafts} crafts from {@code resultSlot} onto the cursor,
     * drops the cursor into the player inventory, then shift-clicks the
     * crafted item back into the network.
     */
    public static void takeIntoNetwork(AbstractContainerMenu menu, int resultSlot,
                                       ItemStack expectedOutput, int crafts) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        Player player = mc.player;
        if (gm == null || player == null || resultSlot < 0) return;

        // Each PICKUP crafts once and stacks onto the cursor; the server has
        // refilled the grid before handling the next click.
        for (int i = 0; i < crafts; i++) {
            gm.handleContainerInput(menu.containerId, resultSlot, 0, ContainerInput.PICKUP, player);
        }

        // Drop the cursor stack into the first empty (or stack-able) player
        // inventory slot so the cursor clears for the next batch.
        Inventory inv = player.getInventory();
        int depositSlot = findDepositSlot(menu, expectedOutput, inv);
        if (depositSlot >= 0) {
            gm.handleContainerInput(menu.containerId, depositSlot, 0, ContainerInput.PICKUP, player);
        }

        // Shift-click every player-inv slot holding the crafted item over to
        // the network. Player inv → network is loop-free on both mods. If the
        // network is full the click is a no-op and the stack stays put.
        if (expectedOutput == null || expectedOutput.isEmpty()) return;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != inv) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != expectedOutput.getItem()) continue;
            gm.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
        }
    }

    private static int findDepositSlot(AbstractContainerMenu menu, ItemStack cursor, Inventory inv) {
        int emptySlot = -1;
        int partialSlot = -1;
        int maxStack = cursor == null ? 64 : cursor.getMaxStackSize();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != inv) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                if (emptySlot < 0) emptySlot = i;
            } else if (cursor != null
                    && ItemStack.isSameItemSameComponents(stack, cursor)
                    && stack.getCount() < maxStack
                    && partialSlot < 0) {
                partialSlot = i;
            }
        }
        // Empty preferred so we don't risk a swap if the partial slot got
        // topped up by another packet between our two clicks.
        return emptySlot >= 0 ? emptySlot : partialSlot;
    }
}
