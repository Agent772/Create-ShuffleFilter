package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.world.item.ItemStack;

/**
 * Prevents shuffle filters from being used in standard Create filter slots.
 * Allows them only in deployers and rollers by checking the block entity type.
 * 
 * This implements the "reverse allowlist" pattern used by createrandomizefilters:
 * 1. FilterItem inheritance → Accepted everywhere by default
 * 2. This mixin → Block everywhere EXCEPT deployers and rollers
 */
@Mixin(value = FilteringBehaviour.class, remap = false)
public abstract class MixinFilteringBehaviour {

    private static boolean isShuffleFilter(ItemStack stack) {
        return stack.getItem() instanceof BaseShuffleFilterItem;
    }

    @Inject(
        method = "test",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void restrictShuffleFilters(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (isShuffleFilter(stack)) {
            // Access blockEntity through accessor interface
            SmartBlockEntity blockEntity = ((BlockEntityBehaviourAccessor) this).getBlockEntity();
            
            // Check if this FilteringBehaviour belongs to a deployer or roller
            boolean isDeployer = blockEntity instanceof DeployerBlockEntity;
            boolean isRoller = blockEntity instanceof RollerBlockEntity;
            
            // Only allow in deployers and rollers
            if (!isDeployer && !isRoller) {
                cir.setReturnValue(false);
            }
        }
    }
}