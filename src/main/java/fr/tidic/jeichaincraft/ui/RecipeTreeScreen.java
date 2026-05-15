package fr.tidic.jeichaincraft.ui;

import fr.tidic.jeichaincraft.core.CraftPlanner;
import fr.tidic.jeichaincraft.core.InventoryAnalyzer;
import fr.tidic.jeichaincraft.core.ItemId;
import fr.tidic.jeichaincraft.core.NodeStatus;
import fr.tidic.jeichaincraft.core.PreferenceManager;
import fr.tidic.jeichaincraft.core.RecipeLookup;
import fr.tidic.jeichaincraft.core.RecipeNode;
import fr.tidic.jeichaincraft.core.RecipeTreeBuilder;
import fr.tidic.jeichaincraft.executor.CraftExecutor;
import fr.tidic.jeichaincraft.executor.CraftHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree screen — owns the target stack and desired quantity, rebuilds on demand.
 *
 * Layout:
 *   ┌───────────────────────────────────────────────────────────┐
 *   │  title                              Qty: [__] [Stack]     │  HEADER
 *   ├──────────────────────────────────────────┬────────────────┤
 *   │  tree (clipped + scrollbar)              │  base resources│
 *   ├──────────────────────────────────────────┴────────────────┤
 *   │  [Execute][Abort]   status   [Dump][Reset] [Done]         │  ACTION
 *   └───────────────────────────────────────────────────────────┘
 */
