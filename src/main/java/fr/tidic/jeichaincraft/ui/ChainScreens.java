package fr.tidic.jeichaincraft.ui;

import fr.tidic.jeichaincraft.core.InventoryAnalyzer;
import fr.tidic.jeichaincraft.core.PreferenceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Single entry point for opening the chain tree screen. The analyzer and the
 * preference manager are kept as process-wide singletons so the user's recipe
 * choices survive across openings (still in-memory only — disk persistence
 * is a follow-up).
 */
public final class ChainScreens {
    private static final InventoryAnalyzer INVENTORY = new InventoryAnalyzer();
    private static final PreferenceManager PREFS = new PreferenceManager();

    private ChainScreens() {}

    public static void openTreeFor(ItemStack output) {
        int qty = Math.max(1, output.getCount());
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(output, qty, INVENTORY, PREFS));
    }
}
