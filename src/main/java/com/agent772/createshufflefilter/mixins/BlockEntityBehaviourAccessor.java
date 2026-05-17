package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

/**
 * Accessor for the {@code blockEntity} field on {@link BlockEntityBehaviour}.
 *
 * <p>Used by {@link MixinFilteringBehaviour} to determine whether a {@code FilteringBehaviour}
 * is attached to a deployer or roller (the only block entities allowed to hold shuffle filters).
 */
@Mixin(value = BlockEntityBehaviour.class, remap = false)
public interface BlockEntityBehaviourAccessor {

    @Accessor("blockEntity")
    SmartBlockEntity getBlockEntity();
}
