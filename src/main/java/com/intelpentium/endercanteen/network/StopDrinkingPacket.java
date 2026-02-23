package com.intelpentium.endercanteen.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client to force the client to stop the use-item animation.
 * Used when the server rejects a drink (tank empty / unreachable) but the client
 * has already started the drinking animation because it could not verify the tank.
 */
public record StopDrinkingPacket() implements CustomPacketPayload {

    public static final Type<StopDrinkingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("endercanteen", "stop_drinking"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StopDrinkingPacket> CODEC =
            StreamCodec.unit(new StopDrinkingPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Called on the client thread when the packet is received. */
    public static void handle(StopDrinkingPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.stopUsingItem();
            }
        });
    }
}


