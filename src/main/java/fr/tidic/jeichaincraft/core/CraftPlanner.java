package fr.tidic.jeichaincraft.core;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Walks a built tree and produces:
 *  - Flat list of base resources needed (HAVE/MISSING leaves)
 *  - Ordered list of craft steps, deepest-first (leaves get crafted first)
 */
public class CraftPlanner {

    public record BaseResource(ItemStack stack, int needed, int have) {
        public int missing() { return Math.max(0, needed - have); }
    }

    public record CraftStep(String recipeId, ItemStack output, int crafts) {

        public Optional<RecipeHolder<?>> resolve() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return Optional.empty();
            RecipeManager rm = mc.level.getRecipeManager();
            ResourceLocation rl = ResourceLocation.parse(recipeId);
            return rm.byKey(rl).map(h -> (RecipeHolder<?>) h);
        }
    }

    public static List<BaseResource> baseResources(RecipeNode root) {
        Map<ResourceLocation, BaseResource> agg = new LinkedHashMap<>();
        // Skip the root itself — it is the goal, not a raw input. Including it
        // duplicated the target in the sidebar whenever the root was a HAVE
        // leaf (user has enough already; tree has a single node).
        for (RecipeNode c : root.children) collectLeaves(c, agg);
        return new ArrayList<>(agg.values());
    }

    private static void collectLeaves(RecipeNode node, Map<ResourceLocation, BaseResource> agg) {
        if (node.isLeaf()) {
            ResourceLocation id = ItemId.of(node.target);
            BaseResource existing = agg.get(id);
            if (existing == null) {
                agg.put(id, new BaseResource(node.target, node.needed, node.have));
            } else {
                agg.put(id, new BaseResource(existing.stack, existing.needed + node.needed, existing.have));
            }
            return;
        }
        for (RecipeNode c : node.children) collectLeaves(c, agg);
    }

    public static List<CraftStep> steps(RecipeNode root) {
        List<CraftStep> steps = new ArrayList<>();
        collectSteps(root, steps);
        return steps;
    }

    private static void collectSteps(RecipeNode node, List<CraftStep> out) {
        if (node.isLeaf() || node.recipeId == null) return;
        for (RecipeNode c : node.children) collectSteps(c, out);
        if (node.crafts > 0) {
            out.add(new CraftStep(node.recipeId, node.target, node.crafts));
        }
    }
}
