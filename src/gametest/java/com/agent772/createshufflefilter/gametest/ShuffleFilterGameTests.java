package com.agent772.createshufflefilter.gametest;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.item.SkipItem;
import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.agent772.createshufflefilter.util.RollerSelectionUtil;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
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
import java.util.Set;

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
        ShuffleFilterUtil.SelectionResult atLimit = ShuffleFilterUtil.selectItemCascading(
            stoneOnly, false, helper.getLevel(), inv, ShuffleFilterUtil.MAX_CASCADE_DEPTH, new HashSet<>());
        helper.assertTrue(atLimit.isNone(), "Expected NONE at MAX_CASCADE_DEPTH, got " + atLimit);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 64, "Inventory was modified at depth limit");

        // A filter chain nested deeper than MAX_CASCADE_DEPTH terminates with EMPTY.
        ShuffleBlockList chain = stoneOnly;
        for (int i = 0; i <= ShuffleFilterUtil.MAX_CASCADE_DEPTH; i++) {
            ItemStack filter = (i % 2 == 0 ? CreateShuffleFilter.SHUFFLE_FILTER : CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER).asStack();
            ShuffleBlockList.set(filter, chain);
            chain = ShuffleBlockList.EMPTY.withItemStack(filter, 1.0f);
        }
        ShuffleFilterUtil.SelectionResult nested = ShuffleFilterUtil.selectItemCascading(chain, false, helper.getLevel(), inv, 0, new HashSet<>());
        helper.assertTrue(nested.isNone(), "Expected NONE for over-deep chain, got " + nested);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void skipItemRegisters(GameTestHelper helper) {
        ItemStack skip = CreateShuffleFilter.SKIP.asStack();
        helper.assertTrue(skip.getItem() instanceof SkipItem, "skip not registered as SkipItem");
        helper.assertTrue(skip.getMaxStackSize() == 1, "Skip should stack to 1, got " + skip.getMaxStackSize());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void skipEntryRoundTrip(GameTestHelper helper) {
        ShuffleBlockList list = ShuffleBlockList.EMPTY.withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);
        ItemStack filter = CreateShuffleFilter.SHUFFLE_FILTER.asStack();
        ShuffleBlockList.set(filter, list);

        ShuffleBlockList read = ShuffleBlockList.read(filter);
        helper.assertTrue(read.size() == 1 && ShuffleFilterUtil.isSkipEntry(read.blocks().get(0)),
            "Skip entry lost in round-trip: " + read);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void skipSelectionExtractsNothing(GameTestHelper helper) {
        // A Skip item sitting in the inventory must not be extracted when the Skip entry rolls.
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, CreateShuffleFilter.SKIP.asStack());
        ShuffleBlockList skipOnly = ShuffleBlockList.EMPTY.withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        ShuffleFilterUtil.SelectionResult result = ShuffleFilterUtil.selectItemCascading(
            skipOnly, false, helper.getLevel(), inv, 0, new HashSet<>());
        helper.assertTrue(result.isSkip(), "Expected SKIP, got " + result);
        helper.assertTrue(result.stack().isEmpty(), "SKIP must carry no stack");
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 1, "Inventory was modified on skip");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void skipMixesWithBlocks(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        int skips = 0;
        int stones = 0;
        for (int i = 0; i < 64; i++) {
            ShuffleFilterUtil.SelectionResult r = ShuffleFilterUtil.selectItemCascading(
                list, false, helper.getLevel(), inv, 0, new HashSet<>());
            if (r.isSkip()) skips++;
            else if (r.stack().is(Items.STONE)) stones++;
        }
        helper.assertTrue(skips > 0 && stones > 0, "Expected both outcomes, got skips=" + skips + " stones=" + stones);
        helper.assertTrue(skips + stones == 64, "Unexpected NONE results");
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 64 - stones, "Stone extracted on skip rolls");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void skipNeverUsedAsFallback(GameTestHelper helper) {
        // Dirt always rolls (Skip weight 0) but is missing; fallback must not turn into SKIP.
        ItemStackHandler inv = new ItemStackHandler(1);
        ShuffleBlockList list = new ShuffleBlockList(List.of(
            new ShuffleBlockList.BlockEntry(new ResourceLocation("minecraft", "dirt"), 1.0f),
            new ShuffleBlockList.BlockEntry(new ResourceLocation(CreateShuffleFilter.MODID, "skip"), 0.0f)));

        ShuffleFilterUtil.SelectionResult result = ShuffleFilterUtil.selectItemCascading(
            list, true, helper.getLevel(), inv, 0, new HashSet<>());
        helper.assertTrue(result.isNone(), "Expected NONE, got " + result);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void nestedSkipPropagates(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(1);
        ItemStack inner = CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER.asStack();
        ShuffleBlockList.set(inner, ShuffleBlockList.EMPTY.withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f));
        ShuffleBlockList outer = ShuffleBlockList.EMPTY.withItemStack(inner, 1.0f);

        ShuffleFilterUtil.SelectionResult result = ShuffleFilterUtil.selectItemCascading(
            outer, false, helper.getLevel(), inv, 0, new HashSet<>());
        helper.assertTrue(result.isSkip(), "Expected SKIP from nested filter, got " + result);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void allowListIgnoresSkip(GameTestHelper helper) {
        ItemStack filter = CreateShuffleFilter.SHUFFLE_FILTER.asStack();
        ShuffleBlockList.set(filter, ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f));
        FilterItemStack wrapper = FilterItemStack.of(filter);

        helper.assertFalse(wrapper.test(helper.getLevel(), CreateShuffleFilter.SKIP.asStack()),
            "Skip item must not pass an allow-list containing Skip");
        helper.assertTrue(wrapper.test(helper.getLevel(), new ItemStack(Items.STONE)),
            "Stone should still pass the allow-list");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerSkipAvailableWithoutInventory(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(1);
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        helper.assertTrue(RollerSelectionUtil.isEntryAvailable(CreateShuffleFilter.SKIP.asStack(), inv, 0),
            "Skip should be available with an empty inventory");
        List<ShuffleBlockList.BlockEntry> available = RollerSelectionUtil.getAvailableBlocks(list, inv);
        helper.assertTrue(available.size() == 1 && ShuffleFilterUtil.isSkipEntry(available.get(0)),
            "Expected only Skip available, got " + available);

        for (int x = 0; x < 32; x++) {
            ShuffleFilterUtil.SelectionResult r = RollerSelectionUtil.selectBlockForPosition(
                list, false, new BlockPos(x, 64, 0), helper.getLevel(), inv);
            helper.assertTrue(r.isSkip(), "Expected SKIP at x=" + x + ", got " + r);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerSkipSelectionDeterministic(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        int skips = 0;
        for (int x = 0; x < 64; x++) {
            BlockPos pos = new BlockPos(x, 64, 0);
            ShuffleFilterUtil.SelectionResult first = RollerSelectionUtil.selectBlockForPosition(list, false, pos, helper.getLevel(), inv);
            ShuffleFilterUtil.SelectionResult second = RollerSelectionUtil.selectBlockForPosition(list, false, pos, helper.getLevel(), inv);
            helper.assertTrue(first.isSkip() == second.isSkip()
                    && first.stack().getItem() == second.stack().getItem(),
                "Non-deterministic at " + pos + ": " + first + " vs " + second);
            if (first.isSkip()) skips++;
            else helper.assertTrue(first.stack().is(Items.STONE), "Expected stone or SKIP, got " + first);
        }
        helper.assertTrue(skips > 0 && skips < 64, "Expected a mix of stone and SKIP, got skips=" + skips);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 64, "Selection must not extract");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerAlternateSlabIgnoresSkip(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, CreateShuffleFilter.SKIP.asStack());
        inv.setStackInSlot(1, new ItemStack(Items.STONE_SLAB, 64));
        ItemStack skipStack = CreateShuffleFilter.SKIP.asStack();

        ItemStack none = RollerSelectionUtil.findAlternateBlockWithSlab(
            ShuffleBlockList.EMPTY.withItemStack(skipStack, 1.0f), inv, helper.getLevel());
        helper.assertTrue(none.isEmpty(), "Skip offered as slab candidate: " + none);

        ItemStack slab = RollerSelectionUtil.findAlternateBlockWithSlab(
            ShuffleBlockList.EMPTY.withItemStack(skipStack, 1.0f)
                .withBlock(new ResourceLocation("minecraft", "stone_slab"), 1.0f),
            inv, helper.getLevel());
        helper.assertTrue(slab.is(Items.STONE_SLAB), "Expected stone_slab, got " + slab);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerNestedSkipExtractsNothing(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, CreateShuffleFilter.SKIP.asStack());
        ItemStack nested = CreateShuffleFilter.SHUFFLE_FILTER.asStack();
        ShuffleBlockList.set(nested, ShuffleBlockList.EMPTY.withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f));

        ItemStack extracted = RollerSelectionUtil.extractBlockFromCascadingFilter(
            nested, new BlockPos(0, 64, 0), helper.getLevel(), inv, 0);
        helper.assertTrue(extracted.isEmpty(), "Expected EMPTY for nested Skip, got " + extracted);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 1, "Skip item was extracted from inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerFillVariesVertically(GameTestHelper helper) {
        // The fill fix re-rolls each position (seeded by its full BlockPos, Y included). With two
        // materials stocked, a vertical column must not collapse to a single material.
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.DIRT, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 1.0f);

        Set<Item> seen = new HashSet<>();
        for (int y = 0; y < 32; y++) {
            ShuffleFilterUtil.SelectionResult r = RollerSelectionUtil.selectBlockForPosition(
                list, false, new BlockPos(7, y, 3), helper.getLevel(), inv);
            helper.assertFalse(r.isSkip(), "Unexpected SKIP at y=" + y);
            seen.add(r.stack().getItem());
        }
        helper.assertTrue(seen.size() >= 2, "Column filled with a single material: " + seen);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 64 && inv.getStackInSlot(1).getCount() == 64,
            "Selection must not extract");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerFillPerPositionDeterministic(GameTestHelper helper) {
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.DIRT, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 1.0f);

        for (int y = 0; y < 32; y++) {
            BlockPos pos = new BlockPos(7, y, 3);
            ShuffleFilterUtil.SelectionResult first = RollerSelectionUtil.selectBlockForPosition(list, false, pos, helper.getLevel(), inv);
            ShuffleFilterUtil.SelectionResult second = RollerSelectionUtil.selectBlockForPosition(list, false, pos, helper.getLevel(), inv);
            helper.assertTrue(first.stack().getItem() == second.stack().getItem(),
                "Non-deterministic at " + pos + ": " + first + " vs " + second);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void rollerFillSkipPerPosition(GameTestHelper helper) {
        // Stone + Skip down a column: some positions leave a hole (SKIP), others get stone, so
        // the column keeps descending past individual holes.
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        int skips = 0;
        int stones = 0;
        for (int y = 0; y < 64; y++) {
            ShuffleFilterUtil.SelectionResult r = RollerSelectionUtil.selectBlockForPosition(
                list, false, new BlockPos(2, y, 9), helper.getLevel(), inv);
            if (r.isSkip()) skips++;
            else if (r.stack().is(Items.STONE)) stones++;
        }
        helper.assertTrue(skips > 0 && stones > 0,
            "Expected both Skip and stone down the column, got skips=" + skips + " stones=" + stones);
        helper.assertTrue(inv.getStackInSlot(0).getCount() == 64, "Selection must not extract");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void decideFillRerollsPerPosition(GameTestHelper helper) {
        // The core of the fix: on the non-slab path the block comes from a per-position re-roll,
        // NOT from the column-level lastSelected. lastSelected is pinned to stone; the decision
        // must still yield dirt at some Y levels down the column. The old code (which reused
        // lastSelected for every position) could never produce dirt here.
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.DIRT, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 1.0f);
        ItemStack lastSelected = new ItemStack(Items.STONE);
        BlockState fullBlock = Blocks.STONE.defaultBlockState();

        Set<Item> placed = new HashSet<>();
        for (int y = 0; y < 32; y++) {
            RollerSelectionUtil.FillDecision d = RollerSelectionUtil.decideFill(
                list, false, new BlockPos(7, y, 3), fullBlock, lastSelected,
                helper.getLevel(), inv, Blocks.AIR.defaultBlockState());
            helper.assertTrue(d.outcome() == RollerSelectionUtil.FillOutcome.PLACE,
                "Expected PLACE at y=" + y + ", got " + d.outcome());
            placed.add(d.toExtract().getItem());
            // toPlace is derived from the re-roll, not from lastSelected (stone).
            helper.assertTrue(d.toPlace().is(Blocks.STONE) || d.toPlace().is(Blocks.DIRT),
                "toPlace must be a re-rolled filter block at y=" + y + ", got " + d.toPlace());
        }
        helper.assertTrue(placed.contains(Items.DIRT),
            "Non-slab path never re-rolled off lastSelected (stone): " + placed);
        helper.assertTrue(placed.size() >= 2, "Column collapsed to a single material: " + placed);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void decideFillSlabPassKeepsLastSelected(GameTestHelper helper) {
        // Slab paving pass (BOTTOM/TOP): no re-roll, lastSelected and the partial-slab toPlace
        // are both preserved.
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.DIRT, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 1.0f);
        ItemStack lastSelected = new ItemStack(Items.DIRT);
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        for (int y = 0; y < 16; y++) {
            RollerSelectionUtil.FillDecision d = RollerSelectionUtil.decideFill(
                list, false, new BlockPos(1, y, 1), slab, lastSelected,
                helper.getLevel(), inv, Blocks.AIR.defaultBlockState());
            helper.assertTrue(d.outcome() == RollerSelectionUtil.FillOutcome.PLACE,
                "Expected PLACE at y=" + y + ", got " + d.outcome());
            helper.assertTrue(d.toExtract().is(Items.DIRT), "Slab pass must keep lastSelected, got " + d.toExtract());
            helper.assertTrue(d.toPlace().is(Blocks.STONE_SLAB)
                    && d.toPlace().getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
                "Slab pass must keep the partial-slab toPlace, got " + d.toPlace());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void decideFillExistingFilterBlockPasses(GameTestHelper helper) {
        // A position that already holds a filter block is left alone (idempotent re-visit),
        // regardless of what the re-roll picked.
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.DIRT, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 1.0f);

        for (int y = 0; y < 32; y++) {
            RollerSelectionUtil.FillDecision d = RollerSelectionUtil.decideFill(
                list, false, new BlockPos(4, y, 4), Blocks.STONE.defaultBlockState(),
                new ItemStack(Items.STONE), helper.getLevel(), inv, Blocks.DIRT.defaultBlockState());
            helper.assertTrue(d.outcome() == RollerSelectionUtil.FillOutcome.PASS_ALREADY_FILLED,
                "Existing filter block must PASS at y=" + y + ", got " + d.outcome());
        }
        // A non-filter block is not treated as already filled.
        RollerSelectionUtil.FillDecision place = RollerSelectionUtil.decideFill(
            list, false, new BlockPos(4, 0, 5), Blocks.STONE.defaultBlockState(),
            new ItemStack(Items.STONE), helper.getLevel(), inv, Blocks.GLASS.defaultBlockState());
        helper.assertTrue(place.outcome() == RollerSelectionUtil.FillOutcome.PLACE,
            "Non-filter block should not PASS, got " + place.outcome());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void decideFillSkipLeavesHole(GameTestHelper helper) {
        // stone + Skip down a column: some positions decide PASS_SKIP (hole), others PLACE stone.
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        int skips = 0;
        int places = 0;
        for (int y = 0; y < 64; y++) {
            RollerSelectionUtil.FillDecision d = RollerSelectionUtil.decideFill(
                list, false, new BlockPos(2, y, 9), Blocks.STONE.defaultBlockState(),
                new ItemStack(Items.STONE), helper.getLevel(), inv, Blocks.AIR.defaultBlockState());
            if (d.outcome() == RollerSelectionUtil.FillOutcome.PASS_SKIP) skips++;
            else if (d.outcome() == RollerSelectionUtil.FillOutcome.PLACE && d.toExtract().is(Items.STONE)) places++;
        }
        helper.assertTrue(skips > 0 && places > 0,
            "Expected both holes and stone down the column, got skips=" + skips + " places=" + places);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = CreateShuffleFilter.MODID)
    public static void isFilterBlockMatches(GameTestHelper helper) {
        ShuffleBlockList list = ShuffleBlockList.EMPTY
            .withBlock(new ResourceLocation("minecraft", "stone"), 1.0f)
            .withBlock(new ResourceLocation("minecraft", "dirt"), 1.0f)
            .withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);

        helper.assertTrue(RollerSelectionUtil.isFilterBlock(Blocks.STONE.defaultBlockState(), list),
            "stone should match a filter block");
        helper.assertTrue(RollerSelectionUtil.isFilterBlock(Blocks.DIRT.defaultBlockState(), list),
            "dirt should match a filter block");
        helper.assertFalse(RollerSelectionUtil.isFilterBlock(Blocks.COBBLESTONE.defaultBlockState(), list),
            "cobblestone is not in the filter");

        ShuffleBlockList skipOnly = ShuffleBlockList.EMPTY.withItemStack(CreateShuffleFilter.SKIP.asStack(), 1.0f);
        helper.assertFalse(RollerSelectionUtil.isFilterBlock(Blocks.STONE.defaultBlockState(), skipOnly),
            "Skip entry must not match a placed block");
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
