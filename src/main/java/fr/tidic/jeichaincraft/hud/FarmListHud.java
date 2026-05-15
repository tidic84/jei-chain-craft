package fr.tidic.jeichaincraft.hud;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * HUD overlay listing the pinned items the player still needs to farm. Rows
 * refresh every frame against the live inventory; once an item's count reaches
 * the target, it renders as satisfied (green strikethrough).
 *
 * Positioned at the top-left so it does not clash with JEI's right-side panel.
 * Hidden when the pinned list is empty or the chat / debug screens are open.
 */
@EventBusSubscriber(modid = JEIChainCraftMod.MODID, value = Dist.CLIENT)
public final class FarmListHud {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(JEIChainCraftMod.MODID, "farm_list");

    private static final int ROW_HEIGHT = 20;
    private static final int PAD = 4;
    private static final int WIDTH = 150;

    private FarmListHud() {}

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, LAYER_ID, FarmListHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (PinnedFarmList.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.screen != null) return; // hide while any screen is open

        Font font = mc.font;
        List<PinnedFarmList.DisplayEntry> entries = PinnedFarmList.snapshot();
        int rows = entries.size();
        int width = WIDTH;
        int height = PAD * 2 + ROW_HEIGHT + rows * ROW_HEIGHT;
        int x = 4;
        int y = 4;

        // Background
        g.fill(x, y, x + width, y + height, 0xC0000000);
        g.fill(x, y, x + width, y + 1, 0xFF505050);
        g.fill(x, y + height - 1, x + width, y + height, 0xFF505050);

        // Title
        Component title = Component.translatable("jeichaincraft.hud.title");
        g.drawString(font, title, x + PAD, y + PAD + 2, 0xFFFFFFFF);

        int rowY = y + PAD + ROW_HEIGHT;
        int satisfied = 0;
        for (PinnedFarmList.DisplayEntry e : entries) {
            drawRow(g, font, e, x + PAD, rowY, width - PAD * 2);
            if (e.satisfied()) satisfied++;
            rowY += ROW_HEIGHT;
        }

        if (satisfied == entries.size()) {
            // All done — small green dot in title bar
            g.fill(x + width - 8, y + PAD + 3, x + width - 4, y + PAD + 7, 0xFF40FF40);
        }
    }

    private static void drawRow(GuiGraphics g, Font font, PinnedFarmList.DisplayEntry entry,
                                int x, int y, int width) {
        ItemStack stack = entry.stack();
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);

        String name = stack.getHoverName().getString();
        if (font.width(name) > width - 60) {
            name = font.plainSubstrByWidth(name, width - 60) + "...";
        }

        int textColor = entry.satisfied() ? 0xFF808080 : 0xFFFFFFFF;
        g.drawString(font, name, x + 22, y + 1, textColor);

        String count = entry.have() + "/" + entry.needed();
        int countColor = entry.satisfied() ? 0xFF60FF60 : 0xFFFF8060;
        int countX = x + width - font.width(count);
        g.drawString(font, count, countX, y + 9, countColor);

        if (entry.satisfied()) {
            // Strike-through line
            int sy = y + 5;
            g.fill(x + 22, sy, countX - 4, sy + 1, 0xFF808080);
        }
    }
}
