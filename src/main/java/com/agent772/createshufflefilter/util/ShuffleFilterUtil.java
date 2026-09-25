package com.agent772.createshufflefilter.util;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.SkipItem;
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

    /**
     * Outcome of a selection call: {@link #NONE} (nothing usable), {@link #SKIP}
     * (Skip marker rolled - place nothing, don't fall back) or {@link #of(ItemStack)}.
     */
    public record SelectionResult(ItemStack stack, boolean isSkip) {
        public static final SelectionResult NONE = new SelectionResult(ItemStack.EMPTY, false);
        public static final SelectionResult SKIP = new SelectionResult(ItemStack.EMPTY, true);

        public static SelectionResult of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return NONE;
            return new SelectionResult(stack, false);
        }

        public boolean isNone() {
            return !isSkip && stack.isEmpty();
        }
    }

    public static boolean isSkipEntry(ShuffleBlockList.BlockEntry entry) {
        return entry != null && entry.getItem() instanceof SkipItem;
    }

    public static SelectionResult selectItemCascading(
            ShuffleBlockList blockList,
            boolean useWeighted,
            Level world,
            IItemHandler inv,
            int depth,
            Set<Item> visited) {

        if (depth >= MAX_CASCADE_DEPTH) {
            return SelectionResult.NONE;
        }

        ShuffleBlockList.BlockEntry selectedEntry = selectEntry(blockList, useWeighted, world);
        if (selectedEntry == null) {
            return SelectionResult.NONE;
        }

        // Skip marker: intentional "place nothing". Do NOT fall back to other entries.
        if (isSkipEntry(selectedEntry)) {
            return SelectionResult.SKIP;
        }

        SelectionResult result = tryExtractEntry(selectedEntry, world, inv, depth, visited);
        if (!result.isNone()) {
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
            if (isSkipEntry(fallbackEntry)) continue; // Fallback never resolves to "do nothing"
            result = tryExtractEntry(fallbackEntry, world, inv, depth, visited);
            if (!result.isNone()) {
                return result;
            }
        }

        return SelectionResult.NONE;
    }

    private static SelectionResult tryExtractEntry(
            ShuffleBlockList.BlockEntry entry,
            Level world,
            IItemHandler inv,
            int depth,
            Set<Item> visited) {

        ItemStack configuredStack = entry.getItemStack();
        if (configuredStack.isEmpty()) {
            return SelectionResult.NONE;
        }

        Item item = configuredStack.getItem();

        if (visited.contains(item)) {
            return SelectionResult.NONE;
        }

        Set<Item> branchVisited = new HashSet<>(visited);
        branchVisited.add(item);

        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = ShuffleBlockList.read(configuredStack);
            if (nestedList.isEmpty()) {
                return SelectionResult.NONE;
            }
            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            return selectItemCascading(nestedList, nestedWeighted, world, inv, depth + 1, branchVisited);
        }

        if (item instanceof FilterItem) {
            FilterItemStack filterItemStack = FilterItemStack.of(configuredStack);
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (!stack.isEmpty() && filterItemStack.test(world, stack)) {
                    return SelectionResult.of(ItemHelper.extract(inv, s -> filterItemStack.test(world, s), 1, false));
                }
            }
            return SelectionResult.NONE;
        }

        if (hasItemInInventory(inv, item)) {
            return SelectionResult.of(ItemHelper.extract(inv, stack -> stack.getItem() == item, 1, false));
        }

        return SelectionResult.NONE;
    }

    public static ShuffleBlockList.BlockEntry selectEntry(ShuffleBlockList blockList, boolean useWeighted, Level world) {
        if (blockList.isEmpty()) return null;

        if (useWeighted) {
            // Scale the draw by the actual sum of weights instead of assuming the list is
            // normalized to 1.0. Filtered sub-lists (e.g. only entries available in the
            // contraption inventory) won't sum to 1.0 and would otherwise dump the
            // residual probability mass into the last entry.
            float totalWeight = 0.0f;
            for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
                totalWeight += entry.weight();
            }
            if (totalWeight <= 0.0f) {
                int index = world.getRandom().nextInt(blockList.size());
                return blockList.blocks().get(index);
            }
            float random = world.getRandom().nextFloat() * totalWeight;
            float accumulated = 0.0f;
            for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
                accumulated += entry.weight();
                if (random < accumulated) {
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
}
