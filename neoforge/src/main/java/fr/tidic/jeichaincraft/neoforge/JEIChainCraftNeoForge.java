package fr.tidic.jeichaincraft.neoforge;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.client.KeyBindings;
import fr.tidic.jeichaincraft.executor.CraftExecutor;
import fr.tidic.jeichaincraft.hud.FarmListHud;
import fr.tidic.jeichaincraft.jei.ChainKeyHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry point: wires the loader-agnostic code in :common to
 * NeoForge's events. JEI finds the plugin through its @JeiPlugin annotation.
 */
@Mod(value = JEIChainCraftMod.MODID, dist = Dist.CLIENT)
public class JEIChainCraftNeoForge {

    public JEIChainCraftNeoForge(IEventBus modEventBus, ModContainer container) {
        JEIChainCraftMod.init();

        // hoveredSlot is made public by META-INF/accesstransformer.cfg.
        ChainKeyHandler.setHoveredSlotLookup(screen -> screen.hoveredSlot);

        KeyBindings.OPEN_CHAIN.setKeyConflictContext(KeyConflictContext.GUI);
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(KeyBindings.CATEGORY);
            event.register(KeyBindings.OPEN_CHAIN);
        });
        modEventBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, FarmListHud.LAYER_ID, FarmListHud::render));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CraftExecutor.clientTick());
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
            if (ChainKeyHandler.onKeyPressed(event.getScreen(), event.getKeyEvent())) {
                event.setCanceled(true);
            }
        });
    }
}
