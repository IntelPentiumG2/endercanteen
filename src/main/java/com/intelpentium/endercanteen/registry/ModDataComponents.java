package com.intelpentium.endercanteen.registry;

import com.intelpentium.endercanteen.EnderCanteen;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EnderCanteen.MODID);

    /**
     * Stores the linked block position (dimension + BlockPos) in the Canteen item stack.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINKED_POS =
            DATA_COMPONENTS.register("linked_pos", () ->
                    DataComponentType.<GlobalPos>builder()
                            .persistent(GlobalPos.CODEC)
                            .networkSynchronized(StreamCodec.of(
                                    (buf, pos) -> {
                                        buf.writeResourceKey(pos.dimension());
                                        buf.writeBlockPos(pos.pos());
                                    },
                                    buf -> GlobalPos.of(buf.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION), buf.readBlockPos())
                            ))
                            .build());

    /**
     * Stores the current RF energy stored in the Canteen item stack.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RF_STORED =
            DATA_COMPONENTS.register("rf_stored", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}

