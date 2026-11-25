package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillCooldownPayload;
import com.cyberday1.riftfall.skill.SkillBook;

import java.util.Arrays;

/**
 * The client's copy of each loadout slot as the server last said: its skill, ready-at game tick and timer
 * length. The codex slots and the cooldown HUD read it against the level's own game time.
 */
public final class RiftSkillCooldowns {

    /** One slot: {@code skill} is "" when empty; {@code duration} is 0 when ready. */
    public record Slot(String skill, long readyAt, int duration) {
        public static final Slot EMPTY = new Slot("", 0L, 0);

        public boolean empty() {
            return skill.isEmpty();
        }
    }

    private static final Slot[] SLOTS = new Slot[SkillBook.SLOTS];

    static {
        Arrays.fill(SLOTS, Slot.EMPTY);
    }

    private RiftSkillCooldowns() {
    }

    /** Handler for the clientbound payload. A slot the client does not have is ignored. */
    public static synchronized void told(SkillCooldownPayload payload) {
        if (payload.slot() >= 0 && payload.slot() < SLOTS.length) {
            SLOTS[payload.slot()] = new Slot(payload.skill(), payload.readyAt(), Math.max(0, payload.duration()));
        }
    }

    public static synchronized Slot slot(int slot) {
        return slot < 0 || slot >= SLOTS.length ? Slot.EMPTY : SLOTS[slot];
    }

    /** Ticks until the slot is ready at {@code gameTime}, or 0. */
    public static synchronized long remaining(int slot, long gameTime) {
        if (slot < 0 || slot >= SLOTS.length) {
            return 0L;
        }
        return Math.max(0L, SLOTS[slot].readyAt() - gameTime);
    }

    public static synchronized void forget() {
        Arrays.fill(SLOTS, Slot.EMPTY);
    }
}
