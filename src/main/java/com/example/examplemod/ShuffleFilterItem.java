package com.example.examplemod;

import com.simibubi.create.content.logistics.filter.FilterItem;

import net.minecraft.world.item.Item;

/**
 * Shuffle Filter - A subclass of Create's List Filter.
 * This filter works the same way as the List Filter but can be detected
 * separately to trigger different behavior in the future.
 */
public class ShuffleFilterItem extends FilterItem {
    
    public ShuffleFilterItem(Item.Properties properties) {
        super(properties);
    }
}
