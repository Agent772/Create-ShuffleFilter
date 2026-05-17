package com.agent772.createshufflefilter.screen;

import com.agent772.createshufflefilter.menu.WeightedShuffleFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Placeholder screen for the weighted filter — proves the right-click → menu
 * open path. Texture offsets mirror {@code WeightedShuffleFilterScreen} on the
 * {@code 1.21.1} branch (atlas region at y=102, sized 226x120). Real widgets
 * land in Epic 4 (#10).
 */
public class StubWeightedShuffleFilterScreen extends AbstractContainerScreen<WeightedShuffleFilterMenu> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("createshufflefilter", "textures/gui/shuffle_filter_gui.png");

    private static final ResourceLocation PLAYER_INVENTORY =
        new ResourceLocation("create", "textures/gui/player_inventory.png");

    private static final int FILTER_GUI_HEIGHT = 120;
    private static final int FILTER_TEX_WIDTH = 226;
    private static final int FILTER_TEX_HEIGHT = 120;
    private static final int FILTER_TEX_U = 0;
    private static final int FILTER_TEX_V = 102;

    public StubWeightedShuffleFilterScreen(WeightedShuffleFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 216;
        this.imageHeight = FILTER_GUI_HEIGHT + 4 + 108;
        // Match the 1.21.1 base screen: inventory label sits 8/6 px in from the
        // player_inventory.png blit, which is at (20, FILTER_GUI_HEIGHT + 4).
        this.inventoryLabelX = (this.imageWidth - 176) / 2 + 8;
        this.inventoryLabelY = FILTER_GUI_HEIGHT + 4 + 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int invX = leftPos + (imageWidth - 176) / 2;
        int invY = topPos + FILTER_GUI_HEIGHT + 4;
        graphics.blit(PLAYER_INVENTORY, invX, invY, 0, 0, 176, 108, 256, 256);

        graphics.blit(TEXTURE, leftPos, topPos,
            FILTER_TEX_U, FILTER_TEX_V,
            FILTER_TEX_WIDTH, FILTER_TEX_HEIGHT,
            256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
