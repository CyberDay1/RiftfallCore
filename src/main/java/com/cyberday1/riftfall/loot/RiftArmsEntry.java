package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.compat.TaczLoot;
import com.cyberday1.riftfall.gear.ClassLoot;
import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.gear.RiftArms;
import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.skill.GunnerGuns;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * {@code riftfall:rift_arms}: one TaCZ gun (loaded, with a spare magazine) or attachment, drawn from
 * {@link RiftArms}' table for the rift's tier. {@code boss: true} draws by the table's boss rule
 * ({@link RiftArms.Table#pool}). Nothing without TaCZ, and nothing outside a rift (the {@code rift_only} strip would
 * take it anyway).
 */
public class RiftArmsEntry extends LootPoolSingletonContainer {

    public static final MapCodec<RiftArmsEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("kind").forGetter(entry -> entry.kind.key()),
                    Codec.BOOL.optionalFieldOf("boss", false).forGetter(entry -> entry.boss),
                    BarrelGearChance.PAY_IF.forGetter(entry -> entry.payIf))
            .and(singletonFields(instance))
            .apply(instance, RiftArmsEntry::new));

    private final RiftArms.Kind kind;
    private final boolean boss;
    /** Tested after the pool picks this entry; a miss yields nothing (barrelGearScale). */
    private final List<LootItemCondition> payIf;

    private RiftArmsEntry(String kind, boolean boss, List<LootItemCondition> payIf, int weight, int quality,
            List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.payIf = List.copyOf(payIf);
        RiftArms.Kind parsed = null;
        for (RiftArms.Kind k : RiftArms.Kind.values()) {
            if (k.key().equals(kind)) {
                parsed = k;
            }
        }
        if (parsed == null) {
            throw new IllegalArgumentException("riftfall:rift_arms kind must be gun or attachment, not " + kind);
        }
        this.kind = parsed;
        this.boss = boss;
    }

    @Override
    public LootPoolEntryType getType() {
        return RiftfallLootEntries.RIFT_ARMS.get();
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        BarrelGearChance.validate(payIf, context);
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        if (!BarrelGearChance.pays(payIf, context)) {
            return;
        }
        // A gun is a weapon roll, and so is an attachment for a class with no guns.
        List<ItemStack> mine = ClassLoot.armsRoll(context, kind, boss);
        (mine != null ? mine : roll(kind, boss, context)).forEach(consumer);
    }

    /** One draw from {@code kind}'s table at this rift's tier: a loaded gun and its spare ammo, or an attachment. */
    public static List<ItemStack> roll(RiftArms.Kind kind, boolean boss, LootContext context) {
        ServerLevel level = context.getLevel();
        int total = available(kind, boss, level);
        if (total <= 0) {
            return List.of();
        }
        String picked = RiftArms.table(kind).pick(RiftArms.tier(RiftLevels.tier(level)), boss,
                context.getRandom().nextInt(total));
        ResourceLocation id = picked == null ? null : ResourceLocation.tryParse(picked);
        if (id == null) {
            return List.of();
        }
        List<ItemStack> stacks = kind == RiftArms.Kind.GUN
                ? TaczLoot.gun(id, level.registryAccess())
                : TaczLoot.attachment(id);
        if (kind == RiftArms.Kind.GUN) {
            int tier = RiftArms.tier(RiftLevels.tier(level));
            int rarity = GunGrade.rollRarity(tier, context.getRandom().nextInt());
            for (ItemStack stack : stacks) {
                GunGrade.stamp(stack, tier, rarity);
                com.cyberday1.riftfall.gear.RiftLootRarity.socketGun(stack, context.getRandom());
            }
        }
        return stacks;
    }

    /** The table's total weight here, or 0 when nothing can drop: no TaCZ, no table, or not a rift. */
    public static int available(RiftArms.Kind kind, boolean boss, ServerLevel level) {
        RiftArms.Table table = RiftArms.table(kind);
        if (!GunnerGuns.loaded() || table == null || !RiftLevels.isRiftWorld(level)) {
            return 0;
        }
        return table.total(RiftArms.tier(RiftLevels.tier(level)), boss);
    }
}
