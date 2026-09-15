package fr.tidic.jeichaincraft.executor;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.core.CraftPlanner.CraftStep;
import fr.tidic.jeichaincraft.core.RecipeLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Walks a plan of {@link CraftStep}s one batch at a time:
 *   PLAN+PLACE (N crafts) → WAIT-FOR-OUTPUT → TAKE (N crafts) → COOLDOWN.
 *
 * The handler decides N ({@link CraftHandler#planBatch}) from what it can
 * guarantee — output room for vanilla, network stock for Tom's — and the
 * executor only takes once the output slot actually holds the expected item.
 *
 * Progress is counted from the batch that was taken, not by inventory delta.
 * The delta approach proved unreliable on Tom's terminal: the network reader
 * is one tick behind the inventory packet, the player inventory packet is
 * one tick behind the network update, and a single missed tick made the
 * executor conclude "no progress" and advance the step early.
 *
 * Handlers whose container auto-refills the grid declare
 * {@link CraftHandler#gridAutoRefills()} → after the take, the executor goes
 * back to WAIT-FOR-OUTPUT instead of PLACE, avoiding a redundant recipe-book
 * packet for every batch.
 */
@EventBusSubscriber(modid = JEIChainCraftMod.MODID, value = Dist.CLIENT)
public final class CraftExecutor {

    public enum State { PLACING, WAITING_FOR_OUTPUT, COOLDOWN, DONE, ABORTED }

    private static CraftExecutor active;

    public static CraftExecutor active() { return active; }

    public static void start(List<CraftStep> steps) {
        active = new CraftExecutor(steps);
    }

    public static void cancel() {
        if (active != null && active.isRunning()) active.state = State.ABORTED;
    }

    public boolean isRunning() {
        return state != State.DONE && state != State.ABORTED;
    }

    private final List<CraftStep> steps;
    private final long totalOutputsTarget;
    private int currentStep;
    private int stepOutputsPerCraft;
    private int stepCraftsDone;
    private long totalOutputsCrafted;
    private int tickCounter;
    private int outputSeenTicks;
    private int batchRequested;
    private int batchTaken;
    private State state = State.PLACING;
    private CraftHandler currentHandler;
    private Component error;

    private CraftExecutor(List<CraftStep> steps) {
        this.steps = steps;
        long total = 0;
        for (CraftStep s : steps) total += (long) s.crafts() * outputsPerCraft(s);
        this.totalOutputsTarget = Math.max(1, total);
        if (steps.isEmpty()) state = State.DONE;
        if (!steps.isEmpty()) primeStep();
    }

    public State state() { return state; }
    public int progress() { return (int) Math.min(Integer.MAX_VALUE, totalOutputsCrafted); }
    public int total() { return (int) Math.min(Integer.MAX_VALUE, totalOutputsTarget); }
    public Component error() { return error; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (active == null) return;
        if (!active.isRunning()) return;
        active.advance();
    }

    private void advance() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            abort(Component.translatable("jeichaincraft.executor.error.no_player"));
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            abort(Component.translatable("jeichaincraft.executor.error.no_menu"));
            return;
        }

        if (currentStep >= steps.size()) {
            state = State.DONE;
            return;
        }

        CraftStep step = steps.get(currentStep);

        switch (state) {
            case PLACING -> {
                RecipeHolder<?> holder = step.resolve().orElse(null);
                if (holder == null) {
                    JEIChainCraftMod.LOGGER.warn("Recipe not found: {} (skipping)", step.recipeId());
                    advanceStep();
                    return;
                }
                CraftHandler handler = CraftHandlerRegistry.find(menu).orElse(null);
                if (handler == null) {
                    abort(Component.translatable("jeichaincraft.executor.error.no_handler"));
                    return;
                }
                currentHandler = handler;
                int remaining = step.crafts() - stepCraftsDone;
                batchRequested = Math.max(1, Math.min(remaining, handler.planBatch(holder, menu, remaining)));
                handler.placeIngredients(holder, menu, batchRequested);
                enterWait();
            }
            case WAITING_FOR_OUTPUT -> {
                tickCounter++;
                // Ignore the very first ticks so the server has time to react
                // to the placement / refill packet at all; checking immediately
                // would just see the previous (or empty) state of the slot.
                if (tickCounter < currentHandler.placeToTakeTicks()) return;
                if (outputReady(menu, step.output().getItem(), currentHandler.outputSlotIndex())) {
                    int staged = Math.min(batchRequested, currentHandler.stagedCrafts(menu, batchRequested));
                    // Placement packets may sync over a couple of ticks; give
                    // the full batch a moment to show up before settling for less.
                    if (staged < batchRequested && outputSeenTicks++ < currentHandler.batchSettleTicks()) return;
                    batchTaken = Math.max(1, staged);
                    currentHandler.takeOutput(menu, step.output(), batchTaken);
                    state = State.COOLDOWN;
                    tickCounter = 0;
                } else if (tickCounter >= currentHandler.outputWaitTimeoutTicks()) {
                    // Output never appeared — ingredients exhausted or the
                    // network couldn't supply more. Move on to whatever comes
                    // next so a single starved step doesn't freeze the plan.
                    JEIChainCraftMod.LOGGER.warn(
                            "Step {} timed out waiting for output (slot {}); moving on",
                            step.recipeId(), currentHandler.outputSlotIndex());
                    advanceStep();
                }
            }
            case COOLDOWN -> {
                if (++tickCounter >= currentHandler.afterTakeTicks()) {
                    // A take only fires once the output is visible and the
                    // batch is bounded by what the handler can guarantee, so
                    // the batch size is the craft count — see class header.
                    stepCraftsDone += batchTaken;
                    totalOutputsCrafted += (long) batchTaken * stepOutputsPerCraft;
                    tickCounter = 0;

                    JEIChainCraftMod.LOGGER.info(
                            "Step {} crafts {}/{} (batch {})",
                            step.recipeId(), stepCraftsDone, step.crafts(), batchTaken);

                    if (stepCraftsDone >= step.crafts()) {
                        advanceStep();
                        return;
                    }
                    if (currentHandler.gridAutoRefills()) {
                        batchRequested = 1;
                        enterWait();
                    } else {
                        state = State.PLACING;
                    }
                }
            }
            default -> {}
        }
    }

    private void enterWait() {
        state = State.WAITING_FOR_OUTPUT;
        tickCounter = 0;
        outputSeenTicks = 0;
    }

    private void primeStep() {
        stepOutputsPerCraft = outputsPerCraft(steps.get(currentStep));
        stepCraftsDone = 0;
    }

    private void advanceStep() {
        currentStep++;
        if (currentStep >= steps.size()) {
            state = State.DONE;
        } else {
            primeStep();
            state = State.PLACING;
        }
    }

    private void abort(Component reason) {
        this.error = reason;
        this.state = State.ABORTED;
        JEIChainCraftMod.LOGGER.info("CraftExecutor aborted: {}", reason.getString());
    }

    private static int outputsPerCraft(CraftStep step) {
        RecipeHolder<?> h = step.resolve().orElse(null);
        return h == null ? 1 : RecipeLookup.outputCount(h);
    }

    private static boolean outputReady(AbstractContainerMenu menu, Item expected, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
        ItemStack s = menu.slots.get(slotIndex).getItem();
        return !s.isEmpty() && s.getItem() == expected;
    }
}
