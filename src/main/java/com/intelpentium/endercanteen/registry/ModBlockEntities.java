package com.intelpentium.endercanteen.registry;

import com.intelpentium.endercanteen.EnderCanteen;
import com.intelpentium.endercanteen.blockentity.FluidTapBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, EnderCanteen.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTapBlockEntity>> FLUID_TAP =
            BLOCK_ENTITIES.register("fluid_tap", () ->
                    BlockEntityType.Builder.of(FluidTapBlockEntity::new, ModBlocks.FLUID_TAP.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
