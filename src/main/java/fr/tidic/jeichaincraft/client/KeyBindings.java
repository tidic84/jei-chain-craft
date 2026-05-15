package fr.tidic.jeichaincraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@EventBusSubscriber(modid = JEIChainCraftMod.MODID, value = Dist.CLIENT)
public final class KeyBindings {

    public static final KeyMapping OPEN_CHAIN = new KeyMapping(
            "key." + JEIChainCraftMod.MODID + ".open_chain",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            "key.categories." + JEIChainCraftMod.MODID
    );

    private KeyBindings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAIN);
    }

    public static boolean matches(int keyCode, int scanCode) {
        return OPEN_CHAIN.matches(keyCode, scanCode);
    }
}
