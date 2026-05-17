package com.agent772.createshufflefilter.mixins;

import java.util.HashSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.BaseShuffleFilterItem;
import com.agent772.createshufflefilter.item.WeightedShuffleFilterItem;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

/**
 * Deployer-on-contraption integration for shuffle filters.
 *
 * <p>When the deployer's filter is a {@link BaseShuffleFilterItem}, replace Create's normal
 * grab logic with one that picks an item from the configured shuffle list (with cascading
 * support via {@link ShuffleFilterUtil#selectItemCascading}) and hands it to the fake player.
 */
@Mixin(value = DeployerMovementBehaviour.class, remap = false)
public class MixinDeployerMovementBehaviour {

    @Shadow
    private DeployerFakePlayer getPlayer(MovementContext context) {
        throw new AssertionError();
    }

    @Inject(method = "tryGrabbingItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTryGrabbingItem(MovementContext context, CallbackInfo ci) {
        Level world = context.world;
        if (world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        if (filter == null || filter.item().isEmpty()) return;

        ItemStack filterStack = filter.item();
        Item filterItem = filterStack.getItem();

        if (!(filterItem instanceof BaseShuffleFilterItem)) {
            return;
        }

        DeployerFakePlayer player = getPlayer(context);
        if (player == null || !player.getMainHandItem().isEmpty()) return;

        IItemHandler inv = context.contraption.getStorage().getAllItems();
        if (inv == null) return;

        ShuffleBlockList blockList = ShuffleBlockList.read(filterStack);
        if (blockList.isEmpty()) return;

        boolean useWeighted = filterItem instanceof WeightedShuffleFilterItem;

        ItemStack held = ShuffleFilterUtil.selectItemCascading(
            blockList,
            useWeighted,
            world,
            inv,
            0,
            new HashSet<>()
        );

        if (!held.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }

        ci.cancel();
    }
}
