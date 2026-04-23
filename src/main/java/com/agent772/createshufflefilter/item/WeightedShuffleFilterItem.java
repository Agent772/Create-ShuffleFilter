package com.agent772.createshufflefilter.item;

import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.menu.WeightedShuffleFilterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Weighted shuffle filter - blocks have configurable probability weights
 */
public class WeightedShuffleFilterItem extends BaseShuffleFilterItem {
    
    public WeightedShuffleFilterItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public String getFilterModeName() {
        return "Weighted Mode";
    }
    
    @Override
    public ChatFormatting getFilterModeColor() {
        return ChatFormatting.GOLD;
    }
    
    @Override
    public String getFilterDescription() {
        return "Configurable probability weights per block";
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Get configuration from components
        ShuffleBlockList blockList = stack.getOrDefault(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), ShuffleBlockList.EMPTY);
        
        // Basic description
        tooltip.add(Component.literal("Right-click to configure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(getFilterDescription()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Allow-list in funnels/basins, randomizes in deployers").withStyle(ChatFormatting.DARK_GRAY));
        
        // Show configuration status
        if (blockList.isEmpty()) {
            tooltip.add(Component.literal("Not configured").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal(blockList.size() + " blocks configured")
                .withStyle(ChatFormatting.GREEN));
        }
        
        // Detailed info when holding shift - Include weights for weighted mode
        if (flag.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Configured Blocks:").withStyle(ChatFormatting.GOLD));
            
            if (blockList.isEmpty()) {
                tooltip.add(Component.literal("  (none)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (int i = 0; i < Math.min(blockList.size(), 10); i++) {
                    ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
                    String blockName = entry.getItemStack().getHoverName().getString();
                    int percentage = Math.round(entry.weight() * 100);
                    
                    tooltip.add(Component.literal("  • " + blockName + " (" + percentage + "%)")
                        .withStyle(ChatFormatting.GRAY));
                }
                
                if (blockList.size() > 10) {
                    tooltip.add(Component.literal("  ... and " + (blockList.size() - 10) + " more")
                        .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else {
            tooltip.add(Component.literal("Hold SHIFT for details").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
    
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        // Find the filter in the player's inventory - check main hand first, then offhand
        int handSlot = -1;
        if (player.getMainHandItem().getItem() == this) {
            handSlot = playerInv.selected;
        } else if (player.getOffhandItem().getItem() == this) {
            handSlot = 40; // Offhand slot
        }
        
        return new WeightedShuffleFilterMenu(containerId, playerInv, handSlot);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Open custom GUI on right-click
            int handSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("screen.createshufflefilter.weighted_shuffle_filter.title");
                }
                
                @Override
                public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
                    return new WeightedShuffleFilterMenu(containerId, playerInv, handSlot);
                }
            }, buf -> buf.writeInt(handSlot)); // Write slot index to buffer for client
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
