package fr.tidic.jeichaincraft.core;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.jei.JEIChainCraftPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves "what recipes produce this item?".
 *
 * Since Minecraft 1.21.2 the server no longer sends recipes to the client, so
 * the client-side recipe access is empty. JEI syncs them itself (JEI must be
 * on the server, which is always the case in singleplayer) and exposes them
 * through its recipe manager, so crafting recipes are read from there.
 *
 * Scope: crafting table recipes only. Cooking, stonecutting and smithing are
 * intentionally excluded — the chain tool is about recursive crafting.
 */
public final class RecipeLookup {
    private RecipeLookup() {}

    /** Every crafting recipe JEI knows about; empty until the JEI runtime is available. */
    public static Stream<RecipeHolder<CraftingRecipe>> craftingRecipes() {
        IJeiRuntime runtime = JEIChainCraftPlugin.runtime();
        if (runtime == null) return Stream.empty();
        return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get();
    }

    public static Identifier idOf(RecipeHolder<?> holder) {
        return holder.id().identifier();
    }

    public static Optional<RecipeHolder<?>> byId(Identifier id) {
        return craftingRecipes()
                .filter(h -> idOf(h).equals(id))
                .<RecipeHolder<?>>map(h -> h)
                .findFirst();
    }

    /** Result stack of a recipe, resolved from its display (recipes no longer expose it directly). */
    public static ItemStack resultOf(RecipeHolder<?> holder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ItemStack.EMPTY;
        List<RecipeDisplay> displays = holder.value().display();
        if (displays.isEmpty()) return ItemStack.EMPTY;
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        return displays.getFirst().result().resolveForFirstStack(context);
    }

    public static List<RecipeHolder<?>> recipesProducing(ItemStack stack) {
        List<RecipeHolder<?>> matches = new ArrayList<>();
        craftingRecipes().forEach(holder -> {
            if (holder.value().isSpecial()) return;
            ItemStack out = resultOf(holder);
            if (out.isEmpty() || !ItemStack.isSameItem(out, stack)) return;
            if (selfReferencing(holder, stack)) {
                JEIChainCraftMod.LOGGER.info("Skipping self-ref recipe {} (output appears as ingredient)",
                        idOf(holder));
                return;
            }
            matches.add(holder);
        });
        JEIChainCraftMod.LOGGER.info("recipesProducing({}) -> {}",
                stack.getItem(),
                matches.stream().map(h -> idOf(h).toString()).collect(Collectors.joining(", ")));
        return matches;
    }

    /**
     * Ingredient positions of a recipe. Shaped recipes keep empty positions so
     * slot indices stay stable (they key the ingredient preferences).
     */
    private static List<Optional<Ingredient>> rawIngredients(RecipeHolder<?> holder) {
        if (holder.value() instanceof ShapedRecipe shaped) return shaped.getIngredients();
        return holder.value().placementInfo().ingredients().stream().map(Optional::of).toList();
    }

    private static List<ItemStack> optionsOf(Ingredient ingredient) {
        return ingredient.items().map(item -> new ItemStack(item.value())).toList();
    }

    /**
     * True if any ingredient of the recipe accepts {@code output}. Such
     * recipes (decorated duplicates, upgrade recipes that consume and re-emit
     * the same item) would otherwise drive the tree builder into a cycle.
     */
    private static boolean selfReferencing(RecipeHolder<?> holder, ItemStack output) {
        for (Optional<Ingredient> ing : rawIngredients(holder)) {
            if (ing.isPresent() && ing.get().test(output)) return true;
        }
        return false;
    }

    /**
     * One stack per ingredient slot, picking the first option of each.
     * Display-only helper — does not take preferences or inventory into
     * account; the planner uses {@link #ingredientSlots} + {@link #resolveSlot}.
     */
    public static List<ItemStack> ingredientsOf(RecipeHolder<?> holder) {
        List<ItemStack> result = new ArrayList<>();
        for (Optional<Ingredient> ing : rawIngredients(holder)) {
            if (ing.isEmpty()) continue;
            List<ItemStack> options = optionsOf(ing.get());
            if (!options.isEmpty()) result.add(options.getFirst().copy());
        }
        return result;
    }

