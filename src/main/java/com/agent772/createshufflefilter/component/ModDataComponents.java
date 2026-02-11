package com.agent772.createshufflefilter.component;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = 
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateShuffleFilter.MODID);
    
    /**
     * Stores the list of configured blocks with their weights
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShuffleBlockList>> 
        SHUFFLE_BLOCK_LIST = DATA_COMPONENTS.register("shuffle_block_list",
            () -> DataComponentType.<ShuffleBlockList>builder()
                .persistent(ShuffleBlockList.CODEC)
                .networkSynchronized(ShuffleBlockList.STREAM_CODEC)
                .build()
    );
    
    /**
     * Deprecated: Stores the current mode (EQUAL or WEIGHTED)
     * No longer used - filter type determines the mode
     * Kept for backwards compatibility with old filter items
     */
    @Deprecated
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShuffleMode>> 
        SHUFFLE_MODE = DATA_COMPONENTS.register("shuffle_mode",
            () -> DataComponentType.<ShuffleMode>builder()
                .persistent(ShuffleMode.CODEC)
                .networkSynchronized(ShuffleMode.STREAM_CODEC)
                .build()
    );
}
