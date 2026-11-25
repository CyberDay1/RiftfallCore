package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.ArrayList;

/** Builds a book snapshot on request. Reading it never casts, spends mana or starts a timer. */
public final class SkillGuideService {
    private SkillGuideService() { }

    public static SkillScreenPayload enrich(ServerPlayer player, SkillScreenPayload view) {
        var entries = new ArrayList<SkillScreenPayload.Entry>();
        for (var entry : view.entries()) {
            SkillDef def = SkillDefs.table().byId().get(entry.id());
            PassiveDef passive = SkillDefs.table().passives().get(entry.id());
            var guide = def == null ? passive == null ? SkillScreenPayload.Guide.NONE : passiveGuide(player, passive, entry.rank())
                    : guide(player, def, entry.rank());
            entries.add(new SkillScreenPayload.Entry(entry.id(), entry.ascendancy(), entry.ultimate(), entry.passive(),
                    entry.rank(), entry.maxRank(), entry.cost(), entry.status(), entry.universal(),
                    def == null ? entry.facts() : facts(player, def, entry), guide));
        }
        return new SkillScreenPayload(view.skillClass(), view.ascension(), view.level(), view.available(), view.inRift(),
                view.ultimateOpen(), entries, view.loadout(), view.action(), view.result(), view.skillRespec(), view.statRespec());
    }

    public static SkillScreenPayload.Guide passiveGuide(PassiveDef def, int rank) {
        int from = Math.max(1, Math.min(rank, def.node().maxRank()));
        int to = rank == 0 ? def.node().maxRank() : Math.min(from + 1, def.node().maxRank());
        return SkillGuide.compare(PassiveGuide.describe(def, from), PassiveGuide.describe(def, to),
                GuideLang.text("rank", Integer.toString(to)));
    }

    public static SkillScreenPayload.Guide passiveGuide(ServerPlayer player, PassiveDef def, int rank) {
        var guide = passiveGuide(def, rank);
        if (!def.grantsSpell()) return guide;
        try {
            JsonObject spell = read(player, def.spell());
            if (spell == null) return guide;
            var detail = SkillGuide.describe(spell, new SkillGuide.Power(1, 1, 0, 0, 0, 0));
            if (detail.fixed) return guide;
            var notes = new ArrayList<>(guide.notes());
            notes.add(GuideLang.text("bonus_strike_scaling", detail.scaling));
            Component formula = SkillScaling.formula(SkillGuide.string(spell, "school", ""));
            if (formula != null) notes.add(formula);
            return new SkillScreenPayload.Guide(guide.figures(), notes, guide.tokens(), guide.scaling());
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Cannot read passive scaling for {}", def.id(), error);
            return guide;
        }
    }

    private static SkillScreenPayload.Facts facts(ServerPlayer player, SkillDef def, SkillScreenPayload.Entry entry) {
        var f = entry.facts();
        var node = def.cast();
        int rank = Math.min(entry.rank(), node.maxRank());
        double haste = SkillCasts.abilityHaste(player, node);
        boolean creative = SkillCooldowns.bypasses(player);
        return new SkillScreenPayload.Facts(f.ability(), f.engine(), f.powerAttribute(), f.lever(), f.leverRate(),
                f.leverSeconds(), creative ? 0 : SkillRanks.cooldownTicks(node, rank, haste),
                creative ? 0 : SkillRanks.cooldownTicks(node, Math.min(rank + 1, node.maxRank()), haste),
                creative ? 0 : SkillRanks.cooldownTicks(node, node.maxRank(), haste), f.costNext(), f.costMax(), f.attribute(),
                f.bonusPerRank(), f.powerMultiplier(), f.bonusBase());
    }

    public static SkillScreenPayload.Guide guide(ServerPlayer player, SkillDef def, int rank) {
        SkillNode node = def.cast();
        if (node.engine() != SkillNode.Engine.SPELL_ENGINE || !SkillCasts.engineAvailable(node.engine())) {
            return SkillScreenPayload.Guide.NONE;
        }
        try {
            JsonObject spell = read(player, node.ability());
            if (spell == null) return SkillScreenPayload.Guide.NONE;
            int from = Math.max(1, Math.min(rank, node.maxRank()));
            int to = rank == 0 ? node.maxRank() : Math.min(from + 1, node.maxRank());
            SkillGuide.Page first = page(player, def, spell, from);
            SkillGuide.Page next = from == to ? first : page(player, def, spell, to);
            return SkillGuide.compare(first, next, GuideLang.text("rank", Integer.toString(to)));
        } catch (IOException | RuntimeException error) {
            // A malformed pack entry cannot prevent opening the rest of the book.
            LogUtils.getLogger().warn("Cannot build skill guide for {}", node.id(), error);
            return SkillScreenPayload.Guide.NONE;
        }
    }

