package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * Light-touch JEI integration. The plugin only captures the JEI runtime so we
 * can ask it which ingredient is under the mouse when the user presses the
 * chain hotkey — there is no UI decoration on JEI recipe views.
 */
@JeiPlugin
public class JEIChainCraftPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(JEIChainCraftMod.MODID, "main");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        ChainKeyHandler.bindJeiRuntime(jeiRuntime);
    }
}
