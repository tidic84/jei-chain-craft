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

        node.alternatives = candidates.size() - 1;
        RecipeHolder<?> chosen = prefs.choose(itemId, candidates);
        node.recipeId = chosen.id().toString();
        JEIChainCraftMod.LOGGER.info("  d={} chose {} for {} (alternatives={})",
                depth, node.recipeId, itemId, node.alternatives);

        int perCraft = RecipeLookup.outputCount(chosen);
        int amountToProduce = isRoot ? needed : Math.max(0, needed - node.have);
        node.crafts = (amountToProduce + perCraft - 1) / perCraft;

        path.add(itemId);
        boolean anyMissing = false;
        for (ItemStack ing : RecipeLookup.ingredientsOf(chosen)) {
            int ingNeed = ing.getCount() * node.crafts;
            RecipeNode child = build(ing, ingNeed, false, path, depth + 1);
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
