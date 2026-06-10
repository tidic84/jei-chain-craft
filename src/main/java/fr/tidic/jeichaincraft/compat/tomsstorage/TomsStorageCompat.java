package fr.tidic.jeichaincraft.compat.tomsstorage;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Tom's Simple Storage integration. Only this
 * class may be referenced from common code — it touches no Tom's classes
 * itself, so it is safe to load when the mod is absent. The actual hooks live
 * in {@link TomsStorageHooks}, which is only class-loaded behind the
 * isLoaded check.
 */
public final class TomsStorageCompat {
    public static final String MOD_ID = "toms_storage";

    private TomsStorageCompat() {}

    public static void init() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            JEIChainCraftMod.LOGGER.info("Tom's Simple Storage absent — compat disabled");
            return;
        }
        TomsStorageHooks.register();
        JEIChainCraftMod.LOGGER.info("Tom's Simple Storage compat enabled");
    }
}
