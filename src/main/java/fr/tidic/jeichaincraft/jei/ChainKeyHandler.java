package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.client.KeyBindings;
import fr.tidic.jeichaincraft.core.ItemId;
import fr.tidic.jeichaincraft.ui.ChainScreens;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Handles the "open chain tree" hotkey from anywhere it can resolve a target
 * stack. Pickup order:
 *   1. Slot under the mouse on a vanilla container screen (inventory, chest,
 *      crafting table, etc.). Works without JEI being involved at all.
 *   2. The JEI recipe view.
 *   3. The JEI ingredient list overlay (right side panel).
 *   4. The JEI bookmark overlay.
 *
 * There is intentionally no recipe-view decorator here: the feature is
 * hotkey-only, so no "C" is drawn on top of JEI's recipe UI.
 */
@EventBusSubscriber(modid = JEIChainCraftMod.MODID, value = Dist.CLIENT)
public class ChainKeyHandler {

    private static IJeiRuntime jeiRuntime;

    public static void bindJeiRuntime(IJeiRuntime rt) {
        jeiRuntime = rt;
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!KeyBindings.matches(event.getKeyCode(), event.getScanCode())) return;

        ItemStack hovered = pickFromScreen(event.getScreen());
        JEIChainCraftMod.LOGGER.info("C pressed; pickHovered -> {} (count={})",
                hovered.isEmpty() ? "EMPTY" : ItemId.of(hovered), hovered.getCount());
        if (hovered.isEmpty()) return;

        ChainScreens.openTreeFor(hovered);
        event.setCanceled(true);
    }

    private static ItemStack pickFromScreen(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) return stack;
            }
        }
        return pickFromJei();
    }

    private static ItemStack pickFromJei() {
        if (jeiRuntime == null) return ItemStack.EMPTY;
        ItemStack recipes = jeiRuntime.getRecipesGui()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                .orElse(ItemStack.EMPTY);
        if (!recipes.isEmpty()) return recipes;

        ItemStack list = jeiRuntime.getIngredientListOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (list != null && !list.isEmpty()) return list;
        ItemStack book = jeiRuntime.getBookmarkOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        return book != null ? book : ItemStack.EMPTY;
    }
}
