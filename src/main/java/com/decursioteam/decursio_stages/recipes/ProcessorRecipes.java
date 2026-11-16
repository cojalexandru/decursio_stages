package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.*;
import com.decursioteam.decursio_stages.persistence.StageFileStore;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

@EventBusSubscriber(modid = "decursio_stages")
public final class ProcessorRecipes {
    public static final String OWNER = "decursio_recipe_owner", INSERTER = "decursio_recipe_inserter", RECEIPTS = "decursio_recipe_receipts";
    public static final ThreadLocal<String> DROPS = new ThreadLocal<>();
    private record Cached(long tick, StagesData stages, String status) {}
    public record Responsibility(UUID id, boolean fake, ServerPlayer online, StagesData stages, String status, long cacheAge) {
        public boolean unlocked(String stage) {
            return online != null ? StageUtil.hasStage(online, stage) : stages != null && stages.hasStage(stage);
        }
        public String status() {
            return cacheAge < 0 ? status : status + " | cached up to 20 ticks; " + cacheAge + " ticks old";
        }
    }
    private static final Map<UUID, Cached> OFFLINE = new LinkedHashMap<>();
    public static boolean supported(RecipeType<?> type) {
        return Set.of(RecipeType.CRAFTING, RecipeType.SMITHING, RecipeType.STONECUTTING, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING).contains(type);
    }
    public static boolean processor(BlockEntity entity) { return entity instanceof AbstractFurnaceBlockEntity || entity instanceof CampfireBlockEntity || entity instanceof CrafterBlockEntity; }
    public static void assign(BlockEntity entity, ServerPlayer player, boolean placement) {
        if (!processor(entity)) return;
        setResponsibility(entity, placement, player.getUUID(), player instanceof FakePlayer);
    }
    /** A null UUID clears only the chosen responsibility; output provenance is preserved. */
    public static void setResponsibility(BlockEntity entity, boolean placement, UUID id, boolean fake) {
        if (!processor(entity)) throw new IllegalArgumentException("Not a supported recipe processor");
        String key = placement ? OWNER : INSERTER;
        if (id == null) {
            entity.getPersistentData().remove(key);
            entity.getPersistentData().remove(key + "_fake");
        } else {
            entity.getPersistentData().putString(key, id.toString());
            entity.getPersistentData().putBoolean(key + "_fake", fake);
            OFFLINE.remove(id);
        }
        entity.setChanged();
    }
    /** Uses the same bounded offline cache for enforcement and diagnostic snapshots. */
    public static Responsibility responsibility(BlockEntity entity, boolean placement) {
        String key = placement ? OWNER : INSERTER;
        String stored = entity.getPersistentData().getStringOr(key, "");
        boolean fake = entity.getPersistentData().getBooleanOr(key + "_fake", false);
        UUID id;
        try { id = UUID.fromString(stored); }
        catch (IllegalArgumentException error) {
            return new Responsibility(null, fake, null, null, stored.isEmpty() ? "No responsible player recorded" : "Invalid responsible UUID: " + stored, -1);
        }
        var level = (ServerLevel) entity.getLevel();
        var online = level.getServer().getPlayerList().getPlayer(id);
        if (online != null) return new Responsibility(id, fake || online instanceof FakePlayer, online, null, "Online: effective stage checks (including events)", -1);
        long tick = level.getServer().getTickCount();
        Cached cached = OFFLINE.get(id);
        if (cached == null || tick - cached.tick >= 20) {
            StagesData stages = null;
            String status;
            var file = level.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("decursio_stages").resolve(id + ".dat");
            try {
                var loaded = StageFileStore.load(file);
                stages = new StagesData();
                if (loaded.tag() != null) stages.readFromNBT(loaded.tag());
                status = loaded.tag() == null ? "Offline: no saved stage file (empty stages)"
                        : loaded.backup() ? "Offline: recovered backup stage file" : "Offline: saved stage file";
            } catch (Exception error) {
                stages = null;
                status = "ERROR offline stage read: " + error.getMessage();
            }
            if (OFFLINE.size() >= 1024) OFFLINE.remove(OFFLINE.keySet().iterator().next());
            cached = new Cached(tick, stages, status);
            OFFLINE.put(id, cached);
        }
        return new Responsibility(id, fake, null, cached.stages, cached.status, tick - cached.tick);
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel().getBlockEntity(event.getPos()) != null) assign(event.getLevel().getBlockEntity(event.getPos()), player, true);
    }
    public static boolean allowed(BlockEntity entity, Identifier id) {
        if (CommonConfig.processorPolicy.get() == CommonConfig.ProcessorPolicy.OUTPUT_ONLY) return true;
        if (!(entity.getLevel() instanceof ServerLevel serverLevel)) return true;
        var matching = RestrictionsData.getRegistry().recipes().rulesFor(serverLevel.getServer(), id);
        if (matching.isEmpty()) return true;
        String key = CommonConfig.processorPolicy.get() == CommonConfig.ProcessorPolicy.PLACER ? OWNER : INSERTER;
        boolean fake = entity.getPersistentData().getBooleanOr(key + "_fake", false);
        if (fake && matching.stream().noneMatch(rule -> rule.appliesToFakePlayers())) return true;
        var access = responsibility(entity, CommonConfig.processorPolicy.get() == CommonConfig.ProcessorPolicy.PLACER);
        for (var rule : matching) {
            if (access.fake() && !rule.appliesToFakePlayers()) continue;
            if (!access.unlocked(rule.stage())) return false;
        }
        return true;
    }
    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> select(BlockEntity entity, RecipeType<T> type, I input, Optional<RecipeHolder<T>> preferred) {
        if (preferred.isPresent() && allowed(entity, preferred.get().id().identifier())) return preferred;
        var level = (ServerLevel) entity.getLevel();
        return level.getServer().getRecipeManager().recipeMap().getRecipesFor(type, input, level).filter(r -> allowed(entity, r.id().identifier())).findFirst();
    }
    public static String furnaceReceipts(AbstractFurnaceBlockEntity entity) {
        String known = entity.getPersistentData().getStringOr(RECEIPTS, "");
        if (!known.isEmpty() || entity.getItem(2).isEmpty()) return known;
        // Legacy outputs have no provenance. Conservatively include every matching vanilla output recipe.
        var type = entity instanceof SmokerBlockEntity ? RecipeType.SMOKING : entity instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING : RecipeType.SMELTING;
        var ids = new ArrayList<String>();
        for (var recipe : ((ServerLevel) entity.getLevel()).getServer().getRecipeManager().getRecipes())
            if (recipe.value().getType() == type && recipe.value() instanceof AbstractCookingRecipe cooking
                    && cooking.assemble(new SingleRecipeInput(ItemStack.EMPTY)).is(entity.getItem(2).getItem())) ids.add(recipe.id().identifier().toString());
        return String.join(",", ids);
    }
    public static boolean mayCollect(ServerPlayer player, String receipts) {
        if (CommonConfig.processorPolicy.get() != CommonConfig.ProcessorPolicy.OUTPUT_ONLY || receipts.isEmpty()) return true;
        var blocked = RecipeStages.policy(player);
        return Arrays.stream(receipts.split(",")).map(Identifier::tryParse).filter(Objects::nonNull).noneMatch(blocked::contains);
    }
    public static void receipt(AbstractFurnaceBlockEntity entity, RecipeHolder<?> recipe) {
        if (recipe == null) return;
        var input = new SingleRecipeInput(entity.getItem(0));
        int count = recipe.value() instanceof AbstractCookingRecipe cooking ? cooking.assemble(input).getCount() : 1;
        String previous = entity.getItem(2).getCount() <= count ? "" : entity.getPersistentData().getStringOr(RECEIPTS, "");
        var ids = new LinkedHashSet<>(List.of(previous.split(","))); ids.remove(""); ids.add(recipe.id().identifier().toString());
        entity.getPersistentData().putString(RECEIPTS, String.join(",", ids)); entity.setChanged();
    }
    public static Map<BlockEntity, List<ItemStack>> inputs(AbstractContainerMenu menu) {
        Map<BlockEntity, List<ItemStack>> result = new HashMap<>();
        for (var slot : menu.slots) if (slot.container instanceof BlockEntity entity && processor(entity) && entity instanceof net.minecraft.world.Container container)
            result.computeIfAbsent(entity, ignored -> {
                int size = entity instanceof AbstractFurnaceBlockEntity ? 1 : container.getContainerSize();
                var list = new ArrayList<ItemStack>(); for (int i=0;i<size;i++) list.add(container.getItem(i).copy()); return list;
            });
        return result;
    }
    public static void inserted(AbstractContainerMenu menu, ServerPlayer player, Map<BlockEntity, List<ItemStack>> before) {
        for (var entry : before.entrySet()) {
            var container = (net.minecraft.world.Container) entry.getKey();
            for (int i=0;i<entry.getValue().size();i++) {
                ItemStack old = entry.getValue().get(i), now = container.getItem(i);
                if (!now.isEmpty() && (!ItemStack.isSameItemSameComponents(old, now) || now.getCount() > old.getCount())) { assign(entry.getKey(), player, false); break; }
            }
        }
    }
    /** Restore an outer context as well as cleaning up after a failed/nested drop operation. */
    public static void withDrops(String recipe, Runnable work) {
        String previous = DROPS.get();
        DROPS.set(recipe);
        try { work.run(); }
        finally { if (previous == null) DROPS.remove(); else DROPS.set(previous); }
    }
    @SubscribeEvent public static void drops(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item && DROPS.get() != null)
            item.getPersistentData().putString(RECEIPTS, DROPS.get());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { OFFLINE.clear(); DROPS.remove(); }
    private ProcessorRecipes() {}
}
