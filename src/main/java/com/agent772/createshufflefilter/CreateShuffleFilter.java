package com.agent772.createshufflefilter;

import com.agent772.createshufflefilter.item.ShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.agent772.createshufflefilter.network.ModPackets;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateShuffleFilter.MODID)
public class CreateShuffleFilter {
    public static final String MODID = "createshufflefilter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate
            .create(MODID)
            .defaultCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey());

    public static final ItemEntry<ShuffleFilterItem> SHUFFLE_FILTER =
        REGISTRATE.item("shuffle_filter", ShuffleFilterItem::new)
            .lang("Shuffle Filter")
            .register();

    public static final ItemEntry<WeightedShuffleFilterItem> WEIGHTED_SHUFFLE_FILTER =
        REGISTRATE.item("weighted_shuffle_filter", WeightedShuffleFilterItem::new)
            .lang("Weighted Shuffle Filter")
            .register();

    public CreateShuffleFilter() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModMenuTypes.MENUS.register(modEventBus);
        REGISTRATE.registerEventListeners(modEventBus);
        ModPackets.register();
        LOGGER.info("Create Shuffle Filter (Forge 1.20.1) loaded");
    }
}
