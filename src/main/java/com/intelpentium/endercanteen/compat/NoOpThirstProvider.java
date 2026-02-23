package com.intelpentium.endercanteen.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class NoOpThirstProvider implements IThirstProvider {

    @Override
    public void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid) {
        // No-op: Thirst Was Taken not installed
    }

    @Override
    public boolean needsDrink(Player player) {
        return true;
    }
}
