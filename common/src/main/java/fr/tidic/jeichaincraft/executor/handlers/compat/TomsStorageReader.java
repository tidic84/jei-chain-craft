package fr.tidic.jeichaincraft.executor.handlers.compat;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Reflective accessor for Tom's Simple Storage Mod terminal menus.
 *
 * Tom's stores the connected-storage view in a custom field
 * {@code itemListClient} (List of {@code com.tom.storagemod.util.StoredItemStack})
 * on {@code StorageTerminalMenu} — not as regular {@link
 * net.minecraft.world.inventory.Slot} entries. Without this reader we cannot
 * see the network from outside Tom's own GUI.
 *
 * No compile dependency on Tom's: every access goes through reflection and
 * fails silently if Tom's is absent or the class shape changes. The
 * per-class lookup is cached so the per-frame inventory polling stays cheap.
 */
public final class TomsStorageReader {

    private TomsStorageReader() {}

    private static final WeakHashMap<Class<?>, Accessor> CACHE = new WeakHashMap<>();
    private static final Accessor MISS = new Accessor(null, null, null);

    private record Accessor(Field listField, Method getStack, Method getQuantity) {}

    /**
     * Returns the total count of {@code item} held in the storage network
     * behind a Tom's terminal menu, or 0 if the menu is not a Tom's terminal
     * or reflection cannot resolve the expected fields.
     */
    public static long countInNetwork(AbstractContainerMenu menu, Item item) {
        if (menu == null) return 0;
        Accessor acc = accessorFor(menu.getClass());
        if (acc == MISS) return 0;

        Object listObj;
        try {
            listObj = acc.listField.get(menu);
        } catch (IllegalAccessException e) {
            return 0;
        }
        if (!(listObj instanceof List<?> list)) return 0;

        long total = 0;
        for (Object entry : list) {
            if (entry == null) continue;
            try {
                ItemStack stack = (ItemStack) acc.getStack.invoke(entry);
                if (stack == null || stack.isEmpty()) continue;
                if (stack.getItem() != item) continue;
                Object qty = acc.getQuantity.invoke(entry);
                if (qty instanceof Number n) total += n.longValue();
            } catch (ReflectiveOperationException ignored) {
                return total;
            }
        }
        return total;
    }

    /**
     * Class names tried for {@code StoredItemStack} — Tom's has moved the
     * class between packages across versions ({@code .inventory} on 1.21.1,
     * {@code .util} earlier). We try each in turn.
     */
    private static final String[] STORED_STACK_CLASSES = {
            "com.tom.storagemod.inventory.StoredItemStack",
            "com.tom.storagemod.util.StoredItemStack",
    };

    private static Accessor accessorFor(Class<?> menuClass) {
        Accessor cached = CACHE.get(menuClass);
        if (cached != null) return cached;

        Accessor resolved = MISS;
        if (menuClass.getName().startsWith("com.tom.storagemod")) {
            try {
                Field listField = findField(menuClass, "itemListClient");
                if (listField == null) {
                    JEIChainCraftMod.LOGGER.warn(
                            "Tom's compat: itemListClient field not found on {}", menuClass.getName());
                } else {
                    listField.setAccessible(true);
                    Class<?> storedItemStackClass = null;
                    for (String name : STORED_STACK_CLASSES) {
                        try {
                            storedItemStackClass = Class.forName(name, false, menuClass.getClassLoader());
                            break;
                        } catch (ClassNotFoundException ignored) {}
                    }
                    if (storedItemStackClass == null) {
                        JEIChainCraftMod.LOGGER.warn(
                                "Tom's compat: StoredItemStack class not found in {}",
                                String.join(", ", STORED_STACK_CLASSES));
                    } else {
                        Method getStack = storedItemStackClass.getMethod("getStack");
                        Method getQuantity = findMethod(storedItemStackClass, "getQuantity", "getCount");
                        if (getQuantity == null) {
                            JEIChainCraftMod.LOGGER.warn(
                                    "Tom's compat: getQuantity()/getCount() missing on {}",
                                    storedItemStackClass.getName());
                        } else {
                            resolved = new Accessor(listField, getStack, getQuantity);
                            JEIChainCraftMod.LOGGER.info(
                                    "Tom's compat: storage reader bound on {} (item class = {})",
                                    menuClass.getName(), storedItemStackClass.getName());
                        }
                    }
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                JEIChainCraftMod.LOGGER.warn(
                        "Tom's compat: reflection setup failed on {}: {}",
                        menuClass.getName(), e.toString());
            }
        }
        CACHE.put(menuClass, resolved);
        return resolved;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String... names) {
        for (String name : names) {
            try {
                Method m = cls.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
}
