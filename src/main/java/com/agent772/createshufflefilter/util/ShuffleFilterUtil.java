package com.agent772.createshufflefilter.util;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for shuffle filter operations with cascading support.
 *
 * <p>On 1.20.1 the per-stack configuration is read from NBT via
 * {@link ShuffleBlockList#read(ItemStack)}.
 */
public class ShuffleFilterUtil {

    public static final int MAX_CASCADE_DEPTH = 10;

    public static ItemStack selectItemCascading(
            ShuffleBlockList blockList,
            boolean useWeighted,
            Level world,
            IItemHandler inv,
            int depth,
            Set<Item> visited) {

        if (depth >= MAX_CASCADE_DEPTH) {
            return ItemStack.EMPTY;
        }

        ShuffleBlockList.BlockEntry selectedEntry = selectEntry(blockList, useWeighted, world);
        if (selectedEntry == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = tryExtractEntry(selectedEntry, world, inv, depth, visited);
        if (!result.isEmpty()) {
            return result;
        }

        List<ShuffleBlockList.BlockEntry> sortedEntries = new ArrayList<>(blockList.blocks());
        sortedEntries.sort((a, b) -> {
            int weightCmp = Float.compare(b.weight(), a.weight());
            if (weightCmp != 0) return weightCmp;
            return blockList.blocks().indexOf(a) - blockList.blocks().indexOf(b);
        });

        for (ShuffleBlockList.BlockEntry fallbackEntry : sortedEntries) {
            if (fallbackEntry == selectedEntry) continue;
            result = tryExtractEntry(fallbackEntry, world, inv, depth, visited);
            if (!result.isEmpty()) {
                return result;
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack tryExtractEntry(
            ShuffleBlockList.BlockEntry entry,
            Level world,
            IItemHandler inv,
            int depth,
            Set<Item> visited) {

        ItemStack configuredStack = entry.getItemStack();
        if (configuredStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Item item = configuredStack.getItem();

        if (visited.contains(item)) {
            return ItemStack.EMPTY;
        }

        Set<Item> branchVisited = new HashSet<>(visited);
        branchVisited.add(item);

        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = ShuffleBlockList.read(configuredStack);
            if (nestedList.isEmpty()) {
                return ItemStack.EMPTY;
            }
            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            return selectItemCascading(nestedList, nestedWeighted, world, inv, depth + 1, branchVisited);
        }

        if (item instanceof FilterItem) {
            FilterItemStack filterItemStack = FilterItemStack.of(configuredStack);
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (!stack.isEmpty() && filterItemStack.test(world, stack)) {
                    return ItemHelper.extract(inv, s -> filterItemStack.test(world, s), 1, false);
                }
            }
            return ItemStack.EMPTY;
        }

        if (hasItemInInventory(inv, item)) {
            return ItemHelper.extract(inv, stack -> stack.getItem() == item, 1, false);
        }

        return ItemStack.EMPTY;
    }

    public static ShuffleBlockList.BlockEntry selectEntry(ShuffleBlockList blockList, boolean useWeighted, Level world) {
        if (blockList.isEmpty()) return null;

        if (useWeighted) {
            float random = world.getRandom().nextFloat();
            float accumulated = 0.0f;
            for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
                accumulated += entry.weight();
                if (random <= accumulated) {
                    return entry;
                }
            }
            return blockList.blocks().get(blockList.size() - 1);
        } else {
            int index = world.getRandom().nextInt(blockList.size());
            return blockList.blocks().get(index);
        }
    }

    public static boolean hasItemInInventory(IItemHandler inv, Item item) {
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack selectBlockForRoller(
            ShuffleBlockList blockList,
            boolean useWeighted,
            Level world,
            IItemHandler inv) {

        if (blockList.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ShuffleBlockList.BlockEntry selectedEntry = selectEntry(blockList, useWeighted, world);
        if (selectedEntry != null) {
            ItemStack configured = selectedEntry.getItemStack();
            if (!configured.isEmpty() && hasItemInInventory(inv, configured.getItem())) {
                return configured;
            }
        }

        List<ShuffleBlockList.BlockEntry> sortedEntries = new ArrayList<>(blockList.blocks());
        sortedEntries.sort((a, b) -> {
            int weightCmp = Float.compare(b.weight(), a.weight());
            if (weightCmp != 0) return weightCmp;
            return blockList.blocks().indexOf(a) - blockList.blocks().indexOf(b);
        });

        for (ShuffleBlockList.BlockEntry entry : sortedEntries) {
            if (entry == selectedEntry) continue;
            ItemStack configured = entry.getItemStack();
            if (!configured.isEmpty() && hasItemInInventory(inv, configured.getItem())) {
                return configured;
            }
        }

        return ItemStack.EMPTY;
    }
}
