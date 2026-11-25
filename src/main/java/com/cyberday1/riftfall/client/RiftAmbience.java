package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.init.RiftfallSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * What the client is currently standing in, and the music that goes with it.
 *
 * <p>
 * Static because there is one client and one of it: the theme arrives by packet from
 * {@code RiftfallPayloads}, and every other reader here just wants to know whether we are in a rift
 * at all.
 */
public final class RiftAmbience {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static ResourceLocation theme;

    @Nullable
    private static RiftAmbienceSoundInstance playing;

    /**
     * The instance {@link #release} handed over to its own fade. Held only so that
     * {@link #clientTick} can see whether the fade actually happened.
     */
    @Nullable
    private static RiftAmbienceSoundInstance fading;

    private static int fadingFor;

    /**
     * How long the backstop waits before it stops disbelieving. Twice the fade, so an ordinary
     * fade-out has finished and been swept up well inside it.
     */
    private static final int FADE_GRACE_TICKS = RiftAmbienceSoundInstance.FADE_OUT_TICKS * 2;

    private RiftAmbience() {
    }

    /** Whether the local player is inside a rift. Drives the suppression of vanilla music. */
    public static boolean inRift() {
        return theme != null;
    }

    /**
     * The server's answer, with null for "not in a rift". Re-sending the same theme is deliberately
     * a no-op: a respawn inside the same rift should not restart the music from its intro.
     */
    public static void themeIs(@Nullable ResourceLocation next) {
        if (next == null) {
            if (theme != null) {
                LOGGER.info("Riftfall ambience: no theme, out of {}", theme);
            }
            theme = null;
            release();
            return;
        }
        if (next.equals(theme) && stillPlaying()) {
            LOGGER.info("Riftfall ambience: theme {} again, leaving it playing", next);
            return;
        }
        theme = next;
        release();
        start();
    }

    /**
     * A reload takes the sound engine down and every instance with it, and no packet follows to say
     * so: the theme outlives the engine, so the music is started again from here.
     */
    public static void soundEngineLoaded() {
        if (theme == null || stillPlaying()) {
            return;
        }
        LOGGER.info("Riftfall ambience: the sound engine restarted, starting {} again", theme);
        playing = null;
        fading = null;
        start();
    }

    /** Leaving the world. The sound engine is torn down with it, so only the state needs clearing. */
    public static void forget() {
        if (theme != null) {
            LOGGER.info("Riftfall ambience: leaving the world, forgetting {}", theme);
        }
        theme = null;
        playing = null;
        fading = null;
    }

    /**
     * The engine is asked again afterwards because {@code play} is quiet about refusing: a channel by
     * the time it returns is the difference between started and only asked for.
     */
    private static void start() {
        playing = new RiftAmbienceSoundInstance(RiftfallSounds.ambienceFor(theme));
        Minecraft.getInstance().getSoundManager().play(playing);
        if (stillPlaying()) {
            LOGGER.info("Riftfall ambience: playing {} for theme {}", playing.getLocation(), theme);
        } else {
            LOGGER.warn("Riftfall ambience: the engine took no channel for {}, theme {}",
                    playing.getLocation(), theme);
        }
    }

    private static void release() {
        if (playing != null) {
            LOGGER.info("Riftfall ambience: fading out {}", playing.getLocation());
            playing.fadeOut();
            fading = playing;
            fadingFor = 0;
            playing = null;
        }
    }

    /**
     * Makes sure a rift's music is actually gone once we have left one.
     *
     * <p>
     * A fade is walked down by the sound engine ticking the instance, so a fade is only as reliable
     * as the instance still being ticked &mdash; and a player left a rift once and kept hearing it.
     * This asks the engine, which is the only thing that knows, and stops the sound outright if the
     * fade has plainly not happened. The WARN is the whole diagnosis: an ordinary exit never logs.
     */
    public static void clientTick() {
        if (fading == null) {
            return;
        }
        if (!Minecraft.getInstance().getSoundManager().isActive(fading)) {
            fading = null;
            return;
        }
        if (++fadingFor < FADE_GRACE_TICKS) {
            return;
        }
        LOGGER.warn("Riftfall ambience: {} was still playing {} ticks after being faded out, stopping it outright",
                fading.getLocation(), fadingFor);
        Minecraft.getInstance().getSoundManager().stop(fading);
        fading = null;
    }

    /**
     * The engine is asked as well as the instance: turning the music slider to zero and back up
     * again drops the channel without the instance ever hearing about it.
     */
    private static boolean stillPlaying() {
        return playing != null
                && !playing.isStopped()
                && Minecraft.getInstance().getSoundManager().isActive(playing);
    }
}
