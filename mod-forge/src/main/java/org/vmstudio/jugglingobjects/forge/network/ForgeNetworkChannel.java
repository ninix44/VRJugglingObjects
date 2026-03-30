package org.vmstudio.jugglingobjects.forge.network;

import io.netty.buffer.Unpooled;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.vmstudio.jugglingobjects.core.network.NetworkChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ForgeNetworkChannel implements NetworkChannel {
    private static final String PROTOCOL_VERSION = "1";

    private final SimpleChannel channel;
    private final Map<ResourceLocation, PacketHandler> handlers = new HashMap<>();
    private int packetId;

    public ForgeNetworkChannel(ResourceLocation channelName) {
        this.channel = NetworkRegistry.newSimpleChannel(
                channelName,
                () -> PROTOCOL_VERSION,
                version -> true,
                version -> true
        );

        registerGenericPacket();
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        channel.sendToServer(new GenericPacket(id, buf));
    }

    @Override
    public void registerReceiver(ResourceLocation id, PacketHandler handler) {
        handlers.put(id, handler);
    }

    private void registerGenericPacket() {
        channel.messageBuilder(GenericPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> {
                    buf.writeResourceLocation(packet.id);
                    buf.writeByteArray(packet.data);
                })
                .decoder(buf -> new GenericPacket(buf.readResourceLocation(), buf.readByteArray()))
                .consumerMainThread(this::handlePacket)
                .add();
    }

    private void handlePacket(GenericPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        PacketHandler handler = handlers.get(packet.id);
        if (handler != null) {
            ServerPlayer sender = context.getSender();
            handler.handle(packet.toFriendlyByteBuf(), sender);
        }
        context.setPacketHandled(true);
    }

    private static class GenericPacket {
        private final ResourceLocation id;
        private final byte[] data;

        private GenericPacket(ResourceLocation id, FriendlyByteBuf buf) {
            this.id = id;
            this.data = new byte[buf.readableBytes()];
            buf.readBytes(this.data);
        }

        private GenericPacket(ResourceLocation id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        private FriendlyByteBuf toFriendlyByteBuf() {
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        }
    }
}
