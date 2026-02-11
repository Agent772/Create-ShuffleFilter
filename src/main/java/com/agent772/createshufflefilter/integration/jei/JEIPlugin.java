package com.agent772.createshufflefilter.integration.jei;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.screen.ShuffleFilterScreen;
import com.agent772.createshufflefilter.screen.WeightedShuffleFilterScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * JEI plugin for Create Shuffle Filter
 * Enables drag and drop from JEI into filter screens
 */
@JeiPlugin
public class JEIPlugin implements IModPlugin {
    
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CreateShuffleFilter.MODID, "jei_plugin");
    }
    
    @Override
    public void registerGuiHandlers(@Nonnull IGuiHandlerRegistration registration) {
        // Register ghost ingredient handlers for both filter types
        registration.addGhostIngredientHandler(
            ShuffleFilterScreen.class, 
            new ShuffleFilterGhostHandler()
        );
        
        registration.addGhostIngredientHandler(
            WeightedShuffleFilterScreen.class,
            new WeightedShuffleFilterGhostHandler()
        );
    }
}
