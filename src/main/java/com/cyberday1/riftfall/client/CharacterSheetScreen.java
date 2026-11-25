package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.CombatStats;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.network.RiftSheetPayload;
import com.cyberday1.riftfall.network.SkillRequestPayload;
import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.network.StatAllocatePayload;
import com.cyberday1.riftfall.progression.RiftTier;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.skill.ManaRules;
import com.cyberday1.riftfall.skill.PassiveModifiers;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import com.cyberday1.riftfall.stat.ClassAlignment;
import com.cyberday1.riftfall.stat.StatBlock;
import com.cyberday1.riftfall.stat.StatModifiers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The character page (B1): portrait, class and its skill-type mix, tier, level and XP on the left;
 * the seven primaries with what they buy, inline, on the right. Attribute points are staged with each
 * primary's + and sent together by Confirm; the stat respec link (E-10) takes them back, for XP.
 * From level 60 an Ascend button opens the choice of ascendancy and its confirm; with no class, Choose a
 * class takes its slot and opens the Classes spread. Rift Log at the foot opens the past runs.
 * Numbers come from {@link CharacterSheet} and the synced attributes.
 */
public class CharacterSheetScreen extends ProgressionScreen {

    private static final int L = ProgressionFrame.LEFT;
    private static final int R = ProgressionFrame.RIGHT;
    private static final int T = ProgressionFrame.TOP;
    private static final int W = ProgressionFrame.PAGE_WIDTH;
    private static final int PORTRAIT_W = 62;
    private static final int PORTRAIT_BOTTOM = T + 83;
    private static final int PORTRAIT_BACK = 0xFF2A2430;
    private static final int PORTRAIT_FLOOR = 0xFF211C27;
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation SHIELD = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");
    private static final int MANA_DROP = 0xFF3D6FD1;
    private static final int BUTTONS = T + 196;
    /** The Rift Log link, under the defence rows; the confirms' buttons take this row, so it shows only without one. */
    private static final int FOOTER = ProgressionFrame.BOTTOM - 8;

    /** The stat respec link was pressed and the page asks before it sends. */
    private boolean confirmingRespec;

    /** Ascend was pressed and the page lists the ascendancies. */
    private boolean choosingAscension;

    /** The ascendancy picked from that list, awaiting its confirm; null before a pick. */
    private String ascendingAs;

    /** Attribute points staged with +, not yet sent. */
    private final CharacterSheet.Staging staging = new CharacterSheet.Staging();

    /** The view whose refusal has been read; one cached from before the page opened counts as read. */
    private SkillScreenPayload answered = RiftSkills.latest();

    public CharacterSheetScreen() {
        super(ProgressionTab.CHARACTER);
    }

    @Override
    protected void init() {
        super.init();
        answered = RiftSkills.latest();
        PacketDistributor.sendToServer(SkillRequestPayload.view());
    }

    private MutableComponent line(String key, Object... args) {
        return Component.translatable(tab.key(key), args);
    }

