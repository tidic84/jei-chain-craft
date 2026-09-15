package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.executor.handlers.compat.TomsStorageReader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Counts items the player has access to as a destination for crafted output
 * or a source of ingredients. Two sources only:
 *
 *  1. The player's own inventory ({@code player.getInventory()}).
 *  2. The Tom's Simple Storage network behind the open terminal, read
 *     reflectively (no hard dependency).
 *
 * We deliberately do NOT walk {@code menu.slots}. The menu wraps the player
 * inventory anyway (so we would double-count), and it also exposes the
 * crafting grid and the recipe-output slot — both of which hold *in-flight*
 * stacks while a craft is in progress. Counting them produced spurious
 * baselines in the executor: PLACE put an output stack in slot 0, baseline
 * captured it, TAKE moved it into the inventory with no change in total →
 * the step looked like it had crafted zero items and stalled.
 */
public class InventoryAnalyzer {

    public int count(ItemStack target) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return 0;
        long total = countAvailable(p, p.containerMenu, target.getItem(), target);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static long countAvailable(Player player,
                                      AbstractContainerMenu menu,
                                      net.minecraft.world.item.Item item,
                                      ItemStack matchAgainst) {
        long total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            boolean ok = matchAgainst != null
                    ? ItemStack.isSameItem(s, matchAgainst)
                    : s.getItem() == item;
            if (ok) total += s.getCount();
        }
        if (menu != null) {
            total += TomsStorageReader.countInNetwork(menu, item);        }
        return total;
    }
}
