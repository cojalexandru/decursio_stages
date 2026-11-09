package com.decursioteam.decursio_stages.commands.arguments;

import com.decursioteam.decursio_stages.utils.StageUtil;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class StageArgumentType  implements ArgumentType<String> {
    private Set<String> KNOWN_STAGES = null;
    public StageArgumentType() {
        KNOWN_STAGES = StageUtil.getStages();
    }

    public static String getStage(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public String toString () {
        return "stage";
    }


    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(KNOWN_STAGES, builder);
    }

}