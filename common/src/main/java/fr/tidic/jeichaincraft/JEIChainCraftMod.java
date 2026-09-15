package fr.tidic.jeichaincraft;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Loader-agnostic constants and bootstrap. The actual entry points live in
 * the loader projects (JEIChainCraftNeoForge, JEIChainCraftFabric) and call
 * {@link #init()}.
 */
public final class JEIChainCraftMod {
    public static final String MODID = "jeichaincraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    private JEIChainCraftMod() {}

    public static void init() {
        LOGGER.info("JEI Chain Craft loading");
    }
}
