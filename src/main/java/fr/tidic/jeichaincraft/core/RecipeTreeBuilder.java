package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Recursive tree builder with cycle detection + depth cap.
 *
 * Semantics:
 *  - The {@code amountToCraft} argument is the number of {@code target} items
 *    the user wants this run to produce. Existing inventory of the target is
 *    NOT subtracted — pressing "craft 2 gearboxes" while already holding one
 *    crafts 2 more, ending with 3 total.
 *  - For ingredient sub-nodes, inventory IS considered: if the player already
 *    has enough planks, the planks node becomes a HAVE leaf and we do not
 *    recurse on its sub-ingredients.
 *
 * Cycle detection: track visited item ids along the current recursion path
 * and emit a CYCLE leaf if a recipe would revisit one.
 */
public class RecipeTreeBuilder {
    private static final int DEFAULT_MAX_DEPTH = 16;

    private final InventoryAnalyzer inventory;
    private final PreferenceManager prefs;
    private final int maxDepth;

    public RecipeTreeBuilder(InventoryAnalyzer inventory, PreferenceManager prefs) {
        this(inventory, prefs, DEFAULT_MAX_DEPTH);
    }

    public RecipeTreeBuilder(InventoryAnalyzer inventory, PreferenceManager prefs, int maxDepth) {
        this.inventory = inventory;
        this.prefs = prefs;
        this.maxDepth = maxDepth;
    }

    public RecipeNode build(ItemStack target, int amountToCraft) {
        JEIChainCraftMod.LOGGER.info("=== build root {} amountToCraft={} ===",
                ItemId.of(target), amountToCraft);
        return build(target, amountToCraft, true, new HashSet<>(), 0);
    }

    private RecipeNode build(ItemStack target, int needed, boolean isRoot,
                             Set<ResourceLocation> path, int depth) {
        RecipeNode node = new RecipeNode(target, needed);
        node.have = inventory.count(target);
        node.isRoot = isRoot;

        // For ingredients, "we already have enough" is a HAVE leaf — no crafting
        // needed for this subtree. For the root, the user explicitly asked to
        // craft this many, regardless of what they currently hold.
        if (!isRoot && node.have >= needed) {
            node.status = NodeStatus.HAVE;
            return node;
        }

        if (depth >= maxDepth) {
            node.status = NodeStatus.MISSING;
            return node;
        }

        ResourceLocation itemId = ItemId.of(target);
        if (path.contains(itemId)) {
            node.status = NodeStatus.CYCLE;
            return node;
        }

        List<RecipeHolder<?>> candidates = RecipeLookup.recipesProducing(target);
        if (candidates.isEmpty()) {
            node.status = NodeStatus.MISSING;
            return node;
        }

        // Drop candidates whose every slot only accepts an item already on the
        // recursion path — e.g. a modded "iron_block → 9 iron" recipe pulled
        // in to satisfy iron, which would then need iron_block, which needs
        // iron again. Without this filter the chosen recipe is the first one
        // found, even if it is the unusable cyclic one.
        List<RecipeHolder<?>> usable = new ArrayList<>();
        for (RecipeHolder<?> h : candidates) {
            if (!alwaysCycles(h, path)) usable.add(h);
        }
        if (usable.isEmpty()) {
            node.status = NodeStatus.MISSING;
            return node;
        }

        node.alternatives = usable.size() - 1;
        RecipeHolder<?> chosen = prefs.choose(itemId, usable);
        node.recipeId = chosen.id().toString();
        JEIChainCraftMod.LOGGER.info("  d={} chose {} for {} (alternatives={})",
                depth, node.recipeId, itemId, node.alternatives);

        int perCraft = RecipeLookup.outputCount(chosen);
        int amountToProduce = isRoot ? needed : Math.max(0, needed - node.have);
        node.crafts = (amountToProduce + perCraft - 1) / perCraft;

        path.add(itemId);
        // Aggregate slots by the resolved item — 4 cogwheel slots × 1 each
        // need to show as one "0/4 cogwheels" child, not four independent
        // "have/1" checks that each look satisfied while the recipe as a
        // whole cannot be filled. Insertion order is preserved so the tree
        // still reads in slot order.
        LinkedHashMap<ResourceLocation, IngredientGroup> groups = new LinkedHashMap<>();
        for (RecipeLookup.IngredientSlot slot : RecipeLookup.ingredientSlots(chosen)) {
            ItemStack ing = RecipeLookup.resolveSlot(chosen, slot, prefs, inventory);
            ResourceLocation id = ItemId.of(ing);
            IngredientGroup g = groups.get(id);
            if (g == null) {
                groups.put(id, new IngredientGroup(ing, slot, ing.getCount()));
            } else {
                g.totalPerCraft += ing.getCount();
                g.slotIndices.add(slot.slotIndex());
            }
        }

        boolean anyMissing = false;
        for (IngredientGroup g : groups.values()) {
            int ingNeed = g.totalPerCraft * node.crafts;
            RecipeNode child = build(g.stack, ingNeed, false, path, depth + 1);
            child.parentRecipeId = chosen.id();
            child.parentSlotIndices = g.slotIndices;
            if (g.firstSlot.isTag()) child.ingredientOptions = g.firstSlot.options();
            node.children.add(child);
            if (child.status == NodeStatus.MISSING || child.status == NodeStatus.CYCLE) {
                anyMissing = true;
            }
        }
        path.remove(itemId);

        node.status = anyMissing ? NodeStatus.MISSING : NodeStatus.CRAFTABLE;
        return node;
    }

    private static final class IngredientGroup {
        final ItemStack stack;
        final RecipeLookup.IngredientSlot firstSlot;
        final List<Integer> slotIndices = new ArrayList<>();
        int totalPerCraft;

        IngredientGroup(ItemStack stack, RecipeLookup.IngredientSlot firstSlot, int countPerCraft) {
            this.stack = stack;
            this.firstSlot = firstSlot;
            this.totalPerCraft = countPerCraft;
            this.slotIndices.add(firstSlot.slotIndex());
        }
    }

    /**
     * True if the recipe is guaranteed to drive recursion back into the path —
     * every required slot's only options are items currently being resolved up
     * the stack. A slot that has at least one non-path option lets the resolver
     * pick that one, so it is not counted against the candidate.
     */
    private static boolean alwaysCycles(RecipeHolder<?> holder, Set<ResourceLocation> path) {
        for (Ingredient ing : holder.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            ItemStack[] options = ing.getItems();
            if (options.length == 0) continue;
            boolean hasEscape = false;
            for (ItemStack opt : options) {
                if (!path.contains(ItemId.of(opt))) {
                    hasEscape = true;
                    break;
                }
            }
            if (!hasEscape) return true;
        }
        return false;
    }
}
