package com.agent772.createshufflefilter.util;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;

import net.minecraft.world.item.ItemStack;

/**
 * Fast utility for determining shuffle filter modes and accessing configurations.
 *
 * <p>On 1.20.1 the per-stack configuration lives in the {@link ItemStack}'s NBT,
 * read via {@link ShuffleBlockList#read(ItemStack)} (see Epic 2 / #8).
 */
public class FilterModeHelper {

    public static boolean isShuffleFilter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BaseShuffleFilterItem;
    }

    public static boolean isWeightedMode(ItemStack stack) {
        if (!isShuffleFilter(stack)) return false;
        return stack.getItem() instanceof WeightedShuffleFilterItem;
    }

    public static ShuffleBlockList getBlockList(ItemStack stack) {
        if (!isShuffleFilter(stack)) return ShuffleBlockList.EMPTY;
        return ShuffleBlockList.read(stack);
    }

    public static boolean isConfigured(ItemStack stack) {
        return !getBlockList(stack).isEmpty();
    }

    public static String getModeName(ItemStack stack) {
        return isWeightedMode(stack) ? "Weighted" : "Equal";
    }
}
