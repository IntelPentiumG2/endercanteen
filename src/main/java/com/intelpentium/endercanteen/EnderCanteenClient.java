package com.intelpentium.endercanteen;

import com.intelpentium.endercanteen.compat.AppleSkinCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = EnderCanteen.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = EnderCanteen.MODID, value = Dist.CLIENT)
public class EnderCanteenClient {

    public EnderCanteenClient(ModContainer container) {
        // Register AppleSkin FoodValuesEvent only on the client and only if AppleSkin is installed.
        // AppleSkin is a client-only mod – its classes are never present on a dedicated server.
        if (ModList.get().isLoaded("appleskin")) {
            NeoForge.EVENT_BUS.addListener(AppleSkinCompat::onFoodValues);
            EnderCanteen.LOGGER.info("[EnderCanteen] AppleSkin detected – thirst preview enabled.");
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        EnderCanteen.LOGGER.info("[EnderCanteen] Client setup complete.");
    }
}


