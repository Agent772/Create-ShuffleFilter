package com.agent772.createshufflefilter.migration;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.component.ShuffleMode;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.simibubi.create.AllDataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles migration of shuffle filters from old architecture to new architecture.
 * 
 * OLD ARCHITECTURE (pre Feb 2026):
 * - Used Create's AllDataComponents.FILTER_ITEMS to store item container
 * - Used filter_items_respect_nbt boolean to indicate mode (true = equal, false = weighted)
 * - Single item type for both modes
 * - Weights calculated at runtime
 * 
 * NEW ARCHITECTURE (current):
 * - Uses custom SHUFFLE_BLOCK_LIST DataComponent
 * - Two separate item types: ShuffleFilterItem (equal) and WeightedShuffleFilterItem (weighted)
 * - Weights stored persistently in BlockEntry records
 * - Full ItemStack component data preserved
 */
public class FilterDataMigration {
    
    /**
     * Check if an ItemStack needs migration from old format
     * 
     * @param stack ItemStack to check
     * @return true if migration needed
     */
    public static boolean needsMigration(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BaseShuffleFilterItem)) {
            return false;
        }
        
        // Has new component - already migrated
        if (stack.has(ModDataComponents.SHUFFLE_BLOCK_LIST.get())) {
            return false;
        }
        
        // Has old Create filter component - needs migration
        return stack.has(AllDataComponents.FILTER_ITEMS);
    }
    
    /**
     * Migrate a shuffle filter from old format to new format
     * 
     * @param oldStack Original filter ItemStack with old data format
     * @param level Level for logging context
     * @return New ItemStack with migrated data (may be different item type)
     */
    public static ItemStack migrate(ItemStack oldStack, Level level) {
        if (!needsMigration(oldStack)) {
            return oldStack; // No migration needed
        }
        
        CreateShuffleFilter.LOGGER.info("Migrating shuffle filter from old format...");
        
        try {
            // Read old data
            MigrationData data = readOldFormat(oldStack);
            
            // Determine target item type based on old mode
            Item targetItem = data.mode == ShuffleMode.EQUAL 
                ? CreateShuffleFilter.SHUFFLE_FILTER.get() 
                : CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER.get();
            
            // Create new ItemStack with correct type
            ItemStack newStack = new ItemStack(targetItem);
            
            // Convert blocks to new format and set component
            ShuffleBlockList blockList = convertToBlockList(data);
            newStack.set(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), blockList);
            
            // Preserve other components (custom name, etc.)
            copyPreservedComponents(oldStack, newStack);
            
            CreateShuffleFilter.LOGGER.info(
                "Migrated {} filter with {} blocks (mode: {})",
                targetItem.getClass().getSimpleName(),
                blockList.size(),
                data.mode
            );
            
            return newStack;
            
        } catch (Exception e) {
            CreateShuffleFilter.LOGGER.error("Failed to migrate shuffle filter", e);
            return oldStack; // Return original on error
        }
    }
    
    /**
     * Read old format data from ItemStack
     */
    private static MigrationData readOldFormat(ItemStack stack) {
        MigrationData data = new MigrationData();
        
        // Read old filter items from Create's FILTER_ITEMS component (ItemContainerContents)
        ItemContainerContents contents = stack.getOrDefault(
            AllDataComponents.FILTER_ITEMS,
            ItemContainerContents.EMPTY
        );
        
        // Extract items from container contents
        for (ItemStack item : contents.stream().toList()) {
            if (!item.isEmpty()) {
                // Check if this item has the respect_nbt flag set
                if (item.has(AllDataComponents.FILTER_ITEMS_RESPECT_NBT)) {
                    boolean respectNBT = item.getOrDefault(
                        AllDataComponents.FILTER_ITEMS_RESPECT_NBT,
                        true
                    );
                    // First item with respect_nbt determines mode for entire filter
                    data.mode = respectNBT ? ShuffleMode.EQUAL : ShuffleMode.WEIGHTED;
                }
                data.items.add(item.copy());
            }
        }
        
        return data;
    }
    
    /**
     * Convert old items list to new BlockList format
     */
    private static ShuffleBlockList convertToBlockList(MigrationData data) {
        if (data.items.isEmpty()) {
            return ShuffleBlockList.EMPTY;
        }
        
        // Calculate equal weights
        float equalWeight = 1.0f / data.items.size();
        
        // Create BlockEntry for each item
        List<ShuffleBlockList.BlockEntry> entries = new ArrayList<>();
        for (ItemStack item : data.items) {
            // Get resource location
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
            
            // Extract component patch (preserves NBT/components)
            DataComponentPatch components = item.getComponentsPatch();
            
            // Create entry with equal weight and stored components
            entries.add(new ShuffleBlockList.BlockEntry(
                itemId,
                equalWeight,
                components.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(components)
            ));
        }
        
        // Create new block list
        ShuffleBlockList blockList = new ShuffleBlockList(entries);
        
        // Normalize weights if in weighted mode
        if (data.mode == ShuffleMode.WEIGHTED) {
            blockList = blockList.normalized();
        }
        
        return blockList;
    }
    
    /**
     * Copy preserved components from old to new stack (custom name, etc.)
     */
    private static void copyPreservedComponents(ItemStack oldStack, ItemStack newStack) {
        // Copy custom name if present
        if (oldStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            newStack.set(
                net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                oldStack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
            );
        }
        
        // Add other preserved components here as needed
    }
    
    /**
     * Helper class to hold old format data during migration
     */
    private static class MigrationData {
        List<ItemStack> items = new ArrayList<>();
        ShuffleMode mode = ShuffleMode.EQUAL;
    }
}
