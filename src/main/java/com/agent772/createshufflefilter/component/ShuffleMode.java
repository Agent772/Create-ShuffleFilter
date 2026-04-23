package com.agent772.createshufflefilter.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Shuffle filter modes
 */
public enum ShuffleMode implements StringRepresentable {
    EQUAL("equal"),      // All blocks have equal probability
    WEIGHTED("weighted"); // Blocks have configured weights
    
    public static final Codec<ShuffleMode> CODEC = StringRepresentable.fromEnum(ShuffleMode::values);
    
    public static final StreamCodec<ByteBuf, ShuffleMode> STREAM_CODEC = 
        StreamCodec.of(
            (buf, mode) -> buf.writeByte(mode.ordinal()),
            buf -> values()[buf.readByte()]
        );
    
    private final String name;
    
    ShuffleMode(String name) {
        this.name = name;
    }
    
    @Override
    public String getSerializedName() {
        return name;
    }
    
    public ShuffleMode toggle() {
        return this == EQUAL ? WEIGHTED : EQUAL;
    }
}
