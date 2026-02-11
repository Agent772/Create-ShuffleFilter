package com.agent772.createshufflefilter.network;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateShuffleFilter.MODID)
public class ModPackets {
    
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        
        registrar.playToServer(
            FilterConfigPacket.TYPE,
            FilterConfigPacket.STREAM_CODEC,
            FilterConfigPacket::handle
        );
    }
}
