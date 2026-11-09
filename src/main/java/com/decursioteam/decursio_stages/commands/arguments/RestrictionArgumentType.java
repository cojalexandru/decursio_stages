package com.decursioteam.decursio_stages.commands.arguments;

import com.decursioteam.decursio_stages.datagen.RestrictionsData;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RestrictionArgumentType implements ArgumentType<String> {

    private Set<String> KNOWN_RESTRICTIONS = null;

    public RestrictionArgumentType() {
        KNOWN_RESTRICTIONS = RestrictionsData.getRegistry().getRestrictions().keySet();;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public String toString () {
        return "restriction";
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(KNOWN_RESTRICTIONS, builder);
    }
}