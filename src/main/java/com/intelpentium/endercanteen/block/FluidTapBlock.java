package com.intelpentium.endercanteen.block;

import com.intelpentium.endercanteen.blockentity.FluidTapBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * FluidTapBlock – an optional interface block that bridges any adjacent fluid tank
 * to a canteen. Place it next to any IFluidHandler-capable block and link your canteen to it.
 *
 * The tap delegates IFluidHandler capability queries to the first adjacent
 * block that exposes the capability (checked at drink time inside FluidTapBlockEntity).
 */
public class FluidTapBlock extends BaseEntityBlock {

    public static final MapCodec<FluidTapBlock> CODEC = simpleCodec(FluidTapBlock::new);

    public FluidTapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTapBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}



