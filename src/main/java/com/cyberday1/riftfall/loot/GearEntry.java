package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.gear.ClassLoot;
import com.cyberday1.riftfall.gear.GearPool;
import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code riftfall:gear} — one piece of modded gear, drawn balanced across {@link GearPool}'s slots.
 *
 * <h2>Why this is an entry and not something {@code fill} does afterwards</h2>
 * A stack handed back by a loot table has already been through Apotheosis' affix conversion,
 * NeoForge's loot modifiers and {@code MixinLootTable}'s rarity re-roll, all of which live inside
 * {@code LootTable#getRandomItems}. Gear injected in Java after that call would skip every one of
 * them and arrive with no affixes, no rarity and no stats.
 *
 * <p>
 * One codec field beyond the usual weight, quality, conditions and functions: {@code pay_if}, which the
 * four barrel tables use for {@code barrelGearScale}. A datapack asks for gear and the pool decides which.
 */
public class GearEntry extends LootPoolSingletonContainer {

    public static final MapCodec<GearEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BarrelGearChance.PAY_IF.forGetter(entry -> entry.payIf))
            .and(singletonFields(instance))
            .apply(instance, GearEntry::new));

    /** Tested after the pool picks this entry; a miss yields nothing (barrelGearScale). */
    private final List<LootItemCondition> payIf;

    private GearEntry(List<LootItemCondition> payIf, int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.payIf = List.copyOf(payIf);
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        BarrelGearChance.validate(payIf, context);
    }

    @Override
    public LootPoolEntryType getType() {
        return RiftfallLootEntries.GEAR.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        if (!BarrelGearChance.pays(payIf, context)) {
            return;
        }
        GearPool.Slot bucket = GearPool.drawBucket(context.getRandom());
        if (bucket == null) {
            return;
        }
        // A weapon bucket is a weapon roll: the opener's class may pick the family.
        List<ItemStack> mine = GearPool.isWeapon(bucket) ? ClassLoot.weaponRoll(context, false) : null;
        if (mine != null) {
            mine.forEach(consumer);
            return;
        }
        ItemStack gear = GearPool.drawFrom(bucket, context.getRandom());
        if (!gear.isEmpty()) {
            com.cyberday1.riftfall.gear.BarrelAccessoryLoot.authored(gear);
            consumer.accept(gear);
        }
    }
}
