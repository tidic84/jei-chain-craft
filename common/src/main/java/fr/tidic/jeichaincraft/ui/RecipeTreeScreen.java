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
import fr.tidic.jeichaincraft.hud.PinnedFarmList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    private static final int ROW_HEIGHT = 24;
    private static final int INDENT = 16;
    private static final int HEADER_HEIGHT = 32;
    private static final int ACTION_BAR_HEIGHT = 32;
    private static final int SIDEBAR_WIDTH = 190;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int STRIPE_WIDTH = 3;
    private static final int TRI_WIDTH = 10;
    private static final int ICON_OFFSET = STRIPE_WIDTH + 2 + TRI_WIDTH;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_OFFSET = ICON_OFFSET + ICON_SIZE + 4;
    private static final int ALT_INDICATOR_X_OFFSET = 160;
    private static final int ALT_INDICATOR_WIDTH = 32;
    private static final int TAG_INDICATOR_X_OFFSET = 196;
    private static final int TAG_INDICATOR_WIDTH = 22;
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
        this.inventory = inventory;
        this.prefs = prefs;
        this.quantity = Math.max(1, quantity);
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
        // EditBox lost setFilter in 26.x: cap the length, non-digits are ignored by onQuantityChanged.
        quantityBox.setMaxLength(5);
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
        Button pin = Button.builder(Component.translatable("jeichaincraft.button.pin"),
                        b -> PinnedFarmList.set(CraftPlanner.baseResources(root)))
                .bounds(164, y, 50, 20).build();
        Button clearPin = Button.builder(Component.translatable("jeichaincraft.button.clear_pin"),
                        b -> PinnedFarmList.clear())
                .bounds(218, y, 60, 20).build();
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
        addRenderableWidget(pin);
        addRenderableWidget(clearPin);
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        observeExecutor();
        if (root != null) root.refreshCounts(inventory);

        super.extractRenderState(g, mouseX, mouseY, partial);

        drawHeader(g);
        Row hovered = drawTree(g, mouseX, mouseY);
        drawSidebar(g);
        drawActionBar(g);
        refreshButtons();

        if (hovered != null && hovered.node.recipeId != null) {
            drawRecipeTooltip(g, hovered.node, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        g.centeredText(font, this.title, this.width / 2, 13, 0xFFFFFFFF);
        // Qty label to the left of the EditBox
        if (quantityBox != null) {
            g.text(font, Component.translatable("jeichaincraft.screen.tree.quantity"),
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

    private Row drawTree(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int top = treeAreaTop();
        int bottom = treeAreaBottom();
        int right = treeRightEdge();

        // Clip everything in the tree area so rows do not bleed into header or action bar.
        g.enableScissor(0, top, right, bottom);

        Row hovered = null;
        int y = top - scroll;
        for (Row r : rows) {
            if (y + ROW_HEIGHT > top && y < bottom) {
                int xOff = TREE_LEFT + r.depth * INDENT;
                boolean isHover = mouseX >= xOff && mouseX <= right - SCROLLBAR_WIDTH - 4
                        && mouseY >= y && mouseY <= y + ROW_HEIGHT;
                drawNodeRow(g, r, TREE_LEFT, y, right - SCROLLBAR_WIDTH - 4, isHover);
                if (isHover) hovered = r;
            }
            y += ROW_HEIGHT;
        }

        g.disableScissor();

        drawScrollbar(g, right - SCROLLBAR_WIDTH - 2, top, bottom);
        return hovered;
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int top, int bottom) {
        int trackH = bottom - top;
        int contentH = contentHeight();
        if (contentH <= trackH) return; // nothing to scroll

        g.fill(x, top, x + SCROLLBAR_WIDTH, bottom, 0xFF202020);
        int handleH = Math.max(20, trackH * trackH / contentH);
        int handleY = top + (maxScroll() == 0 ? 0 : scroll * (trackH - handleH) / maxScroll());
        g.fill(x, handleY, x + SCROLLBAR_WIDTH, handleY + handleH, 0xFFAAAAAA);
    }

    private void drawNodeRow(GuiGraphicsExtractor g, Row row, int x, int y, int rightEdge, boolean hover) {
        int xOff = x + row.depth * INDENT;
        int color = statusColor(row.node.status);

        // Full-row hover highlight (before any content)
        if (hover) g.fill(xOff, y, rightEdge, y + ROW_HEIGHT, 0x40FFFFFF);

        // Left status stripe — 3px wide, full row height
        g.fill(xOff, y + 2, xOff + STRIPE_WIDTH, y + ROW_HEIGHT - 2, color);

        // Expand/collapse triangle for non-leaves
        if (!row.node.isLeaf()) {
            String tri = row.node.expanded ? "v" : ">";
            g.text(font, tri, xOff + STRIPE_WIDTH + 4, y + 8, 0xFFCCCCCC);
        }

        // Item icon
        int iconX = xOff + ICON_OFFSET;
        int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
        g.item(row.node.target, iconX, iconY);
        g.itemDecorations(font, row.node.target, iconX, iconY);

        // Label + count
        String label = row.node.target.getHoverName().getString();
        String count;
        if (row.node.isRoot) {
            count = "x " + row.node.needed
                    + (row.node.have > 0 ? " (have " + row.node.have + ")" : "");
        } else {
            count = row.node.have + " / " + row.node.needed;
        }
        g.text(font, label, xOff + TEXT_OFFSET, y + 4, 0xFFFFFFFF);
        int countColor = switch (row.node.status) {
            case HAVE      -> 0xFF80FF80;
            case MISSING   -> 0xFFFF8080;
            case CYCLE     -> 0xFFFF80FF;
            default        -> 0xFFAAAAAA;
        };
        g.text(font, count, xOff + TEXT_OFFSET, y + 14, countColor);

        if (row.node.alternatives > 0) {
            String alt = "+" + row.node.alternatives;
            int altX = xOff + ALT_INDICATOR_X_OFFSET;
            g.fill(altX, y + 4, altX + ALT_INDICATOR_WIDTH, y + ROW_HEIGHT - 4, 0xFF1A3A60);
            g.text(font, alt, altX + 4, y + 8, 0xFF80C0FF);
        }

        if (row.node.hasIngredientChoice()) {
            int tagX = xOff + TAG_INDICATOR_X_OFFSET;
            g.fill(tagX, y + 4, tagX + TAG_INDICATOR_WIDTH, y + ROW_HEIGHT - 4, 0xFF603A1A);
            g.text(font, "#" + row.node.ingredientOptions.size(),
                    tagX + 4, y + 8, 0xFFFFC080);
        }
    }

    private void drawSidebar(GuiGraphicsExtractor g) {
        int x = this.width - SIDEBAR_WIDTH;
        int y = HEADER_HEIGHT;
        g.text(font, Component.translatable("jeichaincraft.screen.tree.base_resources"),
                x, y, 0xFFFFFFFF);

        List<CraftPlanner.BaseResource> resources = CraftPlanner.baseResources(root);
        if (resources.isEmpty()) {
            g.text(font, Component.translatable("jeichaincraft.screen.tree.no_base_resources"),
                    x, y + 14, 0xFF888888);
            return;
        }

        int row = y + 14;
        for (CraftPlanner.BaseResource res : resources) {
            g.item(res.stack(), x, row);
            g.itemDecorations(font, res.stack(), x, row);
            int color = res.missing() == 0 ? 0xFF60FF60 : 0xFFFF6060;
            g.text(font, res.have() + "/" + res.needed(), x + 22, row + 4, color);
            row += 20;
            if (row > this.height - ACTION_BAR_HEIGHT - 4) break;
        }
    }

    private void drawActionBar(GuiGraphicsExtractor g) {
        int barY = this.height - ACTION_BAR_HEIGHT;
        g.fill(0, barY, this.width, this.height, 0xC0000000);
        g.fill(0, barY, this.width, barY + 1, 0xFF505050);

        CraftExecutor active = CraftExecutor.active();
        // Between the left button group (ends with Clear pin at x=278) and
        // the right group (starts with Dump at width-200).
        int statusX = 282;
        int statusRight = this.width - 204;
        int statusY = barY + 12;

        if (active != null && active.isRunning()) {
            int total = Math.max(1, active.total());
            int prog = active.progress();
            String counter = prog + " / " + total;
            int barW = Math.max(20, Math.min(160, statusRight - statusX - font.width(counter) - 6));
            int barH = 6;
            int barTop = barY + 13;
            g.fill(statusX, barTop, statusX + barW, barTop + barH, 0xFF202020);
            int fill = (int) (barW * (prog / (float) total));
            g.fill(statusX, barTop, statusX + fill, barTop + barH, 0xFF40FF40);
            g.text(font, counter, statusX + barW + 6, barTop - 1, 0xFFFFFFFF);
        } else if (!statusLine.getString().isEmpty()) {
            int color = active != null && active.state() == CraftExecutor.State.ABORTED
                    ? 0xFFFF6060
                    : 0xFFFFAA40;
            int maxW = Math.max(0, statusRight - statusX);
            String text = statusLine.getString();
            if (font.width(text) > maxW) {
                text = font.plainSubstrByWidth(text, Math.max(0, maxW - font.width("..."))) + "...";
            }
            g.text(font, text, statusX, statusY, color);
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

    private void drawRecipeTooltip(GuiGraphicsExtractor g, RecipeNode node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(node.recipeId).withStyle(s -> s.withColor(0xFFFFAA40)));
        if (!node.children.isEmpty()) {
            lines.add(Component.translatable("jeichaincraft.tooltip.ingredients"));
            for (RecipeNode child : node.children) {
                lines.add(Component.literal("  - " + child.needed + " x " + ItemId.of(child.target)));
            }
        }
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    // ───────────────────────────────────────────────────────────────── input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mouseX = event.x();
        double mouseY = event.y();

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
            if (mouseY < treeY || mouseY > treeY + ROW_HEIGHT) {
                treeY += ROW_HEIGHT;
                continue;
            }

            if (r.node.alternatives > 0
                    && mouseX >= xOff + ALT_INDICATOR_X_OFFSET
                    && mouseX <= xOff + ALT_INDICATOR_X_OFFSET + ALT_INDICATOR_WIDTH) {
                openPicker(r.node);
                return true;
            }

            if (r.node.hasIngredientChoice()
                    && mouseX >= xOff + TAG_INDICATOR_X_OFFSET
                    && mouseX <= xOff + TAG_INDICATOR_X_OFFSET + TAG_INDICATOR_WIDTH) {
                openIngredientPicker(r.node);
                return true;
            }

            int rowRightEdge = treeRightEdge() - SCROLLBAR_WIDTH - 4;
            if (mouseX >= xOff && mouseX <= rowRightEdge) {
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
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar) {
            jumpScrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void jumpScrollTo(double mouseY) {
        int top = treeAreaTop();
        int trackH = treeAreaHeight();
        double frac = (mouseY - top) / trackH;
        scroll = (int) Math.max(0, Math.min(maxScroll(), frac * maxScroll()));
    }

    private void openPicker(RecipeNode node) {
        Identifier itemId = ItemId.of(node.target);
        List<RecipeHolder<?>> candidates = RecipeLookup.recipesProducing(node.target);
        if (candidates.size() <= 1) return;
        Minecraft.getInstance().gui.setScreen(new RecipePickerScreen(
                this, node.target, candidates, chosen -> {
            prefs.remember(itemId, RecipeLookup.idOf(chosen));
            rebuildTree();
        }));
    }

    private void openIngredientPicker(RecipeNode node) {
        if (!node.hasIngredientChoice() || node.parentRecipeId == null) return;
        Identifier recipeId = node.parentRecipeId;
        List<Integer> slots = node.parentSlotIndices;
        Minecraft.getInstance().gui.setScreen(new IngredientPickerScreen(
                this, node.ingredientOptions, chosen -> {
            Identifier pick = ItemId.of(chosen);
            // Aggregated children carry every slot they fill. Writing the
            // preference to all of them keeps the swap consistent — picking
            // "chest" once on a recipe with four chest slots replaces every
            // chest, not just one.
            for (int slotIdx : slots) {
                prefs.rememberIngredient(recipeId, slotIdx, pick);
            }
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
