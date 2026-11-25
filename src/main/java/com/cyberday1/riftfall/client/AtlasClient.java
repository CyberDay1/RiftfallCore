package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.AtlasPayload;

/** The last atlas list the server sent for the Cartographer page. Holds no client classes, so common code may read it. */
public final class AtlasClient {
    private static volatile AtlasPayload latest = AtlasPayload.EMPTY;

    private AtlasClient() {}

    public static void told(AtlasPayload payload) { latest = payload; }
    public static AtlasPayload latest() { return latest; }
    static void forget() { latest = AtlasPayload.EMPTY; }
}
