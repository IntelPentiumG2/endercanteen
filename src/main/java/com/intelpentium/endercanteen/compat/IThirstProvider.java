package com.intelpentium.endercanteen.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Abstraction layer for thirst-related operations.
 * Allows the canteen to work with or without "Thirst Was Taken" installed.
 */
public interface IThirstProvider {

    /**
     * Add thirst to the player, optionally reading purity from the given FluidStack.
     *
     * @param player       the player
     * @param baseThirst   base thirst points (before purity scaling)
     * @param baseQuenched base quench points (before purity scaling)
     * @param fluid        the drained FluidStack (may carry thirst:purity component), or null
     * @param level        the level containing the source block (for BlockState purity fallback), or null
     * @param sourcePos    position of the fluid source block (e.g. cauldron), or null
     */
    void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid,
                   @Nullable Level level, @Nullable BlockPos sourcePos);

}
