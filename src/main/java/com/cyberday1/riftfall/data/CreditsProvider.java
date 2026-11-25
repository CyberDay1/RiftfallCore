package com.cyberday1.riftfall.data;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.util.concurrent.CompletableFuture;

/** Writes the credits roll from ATTRIBUTION.md; CreditsDriftTest fails when the two drift apart. */
public final class CreditsProvider implements DataProvider {
    private final PackOutput output;

    public CreditsProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var file = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(AttributionCredits.OUTPUT.substring("assets/".length()));
        return DataProvider.saveStable(cache, AttributionCredits.generate(AttributionCredits.locate()), file);
    }

    @Override
    public String getName() {
        return "Riftfall credits roll";
    }
}
