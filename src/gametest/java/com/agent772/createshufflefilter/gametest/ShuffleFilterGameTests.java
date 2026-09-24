package com.agent772.createshufflefilter.gametest;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Smoke tests. Run headless with {@code ./gradlew runGameTestServer}; the server exits
 * non-zero if any test fails.
 */
@GameTestHolder(CreateShuffleFilter.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = CreateShuffleFilter.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ShuffleFilterGameTests {

    private static final String EMPTY = "empty";
    private static final int DRAWS = 2000;

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ShuffleFilterGameTests.class);
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void itemsRegister(GameTestHelper helper) {
        helper.assertTrue(CreateShuffleFilter.SHUFFLE_FILTER.get() != null, "shuffle_filter not registered");
        helper.assertTrue(CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER.get() != null, "weighted_shuffle_filter not registered");
        helper.assertFalse(CreateShuffleFilter.SHUFFLE_FILTER.asStack().isEmpty(), "shuffle_filter stack is empty");
        helper.assertFalse(CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER.asStack().isEmpty(), "weighted_shuffle_filter stack is empty");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void blockListRoundTrip(GameTestHelper helper) {
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 0.25f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 0.75f);
        ItemStack stack = CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER.asStack();
        ShuffleBlockList.set(stack, list);

        ShuffleBlockList read = ShuffleBlockList.read(stack);
        helper.assertTrue(list.equals(read), "Round-trip mismatch: wrote " + list + ", read " + read);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void uniformSelection(GameTestHelper helper) {
        ShuffleBlockList list = new ShuffleBlockList(List.of(
            new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "stone"), 1.0f),
            new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "dirt"), 1.0f),
            new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "cobblestone"), 1.0f),
            new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "sand"), 1.0f)));

        Map<ShuffleBlockList.BlockEntry, Integer> counts = draw(helper, list, false);
        for (ShuffleBlockList.BlockEntry entry : list.blocks()) {
            helper.assertTrue(counts.getOrDefault(entry, 0) > 0, entry + " was never selected");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void weightedSelectionBias(GameTestHelper helper) {
        ShuffleBlockList.BlockEntry heavy = new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "stone"), 0.99f);
        ShuffleBlockList.BlockEntry light = new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "dirt"), 0.01f);
        // Light entry first so a bias toward early entries can't mask a broken weighting.
        ShuffleBlockList list = new ShuffleBlockList(List.of(light, heavy));

        Map<ShuffleBlockList.BlockEntry, Integer> counts = draw(helper, list, true);
        int heavyCount = counts.getOrDefault(heavy, 0);
        // Expected ~1980/2000; 90% leaves a wide margin against flakiness.
        helper.assertTrue(heavyCount > DRAWS * 0.9, "Heavy entry picked only " + heavyCount + "/" + DRAWS);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void cascadingDepthLimit(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        ShuffleBlockList stoneOnly = ShuffleBlockList.EMPTY.withBlock(new ResourceLocation("minecraft", "stone"), 1.0f);

        // At the limit, nothing is extracted even though the inventory can satisfy the list.
        ItemStack atLimit = ShuffleFilterUtil.selectItemCascading(
            stoneOnly, false, helper.getLevel(), inv, ShuffleFilterUtil.MAX_CASCADE_DEPTH, new HashSet<>());
        helper.assertTrue(atLimit.isEmpty(), "Expected EMPTY at MAX_CASCADE_DEPTH, got " + atLimit);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 64, "Inventory was modified at depth limit");

        // A filter chain nested deeper than MAX_CASCADE_DEPTH terminates with EMPTY.
        ShuffleBlockList chain = stoneOnly;
        for (int i = 0; i <= ShuffleFilterUtil.MAX_CASCADE_DEPTH; i++) {
            ItemStack filter = (i % 2 == 0 ? CreateShuffleFilter.SHUFFLE_FILTER : CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER).asStack();
            ShuffleBlockList.set(filter, chain);
            chain = ShuffleBlockList.EMPTY.withItemStack(filter, 1.0f);
        }
        ItemStack nested = ShuffleFilterUtil.selectItemCascading(chain, false, helper.getLevel(), inv, 0, new HashSet<>());
        helper.assertTrue(nested.isEmpty(), "Expected EMPTY for over-deep chain, got " + nested);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void menuTypesRegister(GameTestHelper helper) {
        helper.assertTrue(ModMenuTypes.SHUFFLE_FILTER.isPresent(), "shuffle_filter menu not registered");
        helper.assertTrue(ModMenuTypes.WEIGHTED_SHUFFLE_FILTER.isPresent(), "weighted_shuffle_filter menu not registered");
        helper.assertTrue(ModMenuTypes.MENUS.getEntries().size() == 2,
            "Expected 2 menu types, got " + ModMenuTypes.MENUS.getEntries().size());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void craftingRecipeExists(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation(CreateShuffleFilter.MODID, "shuffle_filter");
        var recipe = helper.getLevel().getRecipeManager().byKey(id);
        helper.assertTrue(recipe.isPresent(), "Recipe " + id + " missing");
        ItemStack result = recipe.get().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(CreateShuffleFilter.SHUFFLE_FILTER.get()), "Recipe " + id + " yields " + result);
        helper.succeed();
    }

    private static Map<ShuffleBlockList.BlockEntry, Integer> draw(GameTestHelper helper, ShuffleBlockList list, boolean weighted) {
        Map<ShuffleBlockList.BlockEntry, Integer> counts = new HashMap<>();
        for (int i = 0; i < DRAWS; i++) {
            counts.merge(ShuffleFilterUtil.selectEntry(list, weighted, helper.getLevel()), 1, Integer::sum);
        }
        return counts;
    }
}
