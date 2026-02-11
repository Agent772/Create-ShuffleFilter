package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;

import net.minecraft.world.item.ItemStack;

/**
 * Allows shuffle filters in deployer filter slots.
 * Overrides the global restriction from MixinFilteringBehaviour.
 */
@Mixin(value = DeployerBlockEntity.class, remap = false)
public class MixinDeployerBlockEntity {

    @Inject(
        method = "canPlaceItem",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void allowShuffleFilters(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof BaseShuffleFilterItem) {
            cir.setReturnValue(true);
        }
    }
}
