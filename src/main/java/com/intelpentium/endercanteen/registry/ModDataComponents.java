package com.intelpentium.endercanteen.registry;

import com.intelpentium.endercanteen.EnderCanteen;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
                                    ModDataComponents::encodeGlobalPos,
                                    ModDataComponents::decodeGlobalPos
                            ))
                            .build());

    private static void encodeGlobalPos(FriendlyByteBuf buf, GlobalPos pos) {
        buf.writeResourceKey(pos.dimension());
        buf.writeBlockPos(pos.pos());
    }

    private static GlobalPos decodeGlobalPos(FriendlyByteBuf buf) {
        return GlobalPos.of(buf.readResourceKey(Registries.DIMENSION), buf.readBlockPos());
    }

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

