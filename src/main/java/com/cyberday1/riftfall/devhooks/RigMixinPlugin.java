package com.cyberday1.riftfall.devhooks;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plugin of {@code riftfall.mixins.json}: adds the probe set's mixins when the jar carries them (the rig
 * jar) and nothing otherwise, so the release jar's config never names a class it lacks.
 */
public final class RigMixinPlugin implements IMixinConfigPlugin {

    static final String PACKAGE = "com.cyberday1.riftfall.mixin.";
    /** Simple names, in {@link #PACKAGE}, living under src/probe. */
    static final List<String> RIG_ONLY = List.of("MixinProbeStandInSends");

    static List<String> present(Predicate<String> exists) {
        return RIG_ONLY.stream().filter(name -> exists.test(PACKAGE + name)).toList();
    }

    private static boolean onClasspath(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + className, e);
        }
    }

    @Override
    public List<String> getMixins() {
        List<String> found = present(RigMixinPlugin::onClasspath);
        if (!found.isEmpty()) {
            LogManager.getLogger("riftfall").info("Riftfall rig jar: adding probe mixins {}", found);
        }
        return found;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
