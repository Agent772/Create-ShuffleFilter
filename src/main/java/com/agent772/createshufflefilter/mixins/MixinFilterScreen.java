package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.filter.FilterMenu;
import com.simibubi.create.content.logistics.filter.FilterScreen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Replaces Create's "Respect/Ignore Data" tooltip labels with "Equal/Weighted Mode" when the
 * filter screen was opened for a shuffle filter (detected by the screen title containing
 * "shuffle"). Private {@code Component} fields on {@link FilterScreen} are shadowed and
 * overwritten at constructor TAIL.
 */
@Mixin(FilterScreen.class)
public class MixinFilterScreen {

    @Shadow private Component respectDataN;
    @Shadow private Component ignoreDataN;
    @Shadow private Component respectDataDESC;
    @Shadow private Component ignoreDataDESC;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstructor(FilterMenu menu, Inventory inv, Component title, CallbackInfo ci) {
        try {
            if (title == null) {
                return;
            }
            String titleText = title.getString();
            if (!titleText.toLowerCase().contains("shuffle")) {
                return;
            }

            respectDataN = Component.literal("Equal Mode");
            ignoreDataN = Component.literal("Weighted Mode");
            respectDataDESC = Component.literal("Deployer in contraptions: Randomness ignores item qty. All other: NBT Data is considered");
            ignoreDataDESC = Component.literal("Deployer in contraptions: Items chosen by stack count. All other: NBT Data is ignored");
        } catch (Exception ignored) {
        }
    }
}
