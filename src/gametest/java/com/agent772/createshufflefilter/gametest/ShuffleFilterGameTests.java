package com.agent772.createshufflefilter.gametest;

import com.agent772.createshufflefilter.CreateShuffleFilter;
import com.agent772.createshufflefilter.component.ModDataComponents;
import com.agent772.createshufflefilter.component.ShuffleBlockList;
import com.agent772.createshufflefilter.component.ShuffleBlockList.BlockEntry;
import com.agent772.createshufflefilter.menu.ModMenuTypes;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil;
import com.agent772.createshufflefilter.util.ShuffleFilterUtil.SelectionResult;
import com.tterrag.registrate.util.entry.ItemEntry;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Smoke tests run headless via {@code ./gradlew runGameTestServer}.
 * All tests use the 1x1x1 air template at {@code data/createshufflefilter/structure/empty.nbt}.
 */
@GameTestHolder(CreateShuffleFilter.MODID)
@PrefixGameTestTemplate(false)
public class ShuffleFilterGameTests {

    private static final String EMPTY = "empty";
    private static final int DRAWS = 1000;

    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("stone");
    private static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("dirt");
    private static final ResourceLocation DIAMOND = ResourceLocation.withDefaultNamespace("diamond");
    private static final ResourceLocation OAK_LOG = ResourceLocation.withDefaultNamespace("oak_log");

