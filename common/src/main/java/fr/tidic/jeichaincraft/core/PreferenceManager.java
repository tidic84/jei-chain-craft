package fr.tidic.jeichaincraft.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores user preferences in two namespaces:
 *  - recipe: which recipe to use when an item is produced by several
 *    (key = produced item id, value = recipe id).
 *  - ingredient: which item from a tag-ingredient to use for a given slot
 *    of a given recipe (key = recipe id + ":" + slot index, value = item id).
 *
 * In-memory for MVP — persistence to disk is a follow-up.
 */
public class PreferenceManager {
    private final Map<ResourceLocation, ResourceLocation> chosenRecipe = new HashMap<>();
    private final Map<String, ResourceLocation> chosenIngredient = new HashMap<>();

    public void remember(ResourceLocation itemId, ResourceLocation recipeId) {
        chosenRecipe.put(itemId, recipeId);
    }

    public void forget(ResourceLocation itemId) {
        chosenRecipe.remove(itemId);
    }

    public void rememberIngredient(ResourceLocation recipeId, int slotIndex, ResourceLocation itemId) {
        chosenIngredient.put(ingredientKey(recipeId, slotIndex), itemId);
    }

    public ResourceLocation ingredientPref(ResourceLocation recipeId, int slotIndex) {
        return chosenIngredient.get(ingredientKey(recipeId, slotIndex));
    }

    public void clear() {
        chosenRecipe.clear();
        chosenIngredient.clear();
    }

    public int size() {
        return chosenRecipe.size() + chosenIngredient.size();
    }

    public RecipeHolder<?> choose(ResourceLocation itemId, List<RecipeHolder<?>> candidates) {
        ResourceLocation preferred = chosenRecipe.get(itemId);
        if (preferred != null) {
            for (RecipeHolder<?> h : candidates) {
                if (h.id().equals(preferred)) return h;
            }
        }
        return candidates.get(0);
    }

    private static String ingredientKey(ResourceLocation recipeId, int slotIndex) {
        return recipeId + ":" + slotIndex;
    }
}
