package com.cyberday1.riftfall.block.marker;

import net.minecraft.util.StringRepresentable;

/**
 * Values of the {@code rift_mob_spawner_state} blockstate property.
 *
 * <p>
 * The serialized names are an interface contract with the authored templates, not a design choice:
 * a template that says {@code rift_mob_spawner_state: "waiting_for_reward_ejection"} needs a value
 * spelled exactly that, or {@code NbtUtils.readBlockState} drops back to the default and the
 * spawner loses whatever state the room author set.
 *
 * <p>
 * Nothing drives these yet — the spawner is inert until the activation work lands. The enum exists
 * so the property parses, and so that work has the vocabulary already agreed.
 */
public enum RiftMobSpawnerState implements StringRepresentable {

    INACTIVE("inactive"),
    WAITING_FOR_PLAYERS("waiting_for_players"),
    ACTIVE("active"),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection"),
    EJECTING_REWARD("ejecting_reward"),
    COOLDOWN("cooldown");

    private final String name;

    RiftMobSpawnerState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
