package com.agent772.createshufflefilter.network;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.component.ShuffleMode;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet to sync filter configuration from client to server
 */
public record FilterConfigPacket(
    int filterSlot,
    ShuffleMode mode,
    List<ItemStack> items,
    List<Float> weights
) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<FilterConfigPacket> TYPE = 
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateShuffleFilter.MODID, "filter_config")
        );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterConfigPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        FilterConfigPacket::filterSlot,
        ShuffleMode.STREAM_CODEC,
        FilterConfigPacket::mode,
        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
        FilterConfigPacket::items,
        ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()),
        FilterConfigPacket::weights,
        FilterConfigPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * Handle packet on server side
     */
    public static void handle(FilterConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            
            // Get filter from player inventory
            ItemStack filterStack = player.getInventory().getItem(packet.filterSlot);
            if (filterStack.isEmpty() || !(filterStack.getItem() instanceof BaseShuffleFilterItem)) {
                return;
            }
            
            // Build block list from packet data (now with ItemStack data)
            List<ShuffleBlockList.BlockEntry> entries = new ArrayList<>();
            for (int i = 0; i < packet.items.size(); i++) {
                ItemStack item = packet.items.get(i);
                float weight = i < packet.weights.size() ? packet.weights.get(i) : 1.0f;
                
                ShuffleBlockList blockList = new ShuffleBlockList(List.of());
                blockList = blockList.withItemStack(item, weight);
                if (!blockList.isEmpty()) {
                    entries.add(blockList.blocks().get(0));
                }
            }
            
            ShuffleBlockList blockList = new ShuffleBlockList(entries);
            
            // Normalize weights if in weighted mode
            if (packet.mode == ShuffleMode.WEIGHTED) {
                blockList = blockList.normalized();
            }
            
            // Update item components (only block list - mode is determined by filter type)
            filterStack.set(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), blockList);
            
            CreateShuffleFilter.LOGGER.info("Updated {} configuration for player {}: {} blocks", 
                filterStack.getItem().getClass().getSimpleName(),
                player.getName().getString(), 
                blockList.size());
        });
    }
    
    /**
     * Helper to send packet from client
     */
    public static void sendToServer(int filterSlot, ShuffleMode mode, 
                                    List<ItemStack> items, List<Float> weights) {
        FilterConfigPacket packet = new FilterConfigPacket(filterSlot, mode, items, weights);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
}
