package com.agent772.createshufflefilter.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * Button that displays a custom icon texture.
 *
 * <p>1.20.1 has no sprite-atlas backed button textures (no {@code Button.SPRITES} /
 * {@code GuiGraphics.blitSprite}); the button background is composed from the vanilla
 * {@code widgets.png} UV regions.
 */
public class IconButton extends Button {

    private static final ResourceLocation BUTTON_WIDGETS =
        new ResourceLocation("textures/gui/widgets.png");

    private static final int BG_HEIGHT = 20;
    private static final int BG_TEXTURE_WIDTH = 200;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int BG_V_BASE = 46;

    private final ResourceLocation iconTexture;
    private final int iconSize;

    public IconButton(int x, int y, int size, ResourceLocation iconTexture, OnPress onPress, Component tooltip) {
        super(x, y, size, size, tooltip, onPress, DEFAULT_NARRATION);
        this.iconTexture = iconTexture;
        this.iconSize = 16;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // state: 0 = disabled, 1 = idle, 2 = hovered
        int state = !this.active ? 0 : (this.isHoveredOrFocused() ? 2 : 1);
        int v = BG_V_BASE + state * BG_HEIGHT;

        int w = Math.min(this.getWidth(), BG_TEXTURE_WIDTH);
        int h = Math.min(this.getHeight(), BG_HEIGHT);
        graphics.blit(BUTTON_WIDGETS, this.getX(), this.getY(), 0, v, w, h, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);

        if (iconTexture != null) {
            int iconX = getX() + (width - iconSize) / 2;
            int iconY = getY() + (height - iconSize) / 2;
            graphics.blit(iconTexture, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }

    @Override
    public void renderString(@Nonnull GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int color) {
        // icon only — suppress label rendering
    }
}
