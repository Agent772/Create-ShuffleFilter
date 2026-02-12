package com.agent772.createshufflefilter.item;

import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Base class for shuffle filter items with shared functionality.
 * Extends FilterItem to integrate with Create's filter system.
 */
public abstract class BaseShuffleFilterItem extends FilterItem {
    
    protected BaseShuffleFilterItem(Properties properties) {
        super(properties);
    }
    
    /**
     * FilterItem's getFilterItems method - returns the list of configured blocks.
     * This is used by Create's filter system to display filter contents.
     */
    @Override
    public ItemStack[] getFilterItems(ItemStack filterStack) {
        ShuffleBlockList blockList = filterStack.getOrDefault(
            ModDataComponents.SHUFFLE_BLOCK_LIST.get(), 
            ShuffleBlockList.EMPTY
        );
        
        // Convert BlockEntries to ItemStacks array
        return blockList.blocks().stream()
            .map(ShuffleBlockList.BlockEntry::getItemStack)
            .filter(stack -> !stack.isEmpty())
            .toArray(ItemStack[]::new);
    }
    
    /**
     * Creates a FilterItemStack wrapper for this filter.
     * Returns a ShuffleFilterItemStack that doesn't do runtime item matching.
     */
    @Override
    public FilterItemStack makeStackWrapper(ItemStack filterStack) {
        return new ShuffleFilterItemStack(filterStack);
    }
    
    /**
     * Returns the data component type used for filter configuration.
     * Our filters use SHUFFLE_BLOCK_LIST component.
     */
    @Override
    public net.minecraft.core.component.DataComponentType<?> getComponentType() {
        return ModDataComponents.SHUFFLE_BLOCK_LIST.get();
    }
    
    /**
     * Creates the menu for configuring this filter.
     * Each filter type has its own menu implementation.
     */
    @Override
    public abstract net.minecraft.world.inventory.AbstractContainerMenu createMenu(
        int containerId, 
        net.minecraft.world.entity.player.Inventory playerInv, 
        net.minecraft.world.entity.player.Player player
    );
    
    /**
     * Creates a summary component list describing the filter's configuration.
     * Displayed in filter slots and tooltips.
     */
    @Override
    public List<Component> makeSummary(ItemStack filterStack) {
        ShuffleBlockList blockList = filterStack.getOrDefault(
            ModDataComponents.SHUFFLE_BLOCK_LIST.get(), 
            ShuffleBlockList.EMPTY
        );
        
        if (blockList.isEmpty()) {
            return List.of(Component.literal("Not configured").withStyle(ChatFormatting.RED));
        }
        
        Component summary = Component.literal(blockList.size() + " blocks")
            .withStyle(ChatFormatting.GREEN)
            .append(Component.literal(" (" + getFilterModeName() + ")")
                .withStyle(getFilterModeColor()));
        
        return List.of(summary);
    }
    
    /**
     * Gets the display name for this filter type (e.g., "Equal Mode", "Weighted Mode")
     */
    public abstract String getFilterModeName();
    
    /**
     * Gets the ChatFormatting color for this filter type
     */
    public abstract ChatFormatting getFilterModeColor();
    
    /**
     * Gets the description of how this filter works
     */
    public abstract String getFilterDescription();
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Get configuration from components
        ShuffleBlockList blockList = stack.getOrDefault(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), ShuffleBlockList.EMPTY);
        
        // Basic description
        tooltip.add(Component.literal("Right-click to configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(getFilterDescription()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Allow-list in funnels/basins, randomizes in deployers").withStyle(ChatFormatting.DARK_GRAY));
        
        // Show configuration status
        if (blockList.isEmpty()) {
            tooltip.add(Component.literal("Not configured").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal(blockList.size() + " blocks configured")
                .withStyle(ChatFormatting.GREEN));
        }
        
        // Detailed info when holding shift
        if (flag.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Configured Blocks:").withStyle(ChatFormatting.GOLD));
            
            if (blockList.isEmpty()) {
                tooltip.add(Component.literal("  (none)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (int i = 0; i < Math.min(blockList.size(), 10); i++) {
                    ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
                    String blockName = entry.getItemStack().getHoverName().getString();
                    
                    tooltip.add(Component.literal("  • " + blockName)
                        .withStyle(ChatFormatting.GRAY));
                }
                
                if (blockList.size() > 10) {
                    tooltip.add(Component.literal("  ... and " + (blockList.size() - 10) + " more")
                        .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else {
            tooltip.add(Component.literal("Hold SHIFT for details").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
