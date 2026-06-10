package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Recursive tree builder with cycle detection, depth cap, a shared inventory
 * budget and per-recipe backtracking.
 *
 * Semantics:
 *  - The {@code amountToCraft} argument is the number of {@code target} items
 *    the user wants this run to produce. Existing inventory of the target is
 *    NOT subtracted — pressing "craft 2 gearboxes" while already holding one
 *    crafts 2 more, ending with 3 total.
 *  - For ingredient sub-nodes, inventory IS considered, through a shared
 *    {@link InventorySnapshot}: every branch reserves what it consumes, so two
 *    branches can never satisfy themselves with the same stack. Ingredient
 *    matching goes through the recipe's {@link Ingredient}, so tags resolve
 *    against whatever variant the player actually owns (#planks accepts birch).
 *
 * Cycle handling: visited item ids are tracked along the current recursion
 * path; a recipe revisiting one yields a CYCLE leaf. When the chosen recipe
 * for an item dead-ends (CYCLE or missing ingredients), the next candidate is
 * tried before the branch is declared MISSING — this is what untangles
 * compression pairs like iron ingot ⇄ iron block: the block recipe cycles, the
 * nugget recipe (or the blocks already in storage) still gets its chance.
 */
public class RecipeTreeBuilder {
    private static final int DEFAULT_MAX_DEPTH = 16;
    /** Safety valve against backtracking blowup on pathological recipe sets. */
    private static final int MAX_NODES = 4000;

    private final InventoryAnalyzer inventory;
    private final PreferenceManager prefs;
    private final int maxDepth;
    private int nodesBuilt;

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
        nodesBuilt = 0;
        return build(target, null, amountToCraft, true, inventory.snapshot(), new HashSet<>(), 0);
    }

    private RecipeNode build(ItemStack target, Ingredient matcher, int needed, boolean isRoot,
                             InventorySnapshot budget, Set<ResourceLocation> path, int depth) {
        nodesBuilt++;
        Predicate<ItemStack> match = matcher != null ? matcher
                : s -> ItemStack.isSameItem(s, target);
        RecipeNode node = new RecipeNode(target, needed);
        node.have = budget.available(match);
        node.isRoot = isRoot;

        // For ingredients, claim what the budget still holds. If that covers
        // the need, this is a HAVE leaf. For the root, the user explicitly
        // asked to craft this many, regardless of what they currently hold.
        int toProduce = needed;
        if (!isRoot) {
            toProduce = needed - budget.reserve(match, needed);
            if (toProduce == 0) {
                node.status = NodeStatus.HAVE;
                return node;
            }
        }

        if (depth >= maxDepth || nodesBuilt > MAX_NODES) {
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

        path.add(itemId);
        Attempt first = null;
        Attempt success = null;
        for (RecipeHolder<?> candidate : orderCandidates(itemId, candidates)) {
            Attempt attempt = tryRecipe(target, candidate, toProduce, budget, path, depth);
            if (first == null) first = attempt;
            if (attempt.ok()) {
                success = attempt;
                break;
            }
            if (nodesBuilt > MAX_NODES) break;
        }
        path.remove(itemId);

        // On total failure, keep the first attempt's subtree so the user still
        // sees the preferred recipe and what is missing inside it.
        Attempt shown = success != null ? success : first;
        node.recipeId = shown.recipeId();
        node.crafts = shown.crafts();
        node.children.addAll(shown.children());
        if (success != null) {
            budget.replaceWith(success.budget());
            node.status = NodeStatus.CRAFTABLE;
        } else {
            node.status = NodeStatus.MISSING;
        }
        JEIChainCraftMod.LOGGER.info("  d={} {} via {} -> {} (alternatives={})",
                depth, itemId, node.recipeId, node.status, node.alternatives);
        return node;
    }

    private record Attempt(boolean ok, String recipeId, int crafts,
                           List<RecipeNode> children, InventorySnapshot budget) {}

    /**
     * Builds the subtree for one candidate recipe against a forked budget.
     * The fork is only committed by the caller when the attempt succeeds, so
     * a failed attempt releases everything its children had reserved.
     */
    private Attempt tryRecipe(ItemStack target, RecipeHolder<?> recipe, int toProduce,
                              InventorySnapshot budget, Set<ResourceLocation> path, int depth) {
        InventorySnapshot trial = budget.copy();
        int perCraft = RecipeLookup.outputCount(recipe);
        int crafts = (toProduce + perCraft - 1) / perCraft;
        List<RecipeNode> children = new ArrayList<>();
        boolean ok = true;
        for (RecipeLookup.RecipeIngredient ing : RecipeLookup.mergedIngredientsOf(recipe)) {
            int ingNeed = ing.count() * crafts;
            ItemStack display = pickDisplayVariant(ing.matcher(), trial);
            RecipeNode child = build(display, ing.matcher(), ingNeed, false, trial, path, depth + 1);
            children.add(child);
            if (child.status == NodeStatus.MISSING || child.status == NodeStatus.CYCLE) {
                ok = false;
            }
        }
        if (ok) {
            // Rounding up to whole crafts overproduces; that surplus is real
            // once this step runs, so later branches may claim it.
            trial.credit(target, crafts * perCraft - toProduce);
        }
        return new Attempt(ok, recipe.id().toString(), crafts, children, trial);
    }

    /** Preferred recipe first, then the remaining candidates as fallbacks. */
    private List<RecipeHolder<?>> orderCandidates(ResourceLocation itemId,
                                                  List<RecipeHolder<?>> candidates) {
        RecipeHolder<?> preferred = prefs.choose(itemId, candidates);
        List<RecipeHolder<?>> ordered = new ArrayList<>(candidates.size());
        ordered.add(preferred);
        for (RecipeHolder<?> c : candidates) {
            if (c != preferred) ordered.add(c);
        }
        return ordered;
    }

    /**
     * The stack shown (and recursed on) for a tag ingredient: prefer a variant
     * the player owns, fall back to the ingredient's first listed item.
     */
    private static ItemStack pickDisplayVariant(Ingredient ing, InventorySnapshot budget) {
        ItemStack[] options = ing.getItems();
        for (ItemStack opt : options) {
            if (budget.available(s -> ItemStack.isSameItem(s, opt)) > 0) return opt.copy();
        }
        return options[0].copy();
    }
}
