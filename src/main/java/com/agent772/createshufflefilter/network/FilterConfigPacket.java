package com.agent772.createshufflefilter.network;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet to sync filter configuration from client to server.
 *
 * <p>Wire payload mirrors the 1.21.1 version (filter slot, items, weights) but uses
 * Forge 1.20.1's {@link FriendlyByteBuf} framing instead of NeoForge's {@code StreamCodec}.
 * The mode field is dropped — the server derives weighted vs equal from the filter
 * item's class.
 */
public class FilterConfigPacket {

    private final int filterSlot;
    private final List<ItemStack> items;
    private final List<Float> weights;

    public FilterConfigPacket(int filterSlot, List<ItemStack> items, List<Float> weights) {
        this.filterSlot = filterSlot;
        this.items = items;
        this.weights = weights;
    }

    public int filterSlot() {
        return filterSlot;
    }

    public List<ItemStack> items() {
        return items;
    }

    public List<Float> weights() {
        return weights;
    }

    public static void encode(FilterConfigPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.filterSlot);
        buf.writeCollection(pkt.items, FriendlyByteBuf::writeItem);
        buf.writeCollection(pkt.weights, FriendlyByteBuf::writeFloat);
    }

    public static FilterConfigPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        List<ItemStack> items = buf.readCollection(ArrayList::new, FriendlyByteBuf::readItem);
        List<Float> weights = buf.readCollection(ArrayList::new, FriendlyByteBuf::readFloat);
        return new FilterConfigPacket(slot, items, weights);
    }

    public static void handle(FilterConfigPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            Player player = ctx.getSender();
            if (player == null) return;

            ItemStack filterStack = player.getInventory().getItem(pkt.filterSlot);
            if (filterStack.isEmpty() || !(filterStack.getItem() instanceof BaseShuffleFilterItem)) {
                return;
            }

            List<ShuffleBlockList.BlockEntry> entries = new ArrayList<>();
            for (int i = 0; i < pkt.items.size(); i++) {
                ItemStack item = pkt.items.get(i);
                float weight = i < pkt.weights.size() ? pkt.weights.get(i) : 1.0f;

                ShuffleBlockList tmp = ShuffleBlockList.EMPTY.withItemStack(item, weight);
                if (!tmp.isEmpty()) {
                    entries.add(tmp.blocks().get(0));
                }
            }

            ShuffleBlockList blockList = new ShuffleBlockList(entries);

            boolean weighted = filterStack.getItem() instanceof WeightedShuffleFilterItem;
            if (weighted) {
                blockList = blockList.normalized();
            }

            ShuffleBlockList.set(filterStack, blockList);

            CreateShuffleFilter.LOGGER.info("Updated {} configuration for player {}: {} blocks",
                filterStack.getItem().getClass().getSimpleName(),
                player.getName().getString(),
                blockList.size());
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Convenience for screen code: send a configuration packet to the server.
     */
    public static void sendToServer(int filterSlot, List<ItemStack> items, List<Float> weights) {
        ModPackets.CHANNEL.sendToServer(new FilterConfigPacket(filterSlot, items, weights));
    }
}
