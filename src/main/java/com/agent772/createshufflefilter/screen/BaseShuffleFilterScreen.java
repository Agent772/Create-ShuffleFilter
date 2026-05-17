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
 * Base class for shuffle filter screens with shared rendering and UI logic.
 * Supports configurable texture offsets, row spacing, and button positions.
 */
public abstract class BaseShuffleFilterScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(CreateShuffleFilter.MODID, "textures/gui/shuffle_filter_gui.png");

    // Create's player inventory texture
    private static final ResourceLocation PLAYER_INVENTORY =
        new ResourceLocation("create", "textures/gui/player_inventory.png");

    // Create's button and icon textures
    private static final ResourceLocation WIDGETS =
        new ResourceLocation("create", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS =
        new ResourceLocation("create", "textures/gui/icons.png");

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

    protected ButtonArea saveButton;
    protected ButtonArea clearButton;

    public BaseShuffleFilterScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);

        this.imageWidth = 216;
        this.imageHeight = getFilterGuiHeight() + 4 + 108;
        this.inventoryLabelY = 200;
    }

    protected abstract int getFilterGuiHeight();

    protected abstract int getTextureOffsetX();

    protected abstract int getTextureOffsetY();

    protected abstract int getTextureWidth();

    protected abstract int getTextureHeight();

    protected abstract int getRowStartX();

    protected abstract int getRow1Y();

    protected abstract int getRowGap();

    protected abstract int getSlotSpacing();

    protected abstract int getButtonY();

    protected abstract int getClearButtonX();

    protected abstract int getSaveButtonX();

    protected abstract ShuffleBlockList getBlockList();

    protected abstract int getFilterSlot();

    protected abstract void saveConfiguration();

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

    protected void initRow2Slots() {
        row2Slots.clear();

        int startX = leftPos + getRowStartX();
        int row2Y = topPos + getRow1Y() + SLOT_SIZE + getRowGap();
        int spacing = getSlotSpacing();

        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int slotX = startX + col * spacing;
            int index = SLOTS_PER_ROW + col;

            BlockSlotWidget blockSlot = new BlockSlotWidget(
                slotX, row2Y, index,
                this::onBlockChanged
            );
            row2Slots.add(blockSlot);
            addRenderableWidget(blockSlot);
        }
    }

    protected void initButtons() {
        int x = leftPos;
        int y = topPos;
        int buttonY = y + getButtonY();
        int buttonSize = 18;

        clearButton = new ButtonArea(x + getClearButtonX(), buttonY, buttonSize, buttonSize, "Clear");
        saveButton = new ButtonArea(x + getSaveButtonX(), buttonY, buttonSize, buttonSize, "Save");
    }

    protected void loadRow1Slots() {
        ShuffleBlockList blockList = getBlockList();

        for (int i = 0; i < row1Slots.size() && i < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
            row1Slots.get(i).setItem(entry.getItemStack());
        }
    }

    protected void loadRow2Slots() {
        ShuffleBlockList blockList = getBlockList();

        for (int i = 0; i < row2Slots.size() && (i + SLOTS_PER_ROW) < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i + SLOTS_PER_ROW);
            row2Slots.get(i).setItem(entry.getItemStack());
        }
    }

    protected void onBlockChanged(int index) {
        saveConfiguration();
    }

    public List<BlockSlotWidget> getAllSlots() {
        List<BlockSlotWidget> allSlots = new ArrayList<>();
        allSlots.addAll(row1Slots);
        allSlots.addAll(row2Slots);
        return allSlots;
    }

    public void setSlotItem(int index, ItemStack item) {
        List<BlockSlotWidget> allSlots = getAllSlots();
        if (index >= 0 && index < allSlots.size()) {
            BlockSlotWidget slot = allSlots.get(index);
            slot.setItem(item);
            onBlockChanged(index);
        }
    }

    protected void clearAll() {
        for (BlockSlotWidget slot : row1Slots) {
            slot.clearBlock();
        }
        for (BlockSlotWidget slot : row2Slots) {
            slot.clearBlock();
        }
        saveConfiguration();
    }

    protected void collectRowBlocks(int row, List<ItemStack> items, List<Float> weights) {
        List<BlockSlotWidget> slots = (row == 1) ? row1Slots : row2Slots;

        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).hasBlock()) {
                items.add(slots.get(i).getItem());
                weights.add(1.0f);
            }
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, titleLabelX, titleLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int invX = leftPos + (imageWidth - 176) / 2;
        int invY = topPos + getFilterGuiHeight() + 4;
        renderPlayerInventory(graphics, invX, invY);

        graphics.blit(TEXTURE, leftPos, topPos,
            getTextureOffsetX(), getTextureOffsetY(),
            getTextureWidth(), getTextureHeight(),
            256, 256);
    }

    protected void renderPlayerInventory(GuiGraphics graphics, int x, int y) {
        graphics.blit(PLAYER_INVENTORY, x, y, 0, 0, 176, 108, 256, 256);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + 6, 0x404040, false);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        renderButton(graphics, clearButton, false);
        renderButton(graphics, saveButton, false);

        renderTooltip(graphics, mouseX, mouseY);

        clearButton.isHovered = isHovering(clearButton, mouseX, mouseY);
        saveButton.isHovered = isHovering(saveButton, mouseX, mouseY);

        for (BlockSlotWidget slot : row1Slots) {
            slot.renderTooltip(graphics, mouseX, mouseY);
        }
        for (BlockSlotWidget slot : row2Slots) {
            slot.renderTooltip(graphics, mouseX, mouseY);
        }

        if (clearButton.isHovered) {
            graphics.renderTooltip(font, Component.literal(clearButton.tooltip), mouseX, mouseY);
        }
        if (saveButton.isHovered) {
            graphics.renderTooltip(font, Component.literal(saveButton.tooltip), mouseX, mouseY);
        }
    }

    protected void renderButton(GuiGraphics graphics, ButtonArea button, boolean active) {
        int buttonTextureX;
        if (button.isHovered && minecraft != null
            && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.MOUSE_BUTTON_LEFT)) {
            buttonTextureX = BUTTON_DOWN_X;
        } else if (button.isHovered) {
            buttonTextureX = BUTTON_HOVER_X;
        } else {
            buttonTextureX = BUTTON_X;
        }

        graphics.blit(WIDGETS, button.x, button.y, buttonTextureX, BUTTON_Y, 18, 18, 256, 256);

        int iconX = button.x + 1;
        int iconY = button.y + 1;

        if (button == clearButton) {
            graphics.blit(ICONS, iconX, iconY, ICON_TRASH_X, ICON_TRASH_Y, 16, 16, 256, 256);
        } else if (button == saveButton) {
            graphics.blit(ICONS, iconX, iconY, ICON_CONFIRM_X, ICON_CONFIRM_Y, 16, 16, 256, 256);
        }
    }

    protected boolean isHovering(ButtonArea button, int mouseX, int mouseY) {
        return mouseX >= button.x && mouseX < button.x + button.width
            && mouseY >= button.y && mouseY < button.y + button.height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clearButton.isHovered) {
                clearAll();
                return true;
            }
            if (saveButton.isHovered) {
                saveConfiguration();
                var mc = minecraft;
                if (mc != null) {
                    var player = mc.player;
                    if (player != null) {
                        player.closeContainer();
                    }
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

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
