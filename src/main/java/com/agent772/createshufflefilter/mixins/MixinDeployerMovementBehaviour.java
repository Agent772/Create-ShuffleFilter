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
import net.neoforged.neoforge.items.IItemHandler;

@Mixin(DeployerMovementBehaviour.class)
public class MixinDeployerMovementBehaviour {

    @Shadow
    private DeployerFakePlayer getPlayer(MovementContext context) { throw new AssertionError(); }

    @Inject(method = "tryGrabbingItem", at = @At("HEAD"), cancellable = true)
    private void onTryGrabbingItem(MovementContext context, CallbackInfo ci) {
        // Only server side
        Level world = context.world;
        if (world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        
        // Check if this is a shuffle filter
        boolean isShuffleFilter = filter != null && !filter.item().isEmpty() && 
            filter.item().getItem() == CreateShuffleFilter.SHUFFLE_FILTER.get();
        
        if (!isShuffleFilter) return; // Let original method handle regular filters

        DeployerFakePlayer player = getPlayer(context);
        if (player == null || !player.getMainHandItem().isEmpty()) return;

        IItemHandler inv = context.contraption.getStorage().getAllItems();
        if (inv == null) return;

        // Collect unique candidates by full item+components
        List<ItemStack> candidates = new ArrayList<>();
        
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack s = inv.getStackInSlot(slot);
            // At this point filter is guaranteed to be non-null due to isShuffleFilter check above
            if (s.isEmpty() || filter == null || !filter.test(world, s)) continue;

            boolean found = false;
            for (ItemStack c : candidates) {
                if (ItemStack.isSameItemSameComponents(c, s)) {
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
        // The respectNBT setting is stored in Create's data components
        // For shuffle filters: respectNBT=true = equal mode, respectNBT=false = weighted mode
        boolean useWeightedMode = false;  // Default to equal mode
        
        if (filter != null) {
            try {
                ItemStack filterItem = filter.item();
                var components = filterItem.getComponents();
                
                // Parse the components string to find respectNBT value
                String componentsStr = components.toString();
                
                if (componentsStr.contains("create:filter_items_respect_nbt=>")) {
                    int startIndex = componentsStr.indexOf("create:filter_items_respect_nbt=>") + "create:filter_items_respect_nbt=>".length();
                    String remaining = componentsStr.substring(startIndex);
                    
                    int endIndex = remaining.indexOf(',');
                    if (endIndex == -1) endIndex = remaining.indexOf('}');
                    if (endIndex == -1) endIndex = remaining.length();
                    
                    String valueStr = remaining.substring(0, endIndex).trim();
                    
                    if (valueStr.equals("true")) {
                        useWeightedMode = false; // respectNBT=true means equal mode
                    } else if (valueStr.equals("false")) {
                        useWeightedMode = true; // respectNBT=false means weighted mode
                    }
                }
                
            } catch (Exception e) {
                // Silently fall back to equal mode
            }
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
                        if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, candidate)) {
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
        ItemStack held = ItemHelper.extract(inv, stack -> ItemStack.isSameItemSameComponents(stack, chosen), 1, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        // Cancel original method to prevent double execution
        ci.cancel();
    }
}