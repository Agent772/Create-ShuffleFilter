package com.agent772.createshufflefilter.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Widget for selecting a block or item in the configuration GUI
 */
public class BlockSlotWidget extends AbstractWidget {
    
    private static final int SIZE = 18;
    private ItemStack selectedItem = ItemStack.EMPTY;
    private final int index;
    private final Consumer<Integer> onChanged;
    
    public BlockSlotWidget(int x, int y, int index, Consumer<Integer> onChanged) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.index = index;
        this.onChanged = onChanged;
    }
    
    public void setBlock(Block block) {
        this.selectedItem = block != null ? new ItemStack(block) : ItemStack.EMPTY;
    }
    
    public void setItem(ItemStack item) {
        this.selectedItem = item != null ? item.copy() : ItemStack.EMPTY;
    }
    
    public void clearBlock() {
        this.selectedItem = ItemStack.EMPTY;
        if (onChanged != null) {
            onChanged.accept(index);
        }
    }
    
    public boolean hasBlock() {
        return !selectedItem.isEmpty();
    }
    
    public ResourceLocation getBlockId() {
        if (!selectedItem.isEmpty()) {
            Block block = Block.byItem(selectedItem.getItem());
            // Check if it's actually a block (not AIR)
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                return BuiltInRegistries.BLOCK.getKey(block);
            }
            // If not a block, return item registry name
            return BuiltInRegistries.ITEM.getKey(selectedItem.getItem());
        }
        return null;
    }
    
    public Block getBlock() {
        if (!selectedItem.isEmpty()) {
            Block block = Block.byItem(selectedItem.getItem());
            // Return null for non-block items (AIR)
            if (block == net.minecraft.world.level.block.Blocks.AIR) {
                return null;
            }
            return block;
        }
        return null;
    }
    
    public ItemStack getItem() {
        return selectedItem.copy();
    }
    
    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw item if selected (background comes from GUI texture)
        if (!selectedItem.isEmpty()) {
            graphics.renderItem(selectedItem, getX() + 1, getY() + 1);
        }
        
        // Highlight on hover
        if (isHovered) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80FFFFFF);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered) {
            if (button == 0) {
                // Left click: select item/block from mouse cursor
                if (Minecraft.getInstance().player != null) {
                    ItemStack mouseStack = Minecraft.getInstance().player.containerMenu.getCarried();
                    if (!mouseStack.isEmpty()) {
                        setItem(mouseStack);
                        if (onChanged != null) {
                            onChanged.accept(index);
                        }
                        return true;
                    }
                }
            } else if (button == 1) {
                // Right click: clear
                clearBlock();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Render tooltip for the item in this slot
     */
    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!selectedItem.isEmpty() && isHovered) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                // Get the full tooltip including item name and all custom tooltip lines
                List<Component> tooltipLines = new ArrayList<>();
                
                // Add item name as first line
                tooltipLines.add(selectedItem.getHoverName());
                
                // Add all additional tooltip lines from the item
                // This will include filter configuration for filter items
                // Create a TooltipFlag that properly detects shift state
                boolean isShiftDown = Screen.hasShiftDown();
                boolean isAdvanced = mc.options.advancedItemTooltips;
                TooltipFlag tooltipFlag = new TooltipFlag() {
                    @Override
                    public boolean isAdvanced() {
                        return isAdvanced;
                    }
                    
                    @Override
                    public boolean isCreative() {
                        return mc.player != null && mc.player.getAbilities().instabuild;
                    }
                    
                    @Override
                    public boolean hasShiftDown() {
                        return isShiftDown;
                    }
                };
                
                selectedItem.getItem().appendHoverText(selectedItem, 
                    mc.level != null ? 
                        net.minecraft.world.item.Item.TooltipContext.of(mc.level) : 
                        net.minecraft.world.item.Item.TooltipContext.EMPTY,
                    tooltipLines, 
                    tooltipFlag);
                
                graphics.renderTooltip(mc.font, tooltipLines, selectedItem.getTooltipImage(), mouseX, mouseY);
            }
        }
    }
    
    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
