package fr.tidic.jeichaincraft.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores which recipe the user prefers for each ambiguous item.
 * Key = produced item id. Value = recipe id chosen.
 * In-memory for MVP — persistence to disk is a follow-up.
 */
public class PreferenceManager {
    private final Map<ResourceLocation, ResourceLocation> chosen = new HashMap<>();

    public void remember(ResourceLocation itemId, ResourceLocation recipeId) {
        chosen.put(itemId, recipeId);
    }

    public void forget(ResourceLocation itemId) {
        chosen.remove(itemId);
    }

    public RecipeHolder<?> choose(ResourceLocation itemId, List<RecipeHolder<?>> candidates) {
        ResourceLocation preferred = chosen.get(itemId);
        if (preferred != null) {
            for (RecipeHolder<?> h : candidates) {
                if (h.id().equals(preferred)) return h;
            }
        }
        return candidates.get(0);
    }
}
