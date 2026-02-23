package com.intelpentium.endercanteen.compat;

import com.intelpentium.endercanteen.item.CanteenItem;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import squeek.appleskin.api.event.FoodValuesEvent;

/**
 * Client-only AppleSkin integration.
 *
 * This class MUST only be loaded on the client (Dist.CLIENT) because AppleSkin
 * is a client-only mod and its classes are not present on a dedicated server.
 * Registering this on the NeoForge event bus from within EnderCanteenClient
 * ensures it is never touched server-side.
 */
@OnlyIn(Dist.CLIENT)
public class AppleSkinCompat {

    /**
     * Provides synthetic FoodProperties to AppleSkin so the HUD overlay shows
     * a preview of how much thirst the canteen will restore.
     */
    public static void onFoodValues(FoodValuesEvent event) {
        if (!(event.itemStack.getItem() instanceof CanteenItem)) return;
        int thirst   = CanteenItem.calcThirst(CanteenItem.drinkMb());
        int quenched = CanteenItem.calcQuenched(CanteenItem.drinkMb());
        if (thirst <= 0) return;
        event.modifiedFoodProperties = new FoodProperties.Builder()
                .nutrition(thirst)
                .saturationModifier(quenched / (float) (thirst * 2))
                .build();
    }
}

