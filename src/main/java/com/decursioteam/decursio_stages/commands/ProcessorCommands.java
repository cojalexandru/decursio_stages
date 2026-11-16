package com.decursioteam.decursio_stages.commands;

import com.decursioteam.decursio_stages.config.CommonConfig;
import com.decursioteam.decursio_stages.diagnostics.ProcessorInspector;
import com.decursioteam.decursio_stages.recipes.ProcessorRecipes;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

/** Loaded blocks only, in the command source's dimension; all operations require operator permission. */
final class ProcessorCommands {
    private static final SimpleCommandExceptionType NOT_PROCESSOR = new SimpleCommandExceptionType(Component.literal(
            "Target is not a supported recipe processor. Choose a furnace, smoker, blast furnace or campfire."));

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("blockEntity").requires(sender -> sender.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> inspect(ctx, null, ctx.getSource().getPlayer()))
                        .then(Commands.literal("inspect").executes(ctx -> inspect(ctx, null, ctx.getSource().getPlayer()))
                                .then(Commands.literal("player").then(Commands.argument("collector", EntityArgument.player())
                                        .executes(ctx -> inspect(ctx, null, EntityArgument.getPlayer(ctx, "collector"))))))
                        .then(Commands.literal("recipe").then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getRecipeManager().getRecipes().stream()
                                        .map(recipe -> recipe.getId().toString()), builder))
                                .executes(ctx -> inspectRecipe(ctx, ctx.getSource().getPlayer()))
                                .then(Commands.literal("player").then(Commands.argument("collector", EntityArgument.player())
                                        .executes(ctx -> inspectRecipe(ctx, EntityArgument.getPlayer(ctx, "collector")))))))
                        .then(role("owner", true))
                        .then(role("inserter", false))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> role(String name, boolean placement) {
        return Commands.literal(name)
                .then(Commands.literal("clear").executes(ctx -> transfer(ctx, placement, null, false)))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    var player = EntityArgument.getPlayer(ctx, "player");
                    return transfer(ctx, placement, player.getUUID(), player instanceof FakePlayer);
                }))
                .then(Commands.literal("uuid").then(Commands.argument("uuid", UuidArgument.uuid())
                        .executes(ctx -> transfer(ctx, placement, UuidArgument.getUuid(ctx, "uuid"), false))
                        .then(Commands.argument("fake", BoolArgumentType.bool())
                                .executes(ctx -> transfer(ctx, placement, UuidArgument.getUuid(ctx, "uuid"), BoolArgumentType.getBool(ctx, "fake"))))));
    }

    private static BlockEntity target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        var entity = ctx.getSource().getLevel().getBlockEntity(pos);
        if (!ProcessorRecipes.processor(entity)) throw NOT_PROCESSOR.create();
        return entity;
    }

    private static int inspectRecipe(CommandContext<CommandSourceStack> ctx, ServerPlayer collector) throws CommandSyntaxException {
        return inspect(ctx, ResourceLocationArgument.getId(ctx, "id"), collector);
    }

    private static int inspect(CommandContext<CommandSourceStack> ctx, ResourceLocation id, ServerPlayer collector) throws CommandSyntaxException {
        return DiagnosticCommands.show(ctx.getSource(), "Recipe processor inspector", ProcessorInspector.inspect(target(ctx), id, collector));
    }

    private static int transfer(CommandContext<CommandSourceStack> ctx, boolean placement, UUID id, boolean fake) throws CommandSyntaxException {
        var entity = target(ctx);
        String before = ProcessorInspector.stored(entity, placement);
        ProcessorRecipes.setResponsibility(entity, placement, id, fake);
        var policy = CommonConfig.processorPolicy.get();
        boolean active = policy == (placement ? CommonConfig.ProcessorPolicy.PLACER : CommonConfig.ProcessorPolicy.LAST_INSERT);
        ctx.getSource().sendSuccess(() -> Component.literal("Updated processor at " + entity.getBlockPos().toShortString()
                + " in " + ctx.getSource().getLevel().dimension().location() + ": " + before + " -> " + ProcessorInspector.stored(entity, placement)
                + ". " + (active ? "Controls processing now; unknown responsibility blocks restricted recipes."
                : "Stored for later; the current " + policy + " policy does not use this role.")), true);
        return 1;
    }

    private ProcessorCommands() {}
}
