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
 * Handles edge case where breaking falling blocks (sand, gravel, concrete powder)
 * causes chain reactions that would destroy freshly placed blocks.
 * 
 * The issue:
 * 1. Roller breaks sand/gravel in TUNNEL_PAVE mode
 * 2. This triggers falling block physics above
 * 3. Roller immediately tries to place new blocks
 * 4. Falling blocks destroy freshly placed blocks
 * 
 * The solution:
 * - Stall contraption for 10 ticks (0.5 seconds) after breaking falling blocks
 * - Let physics settle before continuing placement
 * - Prevents placed blocks from being immediately destroyed
 */
@Mixin(value = RollerMovementBehaviour.class, remap = false)
public class MixinRollerFallingBlock {

    /**
     * Injects after a block is broken to detect falling blocks and add delay
     */
    @Inject(
        method = "onBlockBroken",
        at = @At("TAIL"),
        remap = false
    )
    private void handleFallingBlockPhysics(
        MovementContext context,
        BlockPos pos,
        BlockState brokenState,
        CallbackInfo ci
    ) {
        // Check if the broken block was a falling block
        if (brokenState.getBlock() instanceof FallingBlock) {
            CompoundTag data = context.data;
            
            // Stall contraption for 10 ticks to let falling block physics settle
            data.putInt("WaitingTicks", 10);
            
            // Store position for reference
            data.put("LastPos", NbtUtils.writeBlockPos(pos));
            
            // Activate stall
            context.stall = true;
            
            com.agent772.createshufflefilter.CreateShuffleFilter.LOGGER.debug(
                "Falling block broken at {}, stalling for 10 ticks to let physics settle", 
                pos
            );
        }
    }
}
