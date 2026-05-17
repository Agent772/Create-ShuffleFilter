package com.agent772.createshufflefilter.screen;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.menu.ShuffleFilterMenu;
import com.agent772.createshufflefilter.network.FilterConfigPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration screen for shuffle filter (equal probability mode).
 * 2 rows of 9 slots each (18 slots total). All blocks have equal selection probability.
 */
public class ShuffleFilterScreen extends BaseShuffleFilterScreen<ShuffleFilterMenu> {

    public ShuffleFilterScreen(ShuffleFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getFilterGuiHeight() {
        return 101;
    }

    @Override
    protected int getTextureOffsetX() {
        return 0;
    }

    @Override
    protected int getTextureOffsetY() {
        return 0;
    }

    @Override
    protected int getTextureWidth() {
        return 209;
    }

    @Override
    protected int getTextureHeight() {
        return 102;
    }

    @Override
    protected int getRowStartX() {
        return 23;
    }

    @Override
    protected int getRow1Y() {
        return 25;
    }

    @Override
    protected int getRowGap() {
        return 1;
    }

    @Override
    protected int getSlotSpacing() {
        return 18;
    }

    @Override
    protected int getButtonY() {
        return 77;
    }

    @Override
    protected int getClearButtonX() {
        return imageWidth - 54;
    }

    @Override
    protected int getSaveButtonX() {
        return imageWidth - 32;
    }

    @Override
    protected ShuffleBlockList getBlockList() {
        return menu.getBlockList();
    }

    @Override
    protected int getFilterSlot() {
        return menu.getFilterSlot();
    }

    @Override
    protected void init() {
        super.init();

        this.titleLabelX = (this.imageWidth - 8) / 2 - this.font.width(this.title) / 2;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight + 8;

        initRow1Slots();
        initRow2Slots();
        initButtons();

        loadRow1Slots();
        loadRow2Slots();
    }

    @Override
    protected void saveConfiguration() {
        List<ItemStack> items = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        collectRowBlocks(1, items, weights);
        collectRowBlocks(2, items, weights);

        FilterConfigPacket.sendToServer(getFilterSlot(), items, weights);
    }
}
