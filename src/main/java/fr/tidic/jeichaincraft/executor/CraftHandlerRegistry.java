package fr.tidic.jeichaincraft.executor;

import fr.tidic.jeichaincraft.executor.handlers.VanillaCraftingHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Public API for registering additional handlers. The MVP registers vanilla
 * crafting; modders can call {@link #register} from their own JEI plugin
 * (or any client init point) to add support for their custom menus.
 */
public final class CraftHandlerRegistry {
    private static final List<CraftHandler> HANDLERS = new ArrayList<>();

    static {
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
