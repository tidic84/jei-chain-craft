package fr.tidic.jeichaincraft.fabric;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.client.KeyBindings;
import fr.tidic.jeichaincraft.executor.CraftExecutor;
import fr.tidic.jeichaincraft.hud.FarmListHud;
import fr.tidic.jeichaincraft.jei.ChainKeyHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

/**
 * Fabric entry point: wires the loader-agnostic code in :common to Fabric
 * API callbacks. JEI finds the plugin through the "jei_mod_plugin"
 * entrypoint in fabric.mod.json.
 */
public class JEIChainCraftFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        JEIChainCraftMod.init();

        // hoveredSlot is made public by jeichaincraft.accesswidener.
        ChainKeyHandler.setHoveredSlotLookup(screen -> screen.hoveredSlot);

        KeyBindingHelper.registerKeyBinding(KeyBindings.OPEN_CHAIN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> CraftExecutor.clientTick());
        HudRenderCallback.EVENT.register(FarmListHud::render);

        // Fabric has no global "key pressed in a screen" event: hook every
        // screen as it opens. Returning false from allowKeyPress cancels it.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) ->
                        !ChainKeyHandler.onKeyPressed(s, key, scancode)));
    }
}
