package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.simibubi.create.content.logistics.filter.FilterItem;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

@Mixin(FilterItem.class)
public class MixinFilterItem {

    @Inject(method = "getFilterItems", at = @At("HEAD"), cancellable = true)
    private static void onGetFilterItems(ItemStack stack, CallbackInfoReturnable<ItemStackHandler> cir) {
        // Check if this is our shuffle filter and allow it to work like a regular filter
        if (stack.is(CreateShuffleFilter.SHUFFLE_FILTER.get())) {
            // Use the same logic as Create's original method, but skip the hardcoded item check
            ItemStackHandler newInv = new ItemStackHandler(18);
            if (!stack.has(com.simibubi.create.AllDataComponents.FILTER_ITEMS)) {
                cir.setReturnValue(newInv);
                return;
            }
            
            com.simibubi.create.foundation.item.ItemHelper.fillItemStackHandler(
                stack.get(com.simibubi.create.AllDataComponents.FILTER_ITEMS), newInv);
            cir.setReturnValue(newInv);
        }
    }
}