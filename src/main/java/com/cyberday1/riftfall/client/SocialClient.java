package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SocialBadgePayload;
import com.cyberday1.riftfall.network.SocialReplyPayload;
import com.cyberday1.riftfall.network.SocialViewPayload;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/** The client's copy of the Party page's view, the last reply to a button, and the bookmark's badge count. */
public final class SocialClient {

    /** How long a reply stays on the page. */
    static final long REPLY_MILLIS = 15_000L;

    private static SocialViewPayload latest = SocialViewPayload.EMPTY;
    private static boolean answered;
    private static SocialBadgePayload badge = SocialBadgePayload.NONE;
    private static @Nullable SocialReplyPayload reply;
    private static long replyAt;

    private SocialClient() {
    }

    public static void told(SocialViewPayload payload) {
        latest = payload;
        answered = true;
    }

    public static void replied(SocialReplyPayload payload) {
        reply = payload;
        replyAt = Util.getMillis();
    }

    public static void badge(SocialBadgePayload payload) {
        badge = payload;
    }

    static SocialViewPayload latest() {
        return latest;
    }

    static boolean answered() {
        return answered;
    }

    static void asked() {
        answered = false;
    }

    static SocialBadgePayload badge() {
        return badge;
    }

    static @Nullable SocialReplyPayload reply() {
        return reply != null && Util.getMillis() - replyAt < REPLY_MILLIS ? reply : null;
    }

    static void clearReply() {
        reply = null;
    }

    static void forget() {
        latest = SocialViewPayload.EMPTY;
        answered = false;
        badge = SocialBadgePayload.NONE;
        reply = null;
    }
}
