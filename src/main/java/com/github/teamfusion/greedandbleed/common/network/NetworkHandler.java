package com.github.teamfusion.greedandbleed.common.network;

import com.github.teamfusion.greedandbleed.GreedAndBleed;
import com.github.teamfusion.greedandbleed.server.network.packet.SOpenHoglinWindowPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder.named(
            new ResourceLocation(GreedAndBleed.MOD_ID, "network"))
            .clientAcceptedVersions("1"::equals)
            .serverAcceptedVersions("1"::equals)
            .networkProtocolVersion(() -> "1")
            .simpleChannel();

    protected static int packetID = 0;

    public NetworkHandler() {
    }

    public static void register() {
        INSTANCE.registerMessage(
                getPacketID(),
                SOpenHoglinWindowPacket.class,
                SOpenHoglinWindowPacket::write,
                SOpenHoglinWindowPacket::read,
                SOpenHoglinWindowPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static int getPacketID() {
        return packetID++;
    }
}
