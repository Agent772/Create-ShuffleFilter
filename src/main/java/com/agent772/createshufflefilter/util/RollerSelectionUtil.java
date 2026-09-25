package com.agent772.createshufflefilter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.SkipItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil.SelectionResult;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 * Roller selection helpers used by {@code MixinRollerMovementBehaviour}. Kept outside the
 * mixin so they can be exercised directly by GameTests.
 */
public final class RollerSelectionUtil {

    private RollerSelectionUtil() {}

    public static SelectionResult selectBlockForPosition(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos pos,
            Level world,
            IItemHandler inv) {
        // Build the position-seeded Random once at the top level and thread it through the
        // recursion. Re-creating `new Random(seed)` at each cascade level (the original
        // pattern) made the outer and inner draws share the same state₁ bits — outer's
        // `nextFloat()` and inner's `nextInt(small_n)` both read the high bits of the same
        // advanced state, so the inner pick was deterministically tied to which range of
        // [0, totalWeight) the outer pick landed in. Threading the same Random instance
        // makes every recursive draw consume fresh state and be statistically independent.
        long seed = pos.asLong();
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        seed = seed ^ (seed >>> 31);
        return selectBlockForPositionWithDepth(blockList, useWeighted, pos, world, inv, 0, new Random(seed));
    }

    private static SelectionResult selectBlockForPositionWithDepth(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos pos,
            Level world,
            IItemHandler inv,
            int depth,
            Random posRandom) {

        if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) {
            return SelectionResult.NONE;
        }

        List<ShuffleBlockList.BlockEntry> availableBlocks = getAvailableBlocks(blockList, inv);
        if (availableBlocks.isEmpty()) {
            return SelectionResult.NONE;
        }

