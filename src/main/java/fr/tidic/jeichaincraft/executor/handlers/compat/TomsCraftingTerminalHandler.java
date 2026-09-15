package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.executor.CraftHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft-compat handler for Tom's Simple Storage Mod crafting terminal.
 *
 * No compile dependency on Tom's — detection is by class name so the mod
 * loads fine whether Tom's is installed or not. The terminal extends
 * {@link RecipeBookMenu} which lets us reuse the vanilla recipe-book
 * placement packet: the server moves items from the connected storage
 * network into the grid. Batching and output handling live in
 * {@link NetworkCraftSupport} so other storage terminals can reuse them.
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
        if (!name.endsWith("CraftingTerminalMenu")
                && !name.endsWith("CraftingTerminalContainer")) return false;
        return menu instanceof RecipeBookMenu;
    }

    @Override
    public int planBatch(RecipeHolder<?> recipe, AbstractContainerMenu menu, int remainingCrafts) {
        // Tom's refills the grid inside Result.onTake → craft(), and PICKUP
        // does not go through canCraft()/craftingCooldown (that only gates
        // shift-click), so nothing on the server throttles repeated takes.
        if (!(menu instanceof RecipeBookMenu<?, ?> rb)) return 1;
        List<Integer> grid = new ArrayList<>();
        int gridSlots = rb.getGridWidth() * rb.getGridHeight() + 1;
        for (int i = 0; i < gridSlots; i++) {
            if (i != rb.getResultSlotIndex()) grid.add(i);
        }
        return support.planBatch(recipe, menu, grid, remainingCrafts);
    }

    @Override
    public void placeIngredients(RecipeHolder<?> recipe, AbstractContainerMenu menu, int crafts) {
        Minecraft mc = Minecraft.getInstance();
        MultiPlayerGameMode gm = mc.gameMode;
        if (gm == null) return;
        // craftAll=false → exactly one set; the batch comes from repeated
        // takes, each refilled from the network by Tom's.
        gm.handlePlaceRecipe(menu.containerId, recipe, /* craftAll = */ false);
    }

    @Override
    public boolean gridAutoRefills() {
        // Conservative: even though Tom's terminal refills the grid on its
        // own after a take, we keep one PLACE per batch. Relying on the
        // auto-refill timing caused over-crafting when the executor's poll
        // saw the slot ready before the server had reached our intended
        // count, and the failure mode was silent (more outputs than asked).
        return false;
    }

    @Override
    public void takeOutput(AbstractContainerMenu menu, ItemStack expectedOutput, int crafts) {
        NetworkCraftSupport.takeIntoNetwork(menu, OUTPUT_SLOT, expectedOutput, crafts);
    }

    @Override
    public int placeToTakeTicks() {
        // Minimum ticks before we start polling the output slot. The executor
        // then waits up to outputWaitTimeoutTicks for the slot to populate.
        return 2;
    }

    @Override
    public int outputWaitTimeoutTicks() {
        // Tom's network round-trip can run long on heavy servers; cap at
        // 4 seconds to keep stuck steps from blocking the whole plan forever.
        return 80;
    }
}
