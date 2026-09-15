package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflective accessor for Refined Storage 2 grid menus.
 *
 * RS keeps the network view in a client-side {@code ResourceRepository}
 * exposed by {@code AbstractGridContainerMenu.getRepository()}, keyed by
 * {@code ItemResource}. The crafting grid is not a {@code RecipeBookMenu}:
 * recipes are placed through {@code AbstractCraftingGridContainerMenu
 * .transferRecipe(List<List<ItemResource>>)}, which sends RS's own packet
 * (one list of alternatives per 3x3 matrix slot).
 *
 * No compile dependency on RS: everything goes through reflection and fails
 * silently if RS is absent or its class shape changes.
 */
public final class RefinedStorageReader {

    private RefinedStorageReader() {}

    private static final String GRID_MENU = "com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu";
    private static final String CRAFTING_GRID_MENU = "com.refinedmods.refinedstorage.common.grid.AbstractCraftingGridContainerMenu";
    private static final String RESULT_SLOT = "com.refinedmods.refinedstorage.common.grid.CraftingGridResultSlot";
    private static final String ITEM_RESOURCE = "com.refinedmods.refinedstorage.common.support.resource.ItemResource";
    private static final String RESOURCE_KEY = "com.refinedmods.refinedstorage.api.resource.ResourceKey";

    private record Api(Method getRepository, Method getAmount, Constructor<?> newResource,
                       Method ofItemStack, Method transferRecipe, Method getMatrixSlots) {}

    private static Api api;
    private static boolean resolved;

    public static boolean isGrid(AbstractContainerMenu menu) {
        return menu != null && extendsClass(menu.getClass(), GRID_MENU);
    }

    public static boolean isCraftingGrid(AbstractContainerMenu menu) {
        return menu != null && extendsClass(menu.getClass(), CRAFTING_GRID_MENU);
    }

    /** Amount of {@code item} (without components) in the network behind an RS grid, or 0. */
    public static long countInNetwork(AbstractContainerMenu menu, Item item) {
        if (!isGrid(menu)) return 0;
        Api a = api(menu);
        if (a == null) return 0;
        try {
            Object repository = a.getRepository.invoke(menu);
            Object key = a.newResource.newInstance(item);
            Object amount = a.getAmount.invoke(repository, key);
            return amount instanceof Number n ? n.longValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Places one set of a recipe. {@code matrix} holds 9 entries in 3x3 order,
     * each the accepted alternatives for that matrix slot (empty = no item).
     */
    public static boolean transferRecipe(AbstractContainerMenu menu, List<List<ItemStack>> matrix) {
        if (!isCraftingGrid(menu)) return false;
        Api a = api(menu);
        if (a == null || a.transferRecipe == null) return false;
        try {
            List<List<Object>> payload = new ArrayList<>(matrix.size());
            for (List<ItemStack> options : matrix) {
                List<Object> resources = new ArrayList<>(options.size());
                for (ItemStack s : options) {
                    if (!s.isEmpty()) resources.add(a.ofItemStack.invoke(null, s));
                }
                payload.add(resources);
            }
            a.transferRecipe.invoke(menu, payload);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            JEIChainCraftMod.LOGGER.warn("Refined Storage compat: transferRecipe failed: {}", e.toString());
            return false;
        }
    }

    /** Menu slot indices of the 3x3 crafting matrix, in matrix order. */
    public static List<Integer> matrixSlotIndices(AbstractContainerMenu menu) {
        List<Integer> out = new ArrayList<>();
        Api a = api(menu);
        if (a == null || a.getMatrixSlots == null) return out;
        try {
            Object slots = a.getMatrixSlots.invoke(menu);
            if (slots instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Slot slot) out.add(slot.index);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return out;
    }

    /** Menu slot index of the crafting result, or -1. */
    public static int resultSlotIndex(AbstractContainerMenu menu) {
        if (menu == null) return -1;
        for (Slot slot : menu.slots) {
            if (extendsClass(slot.getClass(), RESULT_SLOT)) return slot.index;
        }
        return -1;
    }

    private static Api api(AbstractContainerMenu menu) {
        if (resolved) return api;
        resolved = true;
        try {
            ClassLoader cl = menu.getClass().getClassLoader();
            Class<?> gridMenu = Class.forName(GRID_MENU, false, cl);
            Class<?> itemResource = Class.forName(ITEM_RESOURCE, false, cl);
            Class<?> resourceKey = Class.forName(RESOURCE_KEY, false, cl);
            Method getRepository = gridMenu.getMethod("getRepository");
            Method getAmount = getRepository.getReturnType().getMethod("getAmount", resourceKey);
            Constructor<?> newResource = itemResource.getConstructor(Item.class);
            Method ofItemStack = itemResource.getMethod("ofItemStack", ItemStack.class);
            Method transferRecipe = null;
            Method getMatrixSlots = null;
            try {
                Class<?> craftingMenu = Class.forName(CRAFTING_GRID_MENU, false, cl);
                transferRecipe = craftingMenu.getMethod("transferRecipe", List.class);
                getMatrixSlots = craftingMenu.getMethod("getCraftingMatrixSlots");
            } catch (ReflectiveOperationException e) {
                JEIChainCraftMod.LOGGER.warn("Refined Storage compat: crafting grid API not found: {}", e.toString());
            }
            api = new Api(getRepository, getAmount, newResource, ofItemStack, transferRecipe, getMatrixSlots);
            JEIChainCraftMod.LOGGER.info("Refined Storage compat: grid reader bound");
        } catch (ReflectiveOperationException | LinkageError e) {
            JEIChainCraftMod.LOGGER.warn("Refined Storage compat: reflection setup failed: {}", e.toString());
        }
        return api;
    }

    private static boolean extendsClass(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(name)) return true;
        }
        return false;
    }
}
