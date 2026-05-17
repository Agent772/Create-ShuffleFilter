package com.agent772.createshufflefilter.screen;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.menu.WeightedShuffleFilterMenu;
import com.agent772.createshufflefilter.network.FilterConfigPacket;
import com.agent772.createshufflefilter.screen.widget.WeightEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration screen for weighted shuffle filter.
 * 2 rows of 9 slots with weight edit boxes below each slot.
 * Blocks have configurable probability weights.
 */
public class WeightedShuffleFilterScreen extends BaseShuffleFilterScreen<WeightedShuffleFilterMenu> {

    private final List<WeightEditBox> row1WeightFields = new ArrayList<>();
    private final List<WeightEditBox> row2WeightFields = new ArrayList<>();

    public WeightedShuffleFilterScreen(WeightedShuffleFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getFilterGuiHeight() {
        return getTextureHeight();
    }

    @Override
    protected int getTextureOffsetX() {
        return 0;
    }

    @Override
    protected int getTextureOffsetY() {
        return 102;
    }

    @Override
    protected int getTextureWidth() {
        return 226;
    }

    @Override
    protected int getTextureHeight() {
        return 120;
    }

    @Override
    protected int getRowStartX() {
        return 23;
    }

    @Override
    protected int getRow1Y() {
        return 22;
    }

    @Override
    protected int getRowGap() {
        return 14;
    }

    @Override
    protected int getSlotSpacing() {
        return 21;
    }

    protected int getWeightInputXOffset() {
        return 2;
    }

    protected int getWeightInputYOffsetRow1() {
        return getRow1Y() + 18 + 3;
    }

    protected int getWeightInputYOffsetRow2() {
        return getRow1Y() + 18 + 3 + getRowGap() + 18;
    }

    protected int getWeightInputWidth() {
        return getSlotSpacing();
    }

    protected int getWeightInputHeight() {
        return getRowGap() - 2;
    }

    protected int getWeightInputColumnGap() {
        return getSlotSpacing() + 1;
    }

    protected boolean useWeightInputBorderless() {
        return true;
    }

    protected int getWeightInputTextColor() {
        return 0xE0E0E0;
    }

    @Override
    protected int getButtonY() {
        return 95;
    }

    @Override
    protected int getClearButtonX() {
        return imageWidth - 35;
    }

    @Override
    protected int getSaveButtonX() {
        return imageWidth - 13;
    }

    @Override
    protected ShuffleBlockList getBlockList() {
        return menu.getBlockList();
    }

    @Override
    protected int getFilterSlot() {
        return menu.getFilterSlot();
    }

    @Override
    protected void init() {
        super.init();

        this.titleLabelX = (this.imageWidth - 2) / 2 - this.font.width(this.title) / 2;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight + 8;

        initRow1Slots();
        initRow2Slots();

        initWeightFields();

        initButtons();

        loadRow1Slots();
        loadRow2Slots();
        loadWeights();

        updateAllWeightFieldEditability();
    }

    private void initWeightFields() {
        row1WeightFields.clear();
        row2WeightFields.clear();

        int x = leftPos + getRowStartX();
        int y = topPos;
        int columnGap = getWeightInputColumnGap();

        int row1WeightY = y + getWeightInputYOffsetRow1();
        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int fieldX = x + col * columnGap + getWeightInputXOffset();

            WeightEditBox editBox = new WeightEditBox(
                this.font, fieldX, row1WeightY,
                getWeightInputWidth(), getWeightInputHeight(),
                col, this::onWeightChanged
            );

            if (useWeightInputBorderless()) {
                editBox.setBorderless();
            }
            editBox.setTextColor(getWeightInputTextColor());

            row1WeightFields.add(editBox);
            addRenderableWidget(editBox);
        }

        int row2WeightY = y + getWeightInputYOffsetRow2();
        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int fieldX = x + col * columnGap + getWeightInputXOffset();
            int index = SLOTS_PER_ROW + col;

            WeightEditBox editBox = new WeightEditBox(
                this.font, fieldX, row2WeightY,
                getWeightInputWidth(), getWeightInputHeight(),
                index, this::onWeightChanged
            );

            if (useWeightInputBorderless()) {
                editBox.setBorderless();
            }
            editBox.setTextColor(getWeightInputTextColor());

            row2WeightFields.add(editBox);
            addRenderableWidget(editBox);
        }
    }

    private void loadWeights() {
        ShuffleBlockList blockList = getBlockList();

        for (int i = 0; i < SLOTS_PER_ROW && i < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
            int percentage = Math.round(entry.weight() * 100);
            row1WeightFields.get(i).setWeight(percentage);
        }

        for (int i = 0; i < SLOTS_PER_ROW && (i + SLOTS_PER_ROW) < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i + SLOTS_PER_ROW);
            int percentage = Math.round(entry.weight() * 100);
            row2WeightFields.get(i).setWeight(percentage);
        }
    }

    @Override
    protected void onBlockChanged(int index) {
        super.onBlockChanged(index);
        updateWeightFieldEditability(index);
        normalizeWeights();
    }

    private void updateWeightFieldEditability(int index) {
        if (index < SLOTS_PER_ROW) {
            if (index < row1Slots.size() && index < row1WeightFields.size()) {
                boolean hasBlock = row1Slots.get(index).hasBlock();
                row1WeightFields.get(index).setEditable(hasBlock);
                row1WeightFields.get(index).active = hasBlock;
                row1WeightFields.get(index).visible = hasBlock;
                if (!hasBlock) {
                    row1WeightFields.get(index).setWeight(0);
                } else if (row1WeightFields.get(index).getWeight() == 0) {
                    row1WeightFields.get(index).setWeight(1);
                }
            }
        } else {
            int row2Index = index - SLOTS_PER_ROW;
            if (row2Index < row2Slots.size() && row2Index < row2WeightFields.size()) {
                boolean hasBlock = row2Slots.get(row2Index).hasBlock();
                row2WeightFields.get(row2Index).setEditable(hasBlock);
                row2WeightFields.get(row2Index).active = hasBlock;
                row2WeightFields.get(row2Index).visible = hasBlock;
                if (!hasBlock) {
                    row2WeightFields.get(row2Index).setWeight(0);
                } else if (row2WeightFields.get(row2Index).getWeight() == 0) {
                    row2WeightFields.get(row2Index).setWeight(1);
                }
            }
        }
    }

    private void updateAllWeightFieldEditability() {
        for (int i = 0; i < row1Slots.size() && i < row1WeightFields.size(); i++) {
            boolean hasBlock = row1Slots.get(i).hasBlock();
            row1WeightFields.get(i).setEditable(hasBlock);
            row1WeightFields.get(i).active = hasBlock;
            row1WeightFields.get(i).visible = hasBlock;
        }

        for (int i = 0; i < row2Slots.size() && i < row2WeightFields.size(); i++) {
            boolean hasBlock = row2Slots.get(i).hasBlock();
            row2WeightFields.get(i).setEditable(hasBlock);
            row2WeightFields.get(i).active = hasBlock;
            row2WeightFields.get(i).visible = hasBlock;
        }
    }

    private void onWeightChanged(int index, int percentage) {
        normalizeWeights(index);
        saveConfiguration();
    }

    private void normalizeWeights(int fixedIndex) {
        int fixedWeight;
        if (fixedIndex < SLOTS_PER_ROW) {
            fixedWeight = row1WeightFields.get(fixedIndex).getWeight();
        } else {
            fixedWeight = row2WeightFields.get(fixedIndex - SLOTS_PER_ROW).getWeight();
        }

        class FieldInfo {
            WeightEditBox field;
            int currentWeight;

            FieldInfo(WeightEditBox field, int weight) {
                this.field = field;
                this.currentWeight = weight;
            }
        }

        List<FieldInfo> otherFields = new ArrayList<>();

        for (int i = 0; i < row1Slots.size(); i++) {
            if (i == fixedIndex) continue;
            if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                otherFields.add(new FieldInfo(
                    row1WeightFields.get(i),
                    row1WeightFields.get(i).getWeight()
                ));
            }
        }

        for (int i = 0; i < row2Slots.size(); i++) {
            int globalIndex = SLOTS_PER_ROW + i;
            if (globalIndex == fixedIndex) continue;
            if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                otherFields.add(new FieldInfo(
                    row2WeightFields.get(i),
                    row2WeightFields.get(i).getWeight()
                ));
            }
        }

        if (otherFields.isEmpty()) {
            if (fixedIndex < SLOTS_PER_ROW) {
                row1WeightFields.get(fixedIndex).setWeight(100);
            } else {
                row2WeightFields.get(fixedIndex - SLOTS_PER_ROW).setWeight(100);
            }
            return;
        }

        int targetOtherTotal = 100 - fixedWeight;

        if (targetOtherTotal < otherFields.size()) {
            fixedWeight = 100 - otherFields.size();
            if (fixedIndex < SLOTS_PER_ROW) {
                row1WeightFields.get(fixedIndex).setWeight(fixedWeight);
            } else {
                row2WeightFields.get(fixedIndex - SLOTS_PER_ROW).setWeight(fixedWeight);
            }
            for (FieldInfo info : otherFields) {
                info.field.setWeight(1);
            }
            return;
        }

        int currentOtherTotal = 0;
        for (FieldInfo info : otherFields) {
            currentOtherTotal += info.currentWeight;
        }

        int adjustmentNeeded = currentOtherTotal - targetOtherTotal;

        if (adjustmentNeeded == 0) {
            return;
        }

        int countOthers = otherFields.size();
        int perFieldAdjustment = adjustmentNeeded / countOthers;
        int remainder = Math.abs(adjustmentNeeded % countOthers);

        otherFields.sort((a, b) -> Integer.compare(b.currentWeight, a.currentWeight));

        for (int i = 0; i < otherFields.size(); i++) {
            FieldInfo info = otherFields.get(i);

            int adjustment = perFieldAdjustment;

            if (i < remainder) {
                adjustment += (adjustmentNeeded > 0) ? 1 : -1;
            }

            int newWeight = info.currentWeight - adjustment;

            if (newWeight < 1) {
                newWeight = 1;
            }

            info.field.setWeight(newWeight);
        }

        int finalTotal = fixedWeight;
        for (FieldInfo info : otherFields) {
            finalTotal += info.field.getWeight();
        }

        if (finalTotal != 100) {
            int correction = 100 - finalTotal;
            FieldInfo highest = otherFields.get(0);
            int correctedWeight = highest.field.getWeight() + correction;
            if (correctedWeight >= 1) {
                highest.field.setWeight(correctedWeight);
            }
        }
    }

    private void normalizeWeights() {
        int filledCount = 0;
        int totalWeight = 0;

        for (int i = 0; i < row1Slots.size(); i++) {
            if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                filledCount++;
                totalWeight += row1WeightFields.get(i).getWeight();
            }
        }

        for (int i = 0; i < row2Slots.size(); i++) {
            if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                filledCount++;
                totalWeight += row2WeightFields.get(i).getWeight();
            }
        }

        if (filledCount == 0) return;

        if (totalWeight == 0) {
            int equalWeight = 100 / filledCount;
            int remainder = 100 % filledCount;

            int assignedIndex = 0;

            for (int i = 0; i < row1Slots.size(); i++) {
                if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                    int weight = equalWeight + (assignedIndex == 0 ? remainder : 0);
                    row1WeightFields.get(i).setWeight(weight);
                    assignedIndex++;
                }
            }

            for (int i = 0; i < row2Slots.size(); i++) {
                if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                    int weight = equalWeight + (assignedIndex == 0 ? remainder : 0);
                    row2WeightFields.get(i).setWeight(weight);
                    assignedIndex++;
                }
            }
            return;
        }

        int assignedTotal = 0;
        int lastFilledIndex = -1;
        boolean lastInRow1 = true;

        for (int i = 0; i < row1Slots.size(); i++) {
            if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                int currentWeight = row1WeightFields.get(i).getWeight();
                int normalized = Math.round((float) currentWeight * 100 / totalWeight);
                row1WeightFields.get(i).setWeight(normalized);
                assignedTotal += normalized;
                lastFilledIndex = i;
                lastInRow1 = true;
            } else if (i < row1WeightFields.size()) {
                row1WeightFields.get(i).setWeight(0);
            }
        }

        for (int i = 0; i < row2Slots.size(); i++) {
            if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                int currentWeight = row2WeightFields.get(i).getWeight();
                int normalized = Math.round((float) currentWeight * 100 / totalWeight);
                row2WeightFields.get(i).setWeight(normalized);
                assignedTotal += normalized;
                lastFilledIndex = i;
                lastInRow1 = false;
            } else if (i < row2WeightFields.size()) {
                row2WeightFields.get(i).setWeight(0);
            }
        }

        if (lastFilledIndex >= 0 && assignedTotal != 100) {
            int correction = 100 - assignedTotal;
            if (lastInRow1) {
                int adjusted = row1WeightFields.get(lastFilledIndex).getWeight() + correction;
                row1WeightFields.get(lastFilledIndex).setWeight(Math.max(0, adjusted));
            } else {
                int adjusted = row2WeightFields.get(lastFilledIndex).getWeight() + correction;
                row2WeightFields.get(lastFilledIndex).setWeight(Math.max(0, adjusted));
            }
        }
    }

    @Override
    protected void saveConfiguration() {
        List<ItemStack> items = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        for (int i = 0; i < row1Slots.size(); i++) {
            if (row1Slots.get(i).hasBlock()) {
                items.add(row1Slots.get(i).getItem());

                if (i < row1WeightFields.size()) {
                    weights.add(row1WeightFields.get(i).getWeight() / 100.0f);
                } else {
                    weights.add(1.0f);
                }
            }
        }

        for (int i = 0; i < row2Slots.size(); i++) {
            if (row2Slots.get(i).hasBlock()) {
                items.add(row2Slots.get(i).getItem());

                if (i < row2WeightFields.size()) {
                    weights.add(row2WeightFields.get(i).getWeight() / 100.0f);
                } else {
                    weights.add(1.0f);
                }
            }
        }

        FilterConfigPacket.sendToServer(getFilterSlot(), items, weights);
    }
}
