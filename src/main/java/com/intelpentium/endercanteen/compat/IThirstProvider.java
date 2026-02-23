package com.intelpentium.endercanteen.compat;

import net.minecraft.world.entity.player.Player;
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
     */
    void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid);

    /**
     * Check if the player needs water (thirst is not full).
     *
     * @param player the player
     * @return true if the player can benefit from drinking
     */
    boolean needsDrink(Player player);
}
