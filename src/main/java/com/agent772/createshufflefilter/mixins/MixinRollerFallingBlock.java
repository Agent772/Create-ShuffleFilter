package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stalls the contraption briefly when the roller breaks a falling block so the physics
 * has time to settle before the roller paves over the column. Without the stall, fresh
 * placements get destroyed by sand/gravel/concrete-powder falling onto them.
 */
@Mixin(value = RollerMovementBehaviour.class, remap = false)
public class MixinRollerFallingBlock {

    @Inject(method = "onBlockBroken", at = @At("TAIL"), remap = false)
    private void handleFallingBlockPhysics(
        MovementContext context,
        BlockPos pos,
        BlockState brokenState,
        CallbackInfo ci
    ) {
        if (!(brokenState.getBlock() instanceof FallingBlock)) {
            return;
        }
        CompoundTag data = context.data;
        data.putInt("WaitingTicks", 10);
        data.put("LastPos", NbtUtils.writeBlockPos(pos));
        context.stall = true;
    }
}
