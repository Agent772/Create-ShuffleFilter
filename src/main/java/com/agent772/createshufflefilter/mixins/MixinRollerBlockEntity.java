package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;

import net.minecraft.world.item.ItemStack;

/**
 * Allows shuffle filters to bypass the roller's {@code isValidMaterial} gate.
 *
 * <p>{@code RollerBlockEntity#addBehaviours} wires {@code FilteringBehaviour.withPredicate(this::isValidMaterial)},
 * which normally rejects non-block items. Shuffle filters select blocks at runtime so they are
 * always valid as a filter; intercept the gate and return {@code true} for any
 * {@link BaseShuffleFilterItem}.
 */
@Mixin(value = RollerBlockEntity.class, remap = false)
public class MixinRollerBlockEntity {

    @Inject(
        method = "isValidMaterial(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void allowShuffleFilters(ItemStack newFilter, CallbackInfoReturnable<Boolean> cir) {
        if (newFilter.getItem() instanceof BaseShuffleFilterItem) {
            cir.setReturnValue(true);
        }
    }
}
