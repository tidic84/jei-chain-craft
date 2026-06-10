package fr.tidic.jeichaincraft.compat.tomsstorage;

import fr.tidic.jeichaincraft.core.InventoryAnalyzer;
import fr.tidic.jeichaincraft.executor.CraftHandlerRegistry;

/**
 * References Tom's classes (via the registered hooks) — must only be loaded
 * when toms_storage is present. See {@link TomsStorageCompat#init()}.
 */
final class TomsStorageHooks {

    private TomsStorageHooks() {}

    static void register() {
        InventoryAnalyzer.registerSource(new TomsTerminalSource());
        CraftHandlerRegistry.register(new TomsCraftingTerminalHandler());
    }
}
