package com.decursioteam.decursio_stages.compat.plugins;

import com.decursioteam.decursio_stages.client.screens.RestrictEditorScreen;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.gui.screens.Screen;
import dev.architectury.fluid.FluidStack;
import java.util.stream.Stream;

import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.IStagesData;
import com.decursioteam.decursio_stages.events.SyncStagesEvent;
import com.decursioteam.decursio_stages.restrictions.FluidRestriction;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.StageUtil;
import me.shedaniel.rei.api.client.entry.filtering.FilteringRuleTypeRegistry;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.decursioteam.decursio_stages.DecursioStages.LOGGER;
import static com.decursioteam.decursio_stages.utils.ResourceUtil.*;

@REIPluginClient
public class DecursioStagesREI implements REIClientPlugin {

    private final List<EntryStack<?>> hiddenEntries = new ArrayList<>();

    public DecursioStagesREI() {
        if(EffectiveSide.get().isClient()){
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SyncStagesEvent.class, e -> this.updateItems());
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, RecipesUpdatedEvent.class, e -> this.updateItems());
        }
    }

    @Override
    public void registerBasicEntryFiltering(BasicFilteringRule<?> rule) {
        updateItems();
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.exclusionZones().register(RestrictEditorScreen.class, screen -> {
            var b = screen.editorBounds();
            return List.of(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        });
        registry.registerDraggableStackVisitor(new DraggableStackVisitor<RestrictEditorScreen>() {
            @Override public <R extends Screen> boolean isHandingScreen(R screen) {
                return screen instanceof RestrictEditorScreen;
            }
            private boolean supported(EntryStack<?> stack) {
                return stack.getType() == VanillaEntryTypes.ITEM || stack.getType() == VanillaEntryTypes.FLUID;
            }
            @Override public Stream<BoundsProvider> getDraggableAcceptingBounds(
                    DraggingContext<RestrictEditorScreen> context,
                    DraggableStack dragged) {
                if (!supported(dragged.getStack())) return Stream.empty();
                var b = context.getScreen().dropBounds();
                return Stream.of(BoundsProvider.ofRectangle(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())));
            }
            @Override public DraggedAcceptorResult acceptDraggedStack(
                    DraggingContext<RestrictEditorScreen> context,
                    DraggableStack dragged) {
                var screen = context.getScreen();
                var point = context.getCurrentPosition();
                var stack = dragged.getStack();
                boolean accepted = false;
                if (screen.acceptsDrop(point.x, point.y)) {
                    if (stack.getType() == VanillaEntryTypes.ITEM) accepted = screen.acceptItem((ItemStack) stack.getValue());
                    else if (stack.getType() == VanillaEntryTypes.FLUID) accepted = screen.acceptFluid(((FluidStack) stack.getValue()).getFluid());
                }
                return accepted ? DraggedAcceptorResult.ACCEPTED : DraggedAcceptorResult.PASS;
            }
        });
    }

    private void updateItems() {
        EntryRegistry entryRegistry = EntryRegistry.getInstance();
        BasicFilteringRule<?> rule = FilteringRuleTypeRegistry.getInstance().basic();

        collectEntries(rule);
        collectEntries(entryRegistry);
        hideEntries(rule);
    }

    private void collectEntries(BasicFilteringRule<?> rule) {
        if (!this.hiddenEntries.isEmpty()) {
            if(CommonConfig.debugMode.get()) LOGGER.info("Restoring the following items at runtime: " + this.hiddenEntries);
            for (EntryStack<?> stack : this.hiddenEntries) {
                rule.show(stack);
            }
            this.hiddenEntries.clear();
        } else if(CommonConfig.debugMode.get()) LOGGER.warn("There are no items available for restoring to the REI ingredient list!");
    }

    private void collectEntries(EntryRegistry registry) {
        registry.getEntryStacks().forEach(entryStack -> {
            final Player player = Minecraft.getInstance().player;
            final IStagesData stageData = StageUtil.getPlayerData(player);
            try {
                Registry.getRestrictionsHashSet().forEach((restriction) -> {
                    String stage = RestrictionsData.getRestrictionData(restriction).getData().getStage();
                    assert stageData != null;
                    if (!stageData.hasStage(stage) && RestrictionsData.getRestrictionData(restriction).getSettingsCodec().getHideInJEI()) {
                        if (entryStack.getType() == VanillaEntryTypes.FLUID) {
                            Fluid fluid = ForgeRegistries.FLUIDS.getValue(entryStack.getIdentifier());
                            if (fluid != null && isFluidRestricted(restriction, fluid)) add(entryStack);
                        }
                        else if(ForgeRegistries.ITEMS.getDelegate(entryStack.getIdentifier()).isPresent())
                        {
                            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getDelegate(entryStack.getIdentifier()).get());
                            if (!getMods(restriction).isEmpty()) {
                                if(check(restriction, stack, ResourceUtil.CHECK_TYPES.MOD)) {
                                    add(entryStack);
                                }
                            }
                            if(!getTags(restriction).isEmpty()) {
                                if(check(restriction, stack, CHECK_TYPES.TAG)) {
                                    add(entryStack);
                                }
                            }
                            if (!getItems(restriction).isEmpty()) {
                                if (check(restriction, stack, CHECK_TYPES.ITEM)) {
                                    add(entryStack);
                                }
                            }
                        }
                    }
                });
            }
            catch (NullPointerException e){
                //
            }
        });
    }

    private void hideEntries(BasicFilteringRule<?> rule) {
        if (!this.hiddenEntries.isEmpty()) {
            if(CommonConfig.debugMode.get()) LOGGER.info("Hiding the following items: " + this.hiddenEntries);
            for (EntryStack<?> stack : this.hiddenEntries) {
                rule.hide(stack);
            }
        } else if(CommonConfig.debugMode.get()) LOGGER.warn("There are no items that are supposed to be hidden in REI");
    }

    private void add(EntryStack<?> entryStack) {
        this.hiddenEntries.add(entryStack);
    }

    private void add(List<EntryStack<?>> entryStacks) {
        this.hiddenEntries.addAll(entryStacks);
    }
}