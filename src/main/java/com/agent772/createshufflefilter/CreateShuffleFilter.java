package com.agent772.createshufflefilter;

import org.slf4j.Logger;

import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.ShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import com.tterrag.registrate.util.entry.ItemEntry;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateShuffleFilter.MODID)
public class CreateShuffleFilter {
    public static final String MODID = "createshufflefilter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate
            .create(MODID)
            .defaultCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey());

    // Main shuffle filter (equal probability)
    public static final ItemEntry<ShuffleFilterItem> SHUFFLE_FILTER = 
        REGISTRATE.item("shuffle_filter", ShuffleFilterItem::new)
            .properties(p -> p
                .component(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), ShuffleBlockList.EMPTY)
            )
            .lang("Shuffle Filter")
            .register();
    
    // Weighted shuffle filter (configurable weights)
    public static final ItemEntry<WeightedShuffleFilterItem> WEIGHTED_SHUFFLE_FILTER = 
        REGISTRATE.item("weighted_shuffle_filter", WeightedShuffleFilterItem::new)
            .properties(p -> p
                .component(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), ShuffleBlockList.EMPTY)
            )
            .lang("Weighted Shuffle Filter")
            .register();

    public CreateShuffleFilter(IEventBus modEventBus, ModContainer modContainer) {
        // Register data components
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        
        // Register menu types
        ModMenuTypes.MENUS.register(modEventBus);
        
        // Register the registrate to the mod event bus to ensure items are properly registered
        REGISTRATE.registerEventListeners(modEventBus);
        
        LOGGER.info("Create Shuffle Filter mod initialized!");
    }
}
