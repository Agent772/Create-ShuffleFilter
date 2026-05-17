package com.agent772.createshufflefilter.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stores configured blocks/items with weights for the shuffle filter on Forge 1.20.1.
 *
 * <p>1.20.1 has no {@code DataComponentType}, so all per-stack state is persisted as NBT
 * on the {@link ItemStack}'s tag. Per-entry item NBT stands in for the data-component
 * patch used on later versions.
 */
public final class ShuffleBlockList {

    public static final int MAX_ENTRIES = 18;
    public static final ShuffleBlockList EMPTY = new ShuffleBlockList(List.of());

    // Stable NBT key namespace on the ItemStack's root tag.
    public static final String ROOT_KEY = "createshufflefilter.shuffle_block_list";
    private static final String KEY_BLOCKS = "Blocks";
    private static final String KEY_ITEM_ID = "Id";
    private static final String KEY_WEIGHT = "Weight";
    private static final String KEY_ITEM_TAG = "Tag";

    private final List<BlockEntry> blocks;

    public ShuffleBlockList(List<BlockEntry> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        List<BlockEntry> copy = new ArrayList<>(blocks.size());
        for (BlockEntry entry : blocks) {
            if (entry != null) {
                copy.add(entry);
            }
            if (copy.size() >= MAX_ENTRIES) {
                break;
            }
        }
        this.blocks = Collections.unmodifiableList(copy);
    }

    public List<BlockEntry> blocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int size() {
        return blocks.size();
    }

    public List<Block> getBlocks() {
        List<Block> out = new ArrayList<>(blocks.size());
        for (BlockEntry entry : blocks) {
            out.add(entry.getBlock());
        }
        return out;
    }

    public ShuffleBlockList withBlock(ResourceLocation blockId, float weight) {
        if (blocks.size() >= MAX_ENTRIES) {
            return this;
        }
        List<BlockEntry> next = new ArrayList<>(blocks);
        next.add(new BlockEntry(blockId, weight, null));
        return new ShuffleBlockList(next);
    }

