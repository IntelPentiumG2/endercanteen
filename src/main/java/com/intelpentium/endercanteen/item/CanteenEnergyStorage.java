package com.intelpentium.endercanteen.item;

import com.intelpentium.endercanteen.EnderCanteenConfig;
import com.intelpentium.endercanteen.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * IEnergyStorage implementation that reads/writes RF directly from/to the
 * {@link ModDataComponents#RF_STORED} data component on the given ItemStack.
 *
 * <p>The capacity is driven by {@link EnderCanteenConfig#RF_CAPACITY} so it
 * always reflects the currently loaded config value.
 */
public class CanteenEnergyStorage implements IEnergyStorage {

    private final ItemStack stack;

    public CanteenEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private int stored() {
        Integer val = stack.get(ModDataComponents.RF_STORED.get());
        return val != null ? val : 0;
    }

    private void setStored(int amount) {
        stack.set(ModDataComponents.RF_STORED.get(), amount);
    }

    private int capacity() {
        return EnderCanteenConfig.RF_CAPACITY.get();
    }

    // ------------------------------------------------------------------
    // IEnergyStorage
    // ------------------------------------------------------------------

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        int current = stored();
        int cap = capacity();
        int accepted = Math.min(maxReceive, cap - current);
        if (!simulate && accepted > 0) {
            setStored(current + accepted);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        int current = stored();
        int extracted = Math.min(maxExtract, current);
        if (!simulate && extracted > 0) {
            setStored(current - extracted);
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return stored();
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity();
    }

    @Override
    public boolean canExtract() {
        return false; // RF is only inserted, not extracted externally
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}