    private static SkillGuide.Page page(ServerPlayer player, SkillDef def, JsonObject spell, int rank) throws IOException {
        SkillNode node = def.cast();
        double base = RiftSpellEngine.guidePower(player, node, rank);
        if (!Double.isFinite(base)) throw new IllegalArgumentException("Unresolved school power");
        SkillCasts.Riders riders = SkillCasts.Riders.of(node, rank,
                SkillCasts.attribute(player, RiftfallAttributes.SUMMON_DURATION),
                SkillCasts.attribute(player, RiftfallAttributes.SUMMON_POWER),
                SkillCasts.attribute(player, RiftfallAttributes.DOT_DURATION));
        var power = new SkillGuide.Power(base, node.powerMultiplier(), riders.dotSeconds() + riders.buffSeconds(),
                riders.dotSeconds() + riders.summonSeconds(), riders.summonSeconds(), riders.ownerScaling(),
                SkillCasts.attribute(player, RiftfallAttributes.HEALING_DONE), SkillCasts.attribute(player, RiftfallAttributes.SELF_HEALING));
        SkillGuide.Page page = SkillGuide.describe(spell, power);
        SkillSummonGuide.describe(spell, power, page, id ->
                net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id))
                        .map(holder -> player.getAttributeValue(holder)).orElse(0.0));
        String school = SkillGuide.string(spell, "school", "");
        if (node.dot() != null) {
            Component label = page.fixed ? SkillScaling.label(school) : page.scaling;
            Component dotScaling = GuideLang.text("scaling.dot");
            page.scaling(label == null ? dotScaling : GuideLang.text("join.semicolon", label, dotScaling),
                    page.fixed ? SkillScaling.attackScaled(school) : page.attackScaled);
            SkillNode.DotSpec dot = node.dot();
            double tick = Dots.perTick(base * node.powerMultiplier(), dot.coefficient(), dot.seconds(), Dots.bonus(player, dot.type()));
            double seconds = dot.seconds() + power.effectSeconds();
            Component dotName = GuideLang.text("dot_type." + dot.type().key());
            page.figure(GuideLang.figure("dot_per_second", dotName), tick);
            page.figure(GuideLang.figure("dot_full"), tick * Dots.ticksIn((int) Math.round(seconds * 20)));
            page.notes.add(GuideLang.text("dot", dotName, SkillGuide.number(seconds)));
        }
        shields(player, def, rank, page);
        if (def.reflect() != null) {
            var reflect = def.reflect();
            Component reflected = GuideLang.text("scaling.reflect");
            page.scaling(page.fixed ? reflected : GuideLang.text("join.semicolon", page.scaling, reflected), !page.fixed && page.attackScaled);
            page.notes.add(GuideLang.text(reflect.projectiles() ? "reflect_projectiles" : "reflect",
                    SkillGuide.number(reflect.share() * 100), SkillGuide.number(reflect.seconds())));
        }
        if (def.stored() != null) {
            var stored = def.stored();
            page.notes.add(GuideLang.text("stored_" + (stored.release() == Stored.Release.RECAST ? "recast" : "auto")
                    + (stored.onExpire() == Stored.Expiry.FIZZLE ? "_lost" : "_releases"),
                    SkillGuide.number(stored.windowTicks() / 20.0), SkillGuide.number(stored.armTicks() / 20.0)));
            if (stored.gains()) page.notes.add(GuideLang.text("stored_gains", SkillGuide.number(stored.perHit()),
                    String.valueOf(stored.cap()), SkillGuide.number(stored.perUnit() * 100)));
            if (stored.decayEveryTicks() > 0) page.notes.add(GuideLang.text("stored_decay",
                    SkillGuide.number(stored.decayAfterTicks() / 20.0), SkillGuide.number(stored.decayEveryTicks() / 20.0)));
            // The release path deliberately uses rank riders, without the opening cast's summon/DoT passives.
            SkillCasts.Riders releaseRiders = SkillCasts.Riders.of(node, rank);
            var releasePower = new SkillGuide.Power(base, node.powerMultiplier(), releaseRiders.dotSeconds() + releaseRiders.buffSeconds(),
                    releaseRiders.dotSeconds() + releaseRiders.summonSeconds(), releaseRiders.summonSeconds(), releaseRiders.ownerScaling(),
                    power.healingDone(), power.selfHealing());
            addStage(player, node, page, releasePower, "stored/", Stage.RELEASE, "", stored);
            addStage(player, node, page, new SkillGuide.Power(base, node.powerMultiplier(), 0, 0, 0, 0,
                    power.healingDone(), power.selfHealing()), "stored/", Stage.PULSE, Stored.PULSE_SUFFIX, null);
            if (stored.pulseTicks() > 0) page.notes.add(GuideLang.text(stored.pull() > 0 ? "stored_pulse_pull" : "stored_pulse",
                    SkillGuide.number(stored.pulseTicks() / 20.0), SkillGuide.number(stored.radius())));
        }
        if (spell.toString().contains("riftfall:counter_window")) {
            addStage(player, node, page, new SkillGuide.Power(base, node.powerMultiplier(), 0, 0, 0, 0),
                    "counter/", Stage.COUNTER, "", null);
        }
        if (node.powerAttribute().toString().equals("minecraft:generic.attack_damage") && page.attackScaled) {
            if (def.skillClass().equals("gunner")) {
                page.scaling(GuideLang.text("join.semicolon", page.scaling, GuideLang.text("scaling.gun")), true);
                page.notes.add(GuideLang.text("gun_note"));
            }
            if (def.skillClass().equals("ranger")) {
                page.scaling(GuideLang.text("join.semicolon", page.scaling, GuideLang.text("scaling.bow")), true);
                page.notes.add(GuideLang.text("bow_note"));
            }
        }
        page.notes.add(0, SkillWeapons.requirement(def));
        page.notes.add(GuideLang.text("values_note"));
        return page;
    }

    /** A Shield or per-enemy node's pool at this rank: the figure, the notes and the description's tokens. */
    static void shields(ServerPlayer player, SkillDef def, int rank, SkillGuide.Page page) {
        SkillNode node = def.cast();
        if (!(node.lever() instanceof SkillNode.Shield) && node.perEnemy() == null) return;
        var pair = com.cyberday1.riftfall.stat.ClassAlignment.pair(def.skillClass());
        Component shieldScaling = pair == null ? GuideLang.text("scaling.shield_class")
                : GuideLang.text("scaling.shield_stats", SkillScaling.primaryName(pair.main()), SkillScaling.primaryName(pair.secondary()));
        page.notes.add(GuideLang.text("shield_note", shieldScaling));
        // The shield's pool has its own formula; other impacts may still use school power.
        page.scaling(page.fixed ? GuideLang.text("scaling.shield", shieldScaling)
                : GuideLang.text("scaling.shield_other", shieldScaling, page.scaling), !page.fixed && page.attackScaled);
        if (node.lever() instanceof SkillNode.Shield s) {
            double shield = SkillCasts.shieldPoints(player.serverLevel(), player, node, rank, def.skillClass());
            page.figure(GuideLang.figure("shield_points"), shield);
            page.tokens.put("shield", SkillGuide.number(shield));
            page.notes.add(GuideLang.text("shield_ally", SkillGuide.number(s.allyRadius())));
        }
        if (node.perEnemy() != null) {
            var terms = node.perEnemy();
            double each = SkillCasts.perEnemyShieldPoints(player.serverLevel(), player, node, rank, def.skillClass(), 1);
            page.figure(GuideLang.figure("shield_per_enemy"), each);
            page.tokens.put("shield_per_enemy", SkillGuide.number(each));
            page.notes.add(GuideLang.text("shield_enemies", Integer.toString(terms.cap()), SkillGuide.number(terms.allyRadius())));
        }
    }

    /** A deferred stage: its lang name and the token prefix the descriptions read. */
    private enum Stage {
        RELEASE, PULSE, COUNTER;
        final String token = name().toLowerCase(java.util.Locale.ROOT);
        Component label() { return GuideLang.text("stage." + token); }
    }

    private static void addStage(ServerPlayer player, SkillNode node, SkillGuide.Page page, SkillGuide.Power power,
                                 String directory, Stage label, String suffix, Stored.Spec store) throws IOException {
        JsonObject stage = read(player, ResourceLocation.fromNamespaceAndPath(node.ability().getNamespace(), directory + node.id() + suffix));
        if (stage == null) return;
        SkillGuide.Page detail = SkillGuide.describe(stage, power);
        Component name = label.label();
        detail.figures.forEach((key, value) -> page.figures.put(GuideLang.stageFigure(key, name), value));
        detail.notes.forEach(note -> page.notes.add(GuideLang.text("labelled", name, note)));
        page.notes.add(GuideLang.text("scaling.stage", name, detail.scaling));
        if (page.fixed) page.scaling(GuideLang.text("labelled", name, detail.scaling), detail.attackScaled);
        page.tokens.put(label.token + "_damage", detail.tokens.get("damage"));
        page.tokens.put(label.token + "_damage_type", detail.tokens.get("damage_type"));
        if (store != null && store.gains()) {
            var capPower = new SkillGuide.Power(power.base(), power.multiplier() * (1 + store.perUnit() * store.cap()),
                    power.effectSeconds(), power.spawnSeconds(), power.summonSeconds(), power.ownerScaling(), power.healingDone(), power.selfHealing());
            for (var entry : SkillGuide.describe(stage, capPower).figures.entrySet()) {
                if (GuideLang.isFigure(entry.getKey(), "damage_")) page.figures.put(GuideLang.figure("release_at_cap"), entry.getValue());
            }
        }
    }

    private static JsonObject read(ServerPlayer player, ResourceLocation ability) throws IOException {
        var path = ResourceLocation.fromNamespaceAndPath(ability.getNamespace(), "spell/" + ability.getPath() + ".json");
        var resource = player.server.getResourceManager().getResource(path);
        if (resource.isEmpty()) return null;
        try (var reader = resource.get().openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
}
