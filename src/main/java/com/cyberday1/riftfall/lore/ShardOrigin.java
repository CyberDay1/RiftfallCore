package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.rift.RiftRuns;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Where a Shattered World Shard came from: the rift's theme, the defender (boss entity id) and the world's
 * generated core and world names. Written at the boss kill, read by the tooltip and the boss death line, and
 * recorded in the feeder's atlas when the Core takes the shard. {@code core} is lower-case "the ...".
 */
public record ShardOrigin(ResourceLocation theme, ResourceLocation boss, String core, String world) {
    public static final Codec<ShardOrigin> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("theme").forGetter(ShardOrigin::theme),
            ResourceLocation.CODEC.fieldOf("boss").forGetter(ShardOrigin::boss),
            Codec.STRING.fieldOf("core").forGetter(ShardOrigin::core),
            Codec.STRING.fieldOf("world").forGetter(ShardOrigin::world)).apply(i, ShardOrigin::new));
    public static final StreamCodec<ByteBuf, ShardOrigin> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ShardOrigin::theme,
            ResourceLocation.STREAM_CODEC, ShardOrigin::boss,
            ByteBufCodecs.STRING_UTF8, ShardOrigin::core,
            ByteBufCodecs.STRING_UTF8, ShardOrigin::world,
            ShardOrigin::new);

    /** Names for a theme and seed; a theme with no generator of its own names like default. */
    public static ShardOrigin of(ResourceLocation theme, ResourceLocation boss, long seed) {
        var named = ShardNames.of(theme.getPath(), seed);
        return new ShardOrigin(theme, boss, named.core(), named.world());
    }

    /** The origin of a defender that fell in a rift, seeded from the rift seed; empty outside a rift. */
    public static Optional<ShardOrigin> at(ServerLevel level, Entity boss) {
        if (!RiftLevels.isRift(level.dimension())) return Optional.empty();
        var theme = RiftRuns.theme(level);
        return Optional.of(of(theme != null ? theme : RiftfallCore.id("default"),
                BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()), RiftLevels.seed(level)));
    }

    /** The core as a tooltip opens with it: "The Kept Flame". */
    public String coreTitle() { return core.isEmpty() ? core : Character.toUpperCase(core.charAt(0)) + core.substring(1); }

    public boolean coreHoldsWorld() { return new ShardNames.Named(core, world).coreHoldsWorld(); }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("theme", theme.toString());
        tag.putString("boss", boss.toString());
        tag.putString("core", core);
        tag.putString("world", world);
        return tag;
    }

    public static Optional<ShardOrigin> load(CompoundTag tag) {
        var theme = ResourceLocation.tryParse(tag.getString("theme"));
        var boss = ResourceLocation.tryParse(tag.getString("boss"));
        return theme == null || boss == null ? Optional.empty()
                : Optional.of(new ShardOrigin(theme, boss, tag.getString("core"), tag.getString("world")));
    }
}
