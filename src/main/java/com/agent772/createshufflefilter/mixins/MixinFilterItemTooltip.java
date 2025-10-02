package com.agent772.createshufflefilter.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.logistics.filter.FilterItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@Mixin(FilterItem.class)
public class MixinFilterItemTooltip {

    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void addShuffleFilterTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        // Check if this is our shuffle filter
        if (stack.is(CreateShuffleFilter.SHUFFLE_FILTER.get())) {
            tooltip.add(Component.literal("Randomizes item selection from filtered matches for deployers on contraptions").withStyle(ChatFormatting.GRAY));
            
            if (AllKeys.shiftDown()) {
                // Detailed tooltip when holding shift
                tooltip.add(Component.literal("Behaviour when in deployer on contraption").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.literal("• Selects items randomly from those that pass the filter.").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Randomness is controlled via 2 mods").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("Behaviour in all other cases").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.literal("• Behaves like a normal List Filter").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Equal Mode  = use NBT Data").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Weighted Mode  = ignore NBT Data").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());
                
                // Add mode explanations
                tooltip.add(Component.literal("Equal Mode").withStyle(ChatFormatting.BLUE));
                tooltip.add(Component.literal("• All matching items have equal selection chance").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Ignores stack quantities for selection").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());
                
                tooltip.add(Component.literal("Weighted Mode").withStyle(ChatFormatting.GREEN));
                tooltip.add(Component.literal("• Items with more stacks are more likely to be selected").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Selection probability based on stack count").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());
                
                tooltip.add(Component.literal("Use the filter GUI toggle to switch between modes").withStyle(ChatFormatting.DARK_GRAY));
                
            } else {
                // Brief tooltip when not holding shift
                tooltip.add(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("SHIFT").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" for details").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
    }
}