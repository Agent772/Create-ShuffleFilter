package com.agent772.createshufflefilter.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores configured blocks with their weights for shuffle filter.
 * Immutable record pattern for thread safety and caching.
 * Supports up to 18 blocks (2 rows of 9 slots).
 */
public record ShuffleBlockList(List<BlockEntry> blocks) {
    
    public static final int MAX_ENTRIES = 18;
    
    // Empty constant for default state
    public static final ShuffleBlockList EMPTY = new ShuffleBlockList(List.of());
    
    /**
     * Single block entry with weight and optional component data for items
     */
    public record BlockEntry(ResourceLocation blockId, float weight, Optional<DataComponentPatch> components) {
        
        /**
         * Constructor without components (for simple blocks/items)
         */
        public BlockEntry(ResourceLocation blockId, float weight) {
            this(blockId, weight, Optional.empty());
        }
        
        public static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("block").forGetter(BlockEntry::blockId),
                Codec.FLOAT.fieldOf("weight").forGetter(BlockEntry::weight),
                DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(BlockEntry::components)
            ).apply(instance, BlockEntry::new)
        );
        
        public static final StreamCodec<RegistryFriendlyByteBuf, BlockEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, BlockEntry::blockId,
            ByteBufCodecs.FLOAT, BlockEntry::weight,
            DataComponentPatch.STREAM_CODEC.apply(ByteBufCodecs::optional), BlockEntry::components,
            BlockEntry::new
        );
        
        /**
         * Get the actual Block from registry
         */
        public Block getBlock() {
            return BuiltInRegistries.BLOCK.get(blockId);
        }
        
        /**
         * Get the item from registry (works for both blocks and items)
         */
        public Item getItem() {
            // Try item registry first
            Item item = BuiltInRegistries.ITEM.get(blockId);
            if (item != net.minecraft.world.item.Items.AIR) {
                return item;
            }
            // Fallback to block item
            Block block = getBlock();
            if (block != net.minecraft.world.level.block.Blocks.AIR) {
                return block.asItem();
            }
            return net.minecraft.world.item.Items.AIR;
        }
        
        /**
         * Get ItemStack with components restored
         */
        public ItemStack getItemStack() {
            Item item = getItem();
            if (item == net.minecraft.world.item.Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item);
            if (components.isPresent()) {
                stack.applyComponents(components.get());
            }
            return stack;
        }
    }
    
    // Codec for JSON serialization (save files)
    public static final Codec<ShuffleBlockList> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockEntry.CODEC.listOf().fieldOf("blocks").forGetter(ShuffleBlockList::blocks)
        ).apply(instance, ShuffleBlockList::new)
    );
    
    // StreamCodec for network synchronization
    public static final StreamCodec<RegistryFriendlyByteBuf, ShuffleBlockList> STREAM_CODEC = StreamCodec.composite(
        BlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
        ShuffleBlockList::blocks,
        ShuffleBlockList::new
    );
    
    /**
     * Check if list is empty
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
    
    /**
     * Get number of configured blocks
     */
    public int size() {
        return blocks.size();
    }
    
    /**
     * Get blocks as list (for compatibility)
     */
    public List<Block> getBlocks() {
        return blocks.stream()
            .map(BlockEntry::getBlock)
            .toList();
    }
    
    /**
     * Create new list with added block (immutable pattern)
     */
    public ShuffleBlockList withBlock(ResourceLocation blockId, float weight) {
        List<BlockEntry> newBlocks = new ArrayList<>(blocks);
        newBlocks.add(new BlockEntry(blockId, weight, Optional.empty()));
        return new ShuffleBlockList(newBlocks);
    }
    
    /**
     * Create new list with added item including components (immutable pattern)
     */
    public ShuffleBlockList withItemStack(ItemStack stack, float weight) {
        List<BlockEntry> newBlocks = new ArrayList<>(blocks);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        DataComponentPatch patch = stack.getComponentsPatch();
        Optional<DataComponentPatch> components = patch.isEmpty() ? Optional.empty() : Optional.of(patch);
        newBlocks.add(new BlockEntry(itemId, weight, components));
        return new ShuffleBlockList(newBlocks);
    }
    
    /**
     * Create new list with removed block at index
     */
    public ShuffleBlockList withoutBlock(int index) {
        if (index < 0 || index >= blocks.size()) {
            return this;
        }
        List<BlockEntry> newBlocks = new ArrayList<>(blocks);
        newBlocks.remove(index);
        return new ShuffleBlockList(newBlocks);
    }
    
    /**
     * Create new list with updated weight
     */
    public ShuffleBlockList withWeight(int index, float weight) {
        if (index < 0 || index >= blocks.size()) {
            return this;
        }
        List<BlockEntry> newBlocks = new ArrayList<>(blocks);
        BlockEntry old = newBlocks.get(index);
        newBlocks.set(index, new BlockEntry(old.blockId, weight, old.components));
        return new ShuffleBlockList(newBlocks);
    }
    
    /**
     * Normalize weights to sum to 1.0
     */
    public ShuffleBlockList normalized() {
        if (blocks.isEmpty()) return this;
        
        float total = 0;
        for (BlockEntry entry : blocks) {
            total += entry.weight;
        }
        
        if (total == 0) total = 1.0f; // Avoid division by zero
        
        List<BlockEntry> normalized = new ArrayList<>();
        for (BlockEntry entry : blocks) {
            normalized.add(new BlockEntry(entry.blockId, entry.weight / total, entry.components));
        }
        
        return new ShuffleBlockList(normalized);
    }
    
    /**
     * Select block based on weighted probability
     * @param random Random value between 0.0 and 1.0
     * @return Selected block or null if empty
     */
    public Block selectWeighted(float random) {
        if (blocks.isEmpty()) return null;
        
        float accumulated = 0.0f;
        for (BlockEntry entry : blocks) {
            accumulated += entry.weight;
            if (random <= accumulated) {
                return entry.getBlock();
            }
        }
        
        // Fallback to last block (handles rounding errors)
        return blocks.get(blocks.size() - 1).getBlock();
    }
    
    /**
     * Select block with equal probability
     * @param index Random index
     * @return Selected block or null if empty
     */
    public Block selectEqual(int index) {
        if (blocks.isEmpty()) return null;
        return blocks.get(index % blocks.size()).getBlock();
    }
    
    /**
     * Select item based on weighted probability (works for blocks and items)
     * @param random Random value between 0.0 and 1.0
     * @return Selected item or null if empty
     */
    public Item selectItemWeighted(float random) {
        if (blocks.isEmpty()) return null;
        
        float accumulated = 0.0f;
        for (BlockEntry entry : blocks) {
            accumulated += entry.weight;
            if (random <= accumulated) {
                return entry.getItem();
            }
        }
        
        // Fallback to last item (handles rounding errors)
        return blocks.get(blocks.size() - 1).getItem();
    }
    
    /**
     * Select item with equal probability (works for blocks and items)
     * @param index Random index
     * @return Selected item or null if empty
     */
    public Item selectItemEqual(int index) {
        if (blocks.isEmpty()) return null;
        return blocks.get(index % blocks.size()).getItem();
    }
}
