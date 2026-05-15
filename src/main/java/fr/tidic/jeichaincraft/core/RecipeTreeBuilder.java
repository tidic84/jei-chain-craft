package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive tree builder with cycle detection + depth cap.
 *
 * Algo:
 *  1. Count what the player has of the target item.
 *  2. If enough → HAVE leaf.
 *  3. Else look up recipes producing the target.
 *     - 0 recipes → MISSING leaf.
 *     - >=1       → pick preferred (or first), recurse on its ingredients.
 *     - >1        → mark node alternatives count.
 *  4. Track visited item IDs in the current path; if a recursion would revisit one,
 *     emit a CYCLE leaf instead of recursing.
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

    public RecipeNode build(ItemStack target, int needed) {
        JEIChainCraftMod.LOGGER.info("=== build root {} qty={} ===", ItemId.of(target), needed);
        return build(target, needed, new HashSet<>(), 0);
    }

    private RecipeNode build(ItemStack target, int needed, Set<ResourceLocation> path, int depth) {
        RecipeNode node = new RecipeNode(target, needed);
        node.have = inventory.count(target);

        if (node.have >= needed) {
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

        node.alternatives = candidates.size() - 1;
        RecipeHolder<?> chosen = prefs.choose(itemId, candidates);
        node.recipeId = chosen.id().toString();
        JEIChainCraftMod.LOGGER.info("  d={} chose {} for {} (alternatives={})",
                depth, node.recipeId, itemId, node.alternatives);

        int perCraft = RecipeLookup.outputCount(chosen);
        int missing = Math.max(0, needed - node.have);
        int crafts = (missing + perCraft - 1) / perCraft;

        path.add(itemId);
        boolean anyMissing = false;
        for (ItemStack ing : RecipeLookup.ingredientsOf(chosen)) {
            int ingNeed = ing.getCount() * crafts;
            RecipeNode child = build(ing, ingNeed, path, depth + 1);
            node.children.add(child);
            if (child.status == NodeStatus.MISSING || child.status == NodeStatus.CYCLE) {
                anyMissing = true;
            }
        }
        path.remove(itemId);

        node.status = anyMissing ? NodeStatus.MISSING : NodeStatus.CRAFTABLE;
        return node;
    }
}
