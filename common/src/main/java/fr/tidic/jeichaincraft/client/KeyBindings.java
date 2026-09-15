package fr.tidic.jeichaincraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

/**
 * Chain hotkey. Registration is loader-specific (NeoForge
 * RegisterKeyMappingsEvent, Fabric KeyMappingHelper) and done by the entry
 * points; NeoForge additionally narrows it to GUI conflict context.
 */
public final class KeyBindings {

    /**
     * Created but not registered here: NeoForge wants modded categories
     * through RegisterKeyMappingsEvent#registerCategory, Fabric through
     * vanilla Category#register. Categories are records compared by id.
     */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(JEIChainCraftMod.MODID, "main"));

    public static final KeyMapping OPEN_CHAIN = new KeyMapping(
            "key." + JEIChainCraftMod.MODID + ".open_chain",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            CATEGORY
    );

    private KeyBindings() {}

    public static boolean matches(KeyEvent event) {
        return OPEN_CHAIN.matches(event);
    }
}
