package com.agent772.createshufflefilter;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateShuffleFilter.MODID)
public class CreateShuffleFilter {
    public static final String MODID = "createshufflefilter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateShuffleFilter() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("Create Shuffle Filter (Forge 1.20.1) loaded");
    }
}
