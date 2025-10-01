package com.agent772.createshufflefilter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateShuffleFilter.MODID)
public class CreateShuffleFilter {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "createshufflefilter";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Items which will all be registered under the "createshufflefilter" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "createshufflefilter" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates the Shuffle Filter item
    public static final DeferredItem<ShuffleFilterItem> SHUFFLE_FILTER = ITEMS.register("shuffle_filter", 
            () -> new ShuffleFilterItem(new Item.Properties().stacksTo(64)));

    // Creates a creative tab with the id "createshufflefilter:shuffle_filter_tab"
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SHUFFLE_FILTER_TAB = CREATIVE_MODE_TABS.register("shuffle_filter_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.createshufflefilter")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SHUFFLE_FILTER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SHUFFLE_FILTER.get()); // Add the shuffle filter to the tab
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CreateShuffleFilter(IEventBus modEventBus, ModContainer modContainer) {
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        LOGGER.info("Create Shuffle Filter mod initialized");
    }
}
