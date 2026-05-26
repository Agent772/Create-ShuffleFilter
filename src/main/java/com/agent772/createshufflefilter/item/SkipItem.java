package com.agent772.createshufflefilter.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Marker item placed inside a (weighted) shuffle filter slot to mean
 * "when this entry is rolled, place nothing at this position".
 *
 * It is deliberately a plain {@link Item} (not a {@link com.simibubi.create.content.logistics.filter.FilterItem})
 * so it can never itself be configured or recursed into.
 */
public class SkipItem extends Item {

    public SkipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.createshufflefilter.skip.tooltip.1")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createshufflefilter.skip.tooltip.2")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
