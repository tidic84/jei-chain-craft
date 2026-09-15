package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Arrays;
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
     *
     * Display-only helper — does not take user preferences or inventory into
     * account. Use {@link #ingredientSlots(RecipeHolder)} +
     * {@link #resolveSlot(RecipeHolder, IngredientSlot, PreferenceManager, InventoryAnalyzer)}
     * for the planner.
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

    /**
     * One ingredient position of a recipe. {@code slotIndex} is the index in
     * the raw {@link Recipe#getIngredients()} list (we keep the original index
     * so empty slots in shaped recipes do not shift the numbering used by
     * preferences). {@code options} is every {@link ItemStack} the ingredient
     * accepts — length > 1 means the slot is a tag / list ingredient.
     */
    public record IngredientSlot(int slotIndex, List<ItemStack> options, int count) {
        public boolean isTag() { return options.size() > 1; }
    }

    public static List<IngredientSlot> ingredientSlots(RecipeHolder<?> holder) {
        List<IngredientSlot> result = new ArrayList<>();
        Recipe<?> recipe = holder.value();
        int idx = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                ItemStack[] items = ing.getItems();
                if (items.length > 0) {
                    List<ItemStack> opts = new ArrayList<>(items.length);
                    for (ItemStack s : items) opts.add(s.copy());
                    result.add(new IngredientSlot(idx, opts, items[0].getCount()));
                }
            }
            idx++;
        }
        return result;
    }

    /**
     * Picks one {@link ItemStack} for the given slot, in this order:
     *   1. a user preference stored in {@link PreferenceManager} (if it still
     *      matches one of the slot's options),
     *   2. the first option the player already has at least one of in their
     *      inventory,
     *   3. {@code options.get(0)} as a fallback.
     *
     * The returned stack always has {@code count == slot.count} so callers can
     * multiply by the number of crafts.
     */
    public static ItemStack resolveSlot(RecipeHolder<?> holder,
                                        IngredientSlot slot,
                                        PreferenceManager prefs,
                                        InventoryAnalyzer inventory) {
        if (prefs != null) {
            ResourceLocation pref = prefs.ingredientPref(holder.id(), slot.slotIndex());
            if (pref != null) {
                for (ItemStack opt : slot.options()) {
                    if (ItemId.of(opt).equals(pref)) return withCount(opt, slot.count());
                }
            }
        }
        if (inventory != null && slot.options().size() > 1) {
            for (ItemStack opt : slot.options()) {
                if (inventory.count(opt) > 0) return withCount(opt, slot.count());
            }
        }
        return withCount(slot.options().get(0), slot.count());
    }

    private static ItemStack withCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    /** Convenience: smart-resolved ingredient list for a recipe. */
    public static List<ItemStack> chosenIngredientsOf(RecipeHolder<?> holder,
                                                      PreferenceManager prefs,
                                                      InventoryAnalyzer inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (IngredientSlot slot : ingredientSlots(holder)) {
            result.add(resolveSlot(holder, slot, prefs, inventory));
        }
        return result;
    }

    /** Used by the picker UI to walk all alternatives of one slot. */
    public static List<ItemStack> optionsForSlot(RecipeHolder<?> holder, int slotIndex) {
        Recipe<?> recipe = holder.value();
        List<Ingredient> ings = recipe.getIngredients();
        if (slotIndex < 0 || slotIndex >= ings.size()) return List.of();
        Ingredient ing = ings.get(slotIndex);
        if (ing.isEmpty()) return List.of();
        return Arrays.stream(ing.getItems()).map(ItemStack::copy).collect(Collectors.toList());
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
