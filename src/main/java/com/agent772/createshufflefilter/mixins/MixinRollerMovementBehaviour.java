package com.agent772.createshufflefilter.mixins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.agent772.createshufflefilter.util.RollerSelectionUtil;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.items.IItemHandler;

/**
 * Roller-on-contraption integration for shuffle filters.
 *
 * <p>Mirrors {@link MixinDeployerMovementBehaviour} but on rollers, with position-based
 * deterministic selection (same world coordinate = same chosen block, even across runs)
 * achieved by feeding {@code BlockPos.asLong()} through the Stafford-13 bit mixer (see
 * commit c2188b9 for the rationale).
 *
 * <p>Five injection sites:
 * <ul>
 *   <li>{@code getStateToPaveWith(MovementContext)} — selects which block to place.</li>
 *   <li>{@code getStateToPaveWithAsSlab(MovementContext)} — picks slab variant, falling back
 *       to another filter entry if the chosen block has no slab.</li>
 *   <li>{@code tryFill(MovementContext, BlockPos, BlockState)} — performs the placement and
 *       returns the private {@code PaveResult.SUCCESS} via reflection (enum order
 *       {@code FAIL=0, PASS=1, SUCCESS=2}).</li>
 *   <li>{@code getPositionsToBreak(MovementContext, BlockPos)} — Create's gate uses
 *       {@code filter.test()} in simulate mode which always rejects shuffle filters, so we
 *       compute the position list ourselves.</li>
 *   <li>{@code testBreakerTarget(MovementContext, BlockPos, int)} — refuses to break blocks
 *       that are in the filter (would un-pave fresh placements).</li>
 * </ul>
 */
@Mixin(value = RollerMovementBehaviour.class, remap = false)
public class MixinRollerMovementBehaviour {

    /**
     * Cached {@code RollerMovementBehaviour.PaveResult} values (private enum, resolved once).
     * Order on Create 6.0.8: FAIL=0, PASS=1, SUCCESS=2.
     */
    @Unique
    private static final Enum<?>[] createshufflefilter$PAVE_RESULT_VALUES = createshufflefilter$resolvePaveResultValues();

    @Unique
    private static Enum<?>[] createshufflefilter$resolvePaveResultValues() {
        try {
            Class<?> paveResultClass = Class.forName("com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour$PaveResult");
            return (Enum<?>[]) paveResultClass.getMethod("values").invoke(null);
        } catch (Exception e) {
            CreateShuffleFilter.LOGGER.error(
                "Failed to resolve RollerMovementBehaviour$PaveResult - skip and place results will be no-ops", e);
            return null;
        }
    }

    @Unique
    private static Enum<?> createshufflefilter$paveResult(int index) {
        return createshufflefilter$PAVE_RESULT_VALUES != null ? createshufflefilter$PAVE_RESULT_VALUES[index] : null;
    }

    @Shadow
    protected BlockState getStateToPaveWith(MovementContext context) {
        throw new AssertionError();
    }

    @Shadow
    protected boolean testBreakerTarget(MovementContext context, BlockPos target, int columnY) {
        throw new AssertionError();
    }

    @Unique
    private ItemStack createshufflefilter$lastSelectedBlock = ItemStack.EMPTY;

    @Unique
    private boolean createshufflefilter$slabAvailable = false;

    /**
     * True when the last {@code getStateToPaveWith} rolled the Skip marker: place nothing
     * and don't break the ground block. Reset on every selection.
     */
    @Unique
    private boolean createshufflefilter$intentionalSkip = false;

