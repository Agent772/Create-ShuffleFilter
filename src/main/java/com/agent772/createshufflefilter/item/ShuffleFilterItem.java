package com.agent772.createshufflefilter.item;

import com.agent772.createshufflefilter.menu.ShuffleFilterMenu;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nonnull;

/**
 * Main shuffle filter - equal probability for all blocks.
 * Supports up to 18 blocks (2 rows of 9).
 */
public class ShuffleFilterItem extends BaseShuffleFilterItem {

    public ShuffleFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getFilterModeName() {
        return "Equal Mode";
    }

    @Override
    public ChatFormatting getFilterModeColor() {
        return ChatFormatting.BLUE;
    }

    @Override
    public String getFilterDescription() {
        return "All blocks have equal selection probability";
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        int handSlot = -1;
        if (player.getMainHandItem().getItem() == this) {
            handSlot = playerInv.selected;
        } else if (player.getOffhandItem().getItem() == this) {
            handSlot = 40;
        }
        return new ShuffleFilterMenu(containerId, playerInv, handSlot);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int handSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            final int finalSlot = handSlot;

            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("screen.createshufflefilter.shuffle_filter.title");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInv, @Nonnull Player player) {
                    return new ShuffleFilterMenu(containerId, playerInv, finalSlot);
                }
            }, buf -> buf.writeInt(finalSlot));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
