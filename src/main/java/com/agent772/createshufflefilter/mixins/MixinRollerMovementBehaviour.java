package com.agent772.createshufflefilter.mixins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

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

        ItemStack selected = selectBlockForPosition(blockList, useWeighted, currentPos, world, inv);
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

        ItemStack slabStack = findSlabVariantInInventory(fullBlock, inv);
        if (!slabStack.isEmpty() && slabStack.getItem() instanceof BlockItem slabBlockItem) {
            BlockState slabState = slabBlockItem.getBlock().defaultBlockState();
            if (slabState.hasProperty(SlabBlock.TYPE)) {
                createshufflefilter$slabAvailable = true;
                cir.setReturnValue(slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                return;
            }
        }

        Block slabBlock = findSlabBlockForFullBlock(fullBlock);
        if (slabBlock != null) {
            createshufflefilter$slabAvailable = true;
            cir.setReturnValue(slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            return;
        }

        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        ItemStack alternateWithSlab = findAlternateBlockWithSlab(blockList, inv, context.world);
        if (!alternateWithSlab.isEmpty() && alternateWithSlab.getItem() instanceof BlockItem altBlockItem) {
            Block altFullBlock = altBlockItem.getBlock();
            BlockState altBlockState = altFullBlock.defaultBlockState();

            if (altBlockState.hasProperty(SlabBlock.TYPE)) {
                createshufflefilter$slabAvailable = true;
                createshufflefilter$lastSelectedBlock = alternateWithSlab.copy();
                cir.setReturnValue(altBlockState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                return;
            }

            Block altSlabBlock = findSlabBlockForFullBlock(altFullBlock);
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

        Level level = context.world;
        if (!level.isLoaded(targetPos)) {
            return;
        }

        BlockState existing = level.getBlockState(targetPos);
        if (existing.is(toPlace.getBlock())) {
            return;
        }

        if (!existing.is(BlockTags.LEAVES) && !existing.canBeReplaced()
            && (!existing.getCollisionShape(level, targetPos).isEmpty()
                || existing.is(BlockTags.PORTALS))) {
            return;
        }

        if (createshufflefilter$lastSelectedBlock.isEmpty()) {
            return;
        }

        ItemStack toExtract = createshufflefilter$lastSelectedBlock;

        IItemHandler inv = context.contraption.getStorage().getAllItems();

        if (toPlace.hasProperty(SlabBlock.TYPE) && toPlace.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
            if (createshufflefilter$lastSelectedBlock.getItem() instanceof BlockItem blockItem) {
                Block fullBlock = blockItem.getBlock();
                ItemStack slabStack = findSlabVariantInInventory(fullBlock, inv);
                if (!slabStack.isEmpty()) {
                    toExtract = slabStack;
                }
            }
        }

        ItemStack held = extractBlockFromCascadingFilter(toExtract, targetPos, level, inv, 0);
        CreateShuffleFilter.LOGGER.info("Extracted: {}", held);

        if (held.isEmpty()) {
            CreateShuffleFilter.LOGGER.info("Extraction failed! Trying fallback...");

            ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
            ItemStack fallbackResult = ShuffleFilterUtil.selectItemCascading(
                blockList,
                filterItem instanceof WeightedShuffleFilterItem,
                level,
                inv,
                0,
                new HashSet<>()
            );

            if (fallbackResult.isEmpty()) {
                return;
            }

            held = fallbackResult;
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

        try {
            Class<?> paveResultClass = Class.forName("com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour$PaveResult");
            Object[] values = (Object[]) paveResultClass.getMethod("values").invoke(null);
            // PaveResult enum order on 1.20.1: FAIL=0, PASS=1, SUCCESS=2
            cir.setReturnValue((Enum<?>) values[2]);
        } catch (Exception e) {
            CreateShuffleFilter.LOGGER.error("Failed to return PaveResult.SUCCESS", e);
        }
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

    @Unique
    private static ItemStack selectBlockForPosition(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos pos,
            Level world,
            IItemHandler inv) {
        return selectBlockForPositionWithDepth(blockList, useWeighted, pos, world, inv, 0);
    }

    @Unique
    private static ItemStack selectBlockForPositionWithDepth(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos pos,
            Level world,
            IItemHandler inv,
            int depth) {

        if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) {
            return ItemStack.EMPTY;
        }

        List<ShuffleBlockList.BlockEntry> availableBlocks = getAvailableBlocks(blockList, inv);
        if (availableBlocks.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ShuffleBlockList filteredList = new ShuffleBlockList(availableBlocks);

        // Stafford variant 13 bit-mixing so adjacent positions produce uncorrelated seeds.
        long seed = pos.asLong();
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        seed = seed ^ (seed >>> 31);
        Random posRandom = new Random(seed);

        ShuffleBlockList.BlockEntry selectedEntry;
        if (useWeighted) {
            // Compute the sum of weights across the *filtered* list. The configured weights
            // are normalized to 1.0 when all blocks are present, but `availableBlocks` may
            // drop entries whose item isn't in the contraption inventory — in that case the
            // sum is less than 1.0, and a naïve `nextFloat()` draw leaks the residual
            // probability mass into the last entry. Multiply the draw by `totalWeight` so
            // the remaining entries keep their relative proportions.
            float totalWeight = 0.0f;
            for (ShuffleBlockList.BlockEntry entry : filteredList.blocks()) {
                totalWeight += entry.weight();
            }

            selectedEntry = null;
            if (totalWeight <= 0.0f) {
                selectedEntry = filteredList.blocks().get(posRandom.nextInt(filteredList.size()));
            } else {
                float random = posRandom.nextFloat() * totalWeight;
                float accumulated = 0.0f;
                for (ShuffleBlockList.BlockEntry entry : filteredList.blocks()) {
                    accumulated += entry.weight();
                    if (random < accumulated) {
                        selectedEntry = entry;
                        break;
                    }
                }
                if (selectedEntry == null) {
                    selectedEntry = filteredList.blocks().get(filteredList.size() - 1);
                }
            }
        } else {
            int index = posRandom.nextInt(filteredList.size());
            selectedEntry = filteredList.blocks().get(index);
        }

        if (selectedEntry == null) {
            return ItemStack.EMPTY;
        }

        ItemStack configured = selectedEntry.getItemStack();
        if (configured.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Item item = configured.getItem();

        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = ShuffleBlockList.read(configured);
            if (nestedList.isEmpty()) {
                return ItemStack.EMPTY;
            }
            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            return selectBlockForPositionWithDepth(nestedList, nestedWeighted, pos, world, inv, depth + 1);
        }

        return configured;
    }

    @Unique
    private static List<ShuffleBlockList.BlockEntry> getAvailableBlocks(
            ShuffleBlockList blockList,
            IItemHandler inv) {
        List<ShuffleBlockList.BlockEntry> available = new ArrayList<>();
        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            ItemStack stack = entry.getItemStack();
            if (stack.isEmpty()) continue;
            if (isEntryAvailable(stack, inv, 0)) {
                available.add(entry);
            }
        }
        return available;
    }

    @Unique
    private static boolean isEntryAvailable(ItemStack configuredStack, IItemHandler inv, int depth) {
        if (configuredStack.isEmpty()) return false;
        if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) return false;

        Item item = configuredStack.getItem();

        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = ShuffleBlockList.read(configuredStack);
            if (nestedList.isEmpty()) return false;
            for (ShuffleBlockList.BlockEntry nestedEntry : nestedList.blocks()) {
                if (isEntryAvailable(nestedEntry.getItemStack(), inv, depth + 1)) {
                    return true;
                }
            }
            return false;
        }

        if (item instanceof FilterItem) {
            FilterItemStack filterStack = FilterItemStack.of(configuredStack);
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stackInSlot = inv.getStackInSlot(slot);
                if (!stackInSlot.isEmpty() && filterStack.test(null, stackInSlot)) {
                    return true;
                }
            }
            return false;
        }

        return hasItemInInventory(item, inv);
    }

    @Unique
    private static boolean hasItemInInventory(Item item, IItemHandler inv) {
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stackInSlot = inv.getStackInSlot(slot);
            if (!stackInSlot.isEmpty() && stackInSlot.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static ItemStack findSlabVariantInInventory(Block fullBlock, IItemHandler inv) {
        Block slabBlock = findSlabBlockForFullBlock(fullBlock);
        if (slabBlock == null) {
            return ItemStack.EMPTY;
        }
        Item slabItem = slabBlock.asItem();
        if (hasItemInInventory(slabItem, inv)) {
            return new ItemStack(slabItem);
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private static ItemStack findAlternateBlockWithSlab(
            ShuffleBlockList blockList,
            IItemHandler inv,
            Level world) {
        if (blockList.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            ItemStack configured = entry.getItemStack();
            if (configured.isEmpty()) {
                continue;
            }

            if (configured.getItem() instanceof BaseShuffleFilterItem) {
                ShuffleBlockList nestedList = ShuffleBlockList.read(configured);
                ItemStack nestedResult = findAlternateBlockWithSlab(nestedList, inv, world);
                if (!nestedResult.isEmpty()) {
                    return nestedResult;
                }
                continue;
            }

            if (configured.getItem() instanceof FilterItem) {
                FilterItemStack filterStack = FilterItemStack.of(configured);
                for (int slot = 0; slot < inv.getSlots(); slot++) {
                    ItemStack stackInSlot = inv.getStackInSlot(slot);
                    if (!stackInSlot.isEmpty() && filterStack.test(world, stackInSlot)) {
                        if (stackInSlot.getItem() instanceof BlockItem blockItem) {
                            Block block = blockItem.getBlock();
                            BlockState blockState = block.defaultBlockState();
                            if (blockState.hasProperty(SlabBlock.TYPE)) {
                                return stackInSlot.copy();
                            }
                            if (findSlabBlockForFullBlock(block) != null) {
                                return stackInSlot.copy();
                            }
                        }
                    }
                }
                continue;
            }

            if (configured.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                BlockState blockState = block.defaultBlockState();
                if (!isEntryAvailable(configured, inv, 0)) {
                    continue;
                }
                if (blockState.hasProperty(SlabBlock.TYPE)) {
                    return configured.copy();
                }
                if (findSlabBlockForFullBlock(block) != null) {
                    return configured.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private static Block findSlabBlockForFullBlock(Block fullBlock) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(fullBlock);
        if (blockId == null) return null;

        String namespace = blockId.getNamespace();
        String path = blockId.getPath();
        int pathLength = path.length();

        List<String> slabCandidates = new ArrayList<>();
        slabCandidates.add(path + "_slab");
        if (path.endsWith("s") && pathLength > 1) {
            slabCandidates.add(path.substring(0, pathLength - 1) + "_slab");
        }
        if (path.endsWith("planks") && pathLength > 7) {
            slabCandidates.add(path.substring(0, pathLength - 7) + "_slab");
        }

        for (String candidate : slabCandidates) {
            ResourceLocation slabId = new ResourceLocation(namespace, candidate);
            Optional<Block> slabBlock = Optional.ofNullable(ForgeRegistries.BLOCKS.getValue(slabId));
            if (slabBlock.isPresent() && slabBlock.get() != Blocks.AIR) {
                return slabBlock.get();
            }
        }
        return null;
    }

    @Unique
    private static ItemStack extractBlockFromCascadingFilter(
            ItemStack configuredStack,
            BlockPos pos,
            Level world,
            IItemHandler inv,
            int depth) {

        if (configuredStack.isEmpty()) return ItemStack.EMPTY;

        Item item = configuredStack.getItem();

        if (item instanceof BaseShuffleFilterItem) {
            CreateShuffleFilter.LOGGER.warn(
                "Unexpected shuffle filter in extraction at depth {}! This should have been resolved during selection.",
                depth
            );

            if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) {
                return ItemStack.EMPTY;
            }

            ShuffleBlockList nestedList = ShuffleBlockList.read(configuredStack);
            if (nestedList.isEmpty()) {
                return ItemStack.EMPTY;
            }

            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            ItemStack nestedSelected = selectBlockForPosition(nestedList, nestedWeighted, pos, world, inv);
            return extractBlockFromCascadingFilter(nestedSelected, pos, world, inv, depth + 1);
        }

        if (item instanceof FilterItem) {
            FilterItemStack filterStack = FilterItemStack.of(configuredStack);
            return ItemHelper.extract(inv, stack -> filterStack.test(world, stack), 1, false);
        }

        return ItemHelper.extract(inv, stack -> stack.getItem() == item, 1, false);
    }
}
