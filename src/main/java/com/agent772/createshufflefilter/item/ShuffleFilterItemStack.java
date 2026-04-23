package com.agent772.createshufflefilter.item;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * FilterItemStack wrapper for shuffle filters.
 * In funnels/basins: acts as an allow-list filter (accepts items in configured list).
 * In deployers on contraptions: randomizes selection from configured list.
 * Supports cascading: configured filters are tested, not matched directly.
 */
public class ShuffleFilterItemStack extends FilterItemStack {
    
    protected ShuffleFilterItemStack(ItemStack filter) {
        super(filter);
    }

    /**
     * Allow-list behavior with cascading support: accepts items that match any configured item.
     * If a configured item is a filter, uses that filter's test logic (cascading).
     * Used in funnels, basins, etc. Deployers use special shuffle behavior via mixin.
     */
    @Override
    public boolean test(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // Get configured items from the filter
        ItemStack filterStack = this.item();
        if (!(filterStack.getItem() instanceof BaseShuffleFilterItem baseFilterItem)) {
            return false;
        }
        
        ItemStack[] filterItems = baseFilterItem.getFilterItems(filterStack);
        
        // Check if the stack matches any configured item
        for (ItemStack configuredItem : filterItems) {
            if (configuredItem.isEmpty()) {
                continue;
            }
            
            // Check if the configured item is itself a filter (cascading)
            if (configuredItem.getItem() instanceof FilterItem) {
                FilterItemStack nestedFilter = FilterItemStack.of(configuredItem);
                if (nestedFilter.test(level, stack)) {
                    return true;
                }
            } else {
                // Direct item comparison for non-filters
                if (ItemStack.isSameItemSameComponents(stack, configuredItem)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
