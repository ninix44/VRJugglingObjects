package org.vmstudio.jugglingobjects.fabric.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.vmstudio.jugglingobjects.core.network.NetworkChannel;

public class FabricNetworkChannel implements NetworkChannel {
    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);

        FriendlyByteBuf packetBuf = PacketByteBufs.create();
        packetBuf.writeByteArray(data);
        ClientPlayNetworking.send(id, packetBuf);
    }

    @Override
    public void registerReceiver(ResourceLocation id, PacketHandler handler) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler1, buf, responseSender) -> {
            byte[] data = buf.readByteArray();
            server.execute(() -> {
                FriendlyByteBuf forwardedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                handler.handle(forwardedBuf, player);
            });
        });
    }
}
