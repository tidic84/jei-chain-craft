package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * JEI integration. Holds the JEI runtime, which the mod uses to:
 *  - read crafting recipes (the client no longer receives them since 1.21.2),
 *  - place recipes in crafting grids through JEI's recipe transfer,
 *  - resolve the ingredient under the mouse for the chain hotkey.
 */
@JeiPlugin
public class JEIChainCraftPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(JEIChainCraftMod.MODID, "main");

    private static @Nullable IJeiRuntime runtime;

    /** The JEI runtime, or null before JEI has finished loading (or after it unloads). */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
