package fr.tidic.jeichaincraft.compat.tomsstorage;

import com.tom.storagemod.inventory.StoredItemStack;
import com.tom.storagemod.menu.StorageTerminalMenu;
import fr.tidic.jeichaincraft.core.InventorySource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Exposes the contents of an open Tom's Simple Storage terminal (storage or
 * crafting variant — CraftingTerminalMenu extends StorageTerminalMenu) to the
 * chain planner. The menu keeps a client-synced list of every stored stack,
 * which stays live while the chain screen is on top: switching screens only
 * calls Screen.removed(), the container menu itself stays open.
 */
class TomsTerminalSource implements InventorySource {

    @Override
    public void collect(List<ItemStack> out) {
        Player p = Minecraft.getInstance().player;
        if (p == null || !(p.containerMenu instanceof StorageTerminalMenu menu)) return;
        for (StoredItemStack stored : menu.itemList) {
            if (stored == null) continue;
            ItemStack unit = stored.getStack();
            if (unit == null || unit.isEmpty()) continue;
            int count = (int) Math.min(stored.getQuantity(), Integer.MAX_VALUE);
            if (count > 0) out.add(unit.copyWithCount(count));
        }
    }
}
