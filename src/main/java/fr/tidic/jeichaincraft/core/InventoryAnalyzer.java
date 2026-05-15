package fr.tidic.jeichaincraft.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class InventoryAnalyzer {

    public int count(ItemStack target) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return 0;
        Inventory inv = p.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItem(s, target)) {
                total += s.getCount();
            }
        }
        return total;
    }
}
