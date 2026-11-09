package com.decursioteam.decursio_stages.compat.plugins;

import com.decursioteam.decursio_stages.client.screens.RestrictEditorScreen;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.world.level.material.Fluid;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.utils.StageUtil;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashSet;
import java.util.Set;

import static com.decursioteam.decursio_stages.DecursioStages.LOGGER;
import static com.decursioteam.decursio_stages.utils.ResourceUtil.*;

@EmiEntrypoint
public class DecursioStagesEMI implements EmiPlugin {

    private static final Set<ResourceLocation> hiddenItemIds = new HashSet<>();
    private static final Set<EmiStack> hiddenFluidIds = new HashSet<>();

    public DecursioStagesEMI() {
        if(EffectiveSide.get().isClient()){
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SyncStagesEvent.class, e -> this.reloadEMI());
            NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, RecipesUpdatedEvent.class, e -> this.reloadEMI());
        }
    }

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(RestrictEditorScreen.class, (screen, consumer) -> {
            var b = screen.editorBounds();
            consumer.accept(new Bounds(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        });
        registry.addDragDropHandler(RestrictEditorScreen.class, (screen, ingredient, x, y) -> {
            if (!screen.acceptsDrop(x, y) || ingredient.getEmiStacks().size() != 1) return false;
            EmiStack stack = ingredient.getEmiStacks().get(0);
            if (stack.getKey() instanceof Fluid fluid) return screen.acceptFluid(fluid);
            return screen.acceptItem(stack.getItemStack());
        });
        collectHiddenResources();
        hideResources(registry);
    }

    private void reloadEMI() {
        // Clear previous data
        hiddenItemIds.clear();
        hiddenFluidIds.clear();

        // Force EMI to reload
        if (Minecraft.getInstance().level != null) {
            EmiReloadManager.reload();
        }
    }

    private void collectHiddenResources() {
        final Player player = Minecraft.getInstance().player;
        if (player == null) return;

        final IStagesData stageData = StageUtil.getPlayerData(player);
        if (stageData == null) return;

        try {
            Registry.getRestrictionsHashSet().forEach((s) -> {
                String stage = RestrictionsData.getRestrictionData(s).getData().getStage();

                if (!stageData.hasStage(stage) && RestrictionsData.getRestrictionData(s).getSettingsCodec().getHideInJEI()) {

                    //Collect fluids from the fluid list and from restricted mods
                    if (!getFluids(s).isEmpty() || !getMods(s).isEmpty()) {
                        BuiltInRegistries.FLUID.forEach(fluidEntry -> {
                            if (fluidEntry.isSource(fluidEntry.defaultFluidState()) && isFluidRestricted(s, fluidEntry)) {
                                hiddenFluidIds.add(EmiStack.of(fluidEntry));
                            }
                        });
                    }

                    //Collect items from mod list
                    if (!getMods(s).isEmpty()) {
                        BuiltInRegistries.ITEM.forEach(item -> {
                            ItemStack itemStack = new ItemStack(item);
                            if(check(s, itemStack, CHECK_TYPES.MOD)) {
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                                if (itemId != null) {
                                    hiddenItemIds.add(itemId);
                                }
                            }
                        });
                    }

                    //Collect items from tag list
                    if(!getTags(s).isEmpty()) {
                        BuiltInRegistries.ITEM.forEach(item -> {
                            ItemStack itemStack = new ItemStack(item);
                            if(check(s, itemStack, CHECK_TYPES.TAG)) {
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                                if (itemId != null) hiddenItemIds.add(itemId);
                            }
                        });
                    }

                    //Collect items from item list
                    if (!getItems(s).isEmpty()) {
                        BuiltInRegistries.ITEM.forEach(item -> {
                            ItemStack itemStack = new ItemStack(item);
                            if(check(s, itemStack, CHECK_TYPES.ITEM)) {
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                                if (itemId != null) hiddenItemIds.add(itemId);
                            }
                        });
                    }
                }
            });
        }
        catch (Exception e){
            if(CommonConfig.debugMode.get())
                LOGGER.error("Error collecting hidden resources", e);
        }

        if(CommonConfig.debugMode.get()) {
            LOGGER.info("Total items to hide: {}", hiddenItemIds.size());
            LOGGER.info("Total fluids to hide: {}", hiddenFluidIds.size());
        }
    }

    private void hideResources(EmiRegistry registry) {
        if (hiddenItemIds.isEmpty() && hiddenFluidIds.isEmpty()) {
            if(CommonConfig.debugMode.get())
                LOGGER.warn("No items or fluids to hide in EMI");
            return;
        }

        if(CommonConfig.debugMode.get())
            LOGGER.info("Hiding {} items and {} fluids in EMI", hiddenItemIds.size(), hiddenFluidIds.size());

        // Hide items and fluids using ResourceLocation comparison
        registry.removeEmiStacks(emiStack -> {
            ResourceLocation stackId = emiStack.getId();

            // Check if this stack should be hidden
            boolean shouldHide = hiddenItemIds.contains(stackId) || hiddenFluidIds.contains(emiStack);

            if (shouldHide && CommonConfig.debugMode.get()) {
                LOGGER.info("Hiding stack: {}", stackId);
            }

            return shouldHide;
        });
    }
}