package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.npc.depth.HomeResidentDepth;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.UUID;

/**
 * A resident's rank: cumulative XP plus a gate, the highest tier any home member has cleared (RK1).
 * Order slots 1/1/2/3, board slots 1/2/2/3.
 */
public enum ResidentRank implements StringRepresentable {
    APPRENTICE(0, 0, 1, 1), JOURNEYMAN(100, 3, 1, 2), EXPERT(400, 7, 2, 2), MASTER(1200, 12, 3, 3);

    public static final Codec<ResidentRank> CODEC = StringRepresentable.fromEnum(ResidentRank::values);

    private final int xp, gate, orderSlots, boardSlots;

    ResidentRank(int xp, int gate, int orderSlots, int boardSlots) {
        this.xp = xp; this.gate = gate; this.orderSlots = orderSlots; this.boardSlots = boardSlots;
    }

    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public boolean atLeast(ResidentRank other) { return ordinal() >= other.ordinal(); }
    /** Cumulative XP to reach this rank. */
    public int xp() { return xp; }
    /** The tier a home member must have cleared. */
    public int gate() { return gate; }
    public int orderSlots() { return orderSlots; }
    public int boardSlots() { return boardSlots; }
    public ResidentRank next() { return this == MASTER ? null : values()[ordinal() + 1]; }

    /** The highest rank whose XP and gate are both met. XP alone never promotes. */
    public static ResidentRank earned(int xp, int bestCleared) {
        var rank = APPRENTICE;
        for (var r : values()) if (xp >= r.xp && bestCleared >= r.gate) rank = r;
        return rank;
    }

    /** The home's rank for a role, read in that home's level; anywhere else the floor. */
    public static ResidentRank of(ServerLevel home, NpcRole role) {
        UUID owner = HomeAccess.owner(home.dimension().location());
        return owner == null ? APPRENTICE : HomeResidentDepth.of(home.getServer()).rank(owner, role);
    }
}
