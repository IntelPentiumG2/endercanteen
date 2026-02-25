package com.intelpentium.endercanteen.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Central access point for the thirst integration.
 * Returns the appropriate provider depending on which mod is installed.
 */
public class ThirstCompat {

    private static IThirstProvider instance = null;

    public static void init() {
        if (ThirstWasTakenProvider.isModLoaded()) {
            instance = new ThirstWasTakenProvider();
        } else {
            instance = new NoOpThirstProvider();
        }
    }

    private static IThirstProvider get() {
        if (instance == null) init();
        return instance;
    }

    public static void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid,
                                 @Nullable Level level, @Nullable BlockPos sourcePos) {
        get().addThirst(player, baseThirst, baseQuenched, fluid, level, sourcePos);
    }
}