public class RecipeTreeScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 18;
    private static final int HEADER_HEIGHT = 32;
    private static final int ACTION_BAR_HEIGHT = 32;
    private static final int SIDEBAR_WIDTH = 190;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int ALT_INDICATOR_X_OFFSET = 130;
    private static final int ALT_INDICATOR_WIDTH = 32;
    private static final int TREE_LEFT = 12;

    private final ItemStack targetStack;
    private final InventoryAnalyzer inventory;
    private final PreferenceManager prefs;

    private int quantity;
    private RecipeNode root;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private boolean draggingScrollbar;

    private EditBox quantityBox;
    private Button executeButton;
    private Button abortButton;
    private Component statusLine = Component.empty();
    private CraftExecutor.State lastObservedState;

    public RecipeTreeScreen(ItemStack targetStack, int quantity,
                            InventoryAnalyzer inventory, PreferenceManager prefs) {
        super(Component.translatable("jeichaincraft.screen.tree.title"));
        this.targetStack = targetStack;
        this.quantity = Math.max(1, quantity);
        this.inventory = inventory;
        this.prefs = prefs;
    }

    private record Row(RecipeNode node, int depth) {}

    @Override
    protected void init() {
        rebuildTree();

        // Header: quantity controls on the right
        int qxRight = this.width - 12;
        Button stack = Button.builder(Component.translatable("jeichaincraft.button.stack"),
                        b -> setQuantity(targetStack.getMaxStackSize()))
                .bounds(qxRight - 50, 8, 50, 18).build();
        quantityBox = new EditBox(this.font, qxRight - 50 - 4 - 50, 8, 50, 18,
                Component.translatable("jeichaincraft.screen.tree.quantity"));
        quantityBox.setValue(String.valueOf(quantity));
        quantityBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,5}"));
        quantityBox.setResponder(this::onQuantityChanged);
        addRenderableWidget(quantityBox);
        addRenderableWidget(stack);

        // Action bar
        int y = this.height - ACTION_BAR_HEIGHT + 6;
        executeButton = Button.builder(Component.translatable("jeichaincraft.button.execute"),
                        b -> startExecution())
                .bounds(8, y, 88, 20).build();
        abortButton = Button.builder(Component.translatable("jeichaincraft.button.abort"),
                        b -> CraftExecutor.cancel())
                .bounds(100, y, 60, 20).build();
        Button dump = Button.builder(Component.translatable("jeichaincraft.button.dump"),
                        b -> RecipeLookup.dumpDebug(targetStack))
                .bounds(this.width - 200, y, 50, 20).build();
        Button resetPrefs = Button.builder(Component.translatable("jeichaincraft.button.reset_prefs"),
                        b -> { prefs.clear(); rebuildTree(); })
                .bounds(this.width - 146, y, 70, 20).build();
        Button close = Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width - 72, y, 64, 20).build();
        addRenderableWidget(executeButton);
        addRenderableWidget(abortButton);
        addRenderableWidget(dump);
        addRenderableWidget(resetPrefs);
        addRenderableWidget(close);

        refreshButtons();
    }

    public void setQuantity(int q) {
        quantity = Math.max(1, Math.min(99999, q));
        if (quantityBox != null) quantityBox.setValue(String.valueOf(quantity));
        rebuildTree();
    }

    private void onQuantityChanged(String text) {
        if (text.isEmpty()) return;
        try {
            int q = Integer.parseInt(text);
            if (q < 1) return;
            quantity = q;
            rebuildTree();
        } catch (NumberFormatException ignored) {}
    }

    public void rebuildTree() {
        root = new RecipeTreeBuilder(inventory, prefs).build(targetStack, quantity);
        rebuildRows();
        clampScroll();
    }

    private void rebuildRows() {
        rows.clear();
        appendRow(root, 0);
    }

    private void appendRow(RecipeNode node, int depth) {
        rows.add(new Row(node, depth));
        if (!node.expanded) return;
        for (RecipeNode c : node.children) appendRow(c, depth + 1);
    }

    private void startExecution() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (CraftHandlerRegistry.find(player.containerMenu).isEmpty()) {
            statusLine = Component.translatable("jeichaincraft.executor.error.no_handler");
            return;
        }
        var steps = CraftPlanner.steps(root);
        if (steps.isEmpty()) {
            statusLine = Component.translatable("jeichaincraft.executor.error.nothing_to_craft");
            return;
        }
        CraftExecutor.start(steps);
        statusLine = Component.empty();
        lastObservedState = CraftExecutor.State.PLACING;
    }

    private void refreshButtons() {
        CraftExecutor active = CraftExecutor.active();
        boolean running = active != null && active.isRunning();
        executeButton.active = !running;
        abortButton.active = running;
    }

    /** Detects running→terminal transition and refreshes tree once. */
    private void observeExecutor() {
        CraftExecutor active = CraftExecutor.active();
        if (active == null) return;

        CraftExecutor.State now = active.state();
        boolean wasRunning = lastObservedState != null
                && lastObservedState != CraftExecutor.State.DONE
                && lastObservedState != CraftExecutor.State.ABORTED;
        boolean isTerminal = now == CraftExecutor.State.DONE
                || now == CraftExecutor.State.ABORTED;

        if (wasRunning && isTerminal) {
            if (now == CraftExecutor.State.DONE) {
                statusLine = Component.translatable("jeichaincraft.executor.done");
            } else if (active.error() != null) {
                statusLine = active.error();
            } else {
                statusLine = Component.translatable("jeichaincraft.executor.aborted");
            }
            rebuildTree();
        }
        lastObservedState = now;
    }

    // ─────────────────────────────────────────────────────────────── rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        observeExecutor();

        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);

        drawHeader(g);
        Row hovered = drawTree(g, mouseX, mouseY);
        drawSidebar(g);
        drawActionBar(g);
        refreshButtons();

        if (hovered != null && hovered.node.recipeId != null) {
            drawRecipeTooltip(g, hovered.node, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphics g) {
        g.drawCenteredString(font, this.title, this.width / 2, 13, 0xFFFFFFFF);
        // Qty label to the left of the EditBox
        if (quantityBox != null) {
            g.drawString(font, Component.translatable("jeichaincraft.screen.tree.quantity"),
                    quantityBox.getX() - 26, 13, 0xFFAAAAAA);
        }
    }

    private int treeRightEdge() {
        return this.width - SIDEBAR_WIDTH - 12;
    }

    private int treeAreaTop() {
        return HEADER_HEIGHT;
    }

    private int treeAreaBottom() {
        return this.height - ACTION_BAR_HEIGHT;
    }

    private int treeAreaHeight() {
        return treeAreaBottom() - treeAreaTop();
    }

    private int contentHeight() {
        return rows.size() * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - treeAreaHeight());
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private Row drawTree(GuiGraphics g, int mouseX, int mouseY) {
        int top = treeAreaTop();
        int bottom = treeAreaBottom();
        int right = treeRightEdge();

        // Clip everything in the tree area so rows do not bleed into header or action bar.
        g.enableScissor(0, top, right, bottom);

        Row hovered = null;
        int y = top - scroll;
        for (Row r : rows) {
            if (y + ROW_HEIGHT > top && y < bottom) {
                drawNodeRow(g, r, TREE_LEFT, y);
                if (mouseY >= y && mouseY <= y + 18) {
                    int xOff = TREE_LEFT + r.depth * INDENT;
                    if (mouseX >= xOff && mouseX <= xOff + 200) hovered = r;
                }
            }
            y += ROW_HEIGHT;
        }

        g.disableScissor();

        drawScrollbar(g, right - SCROLLBAR_WIDTH - 2, top, bottom);
        return hovered;
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom) {
        int trackH = bottom - top;
        int contentH = contentHeight();
        if (contentH <= trackH) return; // nothing to scroll

        g.fill(x, top, x + SCROLLBAR_WIDTH, bottom, 0xFF202020);
        int handleH = Math.max(20, trackH * trackH / contentH);
        int handleY = top + (maxScroll() == 0 ? 0 : scroll * (trackH - handleH) / maxScroll());
        g.fill(x, handleY, x + SCROLLBAR_WIDTH, handleY + handleH, 0xFFAAAAAA);
    }

    private void drawNodeRow(GuiGraphics g, Row row, int x, int y) {
        int xOff = x + row.depth * INDENT;
        int color = statusColor(row.node.status);

        g.fill(xOff, y, xOff + 18, y + 18, 0xFF202020);
        g.fill(xOff, y, xOff + 18, y + 18, color & 0x80FFFFFF);
        g.renderItem(row.node.target, xOff + 1, y + 1);
        g.renderItemDecorations(font, row.node.target, xOff + 1, y + 1);

        String label = row.node.target.getHoverName().getString();
        String count = row.node.have + "/" + row.node.needed;
        g.drawString(font, label, xOff + 22, y + 2, 0xFFFFFFFF);
        g.drawString(font, count, xOff + 22, y + 11, 0xFFAAAAAA);

        if (row.node.alternatives > 0) {
            String alt = "+" + row.node.alternatives;
            g.fill(xOff + ALT_INDICATOR_X_OFFSET, y + 2,
                    xOff + ALT_INDICATOR_X_OFFSET + ALT_INDICATOR_WIDTH, y + 16,
                    0xFF1A3A60);
            g.drawString(font, alt, xOff + ALT_INDICATOR_X_OFFSET + 4, y + 6, 0xFF80C0FF);
        }
    }

    private void drawSidebar(GuiGraphics g) {
        int x = this.width - SIDEBAR_WIDTH;
        int y = HEADER_HEIGHT;
        g.drawString(font, Component.translatable("jeichaincraft.screen.tree.base_resources"),
                x, y, 0xFFFFFFFF);
        int row = y + 14;
        for (CraftPlanner.BaseResource res : CraftPlanner.baseResources(root)) {
            g.renderItem(res.stack(), x, row);
            g.renderItemDecorations(font, res.stack(), x, row);
            int color = res.missing() == 0 ? 0xFF60FF60 : 0xFFFF6060;
            g.drawString(font, res.have() + "/" + res.needed(), x + 22, row + 4, color);
            row += 20;
            if (row > this.height - ACTION_BAR_HEIGHT - 4) break;
        }
    }

    private void drawActionBar(GuiGraphics g) {
        int barY = this.height - ACTION_BAR_HEIGHT;
        g.fill(0, barY, this.width, this.height, 0xC0000000);
        g.fill(0, barY, this.width, barY + 1, 0xFF505050);

        CraftExecutor active = CraftExecutor.active();
        int statusX = 168;
        int statusY = barY + 12;

        if (active != null && active.isRunning()) {
            int total = Math.max(1, active.total());
            int prog = active.progress();
            int barW = 160;
            int barH = 6;
            int barTop = barY + 13;
            g.fill(statusX, barTop, statusX + barW, barTop + barH, 0xFF202020);
            int fill = (int) (barW * (prog / (float) total));
            g.fill(statusX, barTop, statusX + fill, barTop + barH, 0xFF40FF40);
            g.drawString(font, prog + " / " + total, statusX + barW + 6, barTop - 1, 0xFFFFFFFF);
        } else if (!statusLine.getString().isEmpty()) {
            int color = active != null && active.state() == CraftExecutor.State.ABORTED
                    ? 0xFFFF6060
                    : 0xFFFFAA40;
            g.drawString(font, statusLine, statusX, statusY, color);
        }
    }

    private int statusColor(NodeStatus s) {
        return switch (s) {
            case HAVE      -> 0xFF40FF40;
            case CRAFTABLE -> 0xFFFFA040;
            case MISSING   -> 0xFFFF4040;
            case CYCLE     -> 0xFFFF40FF;
            case AMBIGUOUS -> 0xFF40A0FF;
        };
    }

    private void drawRecipeTooltip(GuiGraphics g, RecipeNode node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(node.recipeId).withStyle(s -> s.withColor(0xFFFFAA40)));
        if (!node.children.isEmpty()) {
            lines.add(Component.translatable("jeichaincraft.tooltip.ingredients"));
            for (RecipeNode child : node.children) {
                lines.add(Component.literal("  - " + child.needed + " x " + ItemId.of(child.target)));
            }
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ───────────────────────────────────────────────────────────────── input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Scrollbar click: jump-to or start drag
        int top = treeAreaTop();
        int bottom = treeAreaBottom();
        int sbX = treeRightEdge() - SCROLLBAR_WIDTH - 2;
        if (contentHeight() > treeAreaHeight()
                && mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH
                && mouseY >= top && mouseY <= bottom) {
            draggingScrollbar = true;
            jumpScrollTo(mouseY);
            return true;
        }

        // Tree row clicks
        int treeY = top - scroll;
        for (Row r : rows) {
            int xOff = TREE_LEFT + r.depth * INDENT;
            if (mouseY < treeY || mouseY > treeY + 18) {
                treeY += ROW_HEIGHT;
                continue;
            }

            if (r.node.alternatives > 0
                    && mouseX >= xOff + ALT_INDICATOR_X_OFFSET
                    && mouseX <= xOff + ALT_INDICATOR_X_OFFSET + ALT_INDICATOR_WIDTH) {
                openPicker(r.node);
                return true;
            }

            if (mouseX >= xOff && mouseX <= xOff + 200) {
                if (!r.node.isLeaf()) {
                    r.node.expanded = !r.node.expanded;
                    rebuildRows();
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScrollbar) {
            jumpScrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void jumpScrollTo(double mouseY) {
        int top = treeAreaTop();
        int trackH = treeAreaHeight();
        double frac = (mouseY - top) / trackH;
        scroll = (int) Math.max(0, Math.min(maxScroll(), frac * maxScroll()));
    }

    private void openPicker(RecipeNode node) {
        ResourceLocation itemId = ItemId.of(node.target);
        List<RecipeHolder<?>> candidates = RecipeLookup.recipesProducing(node.target);
        if (candidates.size() <= 1) return;
        Minecraft.getInstance().setScreen(new RecipePickerScreen(
                this, node.target, candidates, chosen -> {
            prefs.remember(itemId, chosen.id());
            rebuildTree();
        }));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) (dy * ROW_HEIGHT));
        clampScroll();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
