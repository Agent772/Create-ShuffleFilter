package com.agent772.createshufflefilter.integration.jei;

import com.agent772.createshufflefilter.screen.WeightedShuffleFilterScreen;
import com.agent772.createshufflefilter.screen.widget.BlockSlotWidget;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghost ingredient handler for WeightedShuffleFilterScreen (9 slots)
 * Allows drag and drop from JEI into filter slots
 */
public class WeightedShuffleFilterGhostHandler implements IGhostIngredientHandler<WeightedShuffleFilterScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(@Nonnull WeightedShuffleFilterScreen screen, @Nonnull ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();

        // Only accept ItemStack ingredients
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }

        // Get all slot widgets from row 1 (weighted filter only has 9 slots)
        List<BlockSlotWidget> allSlots = screen.getAllSlots();

        // Create ghost targets for each slot
        for (int i = 0; i < allSlots.size(); i++) {
            BlockSlotWidget slot = allSlots.get(i);
            final int slotIndex = i;

            targets.add(new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(slot.getX(), slot.getY(), slot.getWidth(), slot.getHeight());
                }

                @Override
                public void accept(I ingredientObj) {
                    if (ingredientObj instanceof ItemStack itemStack) {
                        screen.setSlotItem(slotIndex, itemStack);
                    }
                }
            });
        }

        return targets;
    }

    @Override
    public void onComplete() {
        // No special completion action needed
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true; // Highlight slots when dragging
    }
}
