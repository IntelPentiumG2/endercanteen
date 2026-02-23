package com.intelpentium.endercanteen.blockentity;

import com.intelpentium.endercanteen.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * FluidTapBlockEntity – searches all 6 adjacent faces for a neighbour that exposes
 * an IFluidHandler capability and delegates fluid operations to it.
 *
 * The canteen itself can also link directly to any fluid-handler block, skipping
 * this block entity entirely. The tap is simply a convenience for tanks that are
 * otherwise hard to reach or belong to multiblock structures.
 */
public class FluidTapBlockEntity extends BlockEntity {

    public FluidTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TAP.get(), pos, state);
    }

    /**
     * Finds the first adjacent IFluidHandler by checking all 6 directions.
     *
     * @return an IFluidHandler, or null if none found
     */
    @Nullable
    public IFluidHandler findAdjacentHandler() {
        if (level == null) return null;

        for (Direction dir : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(dir);
            IFluidHandler handler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, neighbour, dir.getOpposite());
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }
}

