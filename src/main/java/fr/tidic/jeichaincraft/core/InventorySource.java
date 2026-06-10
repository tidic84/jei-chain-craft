package fr.tidic.jeichaincraft.core;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A place the player can draw items from when planning a chain. The player
 * inventory is built in; compat modules (e.g. Tom's Simple Storage terminals)
 * register extra sources via {@link InventoryAnalyzer#registerSource}.
 */
public interface InventorySource {

    /**
     * Appends every stack currently available from this source. Stack counts
     * are honored; counts above the vanilla max stack size are allowed (storage
     * networks report aggregated quantities).
     */
    void collect(List<ItemStack> out);
}