    public ShuffleBlockList withItemStack(ItemStack stack, float weight) {
        if (blocks.size() >= MAX_ENTRIES) {
            return this;
        }
        List<BlockEntry> next = new ArrayList<>(blocks);
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return this;
        }
        CompoundTag capturedTag = stack.getTag() == null ? null : stack.getTag().copy();
        next.add(new BlockEntry(itemId, weight, capturedTag));
        return new ShuffleBlockList(next);
    }

    public ShuffleBlockList withoutBlock(int index) {
        if (index < 0 || index >= blocks.size()) {
            return this;
        }
        List<BlockEntry> next = new ArrayList<>(blocks);
        next.remove(index);
        return new ShuffleBlockList(next);
    }

    public ShuffleBlockList withWeight(int index, float weight) {
        if (index < 0 || index >= blocks.size()) {
            return this;
        }
        List<BlockEntry> next = new ArrayList<>(blocks);
        BlockEntry old = next.get(index);
        next.set(index, new BlockEntry(old.itemId, weight, old.itemTag));
        return new ShuffleBlockList(next);
    }

    public ShuffleBlockList normalized() {
        if (blocks.isEmpty()) {
            return this;
        }
        float total = 0.0f;
        for (BlockEntry entry : blocks) {
            total += entry.weight;
        }
        if (total == 0.0f) {
            total = 1.0f;
        }
        List<BlockEntry> next = new ArrayList<>(blocks.size());
        for (BlockEntry entry : blocks) {
            next.add(new BlockEntry(entry.itemId, entry.weight / total, entry.itemTag));
        }
        return new ShuffleBlockList(next);
    }

    @Nullable
    public Block selectWeighted(float random) {
        if (blocks.isEmpty()) {
            return null;
        }
        float accumulated = 0.0f;
        for (BlockEntry entry : blocks) {
            accumulated += entry.weight;
            if (random <= accumulated) {
                return entry.getBlock();
            }
        }
        return blocks.get(blocks.size() - 1).getBlock();
    }

    @Nullable
    public Block selectEqual(int index) {
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.get(Math.floorMod(index, blocks.size())).getBlock();
    }

    @Nullable
    public Item selectItemWeighted(float random) {
        if (blocks.isEmpty()) {
            return null;
        }
        float accumulated = 0.0f;
        for (BlockEntry entry : blocks) {
            accumulated += entry.weight;
            if (random <= accumulated) {
                return entry.getItem();
            }
        }
        return blocks.get(blocks.size() - 1).getItem();
    }

    @Nullable
    public Item selectItemEqual(int index) {
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.get(Math.floorMod(index, blocks.size())).getItem();
    }

    // ----- NBT serialization -----

    public static ShuffleBlockList read(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty() || !tag.contains(KEY_BLOCKS, Tag.TAG_LIST)) {
            return EMPTY;
        }
        ListTag list = tag.getList(KEY_BLOCKS, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return EMPTY;
        }
        List<BlockEntry> entries = new ArrayList<>(Math.min(list.size(), MAX_ENTRIES));
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            CompoundTag entryTag = list.getCompound(i);
            String idStr = entryTag.getString(KEY_ITEM_ID);
            ResourceLocation itemId = ResourceLocation.tryParse(idStr);
            if (itemId == null) {
                continue;
            }
            float weight = entryTag.contains(KEY_WEIGHT, Tag.TAG_FLOAT)
                ? entryTag.getFloat(KEY_WEIGHT)
                : 1.0f;
            CompoundTag itemTag = null;
            if (entryTag.contains(KEY_ITEM_TAG, Tag.TAG_COMPOUND)) {
                CompoundTag candidate = entryTag.getCompound(KEY_ITEM_TAG);
                if (!candidate.isEmpty()) {
                    itemTag = candidate.copy();
                }
            }
            entries.add(new BlockEntry(itemId, weight, itemTag));
        }
        return entries.isEmpty() ? EMPTY : new ShuffleBlockList(entries);
    }

    public static void write(CompoundTag tag, ShuffleBlockList list) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(list, "list");
        ListTag listTag = new ListTag();
        for (BlockEntry entry : list.blocks) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(KEY_ITEM_ID, entry.itemId.toString());
            entryTag.putFloat(KEY_WEIGHT, entry.weight);
            if (entry.itemTag != null && !entry.itemTag.isEmpty()) {
                entryTag.put(KEY_ITEM_TAG, entry.itemTag.copy());
            }
            listTag.add(entryTag);
        }
        tag.put(KEY_BLOCKS, listTag);
    }

    public static ShuffleBlockList getOrCreate(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return EMPTY;
        }
        return read(root.getCompound(ROOT_KEY));
    }

    public static void set(ItemStack stack, ShuffleBlockList list) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(list, "list");
        if (list.isEmpty()) {
            CompoundTag existing = stack.getTag();
            if (existing == null) {
                return;
            }
            existing.remove(ROOT_KEY);
            if (existing.isEmpty()) {
                stack.setTag(null);
            }
            return;
        }
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag payload = new CompoundTag();
        write(payload, list);
        root.put(ROOT_KEY, payload);
    }

    // ----- Network helpers (groundwork for Epic #9) -----

    public void writeToBuf(FriendlyByteBuf buf) {
        CompoundTag tmp = new CompoundTag();
        write(tmp, this);
        buf.writeNbt(tmp);
    }

    public static ShuffleBlockList readFromBuf(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? EMPTY : read(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShuffleBlockList other)) return false;
        return blocks.equals(other.blocks);
    }

    @Override
    public int hashCode() {
        return blocks.hashCode();
    }

    @Override
    public String toString() {
        return "ShuffleBlockList" + blocks;
    }

    /**
     * Single block/item entry with weight and optional captured item NBT.
     *
     * <p>{@code itemTag} stands in for the data-component patch used on Minecraft 1.21+,
     * holding any NBT the configured stack carried at capture time.
     */
    public static final class BlockEntry {
        private final ResourceLocation itemId;
        private final float weight;
        @Nullable
        private final CompoundTag itemTag;

        public BlockEntry(ResourceLocation itemId, float weight, @Nullable CompoundTag itemTag) {
            this.itemId = Objects.requireNonNull(itemId, "itemId");
            this.weight = weight;
            this.itemTag = itemTag;
        }

        public BlockEntry(ResourceLocation itemId, float weight) {
            this(itemId, weight, null);
        }

        public ResourceLocation itemId() {
            return itemId;
        }

        public float weight() {
            return weight;
        }

        @Nullable
        public CompoundTag itemTag() {
            return itemTag == null ? null : itemTag.copy();
        }

        public Block getBlock() {
            Block block = ForgeRegistries.BLOCKS.getValue(itemId);
            return block == null ? Blocks.AIR : block;
        }

        public Item getItem() {
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null && item != Items.AIR) {
                return item;
            }
            Block block = getBlock();
            if (block != Blocks.AIR) {
                return block.asItem();
            }
            return Items.AIR;
        }

        public ItemStack getItemStack() {
            Item item = getItem();
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item);
            if (itemTag != null) {
                stack.setTag(itemTag.copy());
            }
            return stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockEntry other)) return false;
            if (Float.compare(other.weight, weight) != 0) return false;
            if (!itemId.equals(other.itemId)) return false;
            return Objects.equals(itemTag, other.itemTag);
        }

        @Override
        public int hashCode() {
            int result = itemId.hashCode();
            result = 31 * result + Float.hashCode(weight);
            result = 31 * result + (itemTag == null ? 0 : itemTag.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "BlockEntry{" + itemId + " x" + weight + (itemTag != null ? " +nbt" : "") + "}";
        }
    }
}
