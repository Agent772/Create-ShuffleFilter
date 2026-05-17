package com.agent772.createshufflefilter.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Widget for selecting a block or item in the configuration GUI.
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
            if (block != null && block != Blocks.AIR) {
                return ForgeRegistries.BLOCKS.getKey(block);
            }
            return ForgeRegistries.ITEMS.getKey(selectedItem.getItem());
        }
        return null;
    }

    public Block getBlock() {
        if (!selectedItem.isEmpty()) {
            Block block = Block.byItem(selectedItem.getItem());
            if (block == Blocks.AIR) {
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
        if (!selectedItem.isEmpty()) {
            graphics.renderItem(selectedItem, getX() + 1, getY() + 1);
        }

        if (isHovered) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered) {
            if (button == 0) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    var player = mc.player;
                    ItemStack mouseStack = player.containerMenu.getCarried();
                    if (!mouseStack.isEmpty()) {
                        setItem(mouseStack);
                        if (onChanged != null) {
                            onChanged.accept(index);
                        }
                        return true;
                    }
                }
            } else if (button == 1) {
                clearBlock();
                return true;
            }
        }
        return false;
    }

    /**
     * Render tooltip for the item in this slot.
     */
    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!selectedItem.isEmpty() && isHovered) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                List<Component> tooltipLines = new ArrayList<>();
                tooltipLines.add(selectedItem.getHoverName());

                boolean isAdvanced = mc.options.advancedItemTooltips;
                boolean isCreative = mc.player != null && mc.player.getAbilities().instabuild;
                TooltipFlag tooltipFlag = isAdvanced
                    ? (isCreative ? TooltipFlag.ADVANCED.asCreative() : TooltipFlag.ADVANCED)
                    : (isCreative ? TooltipFlag.NORMAL.asCreative() : TooltipFlag.NORMAL);

                selectedItem.getItem().appendHoverText(selectedItem, mc.level, tooltipLines, tooltipFlag);

                graphics.renderTooltip(mc.font, tooltipLines, selectedItem.getTooltipImage(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
