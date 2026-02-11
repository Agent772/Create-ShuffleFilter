package com.agent772.createshufflefilter.migration;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Handles automatic migration of shuffle filters on world load.
 * Scans all locations where filters might be stored and migrates them to new format.
 */
@EventBusSubscriber(modid = CreateShuffleFilter.MODID)
public class MigrationEventHandler {
    
    private static boolean migrationCompleteForLevel = false;
    
    /**
     * Triggered when a level is loaded - perform migration scan
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return; // Client-side or not a server level
        }
        
        if (migrationCompleteForLevel) {
            return; // Already migrated this session
        }
        
        CreateShuffleFilter.LOGGER.info("Starting shuffle filter migration scan for level: {}", 
            serverLevel.dimension().location());
        
        int migratedCount = 0;
        
        try {
            // Scan all players
            for (Player player : serverLevel.players()) {
                migratedCount += migratePlayerInventory(player, serverLevel);
            }
            
            // Scan all block entities (chests, deployers, rollers, etc.)
            migratedCount += migrateBlockEntities(serverLevel);
            
            // Scan all contraptions (mounted structures)
            migratedCount += migrateContraptions(serverLevel);
            
            if (migratedCount > 0) {
                CreateShuffleFilter.LOGGER.info(
                    "Migration complete: {} shuffle filters migrated to new format", 
                    migratedCount
                );
            } else {
                CreateShuffleFilter.LOGGER.info("No shuffle filters needed migration");
            }
            
            migrationCompleteForLevel = true;
            
        } catch (Exception e) {
            CreateShuffleFilter.LOGGER.error("Error during shuffle filter migration", e);
        }
    }
    
    /**
     * Migrate filters in a player's inventory (all slots)
     */
    private static int migratePlayerInventory(Player player, Level level) {
        int count = 0;
        
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            
            if (FilterDataMigration.needsMigration(stack)) {
                ItemStack migrated = FilterDataMigration.migrate(stack, level);
                player.getInventory().setItem(slot, migrated);
                count++;
            }
        }
        
        if (count > 0) {
            CreateShuffleFilter.LOGGER.info("Migrated {} filters in player inventory: {}", 
                count, player.getName().getString());
        }
        
        return count;
    }
    
    /**
     * Migrate filters in all block entities (chests, deployers, rollers, etc.)
     * Note: This currently doesn't scan unloaded chunks.
     * Filters in deployers/rollers will be migrated when contraptions are loaded or disassembled.
     */
    private static int migrateBlockEntities(ServerLevel level) {
        // TODO: Implement chunk-based block entity scanning if needed
        // Most critical filters (in deployers/rollers) are in contraptions which are handled separately
        // Filters in chests will be migrated lazily when players interact with them
        
        CreateShuffleFilter.LOGGER.info("Block entity migration skipped (filters will be migrated on access)");
        return 0;
    }
    
    /**
     * Migrate filters in a single container
     */
    private static int migrateContainer(Container container, Level level) {
        int count = 0;
        
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            
            if (FilterDataMigration.needsMigration(stack)) {
                ItemStack migrated = FilterDataMigration.migrate(stack, level);
                container.setItem(slot, migrated);
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Migrate filters in contraptions (mounted structures)
     */
    private static int migrateContraptions(ServerLevel level) {
        int count = 0;
        
        // Find all contraption entities
        for (var entity : level.getAllEntities()) {
            if (entity instanceof AbstractContraptionEntity contraptionEntity) {
                count += migrateContraption(contraptionEntity, level);
            }
        }
        
        if (count > 0) {
            CreateShuffleFilter.LOGGER.info("Migrated {} filters in contraptions", count);
        }
        
        return count;
    }
    
    /**
     * Migrate filters within a single contraption
     */
    private static int migrateContraption(AbstractContraptionEntity contraptionEntity, Level level) {
        int count = 0;
        
        Contraption contraption = contraptionEntity.getContraption();
        if (contraption == null) {
            return 0;
        }
        
        // Scan contraption's storage (inventory)
        var storage = contraption.getStorage().getAllItems();
        if (storage != null) {
            for (int slot = 0; slot < storage.getSlots(); slot++) {
                ItemStack stack = storage.getStackInSlot(slot);
                
                if (FilterDataMigration.needsMigration(stack)) {
                    ItemStack migrated = FilterDataMigration.migrate(stack, level);
                    storage.setStackInSlot(slot, migrated);
                    count++;
                }
            }
        }
        
        // Note: Deployer/roller filter slots are not accessible via blocks
        // Those will be migrated when the contraption is disassembled or
        // when the deployer/roller is directly accessed
        
        return count;
    }
    
    /**
     * Reset migration flag (for testing or when switching worlds)
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) {
            migrationCompleteForLevel = false;
        }
    }
}
