package fr.tidic.jeichaincraft.core;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Mutable budget of every item the player can draw from, captured once per
 * tree build. Branches {@link #reserve} materials as they claim them, so two
 * branches can never count the same stack twice, and crafting steps
 * {@link #credit} surplus output back so later branches may reuse it.
 *
 * Cheap to {@link #copy} — the tree builder forks a trial budget per recipe
 * attempt and commits it via {@link #replaceWith} only when the subtree
 * resolves.
 */
public final class InventorySnapshot {

    private static final class Entry {
        final ItemStack unit; // count on this stack is meaningless; see count field
        int count;

        Entry(ItemStack unit, int count) {
            this.unit = unit;
            this.count = count;
        }
    }

    private List<Entry> entries = new ArrayList<>();

    public InventorySnapshot(List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) credit(s, s.getCount());
        }
    }

    private InventorySnapshot(InventorySnapshot other) {
        entries = new ArrayList<>(other.entries.size());
        for (Entry e : other.entries) entries.add(new Entry(e.unit, e.count));
    }

    public InventorySnapshot copy() {
        return new InventorySnapshot(this);
    }

    /** Adopts {@code other}'s state — commit of a successful trial budget. */
    public void replaceWith(InventorySnapshot other) {
        this.entries = other.entries;
    }

    public int available(Predicate<ItemStack> matcher) {
        long total = 0;
        for (Entry e : entries) {
            if (e.count > 0 && matcher.test(e.unit)) total += e.count;
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    /** Consumes up to {@code amount} matching items; returns how many were actually reserved. */
    public int reserve(Predicate<ItemStack> matcher, int amount) {
        int reserved = 0;
        for (Entry e : entries) {
            if (reserved >= amount) break;
            if (e.count <= 0 || !matcher.test(e.unit)) continue;
            int take = Math.min(e.count, amount - reserved);
            e.count -= take;
            reserved += take;
        }
        return reserved;
    }

    public void credit(ItemStack unit, int amount) {
        if (amount <= 0) return;
        for (Entry e : entries) {
            if (ItemStack.isSameItemSameComponents(e.unit, unit)) {
                e.count += amount;
                return;
            }
        }
        entries.add(new Entry(unit.copyWithCount(1), amount));
    }
}
