package com.agent772.createshufflefilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

@Mixin(DeployerMovementBehaviour.class)
public class MixinDeployerMovementBehaviour {

    @Shadow
    private DeployerFakePlayer getPlayer(MovementContext context) { throw new AssertionError(); }

    @Inject(method = "tryGrabbingItem", at = @At("HEAD"), cancellable = true)
    private void onTryGrabbingItem(MovementContext context, CallbackInfo ci) {
        // only server side
        Level world = context.world;
        if (world.isClientSide) return;

        DeployerFakePlayer player = getPlayer(context);
        if (player == null) return;
        if (!player.getMainHandItem().isEmpty()) return;

        FilterItemStack filter = context.getFilterFromBE();
        // keep existing schematic behavior
        if (com.simibubi.create.AllItems.SCHEMATIC.isIn(filter.item()))
            return;
        // --- detection: use an item tag to detect the special shuffle filter ---
        // We recommend using a tag so resource packs and other mods can opt-in.
        TagKey<net.minecraft.world.item.Item> SHUFFLE_FILTER_TAG = TagKey.create(Registries.ITEM, new ResourceLocation("createshufflefilter", "shuffle_filter"));

        boolean isShuffleFilter = filter != null && !filter.item().isEmpty() && filter.item().is(SHUFFLE_FILTER_TAG);
        if (!isShuffleFilter) return;
        // --- end detection ---

        IItemHandler inv = context.contraption.getStorage().getAllItems();
        if (inv == null) return;

        // collect unique candidates by full item+components (same as ItemStack.isSameItemSameComponents)
        List<ItemStack> candidates = new ArrayList<>();
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack s = inv.getStackInSlot(slot);
            if (s.isEmpty()) continue;
            if (!filter.test(world, s)) continue;

            boolean found = false;
            for (ItemStack c : candidates) {
                if (ItemStack.isSameItemSameComponents(c, s)) {
                    found = true;
                    break;
                }
            }
            if (!found) candidates.add(s.copy());
        }

        if (candidates.isEmpty()) {
            // no matches -> leave original logic
            return;
        }

        ItemStack chosen;
        if (candidates.size() == 1) {
            chosen = candidates.get(0);
        } else {
            Random r = world.getRandom();
            chosen = candidates.get(r.nextInt(candidates.size()));
        }

        // extract only the chosen item type (amount = 1)
        ItemStack held = ItemHelper.extract(inv, stack -> ItemStack.isSameItemSameComponents(stack, chosen), 1, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        // cancel original method to prevent double execution
        ci.cancel();
    }
}