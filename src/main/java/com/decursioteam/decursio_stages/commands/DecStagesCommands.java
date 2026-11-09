package com.decursioteam.decursio_stages.commands;

import com.decursioteam.decursio_stages.DecursioStages;
import com.decursioteam.decursio_stages.Registry;
import com.decursioteam.decursio_stages.commands.arguments.RestrictionArgumentType;
import com.decursioteam.decursio_stages.commands.arguments.StageArgumentType;
import com.decursioteam.decursio_stages.commands.arguments.TooltipArgumentType;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.datagen.utils.FileUtils;
import com.decursioteam.decursio_stages.network.RestrictionEditorServer;
import com.decursioteam.decursio_stages.utils.ResourceUtil;
import com.decursioteam.decursio_stages.utils.StageUtil;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredRegister;


import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.decursioteam.decursio_stages.datagen.utils.FileUtils.restrictionExists;

public class DecStagesCommands {

    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES = DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, DecursioStages.MOD_ID);
    private static final java.util.function.Supplier<SingletonArgumentInfo<StageArgumentType>> STAGE_ARGUMENT = COMMAND_ARGUMENT_TYPES.register("stage_argument", () -> ArgumentTypeInfos.registerByClass(StageArgumentType.class, SingletonArgumentInfo.contextFree(StageArgumentType::new)));
    private static final java.util.function.Supplier<SingletonArgumentInfo<RestrictionArgumentType>> RESTRICTION_ARGUMENT = COMMAND_ARGUMENT_TYPES.register("restriction_argument", () -> ArgumentTypeInfos.registerByClass(RestrictionArgumentType.class, SingletonArgumentInfo.contextFree(RestrictionArgumentType::new)));

    private static final java.util.function.Supplier<SingletonArgumentInfo<TooltipArgumentType>> TOOLTIP_ARGUMENT = COMMAND_ARGUMENT_TYPES.register("tooltip_argument", () -> ArgumentTypeInfos.registerByClass(TooltipArgumentType.class, SingletonArgumentInfo.contextFree(TooltipArgumentType::new)));

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(DecStagesCommands::registerCommands);
        COMMAND_ARGUMENT_TYPES.register(modBus);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        final LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("decstages");
        command.then(createSilentStageCommand("add", 2, ctx -> addStage(ctx, false), ctx -> addStage(ctx, true)));
        command.then(createSilentStageCommand("remove", 2, ctx -> removeStage(ctx, false), ctx -> removeStage(ctx, true)));
        command.then(createPlayerCommand("check", 0, ctx -> getPlayerStages(ctx, true), ctx -> getPlayerStages(ctx, false)));
        command.then(createPlayerCommand("clear", 2, ctx -> clearStages(ctx, true), ctx -> clearStages(ctx, false)));
        command.then(createPlayerCommand("all", 2, ctx -> giveStages(ctx, true), ctx -> giveStages(ctx, false)));
        command.then(createRestrictCommand("restrict", 2, DecStagesCommands::restrictItem));
        command.then(createInfoCommand("reload", 2, DecStagesCommands::reloadStages));
        command.then(createLinksCommand("links", 2, DecStagesCommands::linksCommand));
        command.then(createInfoCommand("info", 2, DecStagesCommands::listStages));
        command.then(Commands.literal("gui").requires(sender -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(sender))
                .executes(ctx -> {
                    RestrictionEditorServer.open(ctx.getSource().getPlayerOrException(), null, null, false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("restriction", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RestrictionsData.getRegistry().getRawRestrictions().keySet(), builder))
                        .executes(ctx -> {
                            RestrictionEditorServer.open(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "restriction"), null, false);
                            return Command.SINGLE_SUCCESS;
                        })));
        event.getDispatcher().register(command);
    }

    private static net.minecraft.server.permissions.PermissionCheck permissionForLevel(int level) {
        return switch (level) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            default -> Commands.LEVEL_OWNERS;
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createInfoCommand (String key, int permissions, Command<CommandSourceStack> command) {
        return Commands.literal(key).requires(sender -> Commands.hasPermission(permissionForLevel(permissions)).test(sender)).executes(command);
    }
    private static LiteralArgumentBuilder<CommandSourceStack> createLinksCommand (String key, int permissions, Command<CommandSourceStack> command) {
        return Commands.literal(key).requires(sender -> Commands.hasPermission(permissionForLevel(permissions)).test(sender)).then(Commands.argument("stage", new StageArgumentType()).executes(command));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createPlayerCommand (String key, int permissions, Command<CommandSourceStack> command, Command<CommandSourceStack> commandNoPlayer) {
        return Commands.literal(key).requires(sender -> Commands.hasPermission(permissionForLevel(permissions)).test(sender)).executes(commandNoPlayer).then(Commands.argument("targets", EntityArgument.player()).executes(command));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRestrictCommand(String key, int permissions, Command<CommandSourceStack> command) {
        return Commands.literal(key).requires(sender -> Commands.hasPermission(permissionForLevel(permissions)).test(sender))
                .then(Commands.argument("stage", new StageArgumentType())
                        .then(Commands.argument("advancedTooltips", new TooltipArgumentType())
                                .then(Commands.argument("itemTitle", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.asList(
                                                        "\"Locked Item\"",
                                                        "\"Requires " + StageArgumentType.getStage(context, "stage") + "\"",
                                                        "\"Stage: " + StageArgumentType.getStage(context, "stage") + "\""),
                                                builder))
                                        .then(Commands.argument("pickupDelay", IntegerArgumentType.integer(0))
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.asList("40", "20", "0"), builder))
                                                .then(Commands.argument("hideInJEI", BoolArgumentType.bool())
                                                        .then(Commands.argument("canPickup", BoolArgumentType.bool())
                                                                .then(Commands.argument("containerListWhitelist", BoolArgumentType.bool())
                                                                        .then(Commands.argument("checkPlayerInventory", BoolArgumentType.bool())
                                                                                .then(Commands.argument("checkPlayerEquipment", BoolArgumentType.bool())
                                                                                        .then(Commands.argument("usableItems", BoolArgumentType.bool())
                                                                                                .then(Commands.argument("usableBlocks", BoolArgumentType.bool())
                                                                                                        .then(Commands.argument("destroyableBlocks", BoolArgumentType.bool())
                                                                                                                .executes(command)))))))))))));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> createSilentStageCommand (String key, int permissions, Command<CommandSourceStack> command, Command<CommandSourceStack> silent) {
        return Commands.literal(key).requires(sender -> Commands.hasPermission(permissionForLevel(permissions)).test(sender))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("stage", new StageArgumentType())
                                .executes(command).then(Commands.argument("silent", BoolArgumentType.bool())
                                        .executes(silent))));
    }

    private static int reloadStages (CommandContext<CommandSourceStack> ctx) {
        RestrictionsData.getRegistry().clearRawRestrictionsData();
        Registry.setupRestrictions();
        Registry.registerRestrictionsList();

        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.reloadstages", StagesHandler.getStages()), true);
        return 0;
    }

    private static int linksCommand (CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final String stage = StageArgumentType.getStage(ctx, "stage");
        HashSet<String> restrictions = new HashSet<>();
        RestrictionsData.getRegistry().getRawRestrictions().forEach((name, rawJSON) -> {
            if(rawJSON.getAsJsonObject("Restriction Data").get("stage").getAsString().equals(stage))
                restrictions.add("\"" + name + ".json\"");
        });
        if (!restrictions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.links.success", stage, String.join(", ", restrictions.toArray(new String[0]))), true);
        }
        else
            ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.links.failure", stage));
        return 0;
    }

    private static int listStages (CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.liststages", StagesHandler.getStages()), true);
        return 0;
    }


    private static int addStage(CommandContext<CommandSourceStack> ctx, boolean silent) throws CommandSyntaxException {
        final String stage = StageArgumentType.getStage(ctx, "stage");
        for (final ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            if(stage.contains(",")){
                Set<String> stages = Arrays.stream(stage.split(",")).collect(Collectors.toSet());
                for (String s : stages) {
                    if(!StageUtil.hasStage(player, s)) StageUtil.addStage(player, s);
                }
                if (!silent || !BoolArgumentType.getBool(ctx, "silent")) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.addstages.success.target", stages), true);
                    if (player != ctx.getSource().getEntity()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.addstages.success.sender", player.getDisplayName(), stages), true);
                    }
                }
            }
            else {
                if(!StageUtil.hasStage(player, stage)) StageUtil.addStage(player, stage);
                else if (!silent || !BoolArgumentType.getBool(ctx, "silent")) {
                    ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.addstage.failure.target", stage));
                    if (player != ctx.getSource().getEntity()) {
                        ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.addstage.failure.sender", player.getDisplayName(), stage));
                    }
                    return 0;
                }
                if (!silent || !BoolArgumentType.getBool(ctx, "silent")) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.addstage.success.target", stage), true);
                    if (player != ctx.getSource().getEntity()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.addstage.success.sender", player.getDisplayName(), stage), true);
                    }
                }
            }
        }
        return 0;
    }

    private static int removeStage(CommandContext<CommandSourceStack> ctx, boolean silent) throws CommandSyntaxException {
        final String stage = StageArgumentType.getStage(ctx, "stage");
        for (final ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            if(stage.contains(",")){
                Set<String> stages = Arrays.stream(stage.split(",")).collect(Collectors.toSet());
                for (String s : stages) {
                    if(StageUtil.hasStage(player, s)) StageUtil.removeStage(player, s);
                }
                if (!silent || !BoolArgumentType.getBool(ctx, "silent")) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.removestages.success.target", stages), true);
                    if (player != ctx.getSource().getEntity()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.removestages.success.sender", player.getDisplayName(), stages), true);
                    }
                }
            }
            else {
                if(StageUtil.hasStage(player, stage)) StageUtil.removeStage(player, stage);
                else if (!silent || !BoolArgumentType.getBool(ctx, "silent")) {
                    ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.removestage.failure.target", stage));
                    if (player != ctx.getSource().getEntity()) {
                        ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.removestage.failure.sender", player.getDisplayName(), stage));
                    }
                    return 0;
                }
                if (!silent || !BoolArgumentType.getBool(ctx, "silent")) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.removestage.success.target", stage), true);
                    if (player != ctx.getSource().getEntity()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.removestage.success.sender", player.getDisplayName(), stage), true);
                    }
                }
            }
        }
        return 0;
    }

    private static int getPlayerStages(CommandContext<CommandSourceStack> ctx, boolean hasPlayer) throws CommandSyntaxException {
        if (hasPlayer) {
            for (final ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
                getPlayerStages(ctx, player);
            }
        }
        else {
            getPlayerStages(ctx, ctx.getSource().getPlayerOrException());
        }
        return 0;
    }

    private static void getPlayerStages (CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        final String stageInfo = Objects.requireNonNull(StageUtil.getPlayerData(player)).getStages().stream().map(Object::toString).collect(Collectors.joining(", "));
        if (stageInfo.isEmpty()) ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.check.failure.empty", player.getDisplayName()));
        else ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.check.success.list", player.getDisplayName(), stageInfo), false);
    }

    private static int giveStages(CommandContext<CommandSourceStack> ctx, boolean hasPlayer) throws CommandSyntaxException {
        if (hasPlayer)
            for (final ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) giveStages(ctx, player);
        else giveStages(ctx, ctx.getSource().getPlayerOrException());
        return 0;
    }

    private static Path createCustomPath(String pathName) {
        Path customPath = Paths.get(FMLPaths.CONFIGDIR.get().toAbsolutePath().toString(), DecursioStages.MOD_ID, pathName);
        createDirectory(customPath, pathName);
        return customPath;
    }
    private static void createDirectory(Path path, String dirName) {
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            DecursioStages.LOGGER.error("failed to create \"{}\" directory", dirName);
        }
    }

    private static void giveStages(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        for(final String knownStage : StageUtil.getStages()) StageUtil.addStage(player, knownStage);
        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.all.target"), true);
        if(player != ctx.getSource().getEntity()) ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.all.sender", player.getDisplayName()), true);
    }

    private static int clearStages(CommandContext<CommandSourceStack> ctx, boolean hasPlayer) throws CommandSyntaxException {
        if(hasPlayer) for (final ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) clearStages(ctx, player);
        else clearStages(ctx, ctx.getSource().getPlayerOrException());
        return 0;
    }

    private static void clearStages(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        final int removedStages = StageUtil.clearStages(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.clear.target", removedStages), true);
        if(player != ctx.getSource().getEntity()) ctx.getSource().sendSuccess(() -> Component.translatable("decursio_stages.commands.clear.sender", removedStages, player.getDisplayName()), true);
    }

    private static int restrictItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final String stage = StageArgumentType.getStage(ctx, "stage");
        final String advancedTooltips = StringArgumentType.getString(ctx, "advancedTooltips");
        final String itemTitle = StringArgumentType.getString(ctx, "itemTitle");
        final int pickupDelay = IntegerArgumentType.getInteger(ctx, "pickupDelay");
        final boolean hideInJEI = BoolArgumentType.getBool(ctx, "hideInJEI");
        final boolean canPickup = BoolArgumentType.getBool(ctx, "canPickup");
        final boolean containerListWhitelist = BoolArgumentType.getBool(ctx, "containerListWhitelist");
        final boolean checkPlayerInventory = BoolArgumentType.getBool(ctx, "checkPlayerInventory");
        final boolean checkPlayerEquipment = BoolArgumentType.getBool(ctx, "checkPlayerEquipment");
        final boolean usableItems = BoolArgumentType.getBool(ctx, "usableItems");
        final boolean usableBlocks = BoolArgumentType.getBool(ctx, "usableBlocks");
        final boolean destroyableBlocks = BoolArgumentType.getBool(ctx, "destroyableBlocks");

        Player player = ctx.getSource().getPlayerOrException();
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (heldItem.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("decursio_stages.commands.restrictitem.failure.empty_hand"));
            return 0;
        }

        String itemName = ResourceUtil.getRegistryName(heldItem.getItem()).toString();


        AtomicInteger counter = new AtomicInteger();
        RestrictionsData.getRegistry().getRawRestrictions().forEach((restriction, x) -> {
            if (restrictionExists(restriction, stage, advancedTooltips, itemTitle, pickupDelay,
                    hideInJEI, canPickup, containerListWhitelist, checkPlayerInventory,
                    checkPlayerEquipment, usableItems, usableBlocks, destroyableBlocks)) { // Also check by expected ID

                FileUtils.restrictItem(stage, advancedTooltips, itemTitle, pickupDelay, hideInJEI,
                        canPickup, containerListWhitelist, checkPlayerInventory, checkPlayerEquipment,
                        usableItems, usableBlocks, destroyableBlocks, heldItem);
                counter.getAndIncrement();
            }
        });

        if (counter.get() == 0) {
            FileUtils.addRestriction(stage, advancedTooltips, itemTitle, pickupDelay, hideInJEI,
                    canPickup, containerListWhitelist, checkPlayerInventory, checkPlayerEquipment,
                    usableItems, usableBlocks, destroyableBlocks);

            FileUtils.restrictItem(stage, advancedTooltips, itemTitle, pickupDelay, hideInJEI,
                    canPickup, containerListWhitelist, checkPlayerInventory, checkPlayerEquipment,
                    usableItems, usableBlocks, destroyableBlocks, heldItem);
        }

        Component itemComponent = Component.literal(itemName).withStyle(ChatFormatting.AQUA);
        Component stageComponent = Component.literal(stage).withStyle(ChatFormatting.DARK_PURPLE);

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "decursio_stages.commands.restrictitem.success",
                itemComponent, stageComponent), true);

        Registry.setupRestrictions();
        Registry.registerRestrictionsList();

        return 1;
    }

}
