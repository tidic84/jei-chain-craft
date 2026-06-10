package fr.tidic.jeichaincraft.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates everything the player can craft from: the player inventory plus
 * any registered {@link InventorySource} (storage mod terminals, etc).
 */
public class InventoryAnalyzer {

    private static final List<InventorySource> EXTRA_SOURCES = new ArrayList<>();

    /** Compat entry point — e.g. Tom's Simple Storage registers its terminal here. */
    public static void registerSource(InventorySource source) {
        EXTRA_SOURCES.add(source);
    }

    /** Every stack currently reachable: player inventory + extra sources. */
    public List<ItemStack> allStacks() {
        List<ItemStack> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p != null) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) out.add(s);
            }
        }
        for (InventorySource src : EXTRA_SOURCES) src.collect(out);
        return out;
    }

    /** Captures a mutable budget for one tree build. */
    public InventorySnapshot snapshot() {
        return new InventorySnapshot(allStacks());
    }

    public int count(ItemStack target) {
        long total = 0;
        for (ItemStack s : allStacks()) {
            if (ItemStack.isSameItem(s, target)) total += s.getCount();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }
}
