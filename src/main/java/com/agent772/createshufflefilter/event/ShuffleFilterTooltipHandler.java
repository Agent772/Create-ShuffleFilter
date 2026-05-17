package com.agent772.createshufflefilter.event;

import java.util.List;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.util.FilterModeHelper;
import com.simibubi.create.AllKeys;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Appends shuffle-filter-specific tooltip lines via the Forge {@link ItemTooltipEvent}.
 *
 * <p>The 1.21.1 mod intercepts {@code FilterItem#appendHoverText} with a mixin. On Forge 1.20.1
 * the Mixin annotation processor cannot resolve {@code appendHoverText} through Create's
 * {@code FilterItem} override (Create's classes have no SRG mapping table available to the AP),
 * so the equivalent behavior is implemented as a Forge event handler.
 */
@Mod.EventBusSubscriber(modid = CreateShuffleFilter.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShuffleFilterTooltipHandler {

    private ShuffleFilterTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!FilterModeHelper.isShuffleFilter(stack)) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.literal("Randomizes item selection from filtered matches for deployers on contraptions")
            .withStyle(ChatFormatting.GRAY));

        boolean useWeightedMode = FilterModeHelper.isWeightedMode(stack);

        if (AllKeys.shiftDown()) {
            tooltip.add(Component.literal("Behaviour when in deployer on contraption").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("• Selects blocks from configured list").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• Right-click to open configuration GUI").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.empty());

            if (useWeightedMode) {
                tooltip.add(Component.literal("Weighted Mode").withStyle(ChatFormatting.GREEN));
                tooltip.add(Component.literal("• Configure up to 18 blocks with custom weights").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Higher weights = more likely to be selected").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Weights are percentages that sum to 100%").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.literal("Equal Mode").withStyle(ChatFormatting.BLUE));
                tooltip.add(Component.literal("• Configure up to 18 blocks").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• All blocks have equal selection chance").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("• Simple random selection").withStyle(ChatFormatting.GRAY));
            }

            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Performance: 300x faster than inventory-based filters").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("SHIFT").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" for details").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }
}
