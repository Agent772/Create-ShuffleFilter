package com.agent772.createshufflefilter.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/**
 * Edit box for weight percentages with scroll wheel support and auto-normalization.
 */
public class WeightEditBox extends EditBox {

    private final int index;
    private final BiConsumer<Integer, Integer> onChanged;
    private int currentWeight = 0;
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME = 500;

    public WeightEditBox(Font font, int x, int y, int width, int height, int index, BiConsumer<Integer, Integer> onChanged) {
        super(font, x, y, width, height, Component.empty());
        this.index = index;
        this.onChanged = onChanged;

        this.setFilter(s -> s.isEmpty() || s.matches("^[0-9]{1,3}$"));
        this.setMaxLength(3);
        this.setValue("0");
    }

    /**
     * Remove border and background.
     */
    public WeightEditBox setBorderless() {
        this.setBordered(false);
        return this;
    }

    /**
     * Set weight percentage (0-100).
     */
    public void setWeight(int weight) {
        this.currentWeight = Math.max(0, Math.min(100, weight));
        this.setValue(String.valueOf(this.currentWeight));
    }

    /**
     * Get current weight percentage.
     */
    public int getWeight() {
        return currentWeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isHoveredOrFocused()) {
            int change = delta > 0 ? 1 : -1;
            int newWeight = Math.max(0, Math.min(100, currentWeight + change));

            if (newWeight != currentWeight) {
                setWeight(newWeight);
                if (onChanged != null) {
                    onChanged.accept(index, newWeight);
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);

        if (result && button == 0) {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastClick = currentTime - lastClickTime;

            if (timeSinceLastClick < DOUBLE_CLICK_TIME) {
                setCursorPosition(getValue().length());
                lastClickTime = 0;
            } else {
                lastClickTime = currentTime;
            }
        }

        return result;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);

        if (wasFocused && !focused) {
            validateAndNotify();
        }
    }

    private void validateAndNotify() {
        String value = getValue();
        int newWeight = 0;

        if (!value.isEmpty()) {
            try {
                newWeight = Integer.parseInt(value);
                newWeight = Math.max(0, Math.min(100, newWeight));
            } catch (NumberFormatException e) {
                newWeight = 0;
            }
        }

        if (newWeight != currentWeight) {
            currentWeight = newWeight;
            setValue(String.valueOf(currentWeight));

            if (onChanged != null) {
                onChanged.accept(index, currentWeight);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            validateAndNotify();
            setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
