package com.cyberday1.riftfall.mob;

import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/** Which Spore bodies {@link SporeContainment} refuses outside rifts: the decision alone, no world state. */
public final class SporeContainmentRule {

    /** Spawns a person chose: commands, eggs, dispensed eggs. These keep working so Spore can be tested. */
    public static final Set<MobSpawnType> EXPLICIT = EnumSet.of(MobSpawnType.COMMAND, MobSpawnType.SPAWN_EGG,
            MobSpawnType.DISPENSER);

    /** Bodies only infection or spread makes: conversion products, scents, organoids, hivemind raiders. */
    public static final Set<String> ECOLOGY = Set.of("inf_player", "inf_contruct", "bairn", "scent", "mound",
            "tendril", "proto", "hivetumor", "reconstructor", "gastgaber", "corpse_piece", "vigil", "vanguard");

    private SporeContainmentRule() {
    }

    /** A conversion is always refused; an ecology body unless a person placed it; saves and projectiles never. */
    public static boolean refusesJoin(String namespace, String path, boolean projectile, boolean loadedFromDisk,
            @Nullable MobSpawnType type) {
        if (!namespace.equals("spore") || projectile || loadedFromDisk) return false;
        if (type == MobSpawnType.CONVERSION) return true;
        return ECOLOGY.contains(path) && (type == null || !EXPLICIT.contains(type));
    }
}
