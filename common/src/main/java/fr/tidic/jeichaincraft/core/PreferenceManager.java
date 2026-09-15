package fr.tidic.jeichaincraft.core;

import net.minecraft.resources.Identifier;
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
    private final Map<Identifier, Identifier> chosenRecipe = new HashMap<>();
    private final Map<String, Identifier> chosenIngredient = new HashMap<>();

    public void remember(Identifier itemId, Identifier recipeId) {
        chosenRecipe.put(itemId, recipeId);
    }

    public void forget(Identifier itemId) {
        chosenRecipe.remove(itemId);
    }

    public void rememberIngredient(Identifier recipeId, int slotIndex, Identifier itemId) {
        chosenIngredient.put(ingredientKey(recipeId, slotIndex), itemId);
    }

    public Identifier ingredientPref(Identifier recipeId, int slotIndex) {
        return chosenIngredient.get(ingredientKey(recipeId, slotIndex));
    }

    public void clear() {
        chosenRecipe.clear();
        chosenIngredient.clear();
    }

    public int size() {
        return chosenRecipe.size() + chosenIngredient.size();
    }

    public RecipeHolder<?> choose(Identifier itemId, List<RecipeHolder<?>> candidates) {
        Identifier preferred = chosenRecipe.get(itemId);
        if (preferred != null) {
            for (RecipeHolder<?> h : candidates) {
                if (RecipeLookup.idOf(h).equals(preferred)) return h;
            }
        }
        return candidates.get(0);
    }

    private static String ingredientKey(Identifier recipeId, int slotIndex) {
        return recipeId + ":" + slotIndex;
    }
}
