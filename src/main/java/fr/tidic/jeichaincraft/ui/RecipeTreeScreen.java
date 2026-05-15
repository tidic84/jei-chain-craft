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
 * Click zones per tree row:
 *   - icon (xOff..+18)              → expand/collapse if not a leaf
 *   - alternatives indicator (+N)   → open RecipePickerScreen
 *
 * Status colors:
 *   HAVE green / CRAFTABLE orange / MISSING red / CYCLE magenta / AMBIGUOUS blue
 */
public class RecipeTreeScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 18;
    private static final int HEADER_HEIGHT = 32;
    private static final int ACTION_BAR_HEIGHT = 32;
    private static final int ALT_INDICATOR_X_OFFSET = 130;
    private static final int ALT_INDICATOR_WIDTH = 32;

    private final ItemStack targetStack;
    private final InventoryAnalyzer inventory;
    private final PreferenceManager prefs;

    private int quantity;
    private RecipeNode root;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    private EditBox quantityBox;
    private Button executeButton;
    private Button abortButton;
    private Component statusLine = Component.empty();

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

        quantityBox = new EditBox(this.font, 80, 8, 60, 18,
                Component.translatable("jeichaincraft.screen.tree.quantity"));
        quantityBox.setValue(String.valueOf(quantity));
        quantityBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,5}"));
        quantityBox.setResponder(this::onQuantityChanged);
        addRenderableWidget(quantityBox);

        Button minus = Button.builder(Component.literal("-"), b -> bumpQuantity(-1))
                .bounds(60, 8, 18, 18).build();
        Button plus = Button.builder(Component.literal("+"), b -> bumpQuantity(+1))
                .bounds(142, 8, 18, 18).build();
        Button stack = Button.builder(Component.translatable("jeichaincraft.button.stack"),
                        b -> setQuantity(targetStack.getMaxStackSize()))
                .bounds(164, 8, 48, 18).build();
        addRenderableWidget(minus);
        addRenderableWidget(plus);
        addRenderableWidget(stack);

        int y = this.height - ACTION_BAR_HEIGHT + 4;
        executeButton = Button.builder(Component.translatable("jeichaincraft.button.execute"),
                        b -> startExecution())
                .bounds(8, y, 110, 20).build();
        abortButton = Button.builder(Component.translatable("jeichaincraft.button.abort"),
                        b -> CraftExecutor.cancel())
                .bounds(124, y, 110, 20).build();
        Button dump = Button.builder(Component.translatable("jeichaincraft.button.dump"),
                        b -> RecipeLookup.dumpDebug(targetStack))
                .bounds(240, y, 60, 20).build();
        Button resetPrefs = Button.builder(Component.translatable("jeichaincraft.button.reset_prefs"),
                        b -> { prefs.clear(); rebuildTree(); })
                .bounds(304, y, 80, 20).build();
        Button close = Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width - 80, y, 72, 20).build();
        addRenderableWidget(executeButton);
        addRenderableWidget(abortButton);
        addRenderableWidget(dump);
        addRenderableWidget(resetPrefs);
        addRenderableWidget(close);

        refreshButtons();
    }

    private void bumpQuantity(int delta) {
        setQuantity(quantity + delta);
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
        statusLine = Component.translatable("jeichaincraft.executor.running");
    }

    private void refreshButtons() {
        CraftExecutor active = CraftExecutor.active();
        boolean running = active != null
                && active.state() != CraftExecutor.State.DONE
                && active.state() != CraftExecutor.State.ABORTED;
        executeButton.active = !running;
        abortButton.active = running;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);

        int treeX = 12;
        int treeY = HEADER_HEIGHT - scroll;
        int sidebarX = this.width - 200;

        g.drawString(font, Component.translatable("jeichaincraft.screen.tree.quantity"),
                10, 13, 0xFFFFFFFF);
        g.drawCenteredString(font, this.title, this.width / 2, 13, 0xFFFFFFFF);
        g.renderItem(targetStack, 220, 6);
        // Debug aid: show the resolved item id of the root
        g.drawString(font, ItemId.of(targetStack).toString(), 242, 13, 0xFF888888);

        Row hoveredRow = null;
        for (Row r : rows) {
            if (treeY > -ROW_HEIGHT && treeY < this.height - ACTION_BAR_HEIGHT) {
                drawNodeRow(g, r, treeX, treeY);
                if (mouseY >= treeY && mouseY <= treeY + 18) {
                    int xOff = treeX + r.depth * INDENT;
                    if (mouseX >= xOff && mouseX <= xOff + 200) {
                        hoveredRow = r;
                    }
                }
            }
            treeY += ROW_HEIGHT;
        }

        drawSidebar(g, sidebarX, HEADER_HEIGHT);
        drawActionBar(g);
        refreshButtons();

        if (hoveredRow != null && hoveredRow.node.recipeId != null) {
            drawRecipeTooltip(g, hoveredRow.node, mouseX, mouseY);
        }
    }

    private void drawRecipeTooltip(GuiGraphics g, RecipeNode node, int mouseX, int mouseY) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal(node.recipeId).withStyle(s -> s.withColor(0xFFFFAA40)));
        lines.add(Component.translatable("jeichaincraft.tooltip.ingredients"));
        for (RecipeNode child : node.children) {
            lines.add(Component.literal("  - " + child.needed + " x " + ItemId.of(child.target)));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
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

    private void drawSidebar(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("jeichaincraft.screen.tree.base_resources"),
                x, y, 0xFFFFFFFF);
        int row = y + 14;
        for (CraftPlanner.BaseResource res : CraftPlanner.baseResources(root)) {
            g.renderItem(res.stack(), x, row);
            g.renderItemDecorations(font, res.stack(), x, row);
            int missing = res.missing();
            int color = missing == 0 ? 0xFF60FF60 : 0xFFFF6060;
            String text = res.have() + "/" + res.needed();
            g.drawString(font, text, x + 22, row + 4, color);
            row += 20;
            if (row > this.height - ACTION_BAR_HEIGHT - 4) break;
        }
    }

    private void drawActionBar(GuiGraphics g) {
        int barY = this.height - ACTION_BAR_HEIGHT;
        g.fill(0, barY, this.width, this.height, 0xC0000000);

        CraftExecutor active = CraftExecutor.active();
        if (active != null) {
            int total = Math.max(1, active.total());
            int prog = active.progress();
            int barW = 200;
            int barX = 240;
            int barH = 6;
            int barTop = barY + 10;
            g.fill(barX, barTop, barX + barW, barTop + barH, 0xFF202020);
            int fill = (int) (barW * (prog / (float) total));
            g.fill(barX, barTop, barX + fill, barTop + barH, 0xFF40FF40);
            g.drawString(font, prog + " / " + total, barX + barW + 8, barTop - 1, 0xFFFFFFFF);

            if (active.state() == CraftExecutor.State.ABORTED && active.error() != null) {
                g.drawString(font, active.error(), barX, barTop + 12, 0xFFFF6060);
            }
        } else if (!statusLine.getString().isEmpty()) {
            g.drawString(font, statusLine, 240, barY + 12, 0xFFFFAA40);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int treeY = HEADER_HEIGHT - scroll;
        for (Row r : rows) {
            int xOff = 12 + r.depth * INDENT;
            if (mouseY < treeY || mouseY > treeY + 18) {
                treeY += ROW_HEIGHT;
                continue;
            }

            // +N alternatives indicator → open picker
            if (r.node.alternatives > 0
                    && mouseX >= xOff + ALT_INDICATOR_X_OFFSET
                    && mouseX <= xOff + ALT_INDICATOR_X_OFFSET + ALT_INDICATOR_WIDTH) {
                openPicker(r.node);
                return true;
            }

            // Icon / label → expand/collapse
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
        int maxScroll = Math.max(0,
                rows.size() * ROW_HEIGHT - this.height + HEADER_HEIGHT + ACTION_BAR_HEIGHT + 16);
        scroll = Math.min(scroll, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
