package com.decursioteam.decursio_stages.recipes;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.StagesData;
import com.decursioteam.decursio_stages.persistence.StageFileStore;
import com.decursioteam.decursio_stages.utils.StageUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "decursio_stages")
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

    public static boolean processor(BlockEntity entity) {
        return entity instanceof AbstractFurnaceBlockEntity || entity instanceof CampfireBlockEntity;
    }

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
        String stored = entity.getPersistentData().getString(key);
        boolean fake = entity.getPersistentData().getBoolean(key + "_fake");
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
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel().getBlockEntity(event.getPos()) != null) {
            assign(event.getLevel().getBlockEntity(event.getPos()), player, true);
        }
    }

    public static boolean allowed(BlockEntity entity, ResourceLocation id) {
        if (CommonConfig.processorPolicy.get() == CommonConfig.ProcessorPolicy.OUTPUT_ONLY) return true;
        if (!(entity.getLevel() instanceof ServerLevel serverLevel)) return true;
        var matching = RestrictionsData.getRegistry().recipes().rulesFor(serverLevel.getServer(), id);
        if (matching.isEmpty()) return true;
        String key = CommonConfig.processorPolicy.get() == CommonConfig.ProcessorPolicy.PLACER ? OWNER : INSERTER;
        boolean fake = entity.getPersistentData().getBoolean(key + "_fake");
        if (fake && matching.stream().noneMatch(RecipeRestrictionIndex.Rule::appliesToFakePlayers)) return true;
        var access = responsibility(entity, CommonConfig.processorPolicy.get() == CommonConfig.ProcessorPolicy.PLACER);
        for (var rule : matching) {
            if (access.fake() && !rule.appliesToFakePlayers()) continue;
            if (!access.unlocked(rule.stage())) return false;
        }
        return true;
    }

    public static <C extends Container, T extends Recipe<C>> Optional<T> select(BlockEntity entity, RecipeType<T> type, C input, Optional<T> preferred) {
        if (preferred.isPresent() && allowed(entity, preferred.get().getId())) return preferred;
        var level = (ServerLevel) entity.getLevel();
        return level.getServer().getRecipeManager().getRecipesFor(type, input, level).stream().filter(r -> allowed(entity, r.getId())).findFirst();
    }

    public static String furnaceReceipts(AbstractFurnaceBlockEntity entity) {
        String known = entity.getPersistentData().getString(RECEIPTS);
        if (!known.isEmpty() || entity.getItem(2).isEmpty()) return known;
        // Legacy outputs have no provenance. Conservatively include every matching vanilla output recipe.
        var level = (ServerLevel) entity.getLevel();
        var type = entity instanceof SmokerBlockEntity ? RecipeType.SMOKING : entity instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING : RecipeType.SMELTING;
        var ids = new ArrayList<String>();
        for (var recipe : level.getServer().getRecipeManager().getRecipes()) {
            if (recipe.getType() == type && recipe instanceof AbstractCookingRecipe cooking
                    && cooking.assemble(new SimpleContainer(ItemStack.EMPTY), level.registryAccess()).is(entity.getItem(2).getItem())) {
                ids.add(recipe.getId().toString());
            }
        }
        return String.join(",", ids);
    }

    public static boolean mayCollect(ServerPlayer player, String receipts) {
        if (CommonConfig.processorPolicy.get() != CommonConfig.ProcessorPolicy.OUTPUT_ONLY || receipts.isEmpty()) return true;
        var blocked = RecipeStages.policy(player);
        return Arrays.stream(receipts.split(",")).map(ResourceLocation::tryParse).filter(Objects::nonNull).noneMatch(blocked::contains);
    }

    public static void receipt(AbstractFurnaceBlockEntity entity, Recipe<?> recipe) {
        if (recipe == null || entity.getLevel() == null) return;
        var input = new SimpleContainer(entity.getItem(0));
        int count = recipe instanceof AbstractCookingRecipe cooking ? cooking.assemble(input, entity.getLevel().registryAccess()).getCount() : 1;
        String previous = entity.getItem(2).getCount() <= count ? "" : entity.getPersistentData().getString(RECEIPTS);
        var ids = new LinkedHashSet<>(List.of(previous.split(","))); ids.remove(""); ids.add(recipe.getId().toString());
        entity.getPersistentData().putString(RECEIPTS, String.join(",", ids)); entity.setChanged();
    }

    public static Map<BlockEntity, List<ItemStack>> inputs(AbstractContainerMenu menu) {
        Map<BlockEntity, List<ItemStack>> result = new HashMap<>();
        for (var slot : menu.slots) if (slot.container instanceof BlockEntity entity && processor(entity) && entity instanceof Container container)
            result.computeIfAbsent(entity, ignored -> {
                int size = entity instanceof AbstractFurnaceBlockEntity ? 1 : container.getContainerSize();
                var list = new ArrayList<ItemStack>(); for (int i = 0; i < size; i++) list.add(container.getItem(i).copy()); return list;
            });
        return result;
    }

    public static void inserted(AbstractContainerMenu menu, ServerPlayer player, Map<BlockEntity, List<ItemStack>> before) {
        for (var entry : before.entrySet()) {
            var container = (Container) entry.getKey();
            for (int i = 0; i < entry.getValue().size(); i++) {
                ItemStack old = entry.getValue().get(i), now = container.getItem(i);
                if (!now.isEmpty() && (!ItemStack.isSameItemSameTags(old, now) || now.getCount() > old.getCount())) {
                    assign(entry.getKey(), player, false);
                    break;
                }
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

    @SubscribeEvent public static void drops(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && DROPS.get() != null) {
            item.getPersistentData().putString(RECEIPTS, DROPS.get());
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { OFFLINE.clear(); DROPS.remove(); }
    private ProcessorRecipes() {}
}
