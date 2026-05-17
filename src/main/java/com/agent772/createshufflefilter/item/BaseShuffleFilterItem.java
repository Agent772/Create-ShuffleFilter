package com.agent772.createshufflefilter.item;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for shuffle filter items with shared functionality.
 * Extends Create's {@link FilterItem} to integrate with the filter system.
 *
 * <p>On 1.20.1 the per-stack configuration lives in NBT (see Epic 2 / #8) and is
 * read through {@link ShuffleBlockList#read(ItemStack)}.
 */
public abstract class BaseShuffleFilterItem extends FilterItem {

    protected BaseShuffleFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack[] getFilterItems(ItemStack filterStack) {
        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        return blockList.blocks().stream()
            .map(ShuffleBlockList.BlockEntry::getItemStack)
            .filter(stack -> !stack.isEmpty())
            .toArray(ItemStack[]::new);
    }

    @Override
    public FilterItemStack makeStackWrapper(ItemStack filterStack) {
        return new ShuffleFilterItemStack(filterStack);
    }

    @Override
    public boolean canCopyFromItem(ItemStack item) {
        return !ShuffleBlockList.read(item).isEmpty();
    }

    @Override
    public boolean canCopyToItem(ItemStack item) {
        return ShuffleBlockList.read(item).isEmpty();
    }

    @Override
    public abstract net.minecraft.world.inventory.AbstractContainerMenu createMenu(
        int containerId,
        net.minecraft.world.entity.player.Inventory playerInv,
        net.minecraft.world.entity.player.Player player
    );

    @Override
    public List<Component> makeSummary(ItemStack filterStack) {
        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);

        if (blockList.isEmpty()) {
            return List.of(Component.literal("Not configured").withStyle(ChatFormatting.RED));
        }

        Component summary = Component.literal(blockList.size() + " blocks")
            .withStyle(ChatFormatting.GREEN)
            .append(Component.literal(" (" + getFilterModeName() + ")")
                .withStyle(getFilterModeColor()));

        return List.of(summary);
    }

    public abstract String getFilterModeName();

    public abstract ChatFormatting getFilterModeColor();

    public abstract String getFilterDescription();

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ShuffleBlockList blockList = ShuffleBlockList.read(stack);

        tooltip.add(Component.literal("Right-click to configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(getFilterDescription()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Allow-list in funnels/basins, randomizes in deployers").withStyle(ChatFormatting.DARK_GRAY));

        if (blockList.isEmpty()) {
            tooltip.add(Component.literal("Not configured").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal(blockList.size() + " blocks configured")
                .withStyle(ChatFormatting.GREEN));
        }

        if (hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Configured Blocks:").withStyle(ChatFormatting.GOLD));

            if (blockList.isEmpty()) {
                tooltip.add(Component.literal("  (none)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (int i = 0; i < Math.min(blockList.size(), 10); i++) {
                    ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
                    String blockName = entry.getItemStack().getHoverName().getString();
                    tooltip.add(Component.literal("  • " + blockName)
                        .withStyle(ChatFormatting.GRAY));
                }
                if (blockList.size() > 10) {
                    tooltip.add(Component.literal("  ... and " + (blockList.size() - 10) + " more")
                        .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else {
            tooltip.add(Component.literal("Hold SHIFT for details").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    protected static boolean hasShiftDown() {
        // appendHoverText is client-only on 1.20.1, so Screen.hasShiftDown() is safe here.
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }
}
