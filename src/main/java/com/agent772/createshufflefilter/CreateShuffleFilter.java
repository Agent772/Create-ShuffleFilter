package com.agent772.createshufflefilter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.data.CreateRegistrate;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import com.tterrag.registrate.util.entry.ItemEntry;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CreateShuffleFilter.MODID)
public class CreateShuffleFilter {
    public static final String MODID = "createshufflefilter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate
            .create(MODID)
            .defaultCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey());

    // Create 6.0.8 uses FilterItem::regular which automatically creates a ListFilterItem instance
    // This handles all the filter behavior we need, no custom FilterItem mixin required
    public static final ItemEntry<? extends FilterItem> SHUFFLE_FILTER = REGISTRATE.item("shuffle_filter", FilterItem::regular)
        .lang("Shuffle Filter")
        .register();

    public CreateShuffleFilter() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Register the registrate to the mod event bus to ensure items are properly registered
        REGISTRATE.registerEventListeners(modEventBus);
        
        LOGGER.info("Create Shuffle Filter mod initialized!");
    }
}
