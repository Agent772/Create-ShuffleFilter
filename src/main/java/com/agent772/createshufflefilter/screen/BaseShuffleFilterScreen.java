package com.agent772.createshufflefilter.screen;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.screen.widget.BlockSlotWidget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for shuffle filter screens with shared rendering and UI logic
 * Supports configurable texture offsets, row spacing, and button positions
 */
public abstract class BaseShuffleFilterScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    
    private static final ResourceLocation TEXTURE = 
        ResourceLocation.fromNamespaceAndPath(CreateShuffleFilter.MODID, 
            "textures/gui/shuffle_filter_gui.png");
    
    // Create's player inventory texture
    private static final ResourceLocation PLAYER_INVENTORY = 
        ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");
    
    // Create's button and icon textures
    private static final ResourceLocation WIDGETS = 
        ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS = 
        ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");
    
    // Button textures from Create (18x18)
    private static final int BUTTON_X = 0;
    private static final int BUTTON_Y = 0;
    private static final int BUTTON_HOVER_X = 18;
    private static final int BUTTON_DOWN_X = 36;
    
    // Icon positions from Create's icons (16x16)
    private static final int ICON_TRASH_X = 16;
    private static final int ICON_TRASH_Y = 0;
    private static final int ICON_CONFIRM_X = 0;
    private static final int ICON_CONFIRM_Y = 16;
    
    protected static final int SLOTS_PER_ROW = 9;
    protected static final int SLOT_SIZE = 18;
    
    protected final List<BlockSlotWidget> row1Slots = new ArrayList<>();
    protected final List<BlockSlotWidget> row2Slots = new ArrayList<>();
    
    // Button areas
    protected ButtonArea saveButton;
    protected ButtonArea clearButton;
    
    public BaseShuffleFilterScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        
        // These will be set by subclasses via getFilterGuiHeight()
        this.imageWidth = 216; // Width for 9 slots (standard)
        this.imageHeight = getFilterGuiHeight() + 4 + 108; // Filter height + gap + player inventory height
        this.inventoryLabelY = 200; // Will be set properly in init
    }
    
    /**
     * Get the height of the filter GUI portion (before player inventory)
     * @return height in pixels
     */
    protected abstract int getFilterGuiHeight();
    
    /**
     * Get the texture offset X coordinate for background blit
     * @return X offset in texture
     */
    protected abstract int getTextureOffsetX();
    
    /**
     * Get the texture offset Y coordinate for background blit
     * @return Y offset in texture
     */
    protected abstract int getTextureOffsetY();
    
    /**
     * Get the width of the background texture to blit
     * @return width in pixels
     */
    protected abstract int getTextureWidth();
    
    /**
     * Get the height of the background texture to blit
     * @return height in pixels
     */
    protected abstract int getTextureHeight();
    
    /**
     * Get the starting X position for row slots (relative to leftPos)
     * @return X offset in pixels
     */
    protected abstract int getRowStartX();
    
    /**
     * Get the starting Y position for row 1 (relative to topPos)
     * @return Y offset in pixels
     */
    protected abstract int getRow1Y();
    
    /**
     * Get the gap between rows
     * @return gap in pixels
     */
    protected abstract int getRowGap();
    
    /**
     * Get the horizontal spacing between slots
     * @return spacing in pixels
     */
    protected abstract int getSlotSpacing();
    
    /**
     * Get the Y position for buttons (relative to topPos)
     * @return Y offset in pixels
     */
    protected abstract int getButtonY();
    
    /**
     * Get the X position for clear button (relative to leftPos)
     * @return X offset in pixels
     */
    protected abstract int getClearButtonX();
    
    /**
     * Get the X position for save button (relative to leftPos)
     * @return X offset in pixels
     */
    protected abstract int getSaveButtonX();
    
    /**
     * Get the block list from the menu - must be implemented by subclasses
     */
    protected abstract ShuffleBlockList getBlockList();
    
    /**
     * Get the filter slot from the menu - must be implemented by subclasses
     */
    protected abstract int getFilterSlot();
    
    /**
     * Called when configuration is saved - subclasses implement specific save logic
     */
    protected abstract void saveConfiguration();
    
    /**
     * Initialize row 1 block slots (shared across all filter types)
     */
    protected void initRow1Slots() {
        row1Slots.clear();
        
        int startX = leftPos + getRowStartX();
        int startY = topPos + getRow1Y();
        int spacing = getSlotSpacing();
        
        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int slotX = startX + col * spacing;
            
            BlockSlotWidget blockSlot = new BlockSlotWidget(
                slotX, startY, col,
                this::onBlockChanged
            );
            row1Slots.add(blockSlot);
            addRenderableWidget(blockSlot);
        }
    }
    
    /**
     * Initialize row 2 block slots
     */
    protected void initRow2Slots() {
        row2Slots.clear();
        
        int startX = leftPos + getRowStartX();
        int row2Y = topPos + getRow1Y() + SLOT_SIZE + getRowGap();
        int spacing = getSlotSpacing();
        
        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int slotX = startX + col * spacing;
            int index = SLOTS_PER_ROW + col; // 9-17
            
            BlockSlotWidget blockSlot = new BlockSlotWidget(
                slotX, row2Y, index,
                this::onBlockChanged
            );
            row2Slots.add(blockSlot);
            addRenderableWidget(blockSlot);
        }
    }
    
    /**
     * Initialize buttons (shared across all filter types)
     */
    protected void initButtons() {
        int x = leftPos;
        int y = topPos;
        int buttonY = y + getButtonY();
        int buttonSize = 18;
        
        // Clear button
        clearButton = new ButtonArea(x + getClearButtonX(), buttonY, buttonSize, buttonSize, "Clear");
        
        // Save button
        saveButton = new ButtonArea(x + getSaveButtonX(), buttonY, buttonSize, buttonSize, "Save");
    }
    
    /**
     * Load row 1 block slots from configuration
     */
    protected void loadRow1Slots() {
        ShuffleBlockList blockList = getBlockList();
        
        for (int i = 0; i < row1Slots.size() && i < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
            row1Slots.get(i).setItem(entry.getItemStack());
        }
    }
    
    /**
     * Load row 2 block slots from configuration
     */
    protected void loadRow2Slots() {
        ShuffleBlockList blockList = getBlockList();
        
        for (int i = 0; i < row2Slots.size() && (i + SLOTS_PER_ROW) < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i + SLOTS_PER_ROW);
            row2Slots.get(i).setItem(entry.getItemStack());
        }
    }
    
    /**
     * Called when a block is changed in a slot
     */
    protected void onBlockChanged(int index) {
        saveConfiguration();
    }
    
    /**
     * Get all block slot widgets (for JEI ghost ingredient support)
     * @return combined list of all slots from both rows
     */
    public List<BlockSlotWidget> getAllSlots() {
        List<BlockSlotWidget> allSlots = new ArrayList<>();
        allSlots.addAll(row1Slots);
        allSlots.addAll(row2Slots);
        return allSlots;
    }
    
    /**
     * Set an item in a specific slot (for JEI ghost ingredient support)
     * @param index slot index (0-17 for shuffle filter, 0-8 for weighted)
     * @param item ItemStack to set
     */
    public void setSlotItem(int index, ItemStack item) {
        List<BlockSlotWidget> allSlots = getAllSlots();
        if (index >= 0 && index < allSlots.size()) {
            BlockSlotWidget slot = allSlots.get(index);
            slot.setItem(item);
            onBlockChanged(index);
        }
    }
    
    /**
     * Clear all block slots (row 1 and row 2)
     */
    protected void clearAll() {
        for (BlockSlotWidget slot : row1Slots) {
            slot.clearBlock();
        }
        for (BlockSlotWidget slot : row2Slots) {
            slot.clearBlock();
        }
        saveConfiguration();
    }
    
    /**
     * Collect configured blocks from a specific row with default equal weights
     * @param row 1 for row1Slots, 2 for row2Slots
     * @param items list to append ItemStacks to (with components preserved)
     * @param weights list to append weights to (1.0f for equal probability)
     */
    protected void collectRowBlocks(int row, List<ItemStack> items, List<Float> weights) {
        List<BlockSlotWidget> slots = (row == 1) ? row1Slots : row2Slots;
        
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).hasBlock()) {
                items.add(slots.get(i).getItem());
                weights.add(1.0f); // Default equal weight
            }
        }
    }
    
    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        // Center title (Create style) - uses titleLabelX and titleLabelY set by subclass
        graphics.drawString(this.font, this.title, titleLabelX, titleLabelY, 0x404040, false);
    }
    
    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Render Create's player inventory background (positioned below filter GUI)
        int invX = leftPos + (imageWidth - 176) / 2; // Center 176px inventory in 216px width
        int invY = topPos + getFilterGuiHeight() + 4; // Below filter GUI with 4px gap
        renderPlayerInventory(graphics, invX, invY);
        
        // Render filter background texture with configurable offsets
        graphics.blit(TEXTURE, leftPos, topPos, 
            getTextureOffsetX(), getTextureOffsetY(), 
            getTextureWidth(), getTextureHeight(), 
            256, 256);
    }
    
    protected void renderPlayerInventory(GuiGraphics graphics, int x, int y) {
        // Render Create's player inventory background (176x108)
        graphics.blit(PLAYER_INVENTORY, x, y, 0, 0, 176, 108, 256, 256);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + 6, 0x404040, false);
    }
    
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // Render buttons
        renderButton(graphics, clearButton, false);
        renderButton(graphics, saveButton, false);
        
        renderTooltip(graphics, mouseX, mouseY);
        
        // Update button hover states
        clearButton.isHovered = isHovering(clearButton, mouseX, mouseY);
        saveButton.isHovered = isHovering(saveButton, mouseX, mouseY);
        
        // Render slot tooltips for items in block slots
        for (BlockSlotWidget slot : row1Slots) {
            slot.renderTooltip(graphics, mouseX, mouseY);
        }
        for (BlockSlotWidget slot : row2Slots) {
            slot.renderTooltip(graphics, mouseX, mouseY);
        }
        
        // Render button tooltips (buttons render on top of slot tooltips)
        if (clearButton.isHovered) {
            graphics.renderTooltip(font, Component.literal(clearButton.tooltip), mouseX, mouseY);
        }
        if (saveButton.isHovered) {
            graphics.renderTooltip(font, Component.literal(saveButton.tooltip), mouseX, mouseY);
        }
    }
    
    protected void renderButton(GuiGraphics graphics, ButtonArea button, boolean active) {
        // Determine button state texture offset
        int buttonTextureX;
        if (button.isHovered && minecraft != null && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.MOUSE_BUTTON_LEFT)) {
            buttonTextureX = BUTTON_DOWN_X; // Pressed
        } else if (button.isHovered) {
            buttonTextureX = BUTTON_HOVER_X; // Hovered
        } else {
            buttonTextureX = BUTTON_X; // Normal
        }
        
        // Render Create's button background (18x18)
        graphics.blit(WIDGETS, button.x, button.y, buttonTextureX, BUTTON_Y, 18, 18, 256, 256);
        
        // Render icon (16x16 centered in 18x18 button)
        int iconX = button.x + 1;
        int iconY = button.y + 1;
        
        if (button == clearButton) {
            // I_TRASH icon from Create
            graphics.blit(ICONS, iconX, iconY, ICON_TRASH_X, ICON_TRASH_Y, 16, 16, 256, 256);
        } else if (button == saveButton) {
            // I_CONFIRM icon from Create
            graphics.blit(ICONS, iconX, iconY, ICON_CONFIRM_X, ICON_CONFIRM_Y, 16, 16, 256, 256);
        }
    }
    
    protected boolean isHovering(ButtonArea button, int mouseX, int mouseY) {
        return mouseX >= button.x && mouseX < button.x + button.width 
            && mouseY >= button.y && mouseY < button.y + button.height;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle button clicks
        if (button == 0) {
            if (clearButton.isHovered) {
                clearAll();
                return true;
            }
            if (saveButton.isHovered) {
                saveConfiguration();
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.closeContainer();
                }
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    // Helper class
    protected static class ButtonArea {
        int x, y, width, height;
        String tooltip;
        boolean isHovered = false;
        
        ButtonArea(int x, int y, int width, int height, String tooltip) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tooltip = tooltip;
        }
    }
}
