package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.worldgen.StructureBan;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Where a world decides which structure sets it has, and the only place it decides it.
 *
 * <p>
 * The two entry points &mdash; {@code createForNormal} and {@code createForFlat} &mdash; each build
 * their own list and hand it to this constructor, and every later question about structures reads the
 * field it lands in. Injecting on the constructor rather than on either factory covers both, and
 * covers anything that constructs one some other way.
 *
 * <p>
 * The field is replaced rather than emptied because it is assigned from the parameter and the caller
 * still holds the original list. {@link StructureBan} says which sets survive.
 */
@Mixin(ChunkGeneratorStructureState.class)
public class MixinChunkGeneratorStructureState {

    @Shadow
    @Final
    @Mutable
    private List<Holder<StructureSet>> possibleStructureSets;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void riftfall$banModdedStructures(CallbackInfo callback) {
        this.possibleStructureSets = StructureBan.allowed(this.possibleStructureSets);
    }
}
