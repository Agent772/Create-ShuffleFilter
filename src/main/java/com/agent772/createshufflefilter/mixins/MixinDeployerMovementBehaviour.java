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
        CreateShuffleFilter.LOGGER.info("MixinDeployerMovementBehaviour: tryGrabbingItem called");
        
        // only server side
        Level world = context.world;
        if (world.isClientSide) return;

        FilterItemStack filter = context.getFilterFromBE();
        // Debug logging
        if (filter != null && !filter.item().isEmpty()) {
            CreateShuffleFilter.LOGGER.info("Filter present: " + filter.item().getDescriptionId());
            CreateShuffleFilter.LOGGER.info("Filter item: " + filter.item().getItem());
            CreateShuffleFilter.LOGGER.info("Our shuffle filter item: " + CreateShuffleFilter.SHUFFLE_FILTER.get());
            CreateShuffleFilter.LOGGER.info("Items equal: " + (filter.item().getItem() == CreateShuffleFilter.SHUFFLE_FILTER.get()));
            CreateShuffleFilter.LOGGER.info("Is shuffle filter tag: " + filter.item().is(CreateShuffleFilter.SHUFFLE_FILTER_TAG));
            
            // Debug filter configuration
            CreateShuffleFilter.LOGGER.info("Filter configuration debug:");
            CreateShuffleFilter.LOGGER.info("  Filter isEmpty: " + filter.isEmpty());
            if (!filter.isEmpty()) {
                // Let's check what the filter actually contains
                CreateShuffleFilter.LOGGER.info("  Filter item stack: " + filter.item());
                CreateShuffleFilter.LOGGER.info("  Filter has components: " + !filter.item().getComponents().isEmpty());
                CreateShuffleFilter.LOGGER.info("  Filter components: " + filter.item().getComponents());
                
                // Try to access the filter items component specifically
                try {
                    // First try the standard minecraft container component
                    var filterItems = filter.item().getComponents().get(net.minecraft.core.component.DataComponents.CONTAINER);
                    if (filterItems != null) {
                        CreateShuffleFilter.LOGGER.info("  Filter container component: " + filterItems);
                        CreateShuffleFilter.LOGGER.info("  Container items count: " + filterItems.getSlots());
                        for (int i = 0; i < filterItems.getSlots(); i++) {
                            ItemStack filterStack = filterItems.getStackInSlot(i);
                            if (!filterStack.isEmpty()) {
                                CreateShuffleFilter.LOGGER.info("    Slot " + i + ": " + filterStack.getDisplayName().getString() + " x" + filterStack.getCount());
                            }
                        }
                    } else {
                        CreateShuffleFilter.LOGGER.info("  No standard container component found in filter");
                        
                        // Try to access Create's filter component using getFilterItems method from FilterItem
                        CreateShuffleFilter.LOGGER.info("  Trying to access filter items directly...");
                        try {
                            // Use reflection to access the static getFilterItems method from FilterItem
                            Class<?> filterItemClass = Class.forName("com.simibubi.create.content.logistics.filter.FilterItem");
                            java.lang.reflect.Method getFilterItemsMethod = filterItemClass.getMethod("getFilterItems", ItemStack.class);
                            Object filterItemsContainer = getFilterItemsMethod.invoke(null, filter.item());
                            CreateShuffleFilter.LOGGER.info("  FilterItem.getFilterItems() returned: " + filterItemsContainer);
                            
                            if (filterItemsContainer instanceof net.neoforged.neoforge.items.ItemStackHandler handler) {
                                CreateShuffleFilter.LOGGER.info("  Filter ItemStackHandler slots: " + handler.getSlots());
                                for (int i = 0; i < handler.getSlots(); i++) {
                                    ItemStack filterStack = handler.getStackInSlot(i);
                                    if (!filterStack.isEmpty()) {
                                        CreateShuffleFilter.LOGGER.info("    Filter slot " + i + ": " + filterStack.getDisplayName().getString() + " x" + filterStack.getCount());
                                    }
                                }
                                
                                // Now let's try manually testing if cobblestone is in the filter
                                CreateShuffleFilter.LOGGER.info("  Manual filter test using getFilterItems result:");
                                ItemStack testCobble = new ItemStack(net.minecraft.world.item.Items.COBBLESTONE);
                                boolean foundMatch = false;
                                for (int i = 0; i < handler.getSlots(); i++) {
                                    ItemStack filterStack = handler.getStackInSlot(i);
                                    if (!filterStack.isEmpty()) {
                                        boolean matches = net.minecraft.world.item.ItemStack.isSameItem(filterStack, testCobble);
                                        CreateShuffleFilter.LOGGER.info("    Slot " + i + " (" + filterStack.getDisplayName().getString() + ") matches cobblestone: " + matches);
                                        if (matches) foundMatch = true;
                                    }
                                }
                                CreateShuffleFilter.LOGGER.info("  Manual test found cobblestone match: " + foundMatch);
                            }
                        } catch (Exception reflectEx) {
                            CreateShuffleFilter.LOGGER.info("  Reflection failed: " + reflectEx.getMessage());
                            reflectEx.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    CreateShuffleFilter.LOGGER.info("  Error accessing filter container: " + e.getMessage());
                }
            }
        } else {
            CreateShuffleFilter.LOGGER.info("No filter present");
        }

        // --- detection: use both tag and direct item comparison ---
        boolean isShuffleFilter = filter != null && !filter.item().isEmpty() && 
            (filter.item().is(CreateShuffleFilter.SHUFFLE_FILTER_TAG) || 
             filter.item().getItem() == CreateShuffleFilter.SHUFFLE_FILTER.get());
        if (!isShuffleFilter) return; // Let original method handle regular filters
        // --- end detection ---

        CreateShuffleFilter.LOGGER.info("Shuffle filter detected! Applying shuffle logic...");

        DeployerFakePlayer player = getPlayer(context);
        if (player == null) {
            CreateShuffleFilter.LOGGER.info("Player is null, returning");
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            CreateShuffleFilter.LOGGER.info("Player already has item in hand: " + player.getMainHandItem());
            return;
        }

        IItemHandler inv = context.contraption.getStorage().getAllItems();
        if (inv == null) {
            CreateShuffleFilter.LOGGER.info("Inventory is null, returning");
            return;
        }
        CreateShuffleFilter.LOGGER.info("Inventory found with " + inv.getSlots() + " slots");

        // At this point we know filter is not null due to the isShuffleFilter check above
        final FilterItemStack shuffleFilter = filter;

        // collect unique candidates by full item+components (same as ItemStack.isSameItemSameComponents)
        List<ItemStack> candidates = new ArrayList<>();
        CreateShuffleFilter.LOGGER.info("Scanning inventory for matching items...");
        
        // Let's test the first cobblestone specifically
        if (inv.getSlots() > 0 && shuffleFilter != null) {
            ItemStack firstStack = inv.getStackInSlot(0);
            if (!firstStack.isEmpty()) {
                CreateShuffleFilter.LOGGER.info("Testing first item against filter:");
                CreateShuffleFilter.LOGGER.info("  Item: " + firstStack.getDisplayName().getString());
                CreateShuffleFilter.LOGGER.info("  Item ID: " + firstStack.getItem());
                boolean testResult = shuffleFilter.test(world, firstStack);
                CreateShuffleFilter.LOGGER.info("  Filter test result: " + testResult);
                
                // Let's also try a manual test
                CreateShuffleFilter.LOGGER.info("  Manual filter checks:");
                CreateShuffleFilter.LOGGER.info("    Filter empty: " + shuffleFilter.isEmpty());
                CreateShuffleFilter.LOGGER.info("    Filter item count: " + shuffleFilter.item().getCount());
            }
        }
        
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack s = inv.getStackInSlot(slot);
            if (s.isEmpty()) continue;
            
            CreateShuffleFilter.LOGGER.info("Slot " + slot + ": " + s.getDisplayName().getString() + " x" + s.getCount());
            
            if (shuffleFilter == null || !shuffleFilter.test(world, s)) {
                CreateShuffleFilter.LOGGER.info("  -> Does not match filter");
                continue;
            }
            CreateShuffleFilter.LOGGER.info("  -> Matches filter!");

            boolean found = false;
            for (ItemStack c : candidates) {
                if (ItemStack.isSameItemSameComponents(c, s)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                candidates.add(s.copy());
                CreateShuffleFilter.LOGGER.info("  -> Added as new candidate: " + s.getDisplayName().getString());
            } else {
                CreateShuffleFilter.LOGGER.info("  -> Already have this candidate type");
            }
        }

        CreateShuffleFilter.LOGGER.info("Found " + candidates.size() + " unique candidate types");

        if (candidates.isEmpty()) {
            CreateShuffleFilter.LOGGER.info("No matching candidates found, letting original logic handle");
            // no matches -> leave original logic
            return;
        }

        ItemStack chosen;
        if (candidates.size() == 1) {
            chosen = candidates.get(0);
            CreateShuffleFilter.LOGGER.info("Only one candidate type, choosing: " + chosen.getDisplayName().getString());
        } else {
            RandomSource r = world.getRandom();
            int randomIndex = r.nextInt(candidates.size());
            chosen = candidates.get(randomIndex);
            CreateShuffleFilter.LOGGER.info("Multiple candidates, randomly chose index " + randomIndex + ": " + chosen.getDisplayName().getString());
        }

        // extract only the chosen item type (amount = 1)
        CreateShuffleFilter.LOGGER.info("Attempting to extract 1x " + chosen.getDisplayName().getString());
        ItemStack held = ItemHelper.extract(inv, stack -> ItemStack.isSameItemSameComponents(stack, chosen), 1, false);
        
        if (held.isEmpty()) {
            CreateShuffleFilter.LOGGER.info("Failed to extract item! ItemHelper.extract returned empty stack");
        } else {
            CreateShuffleFilter.LOGGER.info("Successfully extracted: " + held.getDisplayName().getString() + " x" + held.getCount());
        }
        
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        CreateShuffleFilter.LOGGER.info("Set player hand item to: " + (held.isEmpty() ? "empty" : held.getDisplayName().getString()));

        // cancel original method to prevent double execution
        ci.cancel();
    }
}