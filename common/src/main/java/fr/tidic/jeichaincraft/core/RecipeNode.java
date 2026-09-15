package fr.tidic.jeichaincraft.core;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One node of the recursive crafting tree.
 * - target: stack we want to obtain (count = required amount)
 * - children: ingredient nodes for the chosen recipe (empty for leaves)
 * - alternatives: how many other recipes could produce target (>0 = ambiguous)
 * - recipeId: the recipe chosen to craft this node (null for leaves)
 * - parentRecipeId / parentSlotIndex: which ingredient slot of which recipe
 *   this node fills (null for the root). Used by the UI to offer a picker
 *   when the slot is a tag with multiple options.
 * - ingredientOptions: every item the parent slot accepts (size > 1 means
 *   the slot is a tag and the user can swap which item we plan around).
 */
public class RecipeNode {
    public final ItemStack target;
    public final List<RecipeNode> children = new ArrayList<>();
    public NodeStatus status = NodeStatus.MISSING;
    public int alternatives = 0;
    public String recipeId;
    public int have;
    public int needed;
    /** Number of craft operations this node will run (0 for leaves / HAVE / MISSING). */
    public int crafts;
    public boolean expanded = true;
    /** True only for the user's chosen target. Display + algo differ from ingredients. */
    public boolean isRoot;
    public Identifier parentRecipeId;
    /**
     * Slot indices of the parent recipe that this child satisfies. Usually
     * one entry; multiple when several slots accept the same item and were
     * aggregated into a single child (e.g. 4 cogwheel slots → one node).
     * Picking an ingredient alternative writes the preference to all of them
     * so the swap applies coherently.
     */
    public List<Integer> parentSlotIndices = Collections.emptyList();
    public List<ItemStack> ingredientOptions;

    public RecipeNode(ItemStack target, int needed) {
        this.target = target;
        this.needed = needed;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean hasIngredientChoice() {
        return ingredientOptions != null && ingredientOptions.size() > 1;
    }

    /**
     * Re-reads {@code have} from the inventory for this node and every
     * descendant. Used during execution so the X/Y counts update as items are
     * crafted, without rebuilding the whole tree (which would also reset
     * collapsed state and re-pick recipes).
     */
    public void refreshCounts(InventoryAnalyzer inventory) {
        have = inventory.count(target);
        for (RecipeNode c : children) c.refreshCounts(inventory);
    }
}
