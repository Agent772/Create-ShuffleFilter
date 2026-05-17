package com.agent772.createshufflefilter.network;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Forge 1.20.1 networking entrypoint. Registers a single {@link SimpleChannel}
 * carrying all of the mod's packets.
 */
public class ModPackets {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation(CreateShuffleFilter.MODID, "main"))
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .simpleChannel();

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
            FilterConfigPacket.class,
            FilterConfigPacket::encode,
            FilterConfigPacket::decode,
            FilterConfigPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(FilterConfigPacket pkt) {
        CHANNEL.sendToServer(pkt);
    }
}