    @Override
    protected void renderPages(GuiGraphics graphics, float partialTick) {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        SkillScreenPayload view = RiftSkills.latest();
        RiftSheetPayload sheet = RiftSheet.latest();
        RiftXp.Progress progress = RiftXp.progress(sheet.xp(), sheet.cap());
        if (staging.total() > Math.max(0, view.attributePoints()) || !view.hasClass() || view.inRift()) {
            staging.clear();
        }
        List<String> ascendancies = CharacterSheet.ascendChoices(view);
        if (ascendingAs != null && !ascendancies.contains(ascendingAs)) {
            ascendingAs = null;
        }
        choosingAscension &= !ascendancies.isEmpty();
        identity(graphics, player, view, progress);
        String refusal = view == answered ? null : CharacterSheet.refusal(view.action(), view.result());
        if (choosingAscension) {
            confirmingRespec = false;
            answered = view;
            if (ascendingAs == null) {
                ascendChoose(graphics, ascendancies);
            } else {
                ascendConfirm(graphics);
            }
        } else if (confirmingRespec && view.statRespec().ok()) {
            answered = view;
            respecConfirm(graphics, view.statRespec());
        } else if (staging.total() > 0) {
            confirmingRespec = false;
            answered = view;
            stageConfirm(graphics);
        } else if (refusal != null) {
            confirmingRespec = false;
            refused(graphics, view, refusal);
        } else {
            confirmingRespec = false;
            experience(graphics, progress);
            vitals(graphics, player);
            defence(graphics, player, view);
            riftLogLink(graphics);
        }
        attributes(graphics, player, view);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && choosingAscension) {
            if (ascendingAs == null) {
                choosingAscension = false;
            }
            ascendingAs = null;
            return true;
        }
        if (key == 256 && confirmingRespec) {
            confirmingRespec = false;
            return true;
        }
        if (key == 256 && staging.total() > 0) {
            staging.clear();
            return true;
        }
        SkillScreenPayload view = RiftSkills.latest();
        if (key == 256 && view != answered && CharacterSheet.refusal(view.action(), view.result()) != null) {
            answered = view;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** The class's ascendancies as links, in place of the lower left page; picking one asks before it sends. */
    private void ascendChoose(GuiGraphics graphics, List<String> ascendancies) {
        text(graphics, bold(line("ascend_title")), L, T + 108, CodexStyle.ACCENT);
        wrapped(graphics, line("ascend_choose"), L, T + 119, W, 9, 2, CodexStyle.MUTED);
        for (int i = 0; i < ascendancies.size(); i++) {
            String name = ascendancies.get(i);
            Component label = fit(Component.literal(CodexNames.ascendancy(name)), W);
            int y = T + 140 + i * 11;
            int end = L + font.width(label);
            boolean hover = hot(L, y - 1, end, y + 9, button -> {
                ascendingAs = name;
                return true;
            });
            text(graphics, label, L, y, hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
            if (hover) {
                graphics.fill(L, y + 9, end, y + 10, CodexStyle.ACCENT);
            }
        }
        button(graphics, L, BUTTONS, W, line("respec_back"), true, () -> choosingAscension = false);
    }

    /** The permanent choice, named, with Ascend and Back. */
    private void ascendConfirm(GuiGraphics graphics) {
        String name = ascendingAs;
        text(graphics, bold(line("ascend_title")), L, T + 108, CodexStyle.ACCENT);
        wrapped(graphics, line("ascend_confirm", CodexNames.ascendancy(name)), L, T + 121, W, 10, 4, CodexStyle.WARN);
        int half = (W - 4) / 2;
        button(graphics, L, BUTTONS, half, line("ascend_yes"), true, () -> {
            choosingAscension = false;
            ascendingAs = null;
            PacketDistributor.sendToServer(SkillRequestPayload.ascend(name));
        });
        button(graphics, L + half + 4, BUTTONS, W - half - 4, line("respec_back"), true, () -> ascendingAs = null);
    }

    /** The stat respec's confirm, with its exact price, in place of the lower left page. */
    private void respecConfirm(GuiGraphics graphics, SkillScreenPayload.Respec respec) {
        text(graphics, bold(line("respec_title")), L, T + 108, CodexStyle.ACCENT);
        wrapped(graphics, line("respec_confirm", respec.refund(), CharacterSheet.grouped(respec.cost())), L, T + 121,
                W, 10, 4, CodexStyle.WARN);
        int half = (W - 4) / 2;
        button(graphics, L, BUTTONS, half, line("respec_yes"), true, () -> {
            confirmingRespec = false;
            PacketDistributor.sendToServer(SkillRequestPayload.respecStats());
        });
        button(graphics, L + half + 4, BUTTONS, W - half - 4, line("respec_back"), true, () -> confirmingRespec = false);
    }

    /** The server's answer to a refused Confirm, Reset or Ascend, in place of the lower left page until it is read. */
    private void refused(GuiGraphics graphics, SkillScreenPayload view, String key) {
        text(graphics, bold(line(CharacterSheet.refusalTitle(view.action()))), L, T + 108, CodexStyle.ACCENT);
        wrapped(graphics, line(key), L, T + 121, W, 10, 4, CodexStyle.WARN);
        button(graphics, L, BUTTONS, W, line("respec_back"), true, () -> answered = view);
    }

    /** Confirm and Cancel for the staged points, in place of the lower left page as the respec confirm is. */
    private void stageConfirm(GuiGraphics graphics) {
        text(graphics, bold(line("stage_title")), L, T + 108, CodexStyle.ACCENT);
        wrapped(graphics, line(CharacterSheet.counted("stage_confirm", staging.total()), staging.total()), L, T + 121, W,
                10, 4, CodexStyle.WARN);
        int half = (W - 4) / 2;
        button(graphics, L, BUTTONS, half, line("stage_yes"), true, () -> {
            PacketDistributor.sendToServer(StatAllocatePayload.of(staging.asMap()));
            staging.clear();
        });
        button(graphics, L + half + 4, BUTTONS, W - half - 4, line("stage_back"), true, staging::clear);
    }

    /** "Rift Log ›": the past runs, newest first, which otherwise only {@code /riftfall recap} opens. */
    private void riftLogLink(GuiGraphics graphics) {
        Component link = fit(line("rift_log_link"), W);
        int end = L + font.width(link);
        boolean hover = hot(L, FOOTER - 1, end, FOOTER + 9, button -> {
            RiftRecapClient.ask();
            return true;
        });
        text(graphics, link, L, FOOTER, hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
        if (hover) {
            graphics.fill(L, FOOTER + 9, end, FOOTER + 10, CodexStyle.ACCENT);
            tooltip(List.of(line("rift_log_hint")));
        }
    }

    /** "Reset stats", right of the skills link: live when the server's quote says yes, its reason on hover. */
    private void respecLink(GuiGraphics graphics, SkillScreenPayload.Respec respec, int y, int minLeft) {
        int right = L + W;
        Component link = fit(line("respec_link"), right - minLeft);
        int left = right - font.width(link);
        boolean ok = respec.ok();
        boolean hover = ok && hot(left, y - 1, right, y + 9, button -> {
            confirmingRespec = true;
            return true;
        });
        text(graphics, link, left, y, !ok ? CodexStyle.MUTED : hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
        if (over(left, y - 1, right, y + 9)) {
            tooltip(List.of(switch (respec.status()) {
                case "OK" -> line("respec_costs", CharacterSheet.grouped(respec.cost()), respec.refund());
                case "IN_RIFT" -> line("respec_in_rift");
                case "NOT_ENOUGH_XP" -> line("respec_short", CharacterSheet.grouped(respec.cost()),
                        CharacterSheet.grouped(respec.shortBy()));
                default -> line("respec_nothing");
            }));
        }
    }

    @Override
    protected void renderScreenSpace(GuiGraphics graphics, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        float scale = bookScale();
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, screenX(L + 1), screenY(T + 14),
                screenX(L + PORTRAIT_W - 1), screenY(PORTRAIT_BOTTOM - 1), Math.max(8, Math.round(28 * scale)),
                0.0625F, mouseX, mouseY, minecraft.player);
    }

    private void identity(GuiGraphics graphics, LocalPlayer player, SkillScreenPayload view, RiftXp.Progress progress) {
        int tier = RiftTier.of(progress.level());
        Component tierLine = bold(line("tier", tier));
        text(graphics, fit(bold(player == null ? Component.empty() : player.getName()), W - font.width(tierLine) - 4), L, T,
                CodexStyle.INK);
        textRight(graphics, tierLine, L + W, T, CodexStyle.ACCENT);

        StellarWorkshop.slab(graphics, L, T + 13, PORTRAIT_W, PORTRAIT_BOTTOM - T - 13, PORTRAIT_BACK);
        graphics.fill(L + 1, PORTRAIT_BOTTOM - 9, L + PORTRAIT_W - 1, PORTRAIT_BOTTOM - 1, PORTRAIT_FLOOR);
        graphics.fill(L + 8, PORTRAIT_BOTTOM - 1, L + PORTRAIT_W - 8, PORTRAIT_BOTTOM, 0xFF87734F);

        int x = L + 70;
        int right = L + W;
        if (!view.hasClass()) {
            // The class rows' places: the hint where main and second go, Choose a class in Ascend's slot.
            text(graphics, fit(bold(line("no_class")), right - x), x, T + 14, CodexStyle.ACCENT);
            text(graphics, line("level", progress.level()), x, T + 24, CodexStyle.INK);
            wrapped(graphics, line("class_hint"), x, T + 33, right - x, 9, 2, CodexStyle.MUTED);
            tagButton(graphics, line("choose_class"), x, T + 51, right - x, () -> switchTo(ProgressionTab.CLASSES));
            return;
        }
        text(graphics, fit(bold(Component.literal(CodexNames.skillClass(view.skillClass()))), right - x), x, T + 14,
                CodexStyle.ACCENT);
        text(graphics, line("level", progress.level()), x, T + 24, CodexStyle.INK);
        ClassAlignment.Pair pair = ClassAlignment.pair(view.skillClass());
        if (pair != null) {
            int mainEnd = text(graphics, line("main"), x, T + 33, CodexStyle.MUTED);
            textRight(graphics, fit(primaryName(pair.main()), right - mainEnd - 4), right, T + 33, CodexStyle.INK);
            int secondEnd = text(graphics, line("second"), x, T + 42, CodexStyle.MUTED);
            textRight(graphics, fit(primaryName(pair.secondary()), right - secondEnd - 4), right, T + 42, CodexStyle.INK);
        }
        Component ascension = !view.ascension().isEmpty() ? ascended(view.ascension(), right - x)
                : view.level() < SkillBook.ASCENSION_LEVEL ? line("can_ascend", SkillBook.ASCENSION_LEVEL)
                : line("ascend_ready");
        if (CharacterSheet.ascendChoices(view).isEmpty()) {
            text(graphics, fit(ascension, right - x), x, T + 51, CodexStyle.MUTED);
        } else {
            ascendButton(graphics, x, T + 51);
        }
        text(graphics, fit(line(CharacterSheet.counted("points", view.available()), view.available()), right - x), x, T + 60,
                CodexStyle.MUTED);
        int left = Math.max(0, view.attributePoints() - staging.total());
        text(graphics, fit(line(CharacterSheet.counted("attribute_points", left), left), right - x), x, T + 69,
                left > 0 ? CodexStyle.ACCENT : CodexStyle.MUTED);
        Component link = line("skills_link");
        int linkEnd = x + font.width(link);
        boolean hover = hot(x, T + 77, linkEnd, T + 87, button -> {
            switchTo(ProgressionTab.SKILLS);
            return true;
        });
        text(graphics, link, x, T + 78, hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
        if (hover) {
            graphics.fill(x, T + 87, linkEnd, T + 88, CodexStyle.ACCENT);
        }
        respecLink(graphics, view.statRespec(), T + 78, linkEnd + 6);

        wrapped(graphics, mixLine(CharacterSheet.mix(view)), L, T + 90, W, 9, 2, CodexStyle.MUTED);
    }

    /** "Ascended: Storm Savant", or the name alone when the line would not fit. */
    private Component ascended(String ascendancy, int room) {
        String name = CodexNames.ascendancy(ascendancy);
        Component full = line("ascended", name);
        return font.width(full) <= room ? full : Component.literal(name);
    }

    /** Ascend, in place of the "Ready to ascend" line while the player can: a filled tag like a primary's +. */
    private void ascendButton(GuiGraphics graphics, int x, int y) {
        tagButton(graphics, line("ascend_button"), x, y, L + W - x, () -> {
            confirmingRespec = false;
            staging.clear();
            ascendingAs = null;
            choosingAscension = true;
        });
    }

    /** "Damage 4 · Control 4 · Heal 1 · Movement 1 · 5 passives", counted from the base rows. */
    private Component mixLine(CharacterSheet.Mix mix) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<LeverType, Integer> lever : mix.levers().entrySet()) {
            if (!first) {
                out.append(line("mix_separator"));
            }
            out.append(line("mix_lever", Component.translatable(ProgressionTab.SKILLS.key("lever_" + lever.getKey().key())),
                    lever.getValue()));
            first = false;
        }
        if (mix.passives() > 0) {
            if (!first) {
                out.append(line("mix_separator"));
            }
            out.append(line(CharacterSheet.counted("mix_passives", mix.passives()), mix.passives()));
            first = false;
        }
        return first ? line("mix_none") : out;
    }

    private void experience(GuiGraphics graphics, RiftXp.Progress progress) {
        int y = T + 108;
        text(graphics, line("experience"), L, y, CodexStyle.ACCENT);
        int tier = RiftTier.of(progress.level());
        if (!progress.atCap()) {
            textRight(graphics, line("next_tier", tier + 1, RiftTier.first(tier + 1)), L + W, y, CodexStyle.MUTED);
        }
        int barY = y + 11;
        graphics.fill(L, barY, L + W, barY + 5, CodexStyle.TROUGH);
        int fill = CharacterSheet.barFill(progress.fraction(), W);
        if (fill > 0) {
            graphics.fill(L, barY, L + fill, barY + 5, CodexStyle.ACCENT);
        }
        frame(graphics, L - 1, barY - 1, L + W + 1, barY + 6, CodexStyle.WELL_DARK);
        if (progress.atCap()) {
            text(graphics, line("capped", CharacterSheet.grouped(progress.total())), L, barY + 9, CodexStyle.INK);
        } else {
            Component into = line("into", CharacterSheet.grouped(progress.into()), CharacterSheet.grouped(progress.span()));
            Component toGo = line("to_go", CharacterSheet.grouped(progress.span() - progress.into()));
            text(graphics, fit(into, W), L, barY + 9, CodexStyle.INK);
            // At high levels both numbers run long; "to go" is the difference, so it gives way.
            if (font.width(into) + 6 + font.width(toGo) <= W) {
                textRight(graphics, toGo, L + W, barY + 9, CodexStyle.MUTED);
            }
        }
    }

    private void vitals(GuiGraphics graphics, LocalPlayer player) {
        int y = T + 140;
        double health = player == null ? 0.0 : player.getMaxHealth();
        double pool = read(player, RiftfallAttributes.MAX_MANA);
        double now = RiftManaHud.current();
        double shield = player == null || minecraft.level == null ? 0.0 : RiftShieldHud.standing(minecraft.level.getGameTime());
        for (int i = 0; i < 3; i++) {
            int x = L + i * 58;
            well(graphics, x, y, x + 52, y + 28);
            Component value;
            Component label;
            switch (i) {
                case 0 -> {
                    graphics.blitSprite(HEART, x + 4, y + 4, 9, 9);
                    value = Component.literal(CharacterSheet.amount(health));
                    label = line("health");
                }
                case 1 -> {
                    int[] widths = {1, 3, 5, 7, 7, 5, 3, 1};
                    for (int k = 0; k < widths.length; k++) {
                        int left = x + 8 - widths[k] / 2;
                        graphics.fill(left, y + 4 + k, left + widths[k], y + 5 + k, MANA_DROP);
                    }
                    value = Component.literal(CharacterSheet.amount(Double.isNaN(now) ? pool : now));
                    label = line("mana_of", CharacterSheet.amount(pool));
                    if (over(x, y, x + 52, y + 28)) {
                        tooltip(manaTooltip(player, Double.isNaN(now) ? pool : now, pool));
                    }
                }
                default -> {
                    graphics.blitSprite(SHIELD, x + 4, y + 4, 9, 9);
                    value = Component.literal(String.valueOf((int) Math.ceil(shield)));
                    label = line("shield");
                }
            }
            textRight(graphics, bold(value), x + 48, y + 5, CodexStyle.INK);
            text(graphics, fit(label, 46), x + 4, y + 17, CodexStyle.MUTED);
        }
    }

    /** Where the pool comes from: base, passives, each primary, and the rest as gear. Synced modifiers keep their ids. */
    private List<Component> manaTooltip(LocalPlayer player, double now, double pool) {
        List<Component> lines = new ArrayList<>();
        lines.add(line("mana_now", CharacterSheet.amount(now), CharacterSheet.amount(pool)));
        AttributeInstance instance = player == null ? null : player.getAttribute(RiftfallAttributes.MAX_MANA);
        if (instance != null) {
            lines.add(line("mana_base", CharacterSheet.amount(instance.getBaseValue())).withStyle(ChatFormatting.GRAY));
            double passives = 0.0;
            double gear = 0.0;
            Map<StatBlock.Primary, Double> primaries = new LinkedHashMap<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
                    continue;
                }
                ResourceLocation id = modifier.id();
                StatBlock.Primary primary = primaryOf(id);
                if (id.getNamespace().equals(RiftfallCore.MODID) && id.getPath().startsWith(PassiveModifiers.PREFIX)) {
                    passives += modifier.amount();
                } else if (primary != null) {
                    primaries.merge(primary, modifier.amount(), Double::sum);
                } else {
                    gear += modifier.amount();
                }
            }
            if (passives != 0.0) {
                lines.add(line("mana_passives", SkillFigures.plus(passives)).withStyle(ChatFormatting.BLUE));
            }
            primaries.forEach((primary, amount) -> lines.add(line("mana_from", SkillFigures.plus(amount),
                    primaryName(primary)).withStyle(ChatFormatting.BLUE)));
            if (gear != 0.0) {
                lines.add(line("mana_gear", SkillFigures.plus(gear)).withStyle(ChatFormatting.BLUE));
            }
        }
        double regen = read(player, RiftfallAttributes.MANA_REGEN);
        lines.add(line("mana_regen", CharacterSheet.amount(regen * ManaRules.IN_COMBAT_REGEN),
                CharacterSheet.amount(regen * ManaRules.OUT_OF_COMBAT_REGEN)).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static StatBlock.Primary primaryOf(ResourceLocation id) {
        if (!id.getNamespace().equals(RiftfallCore.MODID) || !id.getPath().startsWith(StatModifiers.PREFIX)) {
            return null;
        }
        String rest = id.getPath().substring(StatModifiers.PREFIX.length());
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            if (rest.startsWith(primary.key() + "/")) {
                return primary;
            }
        }
        return null;
    }

    /** Armor and toughness in points; Resist and Guard as the share of a hit turned away, as CombatStats reckons it. */
    private void defence(GuiGraphics graphics, LocalPlayer player, SkillScreenPayload view) {
        int y = T + 174;
        text(graphics, line("defence"), L, y, CodexStyle.ACCENT);
        double resist = CombatStats.resistance(read(player, RiftfallAttributes.RESISTANCE),
                read(player, RiftfallAttributes.DAMAGE_RESISTANCE), view.riftTier());
        double guard = CombatStats.guardFraction(read(player, RiftfallAttributes.GUARD));
        String[] keys = {"armor", "toughness", "resist", "guard"};
        Component[] values = {Component.literal(CharacterSheet.amount(read(player, Attributes.ARMOR))),
                Component.literal(CharacterSheet.amount(read(player, Attributes.ARMOR_TOUGHNESS))),
                line("percent", CharacterSheet.percent(resist)), line("percent", CharacterSheet.percent(guard))};
        Component[] hints = {null, null,
                view.inRift() ? line("resist_hint_rift", view.riftTier()) : line("resist_hint"), line("guard_hint")};
        for (int i = 0; i < keys.length; i++) {
            int x = L + (i % 2) * 90;
            int row = y + 11 + (i / 2) * 11;
            text(graphics, fit(line(keys[i]), 78 - font.width(values[i]) - 4), x, row, CodexStyle.MUTED);
            textRight(graphics, values[i], x + 78, row, CodexStyle.INK);
            if (hints[i] != null && over(x, row - 1, x + 78, row + 9)) {
                tooltip(List.of(hints[i]));
            }
        }
    }

    private void attributes(GuiGraphics graphics, LocalPlayer player, SkillScreenPayload view) {
        text(graphics, bold(line("attributes")), R, T, CodexStyle.ACCENT);
        boolean block = ClassAlignment.blockFor(view.hasClass() ? view.skillClass() : null) != null;
        textRight(graphics, block ? line("class_block", ClassAlignment.POINTS) : line("no_block"), R + W, T,
                CodexStyle.MUTED);
        ClassAlignment.Pair pair = view.hasClass() ? ClassAlignment.pair(view.skillClass()) : null;
        int left = view.attributePoints() - staging.total();
        boolean placing = view.hasClass() && !view.inRift() && (left > 0 || staging.total() > 0);
        StatBlock.Primary[] primaries = StatBlock.Primary.values();
        for (int i = 0; i < primaries.length; i++) {
            StatBlock.Primary primary = primaries[i];
            int y = T + 14 + i * 29;
            int own = view.allocated(i);
            int staged = staging.get(primary);
            double held = read(player, RiftfallAttributes.primary(primary));
            double points = held + staged;
            boolean key = pair != null && (pair.main() == primary || pair.secondary() == primary);
            graphics.fill(R, y, R + 22, y + 10, CodexStyle.primary(primary));
            textCenter(graphics, line("tag_" + primary.key()), R + 11, y + 1, CodexStyle.ON_TAG);
            Component name = primaryName(primary);
            text(graphics, key ? bold(name) : name, R + 27, y + 1, key || points > 2 ? CodexStyle.INK : CodexStyle.MUTED);
            int valueRight = placing ? R + W - 12 : R + W;
            Component value = bold(pointsLabel(held, own, staged));
            if (font.width(key ? bold(name) : name) + 4 + font.width(value) > valueRight - (R + 27)) {
                // "45 + 180 (+10)" beside a long name: the total alone, staged points still shown.
                value = bold(staged > 0 ? Component.literal(CharacterSheet.amount(held)).append(line("alloc_staged", staged))
                        : Component.literal(CharacterSheet.amount(held)));
            }
            textRight(graphics, value, valueRight, y + 1, CodexStyle.INK);
            if (placing && left > 0) {
                plus(graphics, primary, points, R + W - 9, y, view.attributePoints());
            }
            List<Component> effects = new ArrayList<>();
            for (CharacterSheet.Effect effect : CharacterSheet.effects(primary, points)) {
                effects.add(line(CharacterSheet.effectLine(effect), CharacterSheet.effectArgs(effect,
                        Component.translatable(tab.key("effect_" + effect.label())))));
            }
            List<Component> packed = pack(effects, W - 27, 2);
            for (int j = 0; j < packed.size(); j++) {
                text(graphics, packed.get(j), R + 27, y + 11 + j * 9, CodexStyle.MUTED);
            }
            if (over(R, y, R + W, y + 28) && !(placing && left > 0 && over(R + W - 9, y, R + W, y + 9))) {
                List<Component> tip = new ArrayList<>();
                tip.add(primaryName(primary));
                effects.forEach(e -> tip.add(e.copy().withStyle(ChatFormatting.BLUE)));
                tooltip(tip);
            }
            if (i < primaries.length - 1) {
                dotted(graphics, R, R + W, y + 28, CodexStyle.DOTS);
            }
        }
    }

    /** "15", or "15 + 30" once the player has placed points, with " (+5)" for what is staged. */
    private Component pointsLabel(double held, int own, int staged) {
        if (own == 0 && staged == 0) {
            return Component.literal(CharacterSheet.amount(held));
        }
        MutableComponent label = line("alloc_value", CharacterSheet.amount(held - own), own);
        return staged > 0 ? label.append(line("alloc_staged", staged)) : label;
    }

    /** A primary's +: stages one point, ten with shift, within what is left; hover shows what the next one buys. */
    private void plus(GuiGraphics graphics, StatBlock.Primary primary, double points, int x, int y, int pool) {
        boolean hover = hot(x, y, x + 9, y + 9, button -> {
            if (button != PRIMARY) {
                return false;
            }
            staging.add(primary, Screen.hasShiftDown() ? 10 : 1, pool);
            return true;
        });
        graphics.fill(x, y, x + 9, y + 9, hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
        textCenter(graphics, Component.literal("+"), x + 5, y + 1, CodexStyle.ON_TAG);
        if (hover) {
            List<Component> tip = new ArrayList<>();
            tip.add(line("next_point"));
            for (CharacterSheet.Effect effect : CharacterSheet.nextPoint(primary, points)) {
                tip.add(line(CharacterSheet.effectLine(effect), CharacterSheet.effectArgs(effect,
                        Component.translatable(tab.key("effect_" + effect.label())))).withStyle(ChatFormatting.BLUE));
            }
            tip.add(line("plus_hint").withStyle(ChatFormatting.GRAY));
            tooltip(tip);
        }
    }

    /** Joins effects with ", " into at most {@code lines} lines of {@code width}; the hover shows them all. */
    private List<Component> pack(List<Component> parts, int width, int lines) {
        List<Component> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Component part : parts) {
            String piece = part.getString();
            String joined = current.isEmpty() ? piece : current + ", " + piece;
            if (font.width(joined) <= width || current.isEmpty()) {
                current = new StringBuilder(joined);
                continue;
            }
            out.add(Component.literal(current + ","));
            current = new StringBuilder(piece);
            if (out.size() == lines) {
                break;
            }
        }
        if (out.size() < lines && !current.isEmpty()) {
            out.add(Component.literal(current.toString()));
        } else if (!out.isEmpty()) {
            String last = out.get(out.size() - 1).getString();
            out.set(out.size() - 1, Component.literal(last.substring(0, last.length() - 1) + " .."));
        }
        List<Component> fitted = new ArrayList<>();
        out.forEach(c -> fitted.add(fit(c, width)));
        return fitted;
    }

    private static Component primaryName(StatBlock.Primary primary) {
        return Component.translatable(RiftfallAttributes.descriptionId(RiftfallCore.id(primary.key())));
    }

    private static double read(LocalPlayer player, Holder<Attribute> attribute) {
        return player == null || player.getAttribute(attribute) == null ? 0.0 : player.getAttributeValue(attribute);
    }
}
