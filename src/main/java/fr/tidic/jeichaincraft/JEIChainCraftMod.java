package fr.tidic.jeichaincraft;

import com.mojang.logging.LogUtils;
import fr.tidic.jeichaincraft.compat.tomsstorage.TomsStorageCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(value = JEIChainCraftMod.MODID, dist = Dist.CLIENT)
public class JEIChainCraftMod {
    public static final String MODID = "jeichaincraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JEIChainCraftMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("JEI Chain Craft loading");
        modEventBus.addListener((FMLClientSetupEvent event) -> TomsStorageCompat.init());
    }
}
