package fr.tidic.jeichaincraft.hud;

import fr.tidic.jeichaincraft.core.CraftPlanner;
import fr.tidic.jeichaincraft.core.InventoryAnalyzer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Pinned shopping list shown on the HUD. Captured once from a chain tree's
 * base resources; counts on the HUD refresh live against the player's
 * inventory so the user can watch the list shrink as they farm.
 *
 * Singleton process-wide. Pinning a new list replaces the previous one.
 */
public final class PinnedFarmList {
    public record Entry(ItemStack stack, int needed) {}
    public record DisplayEntry(ItemStack stack, int needed, int have) {
        public boolean satisfied() { return have >= needed; }
    }

    private static final List<Entry> entries = new ArrayList<>();
    private static final InventoryAnalyzer INVENTORY = new InventoryAnalyzer();

    private PinnedFarmList() {}

    public static void set(List<CraftPlanner.BaseResource> resources) {
        entries.clear();
        for (CraftPlanner.BaseResource r : resources) {
            entries.add(new Entry(r.stack(), r.needed()));
        }
    }

    public static void clear() {
        entries.clear();
    }

    public static boolean isEmpty() {
        return entries.isEmpty();
    }

    public static int size() {
        return entries.size();
    }

    /** Returns the pinned items with live have-counts for rendering. */
    public static List<DisplayEntry> snapshot() {
        List<DisplayEntry> result = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            result.add(new DisplayEntry(e.stack, e.needed, INVENTORY.count(e.stack)));
        }
        return result;
    }
}
