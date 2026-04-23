package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

/**
 * Accessor for accessing the blockEntity field from BlockEntityBehaviour.
 */
@Mixin(value = BlockEntityBehaviour.class, remap = false)
public interface BlockEntityBehaviourAccessor {
    
    @Accessor("blockEntity")
    SmartBlockEntity getBlockEntity();
}
