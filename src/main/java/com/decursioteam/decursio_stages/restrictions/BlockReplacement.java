package com.decursioteam.decursio_stages.restrictions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Uses its containing restriction's stage; exactly one source selector is required. */
public record BlockReplacement(Optional<Identifier> block, Optional<Identifier> tag, Identifier replacement) {
    public static final Codec<BlockReplacement> CODEC = RecordCodecBuilder.<BlockReplacement>create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("block").forGetter(BlockReplacement::block),
            Identifier.CODEC.optionalFieldOf("tag").forGetter(BlockReplacement::tag),
            Identifier.CODEC.fieldOf("replacement").forGetter(BlockReplacement::replacement)
    ).apply(instance, BlockReplacement::new)).validate(rule -> rule.block.isPresent() == rule.tag.isPresent()
            ? DataResult.error(() -> "Block replacement requires exactly one block or tag") : DataResult.success(rule));
}