    @GameTest(template = EMPTY)
    public static void itemsRegister(GameTestHelper helper) {
        for (ItemEntry<?> entry : List.of(CreateShuffleFilter.SHUFFLE_FILTER,
                CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER, CreateShuffleFilter.SKIP)) {
            Item item = entry.get();
            helper.assertTrue(item != null, entry.getId() + " resolved to null");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(entry.getId()), entry.getId() + " missing from item registry");
            helper.assertFalse(new ItemStack(item).isEmpty(), entry.getId() + " stack is empty");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void emptyBlockList(GameTestHelper helper) {
        helper.assertTrue(ShuffleBlockList.EMPTY.isEmpty(), "EMPTY.isEmpty()");
        helper.assertValueEqual(ShuffleBlockList.EMPTY.size(), 0, "EMPTY.size()");
        helper.assertTrue(ShuffleFilterUtil.selectEntry(ShuffleBlockList.EMPTY, false, helper.getLevel()) == null,
                "selectEntry(EMPTY) should be null");
        helper.assertTrue(cascade(helper, ShuffleBlockList.EMPTY, new ItemStackHandler(1), 0).isNone(),
                "selectItemCascading(EMPTY) should be NONE");
        helper.assertValueEqual(
                new ItemStack(CreateShuffleFilter.SHUFFLE_FILTER.get()).get(ModDataComponents.SHUFFLE_BLOCK_LIST.get()),
                ShuffleBlockList.EMPTY, "default shuffle_block_list component");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dataComponentRoundTrip(GameTestHelper helper) {
        ShuffleBlockList list = new ShuffleBlockList(List.of(new BlockEntry(STONE, 0.25f), new BlockEntry(DIRT, 0.75f)));
        ItemStack stack = new ItemStack(CreateShuffleFilter.SHUFFLE_FILTER.get());
        stack.set(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), list);
        helper.assertValueEqual(stack.get(ModDataComponents.SHUFFLE_BLOCK_LIST.get()), list, "component get/set");

        // Codec (save data)
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        helper.assertValueEqual(decoded.get(ModDataComponents.SHUFFLE_BLOCK_LIST.get()), list, "Codec round-trip");

        // StreamCodec (network sync)
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            ItemStack received = ItemStack.STREAM_CODEC.decode(buf);
            helper.assertValueEqual(received.get(ModDataComponents.SHUFFLE_BLOCK_LIST.get()), list, "StreamCodec round-trip");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void blockEntryGetItem(GameTestHelper helper) {
        helper.assertValueEqual(new BlockEntry(STONE, 1f).getItem(), Items.STONE, "stone entry item");
        helper.assertValueEqual(new BlockEntry(DIAMOND, 1f).getItem(), Items.DIAMOND, "item-only entry item");
        helper.assertValueEqual(new BlockEntry(ResourceLocation.fromNamespaceAndPath("nope", "missing"), 1f).getItem(),
                Items.AIR, "unknown entry item");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void uniformSelection(GameTestHelper helper) {
        List<ResourceLocation> ids = List.of(STONE, DIRT, DIAMOND, OAK_LOG);
        ShuffleBlockList list = new ShuffleBlockList(ids.stream().map(id -> new BlockEntry(id, 1f)).toList());

        Map<ResourceLocation, Integer> counts = draw(helper, list, false);
        for (ResourceLocation id : ids) {
            helper.assertTrue(counts.getOrDefault(id, 0) > 0, id + " was never selected in " + DRAWS + " draws");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void weightedSelectionBias(GameTestHelper helper) {
        ShuffleBlockList list = new ShuffleBlockList(List.of(new BlockEntry(STONE, 0.99f), new BlockEntry(DIRT, 0.01f)));

        Map<ResourceLocation, Integer> counts = draw(helper, list, true);
        int heavy = counts.getOrDefault(STONE, 0);
        int light = counts.getOrDefault(DIRT, 0);
        helper.assertValueEqual(heavy + light, DRAWS, "total draws");
        helper.assertTrue(heavy > DRAWS * 0.9, "heavy entry won only " + heavy + "/" + DRAWS);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void skipItemHandling(GameTestHelper helper) {
        BlockEntry skip = new BlockEntry(CreateShuffleFilter.SKIP.getId(), 1f);
        helper.assertTrue(ShuffleFilterUtil.isSkipEntry(skip), "Skip entry not detected");
        helper.assertFalse(ShuffleFilterUtil.isSkipEntry(new BlockEntry(STONE, 1f)), "stone detected as Skip");
        helper.assertFalse(ShuffleFilterUtil.isSkipEntry(null), "null detected as Skip");

        ShuffleBlockList list = new ShuffleBlockList(List.of(skip));
        for (boolean weighted : new boolean[] { false, true }) {
            SelectionResult result = ShuffleFilterUtil.selectItemCascading(
                    list, weighted, helper.getLevel(), new ItemStackHandler(1), 0, new HashSet<>());
            helper.assertValueEqual(result, SelectionResult.SKIP, "result (weighted=" + weighted + ")");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cascadeDepthGuard(GameTestHelper helper) {
        ShuffleBlockList list = new ShuffleBlockList(List.of(new BlockEntry(STONE, 1f)));

        SelectionResult shallow = cascade(helper, list, stoneInventory(), 0);
        helper.assertTrue(shallow.stack().is(Items.STONE), "depth 0 should resolve stone");

        SelectionResult atLimit = cascade(helper, list, stoneInventory(), ShuffleFilterUtil.MAX_CASCADE_DEPTH);
        helper.assertTrue(atLimit.isNone(), "depth " + ShuffleFilterUtil.MAX_CASCADE_DEPTH + " should be NONE");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cascadeDeepChain(GameTestHelper helper) {
        // Nest filters (alternating types) deeper than MAX_CASCADE_DEPTH, with stone at the bottom.
        ShuffleBlockList list = new ShuffleBlockList(List.of(new BlockEntry(STONE, 1f)));
        for (int i = 0; i <= ShuffleFilterUtil.MAX_CASCADE_DEPTH; i++) {
            ItemEntry<?> type = i % 2 == 0 ? CreateShuffleFilter.SHUFFLE_FILTER : CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER;
            ItemStack filter = new ItemStack(type.get());
            filter.set(ModDataComponents.SHUFFLE_BLOCK_LIST.get(), list);
            list = ShuffleBlockList.EMPTY.withItemStack(filter, 1f);
        }

        SelectionResult result = cascade(helper, list, stoneInventory(), 0);
        helper.assertTrue(result.isNone(), "deep chain should resolve to NONE, got " + result);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void selectionResultStates(GameTestHelper helper) {
        helper.assertTrue(SelectionResult.NONE.isNone(), "NONE.isNone()");
        helper.assertFalse(SelectionResult.NONE.isSkip(), "NONE.isSkip()");
        helper.assertTrue(SelectionResult.SKIP.isSkip(), "SKIP.isSkip()");
        helper.assertFalse(SelectionResult.SKIP.isNone(), "SKIP.isNone()");

        ItemStack stone = new ItemStack(Items.STONE);
        SelectionResult of = SelectionResult.of(stone);
        helper.assertTrue(of.stack() == stone, "of(stack).stack() should return the stack");
        helper.assertFalse(of.isNone() || of.isSkip(), "of(stack) should be neither NONE nor SKIP");
        helper.assertValueEqual(SelectionResult.of(ItemStack.EMPTY), SelectionResult.NONE, "of(EMPTY)");
        helper.assertValueEqual(SelectionResult.of(null), SelectionResult.NONE, "of(null)");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void menuTypesRegister(GameTestHelper helper) {
        for (var holder : List.of(ModMenuTypes.SHUFFLE_FILTER, ModMenuTypes.WEIGHTED_SHUFFLE_FILTER)) {
            helper.assertTrue(holder.get() != null, holder.getId() + " resolved to null");
            helper.assertTrue(BuiltInRegistries.MENU.containsKey(holder.getId()), holder.getId() + " missing from menu registry");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingRecipesExist(GameTestHelper helper) {
        for (ItemEntry<?> item : List.of(CreateShuffleFilter.SHUFFLE_FILTER, CreateShuffleFilter.WEIGHTED_SHUFFLE_FILTER)) {
            ResourceLocation id = item.getId();
            RecipeHolder<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            helper.assertTrue(recipe != null, "recipe " + id + " not loaded");
            helper.assertTrue(recipe.value().getResultItem(helper.getLevel().registryAccess()).is(item.get()),
                    "recipe " + id + " does not produce " + id);
        }
        helper.succeed();
    }

    private static Map<ResourceLocation, Integer> draw(GameTestHelper helper, ShuffleBlockList list, boolean weighted) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int i = 0; i < DRAWS; i++) {
            counts.merge(ShuffleFilterUtil.selectEntry(list, weighted, helper.getLevel()).blockId(), 1, Integer::sum);
        }
        return counts;
    }

    private static SelectionResult cascade(GameTestHelper helper, ShuffleBlockList list, ItemStackHandler inv, int depth) {
        return ShuffleFilterUtil.selectItemCascading(list, false, helper.getLevel(), inv, depth, new HashSet<>());
    }

    private static ItemStackHandler stoneInventory() {
        ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        return inv;
    }
}
