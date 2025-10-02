package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.filter.FilterScreen;
import com.simibubi.create.content.logistics.filter.FilterMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@Mixin(FilterScreen.class)
public class MixinFilterScreen {

    // Shadow the private Component fields so we can modify them
    @Shadow private Component respectDataN;
    @Shadow private Component ignoreDataN;
    @Shadow private Component respectDataDESC;
    @Shadow private Component ignoreDataDESC;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstructor(FilterMenu menu, Inventory inv, Component title, CallbackInfo ci) {
        // Detect shuffle filter by title and set tooltip overrides
        try {
            boolean isShuffleFilter = false;
            
            if (title != null) {
                String titleText = title.getString();
                if (titleText.toLowerCase().contains("shuffle")) {
                    isShuffleFilter = true;
                }
            }
            
            if (isShuffleFilter) {
                // Override the tooltip Components for shuffle mode
                respectDataN = Component.literal("Equal Mode");
                ignoreDataN = Component.literal("Weighted Mode");
                
                respectDataDESC = Component.literal("Deployer in contraptions: Randomness ignores item qty. All other: NBT Data is considered");
                ignoreDataDESC = Component.literal("Deployer in contraptions: Items chosen by stack count. All other: NBT Data is ignored");
            }
        } catch (Exception e) {
            // Silently ignore errors - fall back to default behavior
        }
    }
}