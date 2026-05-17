package com.agent772.createshufflefilter.screen;

import com.agent772.createshufflefilter.menu.ShuffleFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Placeholder screen — proves the right-click → menu open path works end-to-end.
 *
 * <p>Texture offsets mirror {@code BaseShuffleFilterScreen} + {@code ShuffleFilterScreen}
 * on the {@code 1.21.1} branch so the wrong half of the atlas (the weighted layout
 * at y=102) is not drawn here. Real configuration widgets land in Epic 4 (#10).
 */
public class StubShuffleFilterScreen extends AbstractContainerScreen<ShuffleFilterMenu> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("createshufflefilter", "textures/gui/shuffle_filter_gui.png");

    // Reuse Create's player inventory background so the inventory section matches
    // the slot grid positioned by ShuffleFilterMenu.
    private static final ResourceLocation PLAYER_INVENTORY =
        new ResourceLocation("create", "textures/gui/player_inventory.png");

    private static final int FILTER_GUI_HEIGHT = 101;
    private static final int FILTER_TEX_WIDTH = 209;
    private static final int FILTER_TEX_HEIGHT = 102;
    private static final int FILTER_TEX_U = 0;
    private static final int FILTER_TEX_V = 0;

    public StubShuffleFilterScreen(ShuffleFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // 216-wide canvas matches the 1.21.1 base screen so leftPos / slot offsets line up.
        this.imageWidth = 216;
        this.imageHeight = FILTER_GUI_HEIGHT + 4 + 108;
        // Inventory label sits at (inventory_bg.x + 8, inventory_bg.y + 6) — same anchor as
        // BaseShuffleFilterScreen on 1.21.1. inventory_bg.x = (imageWidth - 176) / 2 = 20,
        // inventory_bg.y = FILTER_GUI_HEIGHT + 4 = 105.
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
