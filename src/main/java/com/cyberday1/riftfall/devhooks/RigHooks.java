package com.cyberday1.riftfall.devhooks;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;

/**
 * The one way main code reaches the probe source set, which only the rig jar carries. By name, so the
 * release jar never links against it; there the class is simply absent and nothing registers.
 */
public final class RigHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Has a public static {@code register()}, gated on {@link #FLAG} itself. */
    public static final String PROBE_ENTRY = "com.cyberday1.riftfall.probe.ProbeCommand";
    public static final String FLAG = "riftfall.probe";

    private RigHooks() {
    }

    public static void register() {
        Class<?> entry;
        try {
            entry = Class.forName(PROBE_ENTRY, true, RigHooks.class.getClassLoader());
        } catch (ClassNotFoundException releaseJar) {
            if (Boolean.getBoolean(FLAG)) {
                LOGGER.warn("-D{}=true, but this is the release jar: no probes, /riftfallprobe is not registered", FLAG);
            }
            return;
        }
        try {
            entry.getMethod("register").invoke(null);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(PROBE_ENTRY + ".register() failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(PROBE_ENTRY + " has no public static register()", e);
        }
    }
}
