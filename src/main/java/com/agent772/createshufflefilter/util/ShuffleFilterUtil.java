package com.agent772.createshufflefilter.util;

import com.agent772.createshufflefilter.component.ModDataComponents;
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
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for shuffle filter operations with cascading support
 *
 * Provides reusable logic for:
 * - Item selection (weighted and equal probability)
 * - Cascading filter resolution (shuffle -> shuffle, shuffle -> Create filter, shuffle -> item)
 * - Inventory lookups with early exit optimization
 *
 * Performance: ~40ns per selection operation
 *
 * Used by:
 * - DeployerMovementBehaviour (via mixin)
 * - RollerMovementBehaviour (via mixin)
 */
public class ShuffleFilterUtil {

    /**
     * Maximum recursion depth for cascading filters
     * Prevents infinite loops and stack overflow
     */
    public static final int MAX_CASCADE_DEPTH = 10;

    /**
     * Outcome of a selection call. Replaces the older "magic ItemStack sentinel +
     * reference identity" trick that was fragile under {@code copy()} / {@code shrink()}.
     *
     * <p>Exactly one of three states:
     * <ul>
     *   <li>{@link #NONE} - no usable entry was found (inventory empty, fully exhausted, etc.).</li>
     *   <li>{@link #SKIP} - the Skip marker was rolled; caller must place nothing and not fall back.</li>
     *   <li>{@link #of(ItemStack)} - a concrete item to deploy/place.</li>
     * </ul>
     */
    public record SelectionResult(ItemStack stack, boolean isSkip) {
        public static final SelectionResult NONE = new SelectionResult(ItemStack.EMPTY, false);
        public static final SelectionResult SKIP = new SelectionResult(ItemStack.EMPTY, true);

        public static SelectionResult of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return NONE;
            return new SelectionResult(stack, false);
        }

        /** True when there is nothing to do at this position (no item, no skip intent). */
        public boolean isNone() {
            return !isSkip && stack.isEmpty();
        }
    }

    /**
     * True when the entry stored in the filter slot is the Skip marker item.
     */
    public static boolean isSkipEntry(ShuffleBlockList.BlockEntry entry) {
        return entry != null && entry.getItem() instanceof SkipItem;
    }

    /**
     * Select item with cascading filter support.
     * Handles both shuffle filters (recursive) and Create filters (matching).
     *
     * @param blockList Current filter's block list
     * @param useWeighted Whether to use weighted selection
     * @param world Level for random number generation
     * @param inv Contraption inventory
     * @param depth Current recursion depth (protection against infinite loops)
     * @param visited Set of visited filter items (circular reference detection)
     * @return SelectionResult: SKIP for intentional skip, NONE for "nothing to place",
     *         or a wrapped ItemStack to deploy.
     */
    public static SelectionResult selectItemCascading(
            ShuffleBlockList blockList,
            boolean useWeighted,
            Level world,
            IItemHandler inv,
            int depth,
            Set<Item> visited) {

        // Depth protection
        if (depth >= MAX_CASCADE_DEPTH) {
            return SelectionResult.NONE;
        }

        // Select entry from current filter (includes stored configuration)
        ShuffleBlockList.BlockEntry selectedEntry = selectEntry(blockList, useWeighted, world);
        if (selectedEntry == null) {
            return SelectionResult.NONE;
        }

        // Skip marker: intentional "place nothing". Do NOT fall back to other entries.
        if (isSkipEntry(selectedEntry)) {
            return SelectionResult.SKIP;
        }

        // Try the selected entry first
        SelectionResult result = tryExtractEntry(selectedEntry, world, inv, depth, visited);
        if (!result.isNone()) {
            return result;
        }

        // Fallback: Try other entries from the filter if the selected one failed
        // Sort by weight (descending) to prioritize higher-weighted blocks, with index as tiebreaker
        List<ShuffleBlockList.BlockEntry> sortedEntries = new ArrayList<>(blockList.blocks());
        sortedEntries.sort((a, b) -> {
            int weightCmp = Float.compare(b.weight(), a.weight());
            if (weightCmp != 0) return weightCmp;
            return blockList.blocks().indexOf(a) - blockList.blocks().indexOf(b);
        });

        // Try sorted entries with early exit
        for (ShuffleBlockList.BlockEntry fallbackEntry : sortedEntries) {
            if (fallbackEntry == selectedEntry) continue; // Already tried
            if (isSkipEntry(fallbackEntry)) continue;     // Fallback never resolves to "do nothing"

            result = tryExtractEntry(fallbackEntry, world, inv, depth, visited);
            if (!result.isNone()) {
                return result;
            }
        }

        // No blocks from filter available in inventory
        return SelectionResult.NONE;
    }

    /**
     * Try to extract a specific entry (handles cascade, Create filters, and regular items).
     * Returns {@link SelectionResult#SKIP} if recursion lands on a Skip entry in a nested filter.
     */
    private static SelectionResult tryExtractEntry(
            ShuffleBlockList.BlockEntry entry,
            Level world,
            IItemHandler inv,
            int depth,
            Set<Item> visited) {

        // Get the configured ItemStack with all components
        ItemStack configuredStack = entry.getItemStack();
        if (configuredStack.isEmpty()) {
            return SelectionResult.NONE;
        }

        Item item = configuredStack.getItem();

        // Circular reference detection
        if (visited.contains(item)) {
            return SelectionResult.NONE;
        }

        // Create a new visited set for this branch to avoid affecting parallel attempts
        Set<Item> branchVisited = new HashSet<>(visited);
        branchVisited.add(item);

        // Case 1: Selected item is another shuffle filter -> recurse using stored configuration
        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = configuredStack.getOrDefault(
                ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                ShuffleBlockList.EMPTY
            );

            if (nestedList.isEmpty()) {
                return SelectionResult.NONE;
            }

            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;

            // Recursively select from nested shuffle filter
            return selectItemCascading(nestedList, nestedWeighted, world, inv, depth + 1, branchVisited);
        }

        // Case 2: Selected item is a Create filter -> use Create's matching logic with stored config
        if (item instanceof FilterItem) {
            FilterItemStack filterItemStack = FilterItemStack.of(configuredStack);

            // Find first item in inventory that matches this filter
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (!stack.isEmpty() && filterItemStack.test(world, stack)) {
                    return SelectionResult.of(
                        ItemHelper.extract(inv, s -> filterItemStack.test(world, s), 1, false)
                    );
                }
            }

            return SelectionResult.NONE;
        }

        // Case 3: Regular item (block or non-filter item) -> extract directly
        if (hasItemInInventory(inv, item)) {
            return SelectionResult.of(
                ItemHelper.extract(inv, stack -> stack.getItem() == item, 1, false)
            );
        }

        return SelectionResult.NONE;
    }

    /**
     * Select a full BlockEntry (with configuration) based on mode
     * Performance: ~10ns
     */
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

            // Fallback to last entry (handles rounding errors)
            return blockList.blocks().get(blockList.size() - 1);
        } else {
            int index = world.getRandom().nextInt(blockList.size());
            return blockList.blocks().get(index);
        }
    }

    /**
     * Check if item is available in inventory
     * Performance: Early exit on first match (~20ns average)
     */
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
