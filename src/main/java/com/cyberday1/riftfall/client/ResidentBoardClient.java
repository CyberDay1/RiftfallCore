package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.ResidentBoardPayload;

/** The last Requests, Orders and Path snapshot the server sent; the landing redraws when the version moves. */
public final class ResidentBoardClient {
    private static volatile ResidentBoardPayload latest = ResidentBoardPayload.EMPTY;
    private static volatile int version;

    private ResidentBoardClient() {}

    public static void told(ResidentBoardPayload payload) {
        latest = payload;
        version++;
    }

    /** Moves the version too, so an open landing redraws empty. */
    static void forget() { told(ResidentBoardPayload.EMPTY); }

    public static ResidentBoardPayload latest() { return latest; }
    public static int version() { return version; }
}
