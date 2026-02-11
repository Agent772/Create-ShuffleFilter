package com.agent772.createshufflefilter.screen;

import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.component.ShuffleMode;
import com.agent772.createshufflefilter.menu.WeightedShuffleFilterMenu;
import com.agent772.createshufflefilter.network.FilterConfigPacket;
import com.agent772.createshufflefilter.screen.widget.WeightEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration screen for weighted shuffle filter
 * 2 rows of 9 slots with weight edit boxes below each slot
 * Blocks have configurable probability weights
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
        return 0; // Or different if your weighted texture is elsewhere
    }
    
    @Override
    protected int getTextureOffsetY() {
        return 102; // Below the equal filter texture (adjust based on your texture layout)
    }
    
    @Override
    protected int getTextureWidth() {
        return 226; // Same width
    }
    
    @Override
    protected int getTextureHeight() {
        return 120; // Weighted filter height (adjust as needed)
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
    
    // Weight input configuration
    protected int getWeightInputXOffset() {
        return 2; // Offset from slot X to center weight input
    }
    
    protected int getWeightInputYOffsetRow1() {
        return getRow1Y() + 18 + 3; // Y position below row 1 (relative to topPos)
    }
    
    protected int getWeightInputYOffsetRow2() {
        return getRow1Y() + 18 + 3 + getRowGap() + 18; // Y position below row 2 (relative to topPos)
    }
    
    protected int getWeightInputWidth() {
        return getSlotSpacing(); // Width of weight input box
    }
    
    protected int getWeightInputHeight() {
        return getRowGap()-2; // Height of weight input box
    }
    
    protected int getWeightInputColumnGap() {
        return getSlotSpacing() + 1; // Same as slot spacing by default
    }
    
    // Weight input appearance configuration
    protected boolean useWeightInputBorderless() {
        return true; // Remove border and background from input boxes
    }
    
    protected int getWeightInputTextColor() {
        return 0xE0E0E0; // Light gray (default EditBox color)
    }
    
    @Override
    protected int getButtonY() {
        return 95;
    }
    
    @Override
    protected int getClearButtonX() {
        return imageWidth - 35; // Right side, 22px spacing to save button
    }
    
    @Override
    protected int getSaveButtonX() {
        return imageWidth - 13; // Right side
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
        
        // Center title (Create style)
        this.titleLabelX = (this.imageWidth - 2) / 2 - this.font.width(this.title) / 2;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight + 8;
        
        // Initialize row 1 and row 2 (from base class)
        initRow1Slots();
        initRow2Slots();
        
        // Initialize weight edit boxes (weighted mode specific - 2 rows)
        initWeightFields();
        
        // Initialize buttons (from base class)
        initButtons();
        
        // Load current configuration
        loadRow1Slots();
        loadRow2Slots();
        loadWeights();
        
        // Set initial editability based on slot content
        updateAllWeightFieldEditability();
    }
    
    /**
     * Initialize weight edit boxes below both rows of slots
     */
    private void initWeightFields() {
        row1WeightFields.clear();
        row2WeightFields.clear();
        
        int x = leftPos + getRowStartX();
        int y = topPos;
        int columnGap = getWeightInputColumnGap();
        
        // Row 1 weight fields
        int row1WeightY = y + getWeightInputYOffsetRow1();
        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int fieldX = x + col * columnGap + getWeightInputXOffset();
            
            WeightEditBox editBox = new WeightEditBox(
                this.font, fieldX, row1WeightY, 
                getWeightInputWidth(), getWeightInputHeight(),
                col, this::onWeightChanged
            );
            
            // Apply appearance configuration
            if (useWeightInputBorderless()) {
                editBox.setBorderless();
            }
            editBox.setTextColor(getWeightInputTextColor());
            
            row1WeightFields.add(editBox);
            addRenderableWidget(editBox);
        }
        
        // Row 2 weight fields
        int row2WeightY = y + getWeightInputYOffsetRow2();
        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            int fieldX = x + col * columnGap + getWeightInputXOffset();
            int index = SLOTS_PER_ROW + col; // 9-17
            
            WeightEditBox editBox = new WeightEditBox(
                this.font, fieldX, row2WeightY,
                getWeightInputWidth(), getWeightInputHeight(),
                index, this::onWeightChanged
            );
            
            // Apply appearance configuration
            if (useWeightInputBorderless()) {
                editBox.setBorderless();
            }
            editBox.setTextColor(getWeightInputTextColor());
            
            row2WeightFields.add(editBox);
            addRenderableWidget(editBox);
        }
    }
    
    /**
     * Load weights from configuration for both rows
     */
    private void loadWeights() {
        ShuffleBlockList blockList = getBlockList();
        
        // Load row 1 weights
        for (int i = 0; i < SLOTS_PER_ROW && i < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i);
            int percentage = Math.round(entry.weight() * 100);
            row1WeightFields.get(i).setWeight(percentage);
        }
        
        // Load row 2 weights
        for (int i = 0; i < SLOTS_PER_ROW && (i + SLOTS_PER_ROW) < blockList.size(); i++) {
            ShuffleBlockList.BlockEntry entry = blockList.blocks().get(i + SLOTS_PER_ROW);
            int percentage = Math.round(entry.weight() * 100);
            row2WeightFields.get(i).setWeight(percentage);
        }
    }
    
    /**
     * Called when a block is added/removed from a slot
     */
    @Override
    protected void onBlockChanged(int index) {
        super.onBlockChanged(index);
        // Update weight field editability based on slot content
        updateWeightFieldEditability(index);
        // Re-normalize weights after block change
        normalizeWeights();
    }
    
    /**
     * Update editability for a specific weight field based on its slot content
     */
    private void updateWeightFieldEditability(int index) {
        if (index < SLOTS_PER_ROW) {
            // Row 1
            if (index < row1Slots.size() && index < row1WeightFields.size()) {
                boolean hasBlock = row1Slots.get(index).hasBlock();
                row1WeightFields.get(index).setEditable(hasBlock);
                row1WeightFields.get(index).active = hasBlock; // Prevent focus when no block
                row1WeightFields.get(index).visible = hasBlock; // Hide when no block
                if (!hasBlock) {
                    row1WeightFields.get(index).setWeight(0);
                } else if (row1WeightFields.get(index).getWeight() == 0) {
                    // Initialize weight to equal distribution when first added
                    row1WeightFields.get(index).setWeight(0);
                }
            }
        } else {
            // Row 2
            int row2Index = index - SLOTS_PER_ROW;
            if (row2Index < row2Slots.size() && row2Index < row2WeightFields.size()) {
                boolean hasBlock = row2Slots.get(row2Index).hasBlock();
                row2WeightFields.get(row2Index).setEditable(hasBlock);
                row2WeightFields.get(row2Index).active = hasBlock; // Prevent focus when no block
                row2WeightFields.get(row2Index).visible = hasBlock; // Hide when no block
                if (!hasBlock) {
                    row2WeightFields.get(row2Index).setWeight(0);
                } else if (row2WeightFields.get(row2Index).getWeight() == 0) {
                    // Initialize weight to equal distribution when first added
                    row2WeightFields.get(row2Index).setWeight(1);
                }
            }
        }
    }
    
    /**
     * Update editability for all weight fields based on slot content
     */
    private void updateAllWeightFieldEditability() {
        // Row 1
        for (int i = 0; i < row1Slots.size() && i < row1WeightFields.size(); i++) {
            boolean hasBlock = row1Slots.get(i).hasBlock();
            row1WeightFields.get(i).setEditable(hasBlock);
            row1WeightFields.get(i).active = hasBlock; // Prevent focus when no block
            row1WeightFields.get(i).visible = hasBlock; // Hide when no block
        }
        
        // Row 2
        for (int i = 0; i < row2Slots.size() && i < row2WeightFields.size(); i++) {
            boolean hasBlock = row2Slots.get(i).hasBlock();
            row2WeightFields.get(i).setEditable(hasBlock);
            row2WeightFields.get(i).active = hasBlock; // Prevent focus when no block
            row2WeightFields.get(i).visible = hasBlock; // Hide when no block
        }
    }
    
    /**
     * Called when a weight is changed
     */
    private void onWeightChanged(int index, int percentage) {
        // Keep the entered value fixed, adjust all others equally
        normalizeWeights(index);
        // Send update to server
        saveConfiguration();
    }
    
    /**
     * Normalize all weights so they sum to 100%
     * Keeps the specified index fixed and adjusts all others by equal amounts
     * Fractional remainders are taken from/given to the highest weight field
     * No value goes below 1
     * 
     * @param fixedIndex The index that was just changed (should not be adjusted)
     */
    private void normalizeWeights(int fixedIndex) {
        // Get the fixed weight value
        int fixedWeight;
        if (fixedIndex < SLOTS_PER_ROW) {
            fixedWeight = row1WeightFields.get(fixedIndex).getWeight();
        } else {
            fixedWeight = row2WeightFields.get(fixedIndex - SLOTS_PER_ROW).getWeight();
        }
        
        // Collect all other filled slots with their info
        class FieldInfo {
            WeightEditBox field;
            int currentWeight;
            int index;
            boolean inRow1;
            
            FieldInfo(WeightEditBox field, int weight, int index, boolean inRow1) {
                this.field = field;
                this.currentWeight = weight;
                this.index = index;
                this.inRow1 = inRow1;
            }
        }
        
        List<FieldInfo> otherFields = new ArrayList<>();
        
        // Collect from row 1
        for (int i = 0; i < row1Slots.size(); i++) {
            if (i == fixedIndex) continue;
            if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                otherFields.add(new FieldInfo(
                    row1WeightFields.get(i),
                    row1WeightFields.get(i).getWeight(),
                    i,
                    true
                ));
            }
        }
        
        // Collect from row 2
        for (int i = 0; i < row2Slots.size(); i++) {
            int globalIndex = SLOTS_PER_ROW + i;
            if (globalIndex == fixedIndex) continue;
            if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                otherFields.add(new FieldInfo(
                    row2WeightFields.get(i),
                    row2WeightFields.get(i).getWeight(),
                    globalIndex,
                    false
                ));
            }
        }
        
        // If only one slot filled, set it to 100
        if (otherFields.isEmpty()) {
            if (fixedIndex < SLOTS_PER_ROW) {
                row1WeightFields.get(fixedIndex).setWeight(100);
            } else {
                row2WeightFields.get(fixedIndex - SLOTS_PER_ROW).setWeight(100);
            }
            return;
        }
        
        // Calculate target for others
        int targetOtherTotal = 100 - fixedWeight;
        
        // Ensure we have room for minimum 1 per field
        if (targetOtherTotal < otherFields.size()) {
            // Not enough room - clamp fixed weight and set others to 1
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
        
        // Calculate current total of others
        int currentOtherTotal = 0;
        for (FieldInfo info : otherFields) {
            currentOtherTotal += info.currentWeight;
        }
        
        // Calculate adjustment needed (positive = need to reduce, negative = need to increase)
        int adjustmentNeeded = currentOtherTotal - targetOtherTotal;
        
        if (adjustmentNeeded == 0) {
            return; // Already correct
        }
        
        // Calculate per-field adjustment
        int countOthers = otherFields.size();
        int perFieldAdjustment = adjustmentNeeded / countOthers;
        int remainder = Math.abs(adjustmentNeeded % countOthers);
        
        // Sort by current weight descending to handle remainder
        otherFields.sort((a, b) -> Integer.compare(b.currentWeight, a.currentWeight));
        
        // Apply adjustments
        for (int i = 0; i < otherFields.size(); i++) {
            FieldInfo info = otherFields.get(i);
            
            // Base adjustment
            int adjustment = perFieldAdjustment;
            
            // Add extra for remainder (take from/give to highest weight fields)
            if (i < remainder) {
                adjustment += (adjustmentNeeded > 0) ? 1 : -1;
            }
            
            // Calculate new weight
            int newWeight = info.currentWeight - adjustment;
            
            // Ensure minimum of 1
            if (newWeight < 1) {
                newWeight = 1;
            }
            
            info.field.setWeight(newWeight);
        }
        
        // Final verification and correction
        int finalTotal = fixedWeight;
        for (FieldInfo info : otherFields) {
            finalTotal += info.field.getWeight();
        }
        
        // If not exactly 100, adjust the highest weight field
        if (finalTotal != 100) {
            int correction = 100 - finalTotal;
            FieldInfo highest = otherFields.get(0); // Already sorted by weight descending
            int correctedWeight = highest.field.getWeight() + correction;
            if (correctedWeight >= 1) {
                highest.field.setWeight(correctedWeight);
            }
        }
    }
    
    /**
     * Normalize all weights proportionally (used when blocks are added/removed)
     */
    private void normalizeWeights() {
        // Count filled slots across both rows
        int filledCount = 0;
        int totalWeight = 0;
        
        // Count row 1
        for (int i = 0; i < row1Slots.size(); i++) {
            if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                filledCount++;
                totalWeight += row1WeightFields.get(i).getWeight();
            }
        }
        
        // Count row 2
        for (int i = 0; i < row2Slots.size(); i++) {
            if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                filledCount++;
                totalWeight += row2WeightFields.get(i).getWeight();
            }
        }
        
        if (filledCount == 0) return;
        
        // If total is 0, distribute equally
        if (totalWeight == 0) {
            int equalWeight = 100 / filledCount;
            int remainder = 100 % filledCount;
            
            int assignedIndex = 0;
            
            // Distribute to row 1
            for (int i = 0; i < row1Slots.size(); i++) {
                if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                    int weight = equalWeight + (assignedIndex == 0 ? remainder : 0);
                    row1WeightFields.get(i).setWeight(weight);
                    assignedIndex++;
                }
            }
            
            // Distribute to row 2
            for (int i = 0; i < row2Slots.size(); i++) {
                if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                    int weight = equalWeight + (assignedIndex == 0 ? remainder : 0);
                    row2WeightFields.get(i).setWeight(weight);
                    assignedIndex++;
                }
            }
            return;
        }
        
        // Normalize so all weights sum to 100
        int assignedTotal = 0;
        int lastFilledIndex = -1;
        boolean lastInRow1 = true;
        
        // Normalize row 1
        for (int i = 0; i < row1Slots.size(); i++) {
            if (row1Slots.get(i).hasBlock() && i < row1WeightFields.size()) {
                int currentWeight = row1WeightFields.get(i).getWeight();
                int normalized = Math.round((float) currentWeight * 100 / totalWeight);
                row1WeightFields.get(i).setWeight(normalized);
                assignedTotal += normalized;
                lastFilledIndex = i;
                lastInRow1 = true;
            } else if (i < row1WeightFields.size()) {
                // Empty slots get 0 weight
                row1WeightFields.get(i).setWeight(0);
            }
        }
        
        // Normalize row 2
        for (int i = 0; i < row2Slots.size(); i++) {
            if (row2Slots.get(i).hasBlock() && i < row2WeightFields.size()) {
                int currentWeight = row2WeightFields.get(i).getWeight();
                int normalized = Math.round((float) currentWeight * 100 / totalWeight);
                row2WeightFields.get(i).setWeight(normalized);
                assignedTotal += normalized;
                lastFilledIndex = i;
                lastInRow1 = false;
            } else if (i < row2WeightFields.size()) {
                // Empty slots get 0 weight
                row2WeightFields.get(i).setWeight(0);
            }
        }
        
        // Fix rounding errors - adjust the last field
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
        // Collect configured blocks and weights from both rows
        List<ItemStack> items = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        
        // Row 1 blocks with weights
        for (int i = 0; i < row1Slots.size(); i++) {
            if (row1Slots.get(i).hasBlock()) {
                items.add(row1Slots.get(i).getItem());
                
                if (i < row1WeightFields.size()) {
                    // Convert percentage to decimal (0-100 -> 0.0-1.0)
                    weights.add(row1WeightFields.get(i).getWeight() / 100.0f);
                } else {
                    weights.add(1.0f); // Fallback
                }
            }
        }
        
        // Row 2 blocks with weights
        for (int i = 0; i < row2Slots.size(); i++) {
            if (row2Slots.get(i).hasBlock()) {
                items.add(row2Slots.get(i).getItem());
                
                if (i < row2WeightFields.size()) {
                    // Convert percentage to decimal (0-100 -> 0.0-1.0)
                    weights.add(row2WeightFields.get(i).getWeight() / 100.0f);
                } else {
                    weights.add(1.0f); // Fallback
                }
            }
        }
        
        // Send packet to server
        FilterConfigPacket.sendToServer(
            getFilterSlot(),
            ShuffleMode.WEIGHTED,
            items,
            weights
        );
    }
}
