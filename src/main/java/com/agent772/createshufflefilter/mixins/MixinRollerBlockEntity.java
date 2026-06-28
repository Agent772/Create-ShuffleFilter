package com.agent772.createshufflefilter.mixins;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;

import net.minecraft.world.item.ItemStack;

/**
 * Mixin to allow shuffle filters in roller filter slots.
 * 
 * CRITICAL: RollerBlockEntity.isValidMaterial() is called by FilteringBehaviour.withPredicate()
 * to validate filters BEFORE they can be placed in the slot. This method normally checks if the
 * filter represents a valid paving material (full block with collision). Shuffle filters don't
 * represent blocks directly, so we intercept this validation to allow them.
 * 
 * DeployerBlockEntity does NOT use withPredicate(), which is why deployers work without this fix.
 */
@Mixin(RollerBlockEntity.class)
public class MixinRollerBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Allow shuffle filters to bypass material validation.
     * This method is called by FilteringBehaviour when a filter is placed in the slot.
     */
    @Inject(method = "isValidMaterial", at = @At("HEAD"), cancellable = true)
    private void allowShuffleFilters(ItemStack newFilter, CallbackInfoReturnable<Boolean> cir) {
        // Shuffle filters are always valid - they select from configured blocks at runtime
        if (newFilter.getItem() instanceof BaseShuffleFilterItem) {
            LOGGER.debug("Allowing shuffle filter in roller: {}", newFilter.getDescriptionId());
            cir.setReturnValue(true);
        }
    }
}
