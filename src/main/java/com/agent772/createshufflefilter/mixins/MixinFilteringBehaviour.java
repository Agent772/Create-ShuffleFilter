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
 * Restricts shuffle filters so they can only be placed in deployer and roller filter slots.
 *
 * <p>On Forge 1.20.1, {@code FilteringBehaviour#setFilter(ItemStack)} is the placement gate:
 * it returns {@code false} to reject a candidate filter. We hook {@code HEAD}-cancellable
 * and short-circuit to {@code false} when the candidate is a shuffle filter and the owning
 * block entity is not a {@link DeployerBlockEntity} or {@link RollerBlockEntity}.
 *
 * <p>This is the reverse-allowlist pattern: shuffle filters extend {@code FilterItem} so they
 * would otherwise be accepted in any Create filter slot.
 */
@Mixin(value = FilteringBehaviour.class, remap = false)
public abstract class MixinFilteringBehaviour {

    private static boolean isShuffleFilter(ItemStack stack) {
        return stack.getItem() instanceof BaseShuffleFilterItem;
    }

    @Inject(
        method = "setFilter(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void restrictShuffleFilters(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!isShuffleFilter(stack)) {
            return;
        }
        SmartBlockEntity blockEntity = ((BlockEntityBehaviourAccessor) this).getBlockEntity();
        if (blockEntity instanceof DeployerBlockEntity || blockEntity instanceof RollerBlockEntity) {
            return;
        }
        cir.setReturnValue(false);
    }
}
