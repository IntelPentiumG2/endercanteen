package com.intelpentium.endercanteen.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class NoOpThirstProvider implements IThirstProvider {

    @Override
    public void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid,
                          @Nullable Level level, @Nullable BlockPos sourcePos) {
        // No-op: Thirst Was Taken not installed
    }

}
