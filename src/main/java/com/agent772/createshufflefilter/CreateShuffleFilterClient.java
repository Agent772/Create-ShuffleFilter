package com.agent772.createshufflefilter;

import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.agent772.createshufflefilter.screen.ShuffleFilterScreen;
import com.agent772.createshufflefilter.screen.WeightedShuffleFilterScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side bootstrap. Registers the filter configuration screens with
 * {@link MenuScreens} so right-click → open menu surfaces the real GUI.
 */
@Mod.EventBusSubscriber(modid = CreateShuffleFilter.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreateShuffleFilterClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.SHUFFLE_FILTER.get(), ShuffleFilterScreen::new);
            MenuScreens.register(ModMenuTypes.WEIGHTED_SHUFFLE_FILTER.get(), WeightedShuffleFilterScreen::new);
        });
    }
}
