package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.agent772.createshufflefilter.CreateShuffleFilter;

import net.minecraft.world.item.ItemStack;

@Mixin(FilterItemStack.class)
public class MixinFilterItemStack {

    @Inject(method = "of(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/filter/FilterItemStack;", 
            at = @At("HEAD"), cancellable = true)
    private static void onOf(ItemStack filter, CallbackInfoReturnable<FilterItemStack> cir) {
        // Check if this is our shuffle filter
        if (!filter.isComponentsPatchEmpty() && 
            filter.getItem() == CreateShuffleFilter.SHUFFLE_FILTER.get()) {
            
            // Remove extra components like Create does for its filters
            filter.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            filter.remove(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
            
            // Create a ListFilterItemStack for our shuffle filter using reflection
            try {
                Class<?> listFilterClass = FilterItemStack.ListFilterItemStack.class;
                java.lang.reflect.Constructor<?> constructor = listFilterClass.getDeclaredConstructor(ItemStack.class);
                constructor.setAccessible(true);
                FilterItemStack listFilter = (FilterItemStack) constructor.newInstance(filter);
                cir.setReturnValue(listFilter);
            } catch (Exception e) {
                CreateShuffleFilter.LOGGER.error("Failed to create ListFilterItemStack for shuffle filter: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}