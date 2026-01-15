package com.agent772.createshufflefilter.mixins;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/**
 * Mixin connector to ensure our mixin config is loaded in development environment
 */
public class MixinConnector implements IMixinConnector {
    @Override
    public void connect() {
        Mixins.addConfiguration("createshufflefilter.mixins.json");
    }
}
