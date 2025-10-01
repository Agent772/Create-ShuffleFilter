package com.agent772.createshufflefilter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.data.CreateRegistrate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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

    public static final TagKey<Item> SHUFFLE_FILTER_TAG = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(MODID, "shuffle_filter")
    );

    public static final ItemEntry<FilterItem> SHUFFLE_FILTER = REGISTRATE.item("shuffle_filter", FilterItem::regular)
        .lang("Shuffle Filter")
        .register();

    public CreateShuffleFilter(IEventBus modEventBus, ModContainer modContainer) {
        // Register the registrate to the mod event bus to ensure items are properly registered
        REGISTRATE.registerEventListeners(modEventBus);
        
        LOGGER.info("Create Shuffle Filter mod initialized!");
    }
}
