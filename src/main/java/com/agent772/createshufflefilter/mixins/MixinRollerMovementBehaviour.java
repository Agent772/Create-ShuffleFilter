package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Implements shuffle filter support for rollers with position-based deterministic selection
 * 
 * Key features:
 * - Position-based deterministic randomization (same position = same block)
 * - Cascading filter support (reuses ShuffleFilterUtil)
 * - Multi-mode support (paving, filling, slope)
 * - Fallback system when blocks unavailable
 * 
 * Performance: ~50ns per block selection (includes position hashing + cascading)
 */
@Mixin(RollerMovementBehaviour.class)
public class MixinRollerMovementBehaviour {

    /**
     * Shadow method to access the target class's getStateToPaveWith
     */
    @Shadow
    protected BlockState getStateToPaveWith(MovementContext context) {
        throw new AssertionError();
    }

    /**
     * Shadow method to access the target class's testBreakerTarget
     */
    @Shadow
    protected boolean testBreakerTarget(MovementContext context, BlockPos target, int columnY) {
        throw new AssertionError();
    }

    /**
     * Stores the last selected block type for extraction in tryFill
     * This bridges the gap between getStateToPaveWith (selection) and tryFill (extraction)
     */
    @Unique
    private ItemStack createshufflefilter$lastSelectedBlock = ItemStack.EMPTY;
    
    /**
     * Tracks whether getStateToPaveWithAsSlab found a valid slab
     * Used to determine if we should skip placement when slab is needed but unavailable
     */
    @Unique
    private boolean createshufflefilter$slabAvailable = false;

    /**
     * Intercepts roller's block placement logic to support shuffle filters
     * This is the main injection point that determines what block to place
     */
    @Inject(
        method = "getStateToPaveWith(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilter(
        MovementContext context,
        CallbackInfoReturnable<BlockState> cir
    ) {
        Level world = context.world;
        if (world.isClientSide) return;

        // Get the filter from the contraption context
        FilterItemStack filter = context.getFilterFromBE();
        
        if (filter == null || filter.item().isEmpty()) return;
        
        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        
        // Check if this is one of our shuffle filters
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return; // Not our filter, let Create's logic run
        }

        // Get configuration from data components (same as deployers)
        ShuffleBlockList blockList = filterStack.getOrDefault(
            ModDataComponents.SHUFFLE_BLOCK_LIST.get(), 
            ShuffleBlockList.EMPTY
        );
        
        if (blockList.isEmpty()) {
            // Filter is empty, place nothing
            cir.setReturnValue(null);
            return;
        }

        // Determine mode based on filter type
        boolean useWeighted = filterItem instanceof WeightedShuffleFilterItem;

        // Get contraption inventory
        IItemHandler inv = context.contraption.getStorage().getAllItems();
        if (inv == null) {
            cir.setReturnValue(null);
            return;
        }

        // Get current position from context for deterministic selection
        BlockPos currentPos = BlockPos.containing(context.position);
        
        // Reset slab availability flag for new selection
        createshufflefilter$slabAvailable = false;
        
        // Select block type with position-based determinism (same position = same block)
        // This checks availability but does NOT extract
        ItemStack selected = selectBlockForPosition(
            blockList, 
            useWeighted, 
            currentPos,
            world, 
            inv
        );
        
