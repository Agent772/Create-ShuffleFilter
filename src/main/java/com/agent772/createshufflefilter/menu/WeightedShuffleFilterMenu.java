package com.agent772.createshufflefilter.menu;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Container menu for weighted shuffle filter configuration GUI.
 */
public class WeightedShuffleFilterMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final int filterSlot;

    // Client-side construction via IForgeMenuType data buf
    public WeightedShuffleFilterMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, playerInv.selected);
    }

    // Server-side construction
    public WeightedShuffleFilterMenu(int containerId, Inventory playerInv, int filterSlot) {
        super(ModMenuTypes.WEIGHTED_SHUFFLE_FILTER.get(), containerId);

        this.playerInventory = playerInv;
        this.filterSlot = filterSlot;

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

    private void addPlayerInventory(Inventory playerInv) {
        int xOffset = (216 - 176) / 2 + 8;
        int yOffset = 120 + 4 + 18;

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
        int yOffset = 120 + 4 + 18 + 54 + 4;

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
        return ShuffleBlockList.read(getFilterStack());
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