    @Inject(
        method = "getStateToPaveWith(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilter(MovementContext context, CallbackInfoReturnable<BlockState> cir) {
        Level world = context.world;
        if (world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return;
        }

        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        if (blockList.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }

        boolean useWeighted = filterItem instanceof WeightedShuffleFilterItem;

        IItemHandler inv = context.contraption.getStorage().getAllItems();
        if (inv == null) {
            cir.setReturnValue(null);
            return;
        }

        BlockPos currentPos = BlockPos.containing(context.position.x, context.position.y, context.position.z);
        createshufflefilter$slabAvailable = false;
        createshufflefilter$intentionalSkip = false;

        ShuffleFilterUtil.SelectionResult selection = RollerSelectionUtil.selectBlockForPosition(blockList, useWeighted, currentPos, world, inv);
        if (selection.isSkip()) {
            createshufflefilter$intentionalSkip = true;
            createshufflefilter$lastSelectedBlock = ItemStack.EMPTY;
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }

        ItemStack selected = selection.stack();
        if (selected.isEmpty()) {
            createshufflefilter$lastSelectedBlock = ItemStack.EMPTY;
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }

        createshufflefilter$lastSelectedBlock = selected.copy();

        if (selected.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (state.hasProperty(SlabBlock.TYPE)) {
                state = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
            }
            cir.setReturnValue(state);
        } else {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
        method = "getStateToPaveWithAsSlab",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilterSlab(MovementContext context, CallbackInfoReturnable<BlockState> cir) {
        if (context.world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return;
        }

        if (createshufflefilter$intentionalSkip) {
            createshufflefilter$slabAvailable = false;
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }

        if (createshufflefilter$lastSelectedBlock.isEmpty()) {
            return;
        }
        if (!(createshufflefilter$lastSelectedBlock.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        Block fullBlock = blockItem.getBlock();
        BlockState blockState = fullBlock.defaultBlockState();

        if (blockState.hasProperty(SlabBlock.TYPE)) {
            createshufflefilter$slabAvailable = true;
            cir.setReturnValue(blockState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            return;
        }

        IItemHandler inv = context.contraption.getStorage().getAllItems();

        ItemStack slabStack = RollerSelectionUtil.findSlabVariantInInventory(fullBlock, inv);
        if (!slabStack.isEmpty() && slabStack.getItem() instanceof BlockItem slabBlockItem) {
            BlockState slabState = slabBlockItem.getBlock().defaultBlockState();
            if (slabState.hasProperty(SlabBlock.TYPE)) {
                createshufflefilter$slabAvailable = true;
                cir.setReturnValue(slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                return;
            }
        }

        Block slabBlock = RollerSelectionUtil.findSlabBlockForFullBlock(fullBlock);
        if (slabBlock != null) {
            createshufflefilter$slabAvailable = true;
            cir.setReturnValue(slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            return;
        }

        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        ItemStack alternateWithSlab = RollerSelectionUtil.findAlternateBlockWithSlab(blockList, inv, context.world);
        if (!alternateWithSlab.isEmpty() && alternateWithSlab.getItem() instanceof BlockItem altBlockItem) {
            Block altFullBlock = altBlockItem.getBlock();
            BlockState altBlockState = altFullBlock.defaultBlockState();

            if (altBlockState.hasProperty(SlabBlock.TYPE)) {
                createshufflefilter$slabAvailable = true;
                createshufflefilter$lastSelectedBlock = alternateWithSlab.copy();
                cir.setReturnValue(altBlockState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                return;
            }

            Block altSlabBlock = RollerSelectionUtil.findSlabBlockForFullBlock(altFullBlock);
            if (altSlabBlock != null) {
                createshufflefilter$slabAvailable = true;
                createshufflefilter$lastSelectedBlock = alternateWithSlab.copy();
                cir.setReturnValue(altSlabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                return;
            }
        }

        createshufflefilter$slabAvailable = false;
    }

    @Inject(
        method = "tryFill",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilterExtraction(
        MovementContext context,
        BlockPos targetPos,
        BlockState toPlace,
        CallbackInfoReturnable<Enum<?>> cir
    ) {
        if (context.world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return;
        }

        // Intentional skip: position handled, nothing placed, no extraction, no fallback.
        if (createshufflefilter$intentionalSkip) {
            Enum<?> pass = createshufflefilter$paveResult(1); // PASS
            if (pass != null) cir.setReturnValue(pass);
            return;
        }

        Level level = context.world;
        if (!level.isLoaded(targetPos)) {
            return;
        }

        IItemHandler inv = context.contraption.getStorage().getAllItems();
        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        boolean useWeighted = filterItem instanceof WeightedShuffleFilterItem;

        // Pure per-position decision (re-roll, slab-pass handling, idempotency) lives in
        // RollerSelectionUtil so it can be unit-tested without a moving contraption.
        BlockState existing = level.getBlockState(targetPos);
        RollerSelectionUtil.FillDecision decision = RollerSelectionUtil.decideFill(
            blockList, useWeighted, targetPos, toPlace, createshufflefilter$lastSelectedBlock, level, inv, existing);

        if (decision.outcome() != RollerSelectionUtil.FillOutcome.PLACE) {
            // PASS_SKIP (per-position hole) or PASS_ALREADY_FILLED (idempotent re-visit).
            Enum<?> pass = createshufflefilter$paveResult(1); // PASS
            if (pass != null) cir.setReturnValue(pass);
            return;
        }

        ItemStack toExtract = decision.toExtract();
        toPlace = decision.toPlace();
        boolean slabPass = toPlace.hasProperty(SlabBlock.TYPE)
            && toPlace.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;

        if (!existing.is(BlockTags.LEAVES) && !existing.canBeReplaced()
            && (!existing.getCollisionShape(level, targetPos).isEmpty()
                || existing.is(BlockTags.PORTALS))) {
            return;
        }

        if (toExtract.isEmpty()) {
            return;
        }

        // Slab paving pass: prefer a real slab item from inventory if one exists.
        if (slabPass && toExtract.getItem() instanceof BlockItem blockItem) {
            ItemStack slabStack = RollerSelectionUtil.findSlabVariantInInventory(blockItem.getBlock(), inv);
            if (!slabStack.isEmpty()) {
                toExtract = slabStack;
            }
        }

        ItemStack held = RollerSelectionUtil.extractBlockFromCascadingFilter(toExtract, targetPos, level, inv, 0);

        if (held.isEmpty()) {
            // The selected material ran out this tick - fall back to any available filter block.
            ShuffleFilterUtil.SelectionResult fallback = ShuffleFilterUtil.selectItemCascading(
                blockList,
                useWeighted,
                level,
                inv,
                0,
                new HashSet<>()
            );

            // Re-roll landed on Skip: treat the position as intentionally skipped.
            if (fallback.isSkip()) {
                Enum<?> pass = createshufflefilter$paveResult(1); // PASS
                if (pass != null) cir.setReturnValue(pass);
                return;
            }

            if (fallback.stack().isEmpty()) {
                return;
            }

            held = fallback.stack();
            if (held.getItem() instanceof BlockItem fallbackBlockItem) {
                BlockState originalToPlace = toPlace;
                toPlace = fallbackBlockItem.getBlock().defaultBlockState();

                if (originalToPlace.hasProperty(SlabBlock.TYPE) && toPlace.hasProperty(SlabBlock.TYPE)) {
                    SlabType originalType = originalToPlace.getValue(SlabBlock.TYPE);
                    toPlace = toPlace.setValue(SlabBlock.TYPE, originalType);
                } else if (toPlace.hasProperty(SlabBlock.TYPE)) {
                    toPlace = toPlace.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                }
            }
        }

        if (toPlace.hasProperty(SlabBlock.TYPE)) {
            SlabType neededType = toPlace.getValue(SlabBlock.TYPE);
            if (neededType != SlabType.DOUBLE && !createshufflefilter$slabAvailable) {
                return;
            }
        }

        level.setBlockAndUpdate(targetPos, toPlace);

        Enum<?> success = createshufflefilter$paveResult(2); // SUCCESS
        if (success != null) cir.setReturnValue(success);
    }

    @Inject(
        method = "getPositionsToBreak",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilterBreaking(
        MovementContext context,
        BlockPos visitedPos,
        CallbackInfoReturnable<List<BlockPos>> cir
    ) {
        if (context.world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return;
        }

        ArrayList<BlockPos> positions = new ArrayList<>();

        int scrollValue = context.blockEntityData.getInt("ScrollValue");

        BlockState stateToPaveWith = this.getStateToPaveWith(context);
        int startingY = 1;
        if (!stateToPaveWith.isAir() && !createshufflefilter$lastSelectedBlock.isEmpty()) {
            startingY = 0;
        }

        // RollingMode enum: PAVE=0, FILL=1, WIDE_FILL=2, SLOPE=3, TUNNEL_PAVE=4
        if (scrollValue == 4) {
            for (int i = startingY; i <= 2; i++) {
                BlockPos target = visitedPos.above(i);
                if (this.testBreakerTarget(context, target, i)) {
                    positions.add(target);
                }
            }
        } else if (scrollValue == 0) {
            if (startingY == 0 && this.testBreakerTarget(context, visitedPos, 0)) {
                positions.add(visitedPos);
            }
        }

        cir.setReturnValue(positions);
    }

    @Inject(
        method = "testBreakerTarget",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void skipBreakingFilterBlocks(
        MovementContext context,
        BlockPos target,
        int columnY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (context.world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return;
        }

        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        BlockState stateAtTarget = context.world.getBlockState(target);

        if (stateAtTarget.is(BlockTags.RAILS)) {
            cir.setReturnValue(false);
            return;
        }

        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            ItemStack entryStack = entry.getItemStack();
            if (entryStack.getItem() instanceof BlockItem blockItem) {
                if (stateAtTarget.is(blockItem.getBlock())) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
