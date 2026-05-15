package fr.tidic.jeichaincraft.executor;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import fr.tidic.jeichaincraft.core.CraftPlanner.CraftStep;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Walks a plan of {@link CraftStep}s, looping each step's {@code crafts} count
 * so the player ends up with exactly the quantity requested (not "as many as
 * fit in the grid" — that's what {@code craftAll=true} would do).
 *
 * Per craft: PLACE → wait → TAKE → cooldown → next.
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
        if (active != null) active.state = State.ABORTED;
    }

    private final List<CraftStep> steps;
    private final int totalCrafts;
    private int currentStep;
    private int craftsOnStep;
    private int totalCraftsDone;
    private int tickCounter;
    private State state = State.PLACING;
    private CraftHandler currentHandler;
    private Component error;

    private CraftExecutor(List<CraftStep> steps) {
        this.steps = steps;
        this.totalCrafts = steps.stream().mapToInt(CraftStep::crafts).sum();
        if (steps.isEmpty()) state = State.DONE;
    }

    public State state() { return state; }
    public int progress() { return totalCraftsDone; }
    public int total() { return totalCrafts; }
    public Component error() { return error; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (active == null) return;
        if (active.state == State.DONE || active.state == State.ABORTED) {
            active = null;
            return;
        }
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
                handler.placeIngredients(holder, menu);
                state = State.WAITING_FOR_OUTPUT;
                tickCounter = 0;
            }
            case WAITING_FOR_OUTPUT -> {
                if (++tickCounter >= currentHandler.placeToTakeTicks()) {
                    currentHandler.takeOutput(menu);
                    state = State.COOLDOWN;
                    tickCounter = 0;
                }
            }
            case COOLDOWN -> {
                if (++tickCounter >= currentHandler.afterTakeTicks()) {
                    craftsOnStep++;
                    totalCraftsDone++;
                    tickCounter = 0;
                    if (craftsOnStep < step.crafts()) {
                        state = State.PLACING;
                    } else {
                        advanceStep();
                    }
                }
            }
            default -> {}
        }
    }

    private void advanceStep() {
        currentStep++;
        craftsOnStep = 0;
        state = currentStep >= steps.size() ? State.DONE : State.PLACING;
    }

    private void abort(Component reason) {
        this.error = reason;
        this.state = State.ABORTED;
        JEIChainCraftMod.LOGGER.info("CraftExecutor aborted: {}", reason.getString());
    }
}
