package fr.tidic.jeichaincraft.executor;

import fr.tidic.jeichaincraft.executor.handlers.VanillaCraftingHandler;
import fr.tidic.jeichaincraft.executor.handlers.compat.TomsCraftingTerminalHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Public API for registering additional handlers. The MVP registers vanilla
 * crafting + a soft-compat handler for Tom's Simple Storage Mod (detected by
 * class name; if Tom's is not installed the handler simply never matches).
 * Modders can call {@link #register} from their own JEI plugin (or any client
 * init point) to add support for their custom menus.
 *
 * Modded handlers should be registered BEFORE the vanilla one if they wrap
 * vanilla-style menus, so the more specific handler wins. The list is checked
 * top-down and the first match returns.
 */
public final class CraftHandlerRegistry {
    private static final List<CraftHandler> HANDLERS = new ArrayList<>();

    static {
        register(new TomsCraftingTerminalHandler());
        register(new VanillaCraftingHandler());
    }

    private CraftHandlerRegistry() {}

    public static void register(CraftHandler handler) {
        HANDLERS.add(handler);
    }

    public static Optional<CraftHandler> find(AbstractContainerMenu menu) {
        for (CraftHandler h : HANDLERS) {
            if (h.canHandle(menu)) return Optional.of(h);
        }
        return Optional.empty();
    }
}
