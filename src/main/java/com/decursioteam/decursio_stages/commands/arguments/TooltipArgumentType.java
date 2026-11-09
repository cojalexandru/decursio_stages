package com.decursioteam.decursio_stages.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TooltipArgumentType implements ArgumentType<String> {

    private final Set<String> TOOLTIP_ARGUMENTS = Set.of("ALWAYS", "NONE", "ADVANCED");

    public TooltipArgumentType() {
        // empty
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public String toString () {
        return "type";
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(TOOLTIP_ARGUMENTS, builder);
    }
}