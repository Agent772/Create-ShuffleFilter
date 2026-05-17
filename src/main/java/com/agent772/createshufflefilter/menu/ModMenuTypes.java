package com.agent772.createshufflefilter.menu;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, CreateShuffleFilter.MODID);

    public static final RegistryObject<MenuType<ShuffleFilterMenu>> SHUFFLE_FILTER =
        MENUS.register("shuffle_filter",
            () -> IForgeMenuType.create((containerId, inv, data) -> {
                int filterSlot = data.readInt();
                return new ShuffleFilterMenu(containerId, inv, filterSlot);
            })
        );

    public static final RegistryObject<MenuType<WeightedShuffleFilterMenu>> WEIGHTED_SHUFFLE_FILTER =
        MENUS.register("weighted_shuffle_filter",
            () -> IForgeMenuType.create((containerId, inv, data) -> {
                int filterSlot = data.readInt();
                return new WeightedShuffleFilterMenu(containerId, inv, filterSlot);
            })
        );
}
