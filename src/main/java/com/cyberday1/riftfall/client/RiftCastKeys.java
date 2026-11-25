package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.network.SkillCastPayload;
import com.cyberday1.riftfall.skill.SkillBook;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * One key per loadout slot: Ctrl+1 through Ctrl+8 and Ctrl+9 for the ultimate. Saved mouse
 * bindings remain personal choices. There is no cast-selected key: nothing selects a slot until
 * the skill wheel (V4) exists.
 */
public final class RiftCastKeys {

    static final KeyMapping[] SLOTS = new KeyMapping[SkillBook.SLOTS];

    static {
        for (int slot = 0; slot < SLOTS.length; slot++) {
            SLOTS[slot] = RiftKeys.key(slotKey(slot), org.lwjgl.glfw.GLFW.GLFW_KEY_1 + slot, KeyModifier.CONTROL);
        }
    }

    private RiftCastKeys() {
    }

    /** {@code key.riftfall.cast_slot_1} … {@code _8}, and {@code key.riftfall.cast_ultimate} for slot 8. */
    public static String slotKey(int slot) {
        return slot == SkillBook.ULTIMATE_SLOT
                ? "key." + RiftfallCore.MODID + ".cast_ultimate"
                : "key." + RiftfallCore.MODID + ".cast_slot_" + (slot + 1);
    }

    static List<KeyMapping> all() {
        return List.of(SLOTS);
    }

    /** The slots one tick's presses ask for: each slot key its own, once per press. */
    static List<Integer> slotsFor(int[] slotPresses) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < slotPresses.length; slot++) {
            for (int press = 0; press < slotPresses[slot]; press++) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** Client tick: drains every key's queued presses and sends one request each. */
    static void tick(boolean inGame) {
        int[] presses = new int[SLOTS.length];
        for (int slot = 0; slot < SLOTS.length; slot++) {
            presses[slot] = RiftKeys.drain(SLOTS[slot]);
        }
        if (inGame) {
            slotsFor(presses).forEach(slot -> PacketDistributor.sendToServer(new SkillCastPayload(slot)));
        }
    }
}
