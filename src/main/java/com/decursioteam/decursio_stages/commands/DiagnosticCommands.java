package com.decursioteam.decursio_stages.commands;

import com.decursioteam.decursio_stages.datagen.RestrictionReloadStatus;
import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.decursioteam.decursio_stages.diagnostics.RestrictionInspector;
import com.decursioteam.decursio_stages.diagnostics.RestrictionValidator;
import com.decursioteam.decursio_stages.recipes.RecipeStages;
import com.decursioteam.decursio_stages.utils.StagesHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Operator-only, read-only reports, paged in chat with per-requester snapshots. */
final class DiagnosticCommands {
    private static final int PAGE_SIZE = 10;
    private static final Map<String, Report> REPORTS = new LinkedHashMap<>();
    private record Report(String title, List<String> lines, long created) {}
    private DiagnosticCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext buildContext) {
        REPORTS.clear();
        ProcessorCommands.register(root);
        root.then(Commands.literal("diagnose").requires(sender -> sender.hasPermission(2))
                .executes(ctx -> show(ctx.getSource(), "Troubleshooting help", List.of(
                        "/decstages explain [player] — explain the main-hand item, with NBT.",
                        "/decstages explain <player> <item> — diagnose an ID; vanilla item NBT is supported.",
                        "/decstages explain recipe <player> <id> — matching recipe rules, stages and exceptions.",
                        "/decstages blockEntity <x y z> — processor policy, responsibility and recipe verdicts.",
                        "/decstages blockEntity <x y z> recipe <id> [player <collector>] — inspect a specific recipe.",
                        "/decstages blockEntity <x y z> owner|inserter <player> — transfer responsibility; uuid <uuid> [fake] or clear also supported.",
                        "/decstages inspect [player] — stored/effective stages and linked rules.",
                        "/decstages rule <restriction> — source, conditions, priority and effective settings.",
                        "/decstages validate — unknown IDs, empty tags, settings, duplicates and conflicting rules.",
                        "/decstages validate rule <restriction> — validate one rule and its overlaps.",
                        "/decstages diagnose reload — last restriction reload result and source errors.",
                        "/decstages diagnose saves — stage storage failures, recovery and queued retries.",
                        "Use the clickable Previous/Next controls, or /decstages diagnose page <number>.",
                        "Reports are read-only server snapshots, retained for five minutes. Run the original command again to refresh.")))
                .then(Commands.literal("saves").executes(ctx -> show(ctx.getSource(), "Player stage storage", StagesHandler.persistenceReport())))
                .then(Commands.literal("reload").executes(ctx -> show(ctx.getSource(), "Restriction reload status",
                        RestrictionReloadStatus.report())))
                .then(Commands.literal("page").then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> page(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number"))))));
        root.then(Commands.literal("explain").requires(sender -> sender.hasPermission(2))
                .executes(ctx -> explain(ctx.getSource(), ctx.getSource().getPlayerOrException(), null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> explain(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(ctx -> explain(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ItemArgument.getItem(ctx, "item").createItemStack(1, false))))));
        root.then(Commands.literal("explain").requires(sender -> sender.hasPermission(2))
                .then(Commands.literal("recipe").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("id", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getRecipeManager().getRecipes().stream().map(recipe -> recipe.getId().toString()), builder))
                .executes(ctx -> show(ctx.getSource(), "Recipe restriction", RecipeStages.explain(
                        EntityArgument.getPlayer(ctx, "player"), ResourceLocationArgument.getId(ctx, "id"))))))));
        root.then(Commands.literal("inspect").requires(sender -> sender.hasPermission(2))
                .executes(ctx -> inspect(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
        root.then(Commands.literal("rule").requires(sender -> sender.hasPermission(2))
                .then(Commands.argument("restriction", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RestrictionsData.getRegistry().getRawRestrictions().keySet(), builder))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "restriction");
                            if (!exists(ctx.getSource(), name)) return 0;
                            return show(ctx.getSource(), "Restriction " + name, RestrictionInspector.rule(name));
                        })));
        root.then(Commands.literal("validate").requires(sender -> sender.hasPermission(2))
                .executes(ctx -> show(ctx.getSource(), "Restriction validation", RestrictionValidator.validate(ctx.getSource().getServer(), null)))
                .then(Commands.literal("rule").then(Commands.argument("restriction", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RestrictionsData.getRegistry().getRawRestrictions().keySet(), builder))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "restriction");
                            if (!exists(ctx.getSource(), name)) return 0;
                            return show(ctx.getSource(), "Validation: " + name, RestrictionValidator.validate(ctx.getSource().getServer(), name));
                        }))));
    }

    private static boolean exists(CommandSourceStack source, String name) {
        if (RestrictionsData.getRegistry().getRawRestrictions().containsKey(name)) return true;
        source.sendFailure(Component.literal("Unknown restriction '" + name + "'. Use tab completion for loaded names."));
        return false;
    }

    private static int explain(CommandSourceStack source, ServerPlayer player, ItemStack supplied) throws CommandSyntaxException {
        ItemStack stack = supplied == null ? player.getMainHandItem().copy() : supplied;
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("The player's main hand is empty. Hold the blocked item or use /decstages explain "
                    + player.getName().getString() + " <item> (for example minecraft:diamond)."));
            return 0;
        }
        return show(source, "Explain restriction", RestrictionInspector.explain(player, stack));
    }

    private static int inspect(CommandSourceStack source, ServerPlayer player) {
        return show(source, "Player-stage inspector", RestrictionInspector.stages(player));
    }

    private static String key(CommandSourceStack source) {
        return source.getEntity() == null ? "console:" + source.getDisplayName().getString() : source.getEntity().getUUID().toString();
    }

    static int show(CommandSourceStack source, String title, List<String> lines) {
        long now = System.nanoTime();
        REPORTS.entrySet().removeIf(entry -> now - entry.getValue().created() > 300_000_000_000L);
        String key = key(source);
        REPORTS.remove(key);
        if (REPORTS.size() >= 64) REPORTS.remove(REPORTS.keySet().iterator().next());
        REPORTS.put(key, new Report(title + " (revision " + RestrictionsData.getRegistry().revision() + ")", List.copyOf(lines), now));
        return page(source, 1);
    }

    private static Component link(String text, int page) {
        return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/decstages diagnose page " + page)));
    }

    private static int page(CommandSourceStack source, int page) {
        Report report = REPORTS.get(key(source));
        if (report == null || System.nanoTime() - report.created() > 300_000_000_000L) {
            REPORTS.remove(key(source));
            source.sendFailure(Component.literal("No current report, or it expired. Run /decstages diagnose for help.")); return 0;
        }
        int pages = Math.max(1, (report.lines().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) { source.sendFailure(Component.literal("Page out of range: 1-" + pages)); return 0; }
        source.sendSuccess(() -> Component.literal(report.title() + " | Page " + page + "/" + pages).withStyle(ChatFormatting.GOLD), false);
        int first = (page - 1) * PAGE_SIZE;
        for (int i = first; i < Math.min(first + PAGE_SIZE, report.lines().size()); i++) {
            String line = report.lines().get(i);
            var color = line.startsWith("ERROR") || line.contains("BLOCKED") ? ChatFormatting.RED
                    : line.startsWith("WARN") ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            source.sendSuccess(() -> Component.literal(line).withStyle(color), false);
        }
        var controls = Component.empty();
        if (page > 1) controls.append(link("[Previous]", page - 1)).append("  ");
        if (page < pages) controls.append(link("[Next]", page + 1)).append("  ");
        controls.append(Component.literal("/decstages diagnose page <number>").withStyle(ChatFormatting.DARK_GRAY));
        source.sendSuccess(() -> controls, false);
        return 1;
    }
}
