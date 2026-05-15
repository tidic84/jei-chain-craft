package fr.tidic.jeichaincraft.core;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the recursive crafting tree.
 * - target: stack we want to obtain (count = required amount)
 * - children: ingredient nodes for the chosen recipe (empty for leaves)
 * - alternatives: how many other recipes could produce target (>0 = ambiguous)
 * - recipeId: the recipe chosen to craft this node (null for leaves)
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

    public RecipeNode(ItemStack target, int needed) {
        this.target = target;
        this.needed = needed;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}
