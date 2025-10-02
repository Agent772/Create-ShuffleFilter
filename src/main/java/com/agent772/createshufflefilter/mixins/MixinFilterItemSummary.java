package com.agent772.createshufflefilter.mixins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

@Mixin(FilterItem.class)
public class MixinFilterItemSummary {

    @Inject(method = "makeSummary", at = @At("HEAD"), cancellable = true)
    private void replaceShuffleFilterSummary(ItemStack filter, CallbackInfoReturnable<List<Component>> cir) {
        // Check if this is our shuffle filter
        if (filter.is(CreateShuffleFilter.SHUFFLE_FILTER.get())) {
            List<Component> list = new ArrayList<>();
            
            if (filter.isComponentsPatchEmpty()) {
                cir.setReturnValue(list);
                return;
            }
            
            // Determine the current mode based on the respectNBT setting
            // For shuffle filters: respectNBT=true = equal mode, respectNBT=false = weighted mode
            boolean useWeightedMode = false;  // Default to equal mode
            
            try {
                var components = filter.getComponents();
                String componentsStr = components.toString();
                
                if (componentsStr.contains("create:filter_items_respect_nbt=>")) {
                    int startIndex = componentsStr.indexOf("create:filter_items_respect_nbt=>") + "create:filter_items_respect_nbt=>".length();
                    String remaining = componentsStr.substring(startIndex);
                    
                    int endIndex = remaining.indexOf(',');
                    if (endIndex == -1) endIndex = remaining.indexOf('}');
                    if (endIndex == -1) endIndex = remaining.length();
                    
                    String valueStr = remaining.substring(0, endIndex).trim();
                    
                    if (valueStr.equals("false")) {
                        useWeightedMode = true; // respectNBT=false means weighted mode
                    }
                }
            } catch (Exception e) {
                // Silently fall back to equal mode
            }
            
            // Add the mode line at the beginning
            Component modeComponent = Component.literal("Mode: ").withStyle(ChatFormatting.GOLD)
                .append(useWeightedMode 
                    ? Component.literal("Weighted").withStyle(ChatFormatting.GREEN)
                    : Component.literal("Equal").withStyle(ChatFormatting.BLUE));
            
            list.add(modeComponent);
            
            // Add the normal filter summary (copied from Create's original logic)
            try {
                ItemStackHandler filterItems = FilterItem.getFilterItems(filter);
                boolean blacklist = filter.getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST, false);

                list.add((blacklist ? CreateLang.translateDirect("gui.filter.deny_list")
                    : CreateLang.translateDirect("gui.filter.allow_list")).withStyle(ChatFormatting.GOLD));
                int count = 0;
                for (int i = 0; i < filterItems.getSlots(); i++) {
                    if (count > 3) {
                        list.add(Component.literal("- ...")
                            .withStyle(ChatFormatting.DARK_GRAY));
                        break;
                    }

                    ItemStack filterStack = filterItems.getStackInSlot(i);
                    if (filterStack.isEmpty())
                        continue;
                    list.add(Component.literal("- ")
                        .append(filterStack.getHoverName())
                        .withStyle(ChatFormatting.GRAY));
                    count++;
                }

                if (count == 0) {
                    // Only return the mode if no items are configured
                    cir.setReturnValue(Collections.singletonList(modeComponent));
                    return;
                }
            } catch (Exception e) {
                // If there's an error getting filter items, just show the mode
                cir.setReturnValue(Collections.singletonList(modeComponent));
                return;
            }
            
            cir.setReturnValue(list);
        }
    }
}