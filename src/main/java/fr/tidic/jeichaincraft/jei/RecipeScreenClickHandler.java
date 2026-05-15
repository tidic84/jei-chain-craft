package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.client.KeyBindings;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Listens for the "open chain tree" key on any JEI-aware screen. When pressed,
 * grabs the ingredient under the mouse pointer (from the recipe view, the
 * ingredient list overlay, or the bookmark overlay) and opens the tree screen.
 *
 * Pure-key approach (no fragile click hit-testing) because JEI's decorator API
 * does not expose a click hook in 19.27.
 */
@EventBusSubscriber(modid = JEIChainCraftMod.MODID, value = Dist.CLIENT)
public class RecipeScreenClickHandler {

    private static IJeiRuntime runtime;

    public static void bindRuntime(IJeiRuntime rt) {
        runtime = rt;
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (runtime == null) return;
        if (!KeyBindings.matches(event.getKeyCode(), event.getScanCode())) return;

        ItemStack hovered = pickHovered();
        if (hovered.isEmpty()) return;

        ChainButtonDecorator.openTreeFor(hovered);
        event.setCanceled(true);
    }

    private static ItemStack pickHovered() {
        // Recipe view first (we're most likely there when pressing C).
        ItemStack fromRecipes = runtime.getRecipesGui()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                .orElse(ItemStack.EMPTY);
        if (!fromRecipes.isEmpty()) return fromRecipes;

        // Fall back to the right-side ingredient list overlay.
        ItemStack fromList = runtime.getIngredientListOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (fromList != null && !fromList.isEmpty()) return fromList;

        // Then bookmarks.
        ItemStack fromBookmarks = runtime.getBookmarkOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        return fromBookmarks != null ? fromBookmarks : ItemStack.EMPTY;
    }
}
