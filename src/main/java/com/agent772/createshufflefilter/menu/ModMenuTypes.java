package com.agent772.createshufflefilter.menu;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    
    public static final DeferredRegister<MenuType<?>> MENUS = 
        DeferredRegister.create(Registries.MENU, CreateShuffleFilter.MODID);
    
    // Main shuffle filter menu (equal probability)
    public static final DeferredHolder<MenuType<?>, MenuType<ShuffleFilterMenu>> SHUFFLE_FILTER = 
        MENUS.register("shuffle_filter",
            () -> IMenuTypeExtension.create((containerId, inv, data) -> {
                int filterSlot = data.readInt();
                return new ShuffleFilterMenu(containerId, inv, filterSlot);
            })
        );
    
    // Weighted shuffle filter menu
    public static final DeferredHolder<MenuType<?>, MenuType<WeightedShuffleFilterMenu>> WEIGHTED_SHUFFLE_FILTER = 
        MENUS.register("weighted_shuffle_filter",
            () -> IMenuTypeExtension.create((containerId, inv, data) -> {
                int filterSlot = data.readInt();
                return new WeightedShuffleFilterMenu(containerId, inv, filterSlot);
            })
        );
}
