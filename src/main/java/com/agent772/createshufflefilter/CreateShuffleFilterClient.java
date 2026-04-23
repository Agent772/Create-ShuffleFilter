package com.agent772.createshufflefilter;

import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.agent772.createshufflefilter.screen.ShuffleFilterScreen;
import com.agent772.createshufflefilter.screen.WeightedShuffleFilterScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CreateShuffleFilter.MODID, value = Dist.CLIENT)
public class CreateShuffleFilterClient {
    
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // Main shuffle filter screen (equal probability)
        event.register(ModMenuTypes.SHUFFLE_FILTER.get(), ShuffleFilterScreen::new);
        
        // Weighted shuffle filter screen
        event.register(ModMenuTypes.WEIGHTED_SHUFFLE_FILTER.get(), WeightedShuffleFilterScreen::new);
    }
}
