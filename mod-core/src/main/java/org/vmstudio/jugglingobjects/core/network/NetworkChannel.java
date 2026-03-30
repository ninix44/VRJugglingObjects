package org.vmstudio.jugglingobjects.core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface NetworkChannel {
    void sendToServer(ResourceLocation id, FriendlyByteBuf buf);

    void registerReceiver(ResourceLocation id, PacketHandler handler);

    @FunctionalInterface
    interface PacketHandler {
        void handle(FriendlyByteBuf buf, ServerPlayer player);
    }
}
