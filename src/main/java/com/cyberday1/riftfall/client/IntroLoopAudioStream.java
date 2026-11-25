package com.cyberday1.riftfall.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionException;

/**
 * One stream that plays an intro once and then loops a second file forever.
 *
 * <p>
 * The ambience packs cut their music as an intro that leads into a seamless loop, and the join is
 * meant to be exact. Two sound instances cannot do that: a channel is only reaped a tick or more
 * after it drains, and a streaming channel reads seconds ahead, so the second cue would start late
 * by an audible and variable amount. Splicing below the sound engine instead means the loop's first
 * sample follows the intro's last one with nothing in between.
 *
 * <p>
 * NeoForge's {@code SoundInstance#getStream} hook is what makes this reachable at all; vanilla only
 * ever hands the sound engine one file per instance.
 */
final class IntroLoopAudioStream implements AudioStream {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final AudioStream intro;
    private final AudioStream loop;
    private boolean introDone;

    private IntroLoopAudioStream(AudioStream intro, AudioStream loop) {
        this.intro = intro;
        this.loop = loop;
    }

    /**
     * Opens the pair, or just the loop if the intro cannot be used. A missing or oddly encoded intro
     * costs the lead-in, not the music.
     */
    static AudioStream open(ResourceLocation introPath, ResourceLocation loopPath, boolean looping) {
        ResourceProvider resources = Minecraft.getInstance().getResourceManager();
        AudioStream loop;
        try {
            InputStream stream = resources.open(loopPath);
            loop = looping ? new LoopingAudioStream(JOrbisAudioStream::new, stream) : new JOrbisAudioStream(stream);
        } catch (IOException failed) {
            throw new CompletionException(failed);
        }

        AudioStream intro;
        try {
            intro = new JOrbisAudioStream(resources.open(introPath));
        } catch (IOException failed) {
            LOGGER.warn("No intro for {}, starting on the loop: {}", loopPath, failed.toString());
            return loop;
        }

        if (!intro.getFormat().matches(loop.getFormat())) {
            LOGGER.warn("Intro {} is {} but its loop is {}; skipping the intro.",
                    introPath, intro.getFormat(), loop.getFormat());
            closeQuietly(intro);
            return loop;
        }
        return new IntroLoopAudioStream(intro, loop);
    }

    @Override
    public AudioFormat getFormat() {
        return loop.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (!introDone) {
            ByteBuffer buffer = intro.read(size);
            if (buffer.hasRemaining()) {
                return buffer;
            }
            // An empty read is this stream's only end-of-file signal, so hand straight over rather
            // than returning it: the channel would take it for the end of the sound.
            introDone = true;
            intro.close();
        }
        return loop.read(size);
    }

    @Override
    public void close() throws IOException {
        if (!introDone) {
            closeQuietly(intro);
        }
        loop.close();
    }

    private static void closeQuietly(AudioStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing useful to do about a stream we are already abandoning.
        }
    }
}
