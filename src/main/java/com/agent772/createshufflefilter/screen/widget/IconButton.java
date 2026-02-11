package com.agent772.createshufflefilter.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * Button that displays a custom icon texture
 */
public class IconButton extends Button {
    
    private final ResourceLocation iconTexture;
    private final int iconSize;
    
    public IconButton(int x, int y, int size, ResourceLocation iconTexture, OnPress onPress, Component tooltip) {
        super(x, y, size, size, tooltip, onPress, DEFAULT_NARRATION);
        this.iconTexture = iconTexture;
        this.iconSize = 16; // Icon is 16x16, centered in button
    }
    
    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render button background
        graphics.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight());
        
        // Render icon centered in button
        if (iconTexture != null) {
            int iconX = getX() + (width - iconSize) / 2;
            int iconY = getY() + (height - iconSize) / 2;
            graphics.blit(iconTexture, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }
    
    @Override
    public void renderString(@Nonnull GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int color) {
        // Don't render text - icon only
    }
}
