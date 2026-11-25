package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.init.RiftfallSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * The music a rift plays for as long as you are in it.
 *
 * <p>
 * Looping is left to the sound engine, which needs all three of a looping instance, no delay and
 * {@code "stream": true} in {@code sounds.json} to loop a stream without a gap. The delay field is
 * therefore load-bearing: any value above zero switches the engine to replaying the instance by
 * hand, which is audible.
 */
public final class RiftAmbienceSoundInstance extends AbstractTickableSoundInstance {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int FADE_IN_TICKS = 40;
    static final int FADE_OUT_TICKS = 30;

    /**
     * How loud the ambience settles once it is up, before the music slider is applied. Below one
     * because this is the only track playing and it plays for the whole run: a level that reads as
     * right for a piece you hear once is too much for one you hear for an hour.
     */
    private static final float CEILING = 0.5F;

    private final ResourceLocation introEvent;
    private boolean leaving;

    RiftAmbienceSoundInstance(RiftfallSounds.Ambience ambience) {
        super(ambience.loop().value(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.introEvent = ambience.intro().value().getLocation();
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0F;
    }

    /**
     * Without this the engine refuses to start a sound whose volume is zero, which is every sound
     * that fades in.
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Starts the walk down to silence. The instance stops itself when it gets there. */
    void fadeOut() {
        this.leaving = true;
    }

    @Override
    public void tick() {
        if (leaving) {
            volume -= CEILING / FADE_OUT_TICKS;
            if (volume <= 0.0F) {
                volume = 0.0F;
                stop();
                LOGGER.info("Riftfall ambience: {} faded to nothing and stopped itself", getLocation());
            }
        } else if (volume < CEILING) {
            volume = Math.min(CEILING, volume + CEILING / FADE_IN_TICKS);
        }
    }

    /**
     * Plays the theme's intro into its loop as one stream. Falls back to the loop alone for the
     * themes whose pack ships no intro.
     */
    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound loop, boolean looping) {
        Sound intro = introSound();
        if (intro == null) {
            return buffers.getStream(loop.getPath(), looping);
        }
        ResourceLocation introPath = intro.getPath();
        ResourceLocation loopPath = loop.getPath();
        return CompletableFuture.supplyAsync(
                () -> IntroLoopAudioStream.open(introPath, loopPath, looping), Util.nonCriticalIoPool());
    }

    /**
     * Null for a theme with no intro. Whether one exists is a question for {@code sounds.json}: the
     * event is registered either way, and only a file makes it real.
     */
    @Nullable
    private Sound introSound() {
        WeighedSoundEvents events = Minecraft.getInstance().getSoundManager().getSoundEvent(introEvent);
        return events == null ? null : events.getSound(random);
    }
}
