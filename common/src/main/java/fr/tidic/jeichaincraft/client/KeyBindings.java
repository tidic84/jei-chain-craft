package fr.tidic.jeichaincraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.client.KeyMapping;

/**
 * Chain hotkey. Registration is loader-specific (NeoForge
 * RegisterKeyMappingsEvent, Fabric KeyBindingHelper) and done by the entry
 * points; NeoForge additionally narrows it to GUI conflict context.
 */
public final class KeyBindings {

    public static final KeyMapping OPEN_CHAIN = new KeyMapping(
            "key." + JEIChainCraftMod.MODID + ".open_chain",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            "key.categories." + JEIChainCraftMod.MODID
    );

    private KeyBindings() {}

    public static boolean matches(int keyCode, int scanCode) {
        return OPEN_CHAIN.matches(keyCode, scanCode);
    }
}
