package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves "what recipes produce this item?" against the vanilla recipe manager.
 * MVP scope: crafting + smelting via the standard RecipeManager.
 * Modded recipe categories (Create, AE2, etc.) are NOT covered here — a follow-up
 * API will let other mods register their own lookups.
 */
public final class RecipeLookup {
    private RecipeLookup() {}

    public static List<RecipeHolder<?>> recipesProducing(ItemStack stack) {
        List<RecipeHolder<?>> matches = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return matches;

        RecipeManager rm = mc.level.getRecipeManager();
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            // Scope: crafting table recipes only. Cooking (smelting / blasting /
            // smoking / campfire), stonecutting and smithing are intentionally
            // excluded — the chain tool is about recursive crafting, not
            // automating every transformation in the game.
            if (!(recipe instanceof CraftingRecipe)) continue;

            ItemStack out = recipe.getResultItem(mc.level.registryAccess());
            if (out.isEmpty() || !ItemStack.isSameItem(out, stack)) continue;
            if (selfReferencing(recipe, stack)) {
                JEIChainCraftMod.LOGGER.info("Skipping self-ref recipe {} (output appears as ingredient)",
                        holder.id());
                continue;
            }
            matches.add(holder);
        }
        JEIChainCraftMod.LOGGER.info("recipesProducing({}) -> {}",
                stack.getItem(),
                matches.stream().map(h -> h.id().toString()).collect(Collectors.joining(", ")));
        return matches;
    }

    /**
     * True if any ingredient of {@code recipe} accepts {@code output}.
     * Such recipes (decorated trim duplicates, upgrade recipes that consume and
     * re-emit the same item, modded "repair" recipes) would otherwise drive
     * the tree builder into an apparent cycle. The user's bug report —
     * "sticky_piston → sticky_piston" — is exactly this case for some data pack
     * that lists the target on both sides of the recipe.
     */
    private static boolean selfReferencing(Recipe<?> recipe, ItemStack output) {
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            if (ing.test(output)) return true;
        }
        return false;
    }

    /**
     * Extracts the ingredient list from a recipe holder. Returns one ItemStack
     * per ingredient slot, picking the first matching stack of each Ingredient.
     * Empty ingredients are skipped.
     */
    public static List<ItemStack> ingredientsOf(RecipeHolder<?> holder) {
        List<ItemStack> result = new ArrayList<>();
        Recipe<?> recipe = holder.value();
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            ItemStack[] items = ing.getItems();
            if (items.length > 0) {
                result.add(items[0].copy());
            }
        }
        return result;
    }

    public static int outputCount(RecipeHolder<?> holder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 1;
        ItemStack out = holder.value().getResultItem(mc.level.registryAccess());
        return Math.max(1, out.getCount());
    }

    /**
     * Verbose dump used by the Debug button in the tree screen. Walks every
     * recipe in the manager (not just matching ones), logging output, type,
     * filtered-or-not, and full ingredient list for anything that matches the
     * target. Use this to understand which recipe the algorithm actually chose
     * when the tree looks wrong.
     */
    public static void dumpDebug(ItemStack target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            JEIChainCraftMod.LOGGER.warn("dumpDebug: no level");
            return;
        }
        RecipeManager rm = mc.level.getRecipeManager();
        JEIChainCraftMod.LOGGER.info("===== DUMP for target {} =====", target.getItem());
        int totalScanned = 0;
        int totalMatched = 0;
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            totalScanned++;
            Recipe<?> recipe = holder.value();
            ItemStack out = recipe.getResultItem(mc.level.registryAccess());
            if (out.isEmpty() || !ItemStack.isSameItem(out, target)) continue;
            totalMatched++;
            String type = recipe.getClass().getSimpleName();
            boolean isCrafting = recipe instanceof CraftingRecipe;
            boolean selfRef = selfReferencing(recipe, target);
            JEIChainCraftMod.LOGGER.info("  MATCH id={} type={} crafting={} self-ref={}",
                    holder.id(), type, isCrafting, selfRef);
            int slotIdx = 0;
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) { slotIdx++; continue; }
                StringBuilder sb = new StringBuilder();
                for (ItemStack item : ing.getItems()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(item.getItem());
                }
                JEIChainCraftMod.LOGGER.info("      ing[{}] = [{}] test(target)={}",
                        slotIdx, sb, ing.test(target));
                slotIdx++;
            }
        }
        JEIChainCraftMod.LOGGER.info("===== DUMP done: scanned={} matched={} =====",
                totalScanned, totalMatched);
    }
}
