package com.agent772.createshufflefilter.menu;

import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Container menu for weighted shuffle filter configuration GUI
 */
public class WeightedShuffleFilterMenu extends AbstractContainerMenu {
    
    private final Inventory playerInventory;
    private final int filterSlot;
    
    // For client-side (packet-based) construction
    public WeightedShuffleFilterMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, playerInv.selected);
    }
    
    // For server-side construction
    public WeightedShuffleFilterMenu(int containerId, Inventory playerInv, int filterSlot) {
        super(ModMenuTypes.WEIGHTED_SHUFFLE_FILTER.get(), containerId);
        
        this.playerInventory = playerInv;
        this.filterSlot = filterSlot;
        
        // Add player inventory slots
        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }
    
    private void addPlayerInventory(Inventory playerInv) {
        // Position inventory below filter GUI
        int xOffset = (216 - 176) / 2 + 8; // Center and add margin
        int yOffset = 120 + 4 + 18; // Filter height + gap + margin
        
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, 
                    col + row * 9 + 9, 
                    xOffset + col * 18,
                    yOffset + row * 18
                ));
            }
        }
    }
    
    private void addPlayerHotbar(Inventory playerInv) {
        int xOffset = (216 - 176) / 2 + 8;
        int yOffset = 120 + 4 + 18 + 54 + 4; // Below inventory
        
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, xOffset + col * 18, yOffset));
        }
    }
    
    public ItemStack getFilterStack() {
        return playerInventory.getItem(filterSlot);
    }
    
    public int getFilterSlot() {
        return filterSlot;
    }
    
    public ShuffleBlockList getBlockList() {
        ItemStack filterStack = getFilterStack();
        return filterStack.getOrDefault(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), ShuffleBlockList.EMPTY);
    }
    
    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        return ItemStack.EMPTY;
    }
    
    @Override
    public boolean stillValid(@Nonnull Player player) {
        ItemStack stack = player.getInventory().getItem(filterSlot);
        return !stack.isEmpty() && stack.getItem() instanceof WeightedShuffleFilterItem;
    }
}
