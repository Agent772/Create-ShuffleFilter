package com.agent772.createshufflefilter.mixins;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.agent772.createshufflefilter.CreateShuffleFilter;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

@Mixin(value = DeployerMovementBehaviour.class, remap = false)
public class MixinDeployerMovementBehaviour {

    @Shadow
    private DeployerFakePlayer getPlayer(MovementContext context) { throw new AssertionError(); }

    @Inject(method = "tryGrabbingItem", at = @At("HEAD"), cancellable = true)
    private void onTryGrabbingItem(MovementContext context, CallbackInfo ci) {
        // Only server side
        Level world = context.world;
        if (world == null || world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        
        // Check if this is a shuffle filter
        boolean isShuffleFilter = filter != null && !filter.item().isEmpty() && 
            filter.item().getItem() == CreateShuffleFilter.SHUFFLE_FILTER.get();
        
        if (!isShuffleFilter) return; // Let original method handle regular filters

        // Safety checks
        if (context.contraption == null) return;
        
        DeployerFakePlayer player = getPlayer(context);
        if (player == null || !player.getMainHandItem().isEmpty()) return;

        var storage = context.contraption.getStorage();
        if (storage == null) return;
        
        IItemHandler inv = storage.getAllItems();
        if (inv == null) return;

        // Collect unique candidates by full item+components
        List<ItemStack> candidates = new ArrayList<>();
        
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack s = inv.getStackInSlot(slot);
            // filter is guaranteed to be non-null due to isShuffleFilter check above
            if (s.isEmpty() || !filter.test(world, s)) continue;

            boolean found = false;
            for (ItemStack c : candidates) {
                if (ItemStack.isSameItemSameTags(c, s)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                candidates.add(s.copy());
            }
        }

        if (candidates.isEmpty()) return; // No matches, let original logic handle

        // Check if weighted mode is enabled
        // The respectNBT setting is stored in Create's NBT data
        // For shuffle filters: respectNBT=true = equal mode, respectNBT=false = weighted mode
        boolean useWeightedMode = false;  // Default to equal mode
        
        try {
            ItemStack filterItem = filter.item();
            if (filterItem.hasTag() && filterItem.getTag() != null) {
                // In 1.20.1, Create stores filter settings in NBT
                // The respectNBT key determines the mode
                if (filterItem.getTag().contains("RespectNBT")) {
                    boolean respectNBT = filterItem.getTag().getBoolean("RespectNBT");
                    useWeightedMode = !respectNBT; // respectNBT=false means weighted mode
                }
            }
        } catch (Exception e) {
            // Silently fall back to equal mode
        }

        ItemStack chosen;
        if (candidates.size() == 1) {
            chosen = candidates.get(0);
        } else {
            RandomSource r = world.getRandom();
            
            if (useWeightedMode) {
                // Count how many stacks of each candidate type we have
                java.util.Map<ItemStack, Integer> stackCounts = new java.util.HashMap<>();
                for (ItemStack candidate : candidates) {
                    int count = 0;
                    for (int slot = 0; slot < inv.getSlots(); slot++) {
                        ItemStack s = inv.getStackInSlot(slot);
                        if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, candidate)) {
                            count++;
                        }
                    }
                    stackCounts.put(candidate, count);
                }
                
                // Create weighted selection based on stack counts
                java.util.List<ItemStack> weightedList = new java.util.ArrayList<>();
                for (java.util.Map.Entry<ItemStack, Integer> entry : stackCounts.entrySet()) {
                    for (int i = 0; i < entry.getValue(); i++) {
                        weightedList.add(entry.getKey());
                    }
                }
                
                int randomIndex = r.nextInt(weightedList.size());
                chosen = weightedList.get(randomIndex);
                
            } else {
                // Equal mode - simple random selection
                int randomIndex = r.nextInt(candidates.size());
                chosen = candidates.get(randomIndex);
            }
        }

        // Extract only the chosen item type (amount = 1)
        ItemStack held = ItemHelper.extract(inv, stack -> ItemStack.isSameItemSameTags(stack, chosen), 1, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        // Cancel original method to prevent double execution
        ci.cancel();
    }
}