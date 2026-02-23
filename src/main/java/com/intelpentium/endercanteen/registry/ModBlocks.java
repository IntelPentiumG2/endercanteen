package com.intelpentium.endercanteen.registry;

import com.intelpentium.endercanteen.EnderCanteen;
import com.intelpentium.endercanteen.block.FluidTapBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EnderCanteen.MODID);

    public static final DeferredBlock<FluidTapBlock> FLUID_TAP = BLOCKS.register("fluid_tap",
            () -> new FluidTapBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6.0f).requiresCorrectToolForDrops()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}

