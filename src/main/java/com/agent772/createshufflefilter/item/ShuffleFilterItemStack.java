package com.agent772.createshufflefilter.item;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * FilterItemStack wrapper for shuffle filters.
 *
 * <p>In funnels/basins: acts as an allow-list filter. In deployers on contraptions:
 * randomizes selection from the configured list. Configured filters are tested,
 * not matched directly, to support cascading.
 */
public class ShuffleFilterItemStack extends FilterItemStack {

    protected ShuffleFilterItemStack(ItemStack filter) {
        super(filter);
    }

    @Override
    public boolean test(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemStack filterStack = this.item();
        if (!(filterStack.getItem() instanceof BaseShuffleFilterItem baseFilterItem)) {
            return false;
        }

        ItemStack[] filterItems = baseFilterItem.getFilterItems(filterStack);

        for (ItemStack configuredItem : filterItems) {
            if (configuredItem.isEmpty()) {
                continue;
            }

            if (configuredItem.getItem() instanceof FilterItem) {
                FilterItemStack nestedFilter = FilterItemStack.of(configuredItem);
                if (nestedFilter.test(level, stack)) {
                    return true;
                }
            } else {
                // 1.20.1 equivalent of isSameItemSameComponents
                if (ItemStack.isSameItemSameTags(stack, configuredItem)) {
                    return true;
                }
            }
        }

        return false;
    }
}
