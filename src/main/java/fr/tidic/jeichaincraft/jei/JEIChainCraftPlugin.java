package fr.tidic.jeichaincraft.jei;

import fr.tidic.jeichaincraft.JEIChainCraftMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class JEIChainCraftPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(JEIChainCraftMod.MODID, "main");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration reg) {
        // Scope: crafting only. The chain tool simplifies recursive crafting;
        // it does not (intentionally) automate smelting, stonecutting, etc.
        ChainButtonDecorator<Object> decorator = new ChainButtonDecorator<>();
        reg.addRecipeCategoryDecorator(RecipeTypes.CRAFTING, cast(decorator));
    }

    @SuppressWarnings("unchecked")
    private static <T> ChainButtonDecorator<T> cast(ChainButtonDecorator<?> d) {
        return (ChainButtonDecorator<T>) d;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RecipeScreenClickHandler.bindRuntime(jeiRuntime);
    }
}