        ShuffleBlockList filteredList = new ShuffleBlockList(availableBlocks);

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
            return SelectionResult.NONE;
        }

        if (ShuffleFilterUtil.isSkipEntry(selectedEntry)) {
            return SelectionResult.SKIP;
        }

        ItemStack configured = selectedEntry.getItemStack();
        if (configured.isEmpty()) {
            return SelectionResult.NONE;
        }

        Item item = configured.getItem();

        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = ShuffleBlockList.read(configured);
            if (nestedList.isEmpty()) {
                return SelectionResult.NONE;
            }
            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            return selectBlockForPositionWithDepth(nestedList, nestedWeighted, pos, world, inv, depth + 1, posRandom);
        }

        return SelectionResult.of(configured);
    }

    /**
     * True when the block at a position is one of the filter's (top-level) block entries.
     * Used to leave already-placed filter blocks untouched on re-visits, mirroring the
     * check in {@code MixinRollerMovementBehaviour#skipBreakingFilterBlocks}. Skip entries
     * carry no block and never match.
     */
    public static boolean isFilterBlock(BlockState state, ShuffleBlockList blockList) {
        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            if (ShuffleFilterUtil.isSkipEntry(entry)) continue;
            ItemStack entryStack = entry.getItemStack();
            if (entryStack.getItem() instanceof BlockItem blockItem && state.is(blockItem.getBlock())) {
                return true;
            }
        }
        return false;
    }

    /** Outcome of {@link #decideFill}. */
    public enum FillOutcome {
        /** Per-position Skip roll: leave a hole here, the column keeps descending. */
        PASS_SKIP,
        /** Position already holds a filter block: idempotent re-visit, place nothing. */
        PASS_ALREADY_FILLED,
        /** Place {@link FillDecision#toExtract()} as {@link FillDecision#toPlace()}. */
        PLACE
    }

    /** Result of the pure per-position fill decision (no extraction / world writes). */
    public static final class FillDecision {
        private final FillOutcome outcome;
        private final ItemStack toExtract;
        private final BlockState toPlace;

        private FillDecision(FillOutcome outcome, ItemStack toExtract, BlockState toPlace) {
            this.outcome = outcome;
            this.toExtract = toExtract;
            this.toPlace = toPlace;
        }

        public FillOutcome outcome() { return outcome; }
        public ItemStack toExtract() { return toExtract; }
        public BlockState toPlace() { return toPlace; }
    }

    /**
     * Pure decision for one {@code tryFill} position, factored out of the mixin so it can be
     * unit-tested without a moving contraption. Mirrors the head of
     * {@code MixinRollerMovementBehaviour#handleShuffleFilterExtraction}:
     *
     * <ul>
     *   <li>Non-slab pass: re-roll the block seeded by {@code targetPos} (real Y included), so
     *       every position is an independent, position-deterministic draw instead of inheriting
     *       {@code lastSelected} for the whole column. A Skip roll returns {@link FillOutcome#PASS_SKIP}.</li>
     *   <li>Slab pass ({@code BOTTOM}/{@code TOP}): keep {@code lastSelected} and {@code toPlace}
     *       (the train-track paving pass, resolved earlier in {@code getStateToPaveWithAsSlab}).</li>
     *   <li>If the position already holds the target block or any filter block, return
     *       {@link FillOutcome#PASS_ALREADY_FILLED} so re-visits are idempotent.</li>
     * </ul>
     *
     * Extraction, the replaceable check, fallback and slab-availability stay in the mixin.
     */
    public static FillDecision decideFill(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos targetPos,
            BlockState toPlace,
            ItemStack lastSelected,
            Level level,
            IItemHandler inv,
            BlockState existing) {

        boolean slabPass = toPlace.hasProperty(SlabBlock.TYPE)
            && toPlace.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;

        ItemStack toExtract = lastSelected;

        if (!slabPass) {
            SelectionResult reroll = selectBlockForPosition(blockList, useWeighted, targetPos, level, inv);
            if (reroll.isSkip()) {
                return new FillDecision(FillOutcome.PASS_SKIP, ItemStack.EMPTY, toPlace);
            }
            if (!reroll.stack().isEmpty()) {
                toExtract = reroll.stack();
                if (toExtract.getItem() instanceof BlockItem blockItem) {
                    BlockState state = blockItem.getBlock().defaultBlockState();
                    if (state.hasProperty(SlabBlock.TYPE)) {
                        state = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                    }
                    toPlace = state;
                }
            }
        }

        if (existing.is(toPlace.getBlock()) || isFilterBlock(existing, blockList)) {
            return new FillDecision(FillOutcome.PASS_ALREADY_FILLED, toExtract, toPlace);
        }

        return new FillDecision(FillOutcome.PLACE, toExtract, toPlace);
    }

    public static List<ShuffleBlockList.BlockEntry> getAvailableBlocks(
            ShuffleBlockList blockList,
            IItemHandler inv) {
        List<ShuffleBlockList.BlockEntry> available = new ArrayList<>();
        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            // Skip marker is always available - no inventory item needed to do nothing.
            if (ShuffleFilterUtil.isSkipEntry(entry)) {
                available.add(entry);
                continue;
            }
            ItemStack stack = entry.getItemStack();
            if (stack.isEmpty()) continue;
            if (isEntryAvailable(stack, inv, 0)) {
                available.add(entry);
            }
        }
        return available;
    }

    public static boolean isEntryAvailable(ItemStack configuredStack, IItemHandler inv, int depth) {
        if (configuredStack.isEmpty()) return false;
        if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) return false;

        Item item = configuredStack.getItem();

        if (item instanceof SkipItem) {
            return true;
        }

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

    private static boolean hasItemInInventory(Item item, IItemHandler inv) {
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stackInSlot = inv.getStackInSlot(slot);
            if (!stackInSlot.isEmpty() && stackInSlot.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack findSlabVariantInInventory(Block fullBlock, IItemHandler inv) {
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

    public static ItemStack findAlternateBlockWithSlab(
            ShuffleBlockList blockList,
            IItemHandler inv,
            Level world) {
        if (blockList.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            // Skip marker never contributes a slab variant.
            if (ShuffleFilterUtil.isSkipEntry(entry)) {
                continue;
            }
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

    public static Block findSlabBlockForFullBlock(Block fullBlock) {
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

    public static ItemStack extractBlockFromCascadingFilter(
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
            SelectionResult nestedSelection = selectBlockForPosition(nestedList, nestedWeighted, pos, world, inv);
            if (nestedSelection.isSkip() || nestedSelection.stack().isEmpty()) {
                return ItemStack.EMPTY;
            }
            return extractBlockFromCascadingFilter(nestedSelection.stack(), pos, world, inv, depth + 1);
        }

        if (item instanceof FilterItem) {
            FilterItemStack filterStack = FilterItemStack.of(configuredStack);
            return ItemHelper.extract(inv, stack -> filterStack.test(world, stack), 1, false);
        }

        return ItemHelper.extract(inv, stack -> stack.getItem() == item, 1, false);
    }
}