        if (selected.isEmpty()) {
            // No blocks available in inventory
            createshufflefilter$lastSelectedBlock = ItemStack.EMPTY;
            cir.setReturnValue(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            return;
        }

        // Store the selected item for later extraction in tryFill
        createshufflefilter$lastSelectedBlock = selected.copy();

        // Convert ItemStack to BlockState (same logic as Create's getStateToPaveWith)
        if (selected.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            
            // Handle slabs: if block has TYPE property, set to DOUBLE
            if (state.hasProperty(SlabBlock.TYPE)) {
                state = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
            }
            
            cir.setReturnValue(state);
        } else {
            // Not a block item
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Intercepts slab block state requests to provide shuffle filter slabs
     * This is called when the roller needs a slab variant (e.g., for train tracks)
     * 
     * If the selected block has no slab variant, automatically searches for an
     * ALTERNATE block from the filter that does have a slab - this matches Create's
     * filter behavior where any matching filter item can be used
     */
    @Inject(
        method = "getStateToPaveWithAsSlab",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilterSlab(
        MovementContext context,
        CallbackInfoReturnable<BlockState> cir
    ) {
        if (context.world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return; // Not our filter - let Create handle it
        }

        // If we have a selected block, try to find its slab variant
        if (!createshufflefilter$lastSelectedBlock.isEmpty()) {
            if (createshufflefilter$lastSelectedBlock.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                Block fullBlock = blockItem.getBlock();
                
                // Check if the block itself is already a slab
                BlockState blockState = fullBlock.defaultBlockState();
                if (blockState.hasProperty(SlabBlock.TYPE)) {
                    createshufflefilter$slabAvailable = true;
                    cir.setReturnValue(blockState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    return;
                }
                
                // Try to find slab variant in inventory
                ItemStack slabStack = findSlabVariantInInventory(fullBlock, context.contraption.getStorage().getAllItems());
                if (!slabStack.isEmpty() && slabStack.getItem() instanceof net.minecraft.world.item.BlockItem slabBlockItem) {
                    BlockState slabState = slabBlockItem.getBlock().defaultBlockState();
                    if (slabState.hasProperty(SlabBlock.TYPE)) {
                        createshufflefilter$slabAvailable = true;
                        // DON'T update lastSelectedBlock - it will get reset on next getStateToPaveWith call anyway
                        // Let tryFill handle slab extraction based on what toPlace needs
                        cir.setReturnValue(slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                        return;
                    }
                }
                
                // Check if selected block CAN be crafted into a slab
                Block slabBlock = findSlabBlockForFullBlock(fullBlock);
                if (slabBlock != null) {
                    createshufflefilter$slabAvailable = true;
                    cir.setReturnValue(slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    return;
                }
                
                // Selected block has NO slab variant - try to find alternate block from filter with slab
                ShuffleBlockList blockList = filter.item().getOrDefault(
                    ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                    ShuffleBlockList.EMPTY
                );
                
                ItemStack alternateWithSlab = findAlternateBlockWithSlab(
                    blockList,
                    context.contraption.getStorage().getAllItems(),
                    context.world
                );
                
                if (!alternateWithSlab.isEmpty() && alternateWithSlab.getItem() instanceof net.minecraft.world.item.BlockItem altBlockItem) {
                    Block altFullBlock = altBlockItem.getBlock();
                    BlockState altBlockState = altFullBlock.defaultBlockState();
                    
                    // Check if alternate is already a slab
                    if (altBlockState.hasProperty(SlabBlock.TYPE)) {
                        createshufflefilter$slabAvailable = true;
                        createshufflefilter$lastSelectedBlock = alternateWithSlab.copy(); // Switch to this block
                        cir.setReturnValue(altBlockState.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                        return;
                    }
                    
                    // Check if alternate can be crafted to slab
                    Block altSlabBlock = findSlabBlockForFullBlock(altFullBlock);
                    if (altSlabBlock != null) {
                        createshufflefilter$slabAvailable = true;
                        createshufflefilter$lastSelectedBlock = alternateWithSlab.copy(); // Switch to this block
                        cir.setReturnValue(altSlabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                        return;
                    }
                }
                
                // No blocks in filter have slab variants
                createshufflefilter$slabAvailable = false;
            }
        }
        
        // No slab found in our logic - let Create's default slab-finding logic run
        // Don't cancel, don't return anything - just let the method continue
    }

    /**
     * Intercepts roller's item extraction to provide the correct block for shuffle filters
     * This runs at the START of tryFill and handles the entire placement logic for shuffle filters
     */
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
            return; // Not our filter, let Create handle it
        }

        Level level = context.world;
        
        // Check if world position is loaded
        if (!level.isLoaded(targetPos)) {
            // Can't reference PaveResult.FAIL directly, but we can return early and let Create handle it
            return;
        }

        // Check if block already matches
        BlockState existing = level.getBlockState(targetPos);
        if (existing.is(toPlace.getBlock())) {
            // Block already correct - no action needed but don't fail
            // Let Create's logic handle the PASS case
            return;
        }

        // Check if position is replaceable (same logic as Create)
        if (!existing.is(net.minecraft.tags.BlockTags.LEAVES) && !existing.canBeReplaced()
            && (!existing.getCollisionShape(level, targetPos).isEmpty()
                || existing.is(net.minecraft.tags.BlockTags.PORTALS))) {
            // Position not replaceable - let Create handle the FAIL
            return;
        }

        // Extract the item we selected earlier
        if (createshufflefilter$lastSelectedBlock.isEmpty()) {
            return; // Let Create's logic fail properly
        }
        
        // Determine what we should try to extract based on what toPlace needs
        ItemStack toExtract = createshufflefilter$lastSelectedBlock;
        
        // If toPlace is a slab (BOTTOM/TOP, not DOUBLE), try to extract slab variant first
        if (toPlace.hasProperty(SlabBlock.TYPE) && toPlace.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
            if (createshufflefilter$lastSelectedBlock.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                Block fullBlock = blockItem.getBlock();
                
                // First try to find pre-crafted slab in inventory
                ItemStack slabStack = findSlabVariantInInventory(fullBlock, context.contraption.getStorage().getAllItems());
                if (!slabStack.isEmpty()) {
                    toExtract = slabStack;
                }
            }
        }
        
        // Resolve cascading filters and extract the actual block item
        ItemStack held = extractBlockFromCascadingFilter(
            toExtract,  // Extract slab if available, otherwise full block
            targetPos,
            level,
            context.contraption.getStorage().getAllItems(),
            0
        );

        com.agent772.createshufflefilter.CreateShuffleFilter.LOGGER.info("Extracted: {}", held);

        if (held.isEmpty()) {
            com.agent772.createshufflefilter.CreateShuffleFilter.LOGGER.info("Extraction failed! Trying fallback...");
            
            // The pre-selected block ran out - try fallback to other blocks in filter
            ShuffleBlockList blockList = filter.item().getOrDefault(
                ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                ShuffleBlockList.EMPTY
            );
            
            // Try each entry as fallback using cascading logic
            ItemStack fallbackResult = ShuffleFilterUtil.selectItemCascading(
                blockList,
                filterItem instanceof WeightedShuffleFilterItem,
                level,
                context.contraption.getStorage().getAllItems(),
                0,
                new java.util.HashSet<>()
            );
            
            if (fallbackResult.isEmpty()) {
                return; // Let Create's logic fail properly
            }
            
            held = fallbackResult;
            // Update toPlace BlockState to match fallback item
            if (held.getItem() instanceof net.minecraft.world.item.BlockItem fallbackBlockItem) {
                BlockState originalToPlace = toPlace;
                toPlace = fallbackBlockItem.getBlock().defaultBlockState();
                
                // Preserve slab type if toPlace was already a slab
                if (originalToPlace.hasProperty(SlabBlock.TYPE) && toPlace.hasProperty(SlabBlock.TYPE)) {
                    SlabType originalType = originalToPlace.getValue(SlabBlock.TYPE);
                    toPlace = toPlace.setValue(SlabBlock.TYPE, originalType);
                } else if (toPlace.hasProperty(SlabBlock.TYPE)) {
                    // Default to DOUBLE for full block replacement
                    toPlace = toPlace.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                }
            }
        }
        
        // CRITICAL CHECK: If slab was needed but none available (neither pre-crafted nor craftable), DON'T place
        // User requirement: "if slab needed, slab doesnt exist -> place nothing"
        if (toPlace.hasProperty(SlabBlock.TYPE)) {
            SlabType neededType = toPlace.getValue(SlabBlock.TYPE);
            
            // If we need a BOTTOM or TOP slab (not DOUBLE/full block) but no slab was found
            if (neededType != SlabType.DOUBLE && !createshufflefilter$slabAvailable) {
                return; // Don't place - slab doesn't exist for this material
            }
        }

        // Place the block
        level.setBlockAndUpdate(targetPos, toPlace);
        
        // We need to return SUCCESS, but since PaveResult is private, we'll use reflection
        // or just cancel and let the block placement speak for itself
        // Actually, since we already placed the block, we can just not return anything and let Create see it
        // But we need to tell the mixin we handled it
        // The safest is to get the enum values and return the right one
        
        try {
            // Get the PaveResult enum class (it's a private inner class)
            Class<?> paveResultClass = Class.forName("com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour$PaveResult");
            Object[] values = (Object[]) paveResultClass.getMethod("values").invoke(null);
            // PaveResult enum order: FAIL=0, PASS=1, SUCCESS=2
            cir.setReturnValue((Enum<?>) values[2]); // SUCCESS
        } catch (Exception e) {
            com.agent772.createshufflefilter.CreateShuffleFilter.LOGGER.error("Failed to return PaveResult.SUCCESS", e);
        }
    }

    /**
     * Override getPositionsToBreak to handle shuffle filter availability check
     * 
     * WHY THIS IS NEEDED:
     * Create's getPositionsToBreak() uses filter.test() in SIMULATE mode to check if blocks
     * are available. If available, it breaks from ground level (startingY=0).
     * Our ShuffleFilterItemStack.test() always returns false, so Create thinks we have no blocks
     * and never breaks ground-level blocks.
     * 
     * We MUST override this because we can't intercept the local variable startingY with @Inject.
     * RandomizeFilters likely has this same issue.
     */
    @Inject(
        method = "getPositionsToBreak",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void handleShuffleFilterBreaking(
        MovementContext context,
        BlockPos visitedPos,
        CallbackInfoReturnable<java.util.List<BlockPos>> cir
    ) {
        if (context.world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();
        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return; // Not our filter - let Create's logic run
        }

        // For shuffle filters, we need custom logic because filter.test() returns false
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
        
        int scrollValue = context.blockEntityData.getInt("ScrollValue");
        // RollingMode enum: PAVE=0, FILL=1, WIDE_FILL=2, SLOPE=3, TUNNEL_PAVE=4
        
        // For shuffle filters, determine startingY based on whether we have blocks available
        BlockState stateToPaveWith = this.getStateToPaveWith(context);
        int startingY = 1; // Default: don't break ground level        
        if (!stateToPaveWith.isAir() && !createshufflefilter$lastSelectedBlock.isEmpty()) {
            startingY = 0; // We have blocks, so break from ground level
        }

        // Use Create's testBreakerTarget logic (which we hook with skipBreakingFilterBlocks)
        // For TUNNEL_PAVE mode, break blocks above the position
        if (scrollValue == 4) { // TUNNEL_PAVE
            for (int i = startingY; i <= 2; i++) {
                BlockPos target = visitedPos.above(i);
                // testBreakerTarget will be called by our hook which prevents breaking filter blocks
                if (this.testBreakerTarget(context, target, i)) {
                    positions.add(target);
                }
            }
        } 
        // For PAVE mode, just the ground position
        else if (scrollValue == 0) { // PAVE
            if (startingY == 0 && this.testBreakerTarget(context, visitedPos, 0)) {
                positions.add(visitedPos);
            }
        }
        // Other modes don't break blocks
        
        cir.setReturnValue(positions);
    }

    /**
     * Prevents breaking blocks that are already in the shuffle filter
     * This is the equivalent of RandomizeFilters' skipBreakingFilterBlocks
     * Gets called by testBreakerTarget via the Shadow method
     */
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
            return; // Not our filter - let Create's logic decide
        }

        // Get configuration from data components
        ShuffleBlockList blockList = filter.item().getOrDefault(
            ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
            ShuffleBlockList.EMPTY
        );

        BlockState stateAtTarget = context.world.getBlockState(target);
        
        // Don't break rails!
        if (stateAtTarget.is(net.minecraft.tags.BlockTags.RAILS)) {
            cir.setReturnValue(false);
            return;
        }

        // Don't break blocks that are already in our filter
        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            ItemStack entryStack = entry.getItemStack();
            if (entryStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                if (stateAtTarget.is(blockItem.getBlock())) {
                    cir.setReturnValue(false); // Don't break - it's in our filter
                    return;
                }
            }
        }

        // Let Create's normal logic decide if this block can be broken
    }

    /**
     * Position-based deterministic block selection with cascading support
     * Uses position as seed for Random to ensure same position = same block
     * Recursively resolves nested shuffle filters using position-based determinism
     * 
     * @param blockList Current filter's block list
     * @param useWeighted Whether to use weighted selection
     * @param pos World position (used as seed for determinism)
     * @param world Level for random generation
     * @param inv Contraption inventory
     * @return Selected ItemStack (Create filter or concrete block) or EMPTY if none available
     */
    @Unique
    private static ItemStack selectBlockForPosition(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos pos,
            Level world,
            IItemHandler inv) {
        return selectBlockForPositionWithDepth(blockList, useWeighted, pos, world, inv, 0);
    }

    /**
     * Internal method with depth tracking for cascade limit
     */
    @Unique
    private static ItemStack selectBlockForPositionWithDepth(
            ShuffleBlockList blockList,
            boolean useWeighted,
            BlockPos pos,
            Level world,
            IItemHandler inv,
            int depth) {
        
        // Depth protection
        if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) {
            return ItemStack.EMPTY;
        }
        
        // Filter to only blocks available in inventory
        List<ShuffleBlockList.BlockEntry> availableBlocks = getAvailableBlocks(blockList, inv);
        if (availableBlocks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        // Create filtered block list with available blocks only
        ShuffleBlockList filteredList = new ShuffleBlockList(availableBlocks);
        
        // Create deterministic Random from position
        // Apply Stafford variant 13 bit-mixing to pos.asLong() so adjacent positions
        // produce uncorrelated seeds (fixes visible patterns along straight lines)
        long seed = pos.asLong();
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        seed = seed ^ (seed >>> 31);
        Random posRandom = new Random(seed);
        
        // Select entry using position-based random
        ShuffleBlockList.BlockEntry selectedEntry;
        if (useWeighted) {
            // Weighted selection
            float random = posRandom.nextFloat();
            float accumulated = 0.0f;
            
            selectedEntry = null;
            for (ShuffleBlockList.BlockEntry entry : filteredList.blocks()) {
                accumulated += entry.weight();
                if (random <= accumulated) {
                    selectedEntry = entry;
                    break;
                }
            }
            
            // Fallback to last entry (handles rounding errors)
            if (selectedEntry == null && !filteredList.isEmpty()) {
                selectedEntry = filteredList.blocks().get(filteredList.size() - 1);
            }
        } else {
            // Equal probability
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
        
        // If selected item is a nested shuffle filter, recursively resolve it
        // This maintains position-based determinism throughout the cascade
        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = configured.getOrDefault(
                ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                ShuffleBlockList.EMPTY
            );
            
            if (nestedList.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            // Recursively select from nested filter using SAME position for determinism
            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            return selectBlockForPositionWithDepth(
                nestedList,
                nestedWeighted,
                pos,
                world,
                inv,
                depth + 1
            );
        }
        
        // Return the resolved item (Create filter or concrete block)
        // Extraction happens later in tryFill mixin
        return configured;
    }



    /**
     * Filters block list to only entries that are available in inventory
     * This prevents selecting blocks that can't be placed
     * Handles cascading filters recursively
     * 
     * @param blockList Full block list from filter
     * @param inv Contraption inventory
     * @return List of entries that have items available in inventory
     */
    @Unique
    private static List<ShuffleBlockList.BlockEntry> getAvailableBlocks(
            ShuffleBlockList blockList,
            IItemHandler inv) {
        List<ShuffleBlockList.BlockEntry> available = new ArrayList<>();
        
        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            ItemStack stack = entry.getItemStack();
            if (stack.isEmpty()) continue;
            
            // Check if this entry has available items (handles cascading)
            if (isEntryAvailable(stack, inv, 0)) {
                available.add(entry);
            }
        }
        
        return available;
    }

    /**
     * Checks if an entry is available in inventory (supports cascading filters)
     * 
     * @param configuredStack The configured ItemStack from the filter
     * @param inv Inventory to search
     * @param depth Current recursion depth
     * @return true if this entry (or any of its nested entries) is available
     */
    @Unique
    private static boolean isEntryAvailable(ItemStack configuredStack, IItemHandler inv, int depth) {
        if (configuredStack.isEmpty()) return false;
        if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) return false;
        
        Item item = configuredStack.getItem();
        
        // Case 1: Nested shuffle filter - recursively check its contents
        if (item instanceof BaseShuffleFilterItem) {
            ShuffleBlockList nestedList = configuredStack.getOrDefault(
                ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                ShuffleBlockList.EMPTY
            );
            
            if (nestedList.isEmpty()) return false;
            
            // Check if ANY of the nested entries are available
            for (ShuffleBlockList.BlockEntry nestedEntry : nestedList.blocks()) {
                if (isEntryAvailable(nestedEntry.getItemStack(), inv, depth + 1)) {
                    return true;
                }
            }
            
            return false;
        }
        
        // Case 2: Create filter - check if any matching item exists
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
        
        // Case 3: Regular item - direct inventory check
        return hasItemInInventory(item, inv);
    }

    /**
     * Checks if a specific item exists in the inventory
     * 
     * @param item Item to search for
     * @param inv Inventory to search
     * @return true if at least one of this item exists
     */
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

    /**
     * Finds slab variant of a full block in inventory
     * Searches for common naming patterns: block_name -> block_name_slab
     * 
     * @param fullBlock The full block to find a slab for
     * @param inv Inventory to search
     * @return ItemStack of slab if found, EMPTY otherwise
     */
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

    /**
     * Finds an alternate block from the filter that has a slab variant
     * Used when the originally selected block has no slab variant
     * This matches Create's behavior where any matching filter item can be used
     * 
     * @param blockList Shuffle filter block list
     * @param inv Contraption inventory
     * @param world Level for nested filter resolution
     * @return ItemStack of alternate block with slab variant, or EMPTY if none found
     */
    @Unique
    private static ItemStack findAlternateBlockWithSlab(
            ShuffleBlockList blockList,
            IItemHandler inv,
            Level world) {
        
        if (blockList.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        // Try each block in the filter
        for (ShuffleBlockList.BlockEntry entry : blockList.blocks()) {
            ItemStack configured = entry.getItemStack();
            
            if (configured.isEmpty()) {
                continue;
            }
            
            // Handle nested shuffle filters recursively
            if (configured.getItem() instanceof BaseShuffleFilterItem) {
                ShuffleBlockList nestedList = configured.getOrDefault(
                    ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                    ShuffleBlockList.EMPTY
                );
                
                ItemStack nestedResult = findAlternateBlockWithSlab(nestedList, inv, world);
                if (!nestedResult.isEmpty()) {
                    return nestedResult;
                }
                continue;
            }
            
            // Handle Create filters - extract first matching item
            if (configured.getItem() instanceof FilterItem) {
                FilterItemStack filterStack = FilterItemStack.of(configured);
                for (int slot = 0; slot < inv.getSlots(); slot++) {
                    ItemStack stackInSlot = inv.getStackInSlot(slot);
                    if (!stackInSlot.isEmpty() && filterStack.test(world, stackInSlot)) {
                        if (stackInSlot.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                            Block block = blockItem.getBlock();
                            BlockState blockState = block.defaultBlockState();
                            
                            // Check if it's already a slab
                            if (blockState.hasProperty(SlabBlock.TYPE)) {
                                return stackInSlot.copy();
                            }
                            
                            // Check if it can be crafted to slab
                            if (findSlabBlockForFullBlock(block) != null) {
                                return stackInSlot.copy();
                            }
                        }
                    }
                }
                continue;
            }
            
            // Handle concrete blocks
            if (configured.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                Block block = blockItem.getBlock();
                BlockState blockState = block.defaultBlockState();
                
                // Check availability in inventory
                if (!isEntryAvailable(configured, inv, 0)) {
                    continue;
                }
                
                // Check if it's already a slab
                if (blockState.hasProperty(SlabBlock.TYPE)) {
                    return configured.copy();
                }
                
                // Check if it can be crafted to slab
                if (findSlabBlockForFullBlock(block) != null) {
                    return configured.copy();
                }
            }
        }
        
        return ItemStack.EMPTY;
    }

    /**
     * Finds the slab variant of a full block by name patterns
     * Handles common Minecraft naming conventions:
     * - block_name -> block_name_slab
     * - blocks (plural) -> block_slab
     * - planks -> slab
     * 
     * @param fullBlock Block to find slab for
     * @return Slab block if found, null otherwise
     */
    @Unique
    private static Block findSlabBlockForFullBlock(Block fullBlock) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(fullBlock);
        if (blockId == null) return null;
        
        String namespace = blockId.getNamespace();
        String path = blockId.getPath();
        int pathLength = path.length();
        
        List<String> slabCandidates = new ArrayList<>();
        
        // Standard: block_name -> block_name_slab
        slabCandidates.add(path + "_slab");
        
        // Plural ending: blocks -> block_slab
        if (path.endsWith("s") && pathLength > 1) {
            slabCandidates.add(path.substring(0, pathLength - 1) + "_slab");
        }
        
        // Planks special case: oak_planks -> oak_slab
        if (path.endsWith("planks") && pathLength > 7) {
            slabCandidates.add(path.substring(0, pathLength - 7) + "_slab");
        }
        
        // Try each candidate
        for (String candidate : slabCandidates) {
            ResourceLocation slabId = ResourceLocation.fromNamespaceAndPath(namespace, candidate);
            Optional<Block> slabBlock = BuiltInRegistries.BLOCK.getOptional(slabId);
            if (slabBlock.isPresent() && slabBlock.get() != Blocks.AIR) {
                return slabBlock.get();
            }
        }
        
        return null;
    }

    /**
     * Extracts an item from inventory
     * Since selectBlockForPosition already resolved shuffle filters,
     * this only needs to handle Create filters and concrete blocks
     * 
     * @param configuredStack The configured ItemStack (Create filter or concrete block, NOT shuffle filter)
     * @param pos Position for logging/context
     * @param world Level for filter matching
     * @param inv Contraption inventory
     * @param depth Current recursion depth (should always be 0 from normal path)
     * @return Extracted ItemStack from inventory, or EMPTY if unavailable
     */
    @Unique
    private static ItemStack extractBlockFromCascadingFilter(
            ItemStack configuredStack,
            BlockPos pos,
            Level world,
            IItemHandler inv,
            int depth) {
        
        if (configuredStack.isEmpty()) return ItemStack.EMPTY;
        
        Item item = configuredStack.getItem();
        
        // Shuffle filters should already be resolved by selectBlockForPosition
        // But handle defensively just in case
        if (item instanceof BaseShuffleFilterItem) {
            com.agent772.createshufflefilter.CreateShuffleFilter.LOGGER.warn(
                "Unexpected shuffle filter in extraction at depth {}! This should have been resolved during selection.", 
                depth
            );
            
            if (depth >= ShuffleFilterUtil.MAX_CASCADE_DEPTH) {
                return ItemStack.EMPTY;
            }
            
            ShuffleBlockList nestedList = configuredStack.getOrDefault(
                ModDataComponents.SHUFFLE_BLOCK_LIST.get(),
                ShuffleBlockList.EMPTY
            );
            
            if (nestedList.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            // Emergency fallback: select and extract
            boolean nestedWeighted = item instanceof WeightedShuffleFilterItem;
            ItemStack nestedSelected = selectBlockForPosition(
                nestedList,
                nestedWeighted,
                pos,
                world,
                inv
            );
            
            return extractBlockFromCascadingFilter(nestedSelected, pos, world, inv, depth + 1);
        }
        
        // Create filter - use its matching logic
        if (item instanceof FilterItem) {
            FilterItemStack filterStack = FilterItemStack.of(configuredStack);
            return com.simibubi.create.foundation.item.ItemHelper.extract(
                inv,
                stack -> filterStack.test(world, stack),
                1,
                false
            );
        }
        
        // Concrete block item - direct extraction
        return com.simibubi.create.foundation.item.ItemHelper.extract(
            inv,
            stack -> stack.getItem() == item,
            1,
            false
        );
    }
}