    /**
     * One ingredient position of a recipe. {@code slotIndex} is the position in
     * the recipe's ingredient list (empty shaped positions included).
     * {@code options} is every stack the ingredient accepts — more than one
     * means the slot is a tag / list ingredient.
     */
    public record IngredientSlot(int slotIndex, List<ItemStack> options, int count) {
        public boolean isTag() { return options.size() > 1; }
    }

    public static List<IngredientSlot> ingredientSlots(RecipeHolder<?> holder) {
        List<IngredientSlot> result = new ArrayList<>();
        List<Optional<Ingredient>> ingredients = rawIngredients(holder);
        for (int idx = 0; idx < ingredients.size(); idx++) {
            Optional<Ingredient> ing = ingredients.get(idx);
            if (ing.isEmpty()) continue;
            List<ItemStack> options = optionsOf(ing.get());
            if (!options.isEmpty()) result.add(new IngredientSlot(idx, options, 1));
        }
        return result;
    }

    /** Accepted stacks of every non-empty slot, used by the tree builder's cycle check. */
    public static List<List<ItemStack>> slotOptions(RecipeHolder<?> holder) {
        List<List<ItemStack>> result = new ArrayList<>();
        for (IngredientSlot slot : ingredientSlots(holder)) result.add(slot.options());
        return result;
    }

    /**
     * Picks one stack for the given slot, in this order:
     *   1. a user preference stored in {@link PreferenceManager} (if it still
     *      matches one of the slot's options),
     *   2. the first option the player already has at least one of,
     *   3. the first option as a fallback.
     *
     * The returned stack always has {@code count == slot.count} so callers can
     * multiply by the number of crafts.
     */
    public static ItemStack resolveSlot(RecipeHolder<?> holder,
                                        IngredientSlot slot,
                                        PreferenceManager prefs,
                                        InventoryAnalyzer inventory) {
        if (prefs != null) {
            Identifier pref = prefs.ingredientPref(idOf(holder), slot.slotIndex());
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
        return withCount(slot.options().getFirst(), slot.count());
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
        List<Optional<Ingredient>> ingredients = rawIngredients(holder);
        if (slotIndex < 0 || slotIndex >= ingredients.size()) return List.of();
        return ingredients.get(slotIndex).map(RecipeLookup::optionsOf).orElse(List.of());
    }

    public static int outputCount(RecipeHolder<?> holder) {
        return Math.max(1, resultOf(holder).getCount());
    }

    /**
     * Verbose dump used by the Debug button in the tree screen: every crafting
     * recipe producing the target, with type, self-reference flag and full
     * ingredient list.
     */
    public static void dumpDebug(ItemStack target) {
        if (JEIChainCraftPlugin.runtime() == null) {
            JEIChainCraftMod.LOGGER.warn("dumpDebug: JEI runtime not available");
            return;
        }
        JEIChainCraftMod.LOGGER.info("===== DUMP for target {} =====", target.getItem());
        int[] totals = new int[2];
        craftingRecipes().forEach(holder -> {
            totals[0]++;
            ItemStack out = resultOf(holder);
            if (out.isEmpty() || !ItemStack.isSameItem(out, target)) return;
            totals[1]++;
            JEIChainCraftMod.LOGGER.info("  MATCH id={} type={} special={} self-ref={}",
                    idOf(holder), holder.value().getClass().getSimpleName(),
                    holder.value().isSpecial(), selfReferencing(holder, target));
            List<Optional<Ingredient>> ingredients = rawIngredients(holder);
            for (int slotIdx = 0; slotIdx < ingredients.size(); slotIdx++) {
                Optional<Ingredient> ing = ingredients.get(slotIdx);
                if (ing.isEmpty()) continue;
                String items = optionsOf(ing.get()).stream()
                        .map(s -> s.getItem().toString())
                        .collect(Collectors.joining(", "));
                JEIChainCraftMod.LOGGER.info("      ing[{}] = [{}] test(target)={}",
                        slotIdx, items, ing.get().test(target));
            }
        });
        JEIChainCraftMod.LOGGER.info("===== DUMP done: scanned={} matched={} =====", totals[0], totals[1]);
    }
}
