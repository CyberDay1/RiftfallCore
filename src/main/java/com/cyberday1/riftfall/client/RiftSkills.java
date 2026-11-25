package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillScreenPayload;

/**
 * The client's copy of the last skill view the server sent, on {@link RiftSheet}'s shape. The
 * screen asks for a fresh one when it opens; the server also pushes one after every request and
 * after every reload.
 */
public final class RiftSkills {

    private static volatile SkillScreenPayload latest = SkillScreenPayload.EMPTY;

    private RiftSkills() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(SkillScreenPayload payload) {
        latest = payload;
    }

    static SkillScreenPayload latest() {
        return latest;
    }

    /** Leaving a world must not carry one character's skills into the next one. */
    public static void forget() {
        latest = SkillScreenPayload.EMPTY;
    }
}
