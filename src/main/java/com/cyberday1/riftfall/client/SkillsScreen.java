package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillRequestPayload;
import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.skill.GuideLang;
import com.cyberday1.riftfall.skill.PassiveGuide;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The skills spread (B4 to B8). The right page is always the browser: Base, Ascendancy and Universal
 * tabs, Actives and Passives bands, type chips, and the All, Owned and Can learn footer. The left page
 * is the loadout, an open skill's page, or equip mode. Every request is the server's existing
 * {@code SPEND} or {@code EQUIP}; state and listing are {@link CodexBrowser}, numbers {@link SkillFigures}.
 */
public class SkillsScreen extends ProgressionScreen {

    private static final int L = ProgressionFrame.LEFT;
    private static final int R = ProgressionFrame.RIGHT;
    private static final int T = ProgressionFrame.TOP;
    private static final int W = ProgressionFrame.PAGE_WIDTH;
    private static final int LIST_TOP = T + 29;
    private static final int LIST_BOTTOM = T + 205;
    private static final int BUTTONS = T + 196;
    private static final int ROW = 20;
    private static final int SLOT_NUMBER = 0xFFEFE6FF;

    private final CodexBrowser browser = new CodexBrowser();
    private @Nullable SkillScreenPayload shown;
    private int rowCount;
    private String detailId = "";
    private int detailScroll;
    private int detailMaxScroll;
    /** The respec button was pressed and the page asks before it sends. */
    private boolean confirmingRespec;

    public SkillsScreen() {
        super(ProgressionTab.SKILLS);
    }

    @Override
    protected void init() {
        super.init();
        refresh();
        request(SkillRequestPayload.view());
    }

    private void refresh() {
        SkillScreenPayload latest = RiftSkills.latest();
        if (latest != shown) {
            shown = latest;
            browser.accept(latest);
            AbilityLookup.forget();
        }
    }

    private static void request(@Nullable SkillRequestPayload payload) {
        if (payload != null) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private MutableComponent line(String key, Object... args) {
        return Component.translatable(tab.key(key), args);
    }

    @Override
    protected void renderPages(GuiGraphics graphics, float partialTick) {
        refresh();
        SkillScreenPayload view = browser.view();
        if (!view.hasClass()) {
            text(graphics, fit(bold(line("class_none")), W), L, T, CodexStyle.ACCENT);
            int y = wrapped(graphics, line("class_hint"), L, T + 14, W, 10, 4, CodexStyle.MUTED);
            // The sheet's Choose a class: the same tag, to the same Classes spread.
            tagButton(graphics, line("choose_class"), L, y + 4, W, () -> switchTo(ProgressionTab.CLASSES));
        } else if (browser.pickingEntry() != null) {
            equipLeaf(graphics, browser.pickingEntry());
        } else if (browser.selectedEntry() != null) {
            SkillScreenPayload.Entry entry = browser.selectedEntry();
            if (entry.guide().resolved()) {
                guideLeaf(graphics, entry);
            } else if (entry.passive()) {
                passiveLeaf(graphics, entry);
            } else {
                detailLeaf(graphics, entry);
            }
        } else {
            loadoutLeaf(graphics);
        }
        browserPage(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (browser.selectedEntry() != null && browser.selectedEntry().guide().resolved()
                && mouseBookX >= L - 4 && mouseBookX < R - 4) {
            detailScroll = Math.clamp(detailScroll - (int) Math.round(scrollY * 24), 0, detailMaxScroll);
            return true;
        }
        if (mouseBookX >= R - 4) {
            browser.scroll(scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0, rowCount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && browser.picking() != null) {
            browser.cancelEquip();
            return true;
        }
        if (key == 256 && confirmingRespec) {
            confirmingRespec = false;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // -- left page: the loadout ----------------------------------------------------------------------

    private void loadoutLeaf(GuiGraphics graphics) {
        SkillScreenPayload view = browser.view();
        loadoutHeader(graphics);
        hotbar(graphics, T + 13);
        dotted(graphics, L, L + W, T + 84, CodexStyle.DOTS);
        text(graphics, line("this_book"), L, T + 91, CodexStyle.ACCENT);
        List<SkillScreenPayload.Entry> entries = view.entries();
        int[][] counts = new int[3][2];
        for (SkillScreenPayload.Entry entry : entries) {
            int row = entry.universal() ? 2 : !entry.base() ? -1 : entry.passive() ? 1 : 0;
            if (row >= 0) {
                counts[row][1]++;
                counts[row][0] += entry.rank() > 0 ? 1 : 0;
            }
        }
        String[] labels = {"base_actives", "base_passives", "universal_passives"};
        for (int i = 0; i < labels.length; i++) {
            Component of = line("of", counts[i][0], counts[i][1]);
            text(graphics, fit(line(labels[i]), W - font.width(of) - 4), L, T + 103 + i * 10, CodexStyle.MUTED);
            textRight(graphics, of, L + W, T + 103 + i * 10, CodexStyle.INK);
        }
        int labelEnd = text(graphics, line("ascendancy"), L, T + 133, CodexStyle.MUTED);
        Component ascendancy;
        if (!view.ascension().isEmpty()) {
            ascendancy = Component.literal(CodexNames.ascendancy(view.ascension()));
        } else if (view.level() < SkillBook.ASCENSION_LEVEL) {
            ascendancy = line("asc_to_go", SkillBook.ASCENSION_LEVEL, SkillBook.ASCENSION_LEVEL - view.level());
        } else {
            ascendancy = line("asc_ready", SkillBook.ASCENSION_LEVEL);
        }
        // Long ascendancy names ("Crimson Blade Reaver") would run into the label.
        textRight(graphics, fit(ascendancy, L + W - labelEnd - 6), L + W, T + 133, CodexStyle.INK);
        dotted(graphics, L, L + W, T + 147, CodexStyle.DOTS);
        respec(graphics, view);
    }

    /**
     * The skill respec (E-10): a button under the loadout, then a confirm with the exact price in place of
     * the hint. The server prices and refuses it; the button only greys when its last quote said no.
     */
    private void respec(GuiGraphics graphics, SkillScreenPayload view) {
        SkillScreenPayload.Respec respec = view.skillRespec();
        if (confirmingRespec && respec.ok()) {
            wrapped(graphics, line("respec_confirm", respec.refund(), CharacterSheet.grouped(respec.cost())), L,
                    T + 153, W, 10, 4, CodexStyle.WARN);
            int half = (W - 4) / 2;
            button(graphics, L, BUTTONS, half, line("respec_yes"), true, () -> {
                confirmingRespec = false;
                request(SkillRequestPayload.respecSkills());
            });
            // The sheet's stat respec confirm says Back, not "< Loadout".
            button(graphics, L + half + 4, BUTTONS, W - half - 4,
                    Component.translatable(ProgressionTab.CHARACTER.key("respec_back")), true, () -> confirmingRespec = false);
            return;
        }
        confirmingRespec = false;
        String notice = browser.notice();
        if (notice != null) {
            wrapped(graphics, line("result_" + notice.toLowerCase(Locale.ROOT)), L, T + 153, W, 10, 4, CodexStyle.WARN);
        } else if ("RESPEC_SKILLS".equals(view.action())) {
            wrapped(graphics, line("respec_done"), L, T + 153, W, 10, 4, CodexStyle.INK);
        } else {
            wrapped(graphics, line("loadout_hint"), L, T + 153, W, 10, 4, CodexStyle.MUTED);
        }
        button(graphics, L, BUTTONS, W, line("respec_button"), respec.ok(), () -> confirmingRespec = true);
        if (over(L, BUTTONS, L + W, BUTTONS + 18)) {
            tooltip(List.of(respecReason(respec)));
        }
    }

    private Component respecReason(SkillScreenPayload.Respec respec) {
        return switch (respec.status()) {
            case "OK" -> line("respec_costs", CharacterSheet.grouped(respec.cost()), respec.refund());
            case "IN_RIFT" -> line("respec_in_rift");
            case "NOT_ENOUGH_XP" -> line("respec_short", CharacterSheet.grouped(respec.cost()),
                    CharacterSheet.grouped(respec.shortBy()));
            default -> line("respec_nothing");
        };
    }

    private void loadoutHeader(GuiGraphics graphics) {
        int end = text(graphics, bold(line("loadout")), L, T, CodexStyle.ACCENT);
        textRight(graphics, fit(line(browser.view().inRift() ? "locked_in_rift" : "set_outside"), L + W - end - 6), L + W, T,
                browser.view().inRift() ? CodexStyle.WARN : CodexStyle.MUTED);
    }

    /** Eight slots in two rows of four, keys under them, and the framed ultimate slot. */
    private void hotbar(GuiGraphics graphics, int top) {
        SkillScreenPayload view = browser.view();
        boolean picking = browser.picking() != null;
        for (int slot = 0; slot < SkillBook.ACTIVE_SLOTS; slot++) {
            int x = L + (slot % 4) * 30;
            int y = top + (slot / 4) * 34;
            slotBox(graphics, slot, x, y, 24);
            if (picking && browser.slotTakes(slot)) {
                dotted(graphics, x - 1, x + 25, y - 2, CodexStyle.ACCENT);
                dotted(graphics, x - 1, x + 25, y + 25, CodexStyle.ACCENT);
            }
            // Modifier chords must fit the same 30px pitch as single-key bindings.
            Component binding = keyName(slot);
            float keyScale = Math.min(1.0F, 28.0F / Math.max(1, font.width(binding)));
            graphics.pose().pushPose();
            graphics.pose().translate(x + 12, y + 26, 0);
            graphics.pose().scale(keyScale, keyScale, 1);
            textCenter(graphics, binding, 0, 0, CodexStyle.MUTED);
            graphics.pose().popPose();
            if (over(x - 2, y + 25, x + 26, y + 34)) tooltip(List.of(binding));
        }
        int ux = L + 126;
        int uy = top + 5;
        boolean ultimateValid = !picking || browser.slotTakes(SkillBook.ULTIMATE_SLOT);
        StellarWorkshop.slab(graphics, ux - 2, uy - 2, 44, 44, CodexStyle.SELECTED);
        graphics.fill(ux + 5, uy + 41, ux + 35, uy + 42, ultimateValid ? CodexStyle.ACCENT : CodexStyle.BUTTON_OFF_OUTLINE);
        if (!view.ultimateOpen()) {
            slot(graphics, ux, uy, 40, CodexStyle.SLOT_SHUT);
            padlock(graphics, ux + 16, uy + 16);
            // Wider than the slot, so it takes both caption lines.
            List<FormattedCharSequence> caption = font.split(line("opens_ascend"), 62);
            for (int i = 0; i < Math.min(2, caption.size()); i++) {
                graphics.drawString(font, caption.get(i), ux + 20 - font.width(caption.get(i)) / 2, uy + 46 + i * 9,
                        CodexStyle.MUTED, false);
            }
            return;
        }
        slotBox(graphics, SkillBook.ULTIMATE_SLOT, ux, uy, 40);
        // As wide as the locked caption above, so a long word stays on the page.
        textCenter(graphics, fit(line("ultimate_u"), 62), ux + 20, uy + 46, CodexStyle.MUTED);
        SkillScreenPayload.Entry held = browser.entry(browser.slotHolds(SkillBook.ULTIMATE_SLOT));
        textCenter(graphics, fit(held == null ? line("empty") : line("rank_short", held.rank(), held.maxRank()), 62),
                ux + 20, uy + 55, CodexStyle.INK);
    }

    /** One loadout slot: its skill's glyph, the cooling overlay, and its click. */
    private void slotBox(GuiGraphics graphics, int slot, int x, int y, int size) {
        SkillScreenPayload.Entry held = browser.entry(browser.slotHolds(slot));
        boolean hover = hot(x, y, x + size, y + size, button -> {
            if (button != PRIMARY && button != SECONDARY) {
                return false;
            }
            request(browser.clickSlot(slot, button == SECONDARY));
            return true;
        });
        slot(graphics, x, y, size, CodexStyle.SLOT);
        if (held != null) {
            int glyph = size >= 32 ? 32 : 16;
            int offset = (size - glyph) / 2;
            skillIcon(graphics, held, x + offset, y + offset, glyph, false);
            cooling(graphics, slot, held, x, y, size);
            if (hover) {
                tooltip(List.of(CodexNames.skill(held)));
            }
        }
        String picking = browser.picking();
        if (picking != null && picking.equals(browser.slotHolds(slot))) {
            frame(graphics, x - 1, y - 1, x + size + 1, y + size + 1, CodexStyle.ACCENT);
            frame(graphics, x - 2, y - 2, x + size + 2, y + size + 2, CodexStyle.ACCENT);
        }
    }

    /** What is left of the slot's cooldown, as a band from the top like vanilla's item cooldown. */
    private void cooling(GuiGraphics graphics, int slot, SkillScreenPayload.Entry held, int x, int y, int size) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        long left = RiftSkillCooldowns.remaining(slot, minecraft.level.getGameTime());
        // The synced length has haste in it; the codex's own figure is the fallback.
        int synced = RiftSkillCooldowns.slot(slot).duration();
        int total = Math.max(1, synced > 0 ? synced : held.facts().cooldownNow());
        if (left > 0) {
            int band = (int) Math.min(size - 2, Math.ceil((size - 2) * Math.min(1.0, left / (double) total)));
            graphics.fill(x + 1, y + 1, x + size - 1, y + 1 + band, CodexStyle.COOLING);
        }
    }

    private Component keyName(int slot) {
        KeyMapping key = RiftCastKeys.SLOTS[slot];
        return key.isUnbound() ? line("key_none") : key.getTranslatedKeyMessage();
    }

    /** "slot 5, key B", "slot U", or "not equipped". */
    private Component castLine(SkillScreenPayload.Entry entry) {
        int slot = browser.slotOf(entry.id());
        if (slot < 0) {
            return line(entry.rank() > 0 ? "not_equipped" : "not_learned");
        }
        if (slot == SkillBook.ULTIMATE_SLOT) {
            return line("slot_u");
        }
        return line("slot_key", slot + 1, keyName(slot));
    }

    // -- left page: a skill's page -----------------------------------------------------------------

    private Component groupLabel(SkillScreenPayload.Entry entry) {
        if (entry.universal()) {
            return line("group_universal");
        }
        if (!entry.base()) {
            return Component.literal(CodexNames.ascendancy(entry.ascendancy()));
        }
        return line("group_base", CodexNames.skillClass(browser.view().skillClass()));
    }

    /** "< Loadout", the group, the icon, name, tag and school, and the rank pips. */
    private int head(GuiGraphics graphics, SkillScreenPayload.Entry entry, boolean locked, Component sub, int pipsWidth) {
        Component back = line("back");
        boolean hover = hot(L, T - 1, L + font.width(back), T + 9, button -> {
            browser.back();
            return true;
        });
        text(graphics, back, L, T, hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
        textRight(graphics, fit(groupLabel(entry), W - font.width(back) - 6), L + W, T, CodexStyle.MUTED);
        slot(graphics, L, T + 13, 24, CodexStyle.SLOT);
        skillIcon(graphics, entry, L + 4, T + 17, 16, locked);
        if (locked) {
            padlock(graphics, L + 16, T + 14);
        }
        text(graphics, fit(bold(CodexNames.skill(entry)), W - 30), L + 30, T + 14,
                locked ? CodexStyle.DIM : CodexStyle.INK);
        int x = tag(graphics, chipLabel(entry), L + 30, T + 25, CodexStyle.lever(entry.passive() ? null : SkillFigures.lever(entry)));
        text(graphics, fit(sub, L + W - x - 4), x + 4, T + 26, CodexStyle.MUTED);
        Component rank = entry.rank() == 0 ? line("not_learned") : line("rank_of", entry.rank(), entry.maxRank());
        // The pips stop short of the rank line, which is drawn on the same row.
        int pitch = Math.min(pipsWidth, W - font.width(rank) - 4) / Math.max(1, entry.maxRank());
        for (int i = 0; i < entry.maxRank(); i++) {
            int px = L + i * pitch;
            graphics.fill(px, T + 41, px + pitch - 2, T + 45, i < entry.rank() ? CodexStyle.ACCENT : CodexStyle.TROUGH);
        }
        textRight(graphics, rank, L + W, T + 39, locked ? CodexStyle.MUTED : CodexStyle.INK);
        return T + 51;
    }

    private Component chipLabel(SkillScreenPayload.Entry entry) {
        return line("chip_" + leverKey(SkillFigures.lever(entry), entry.passive()));
    }

    /** Two columns of numbers, changed values in bold. Returns the y after them. */
    private int numbers(GuiGraphics graphics, int y, SkillScreenPayload.Entry entry, List<SkillFigures.Row> rows,
                        boolean figureLabels) {
        SkillFigures.Columns columns = SkillFigures.columns(entry);
        Component from = columns.single() ? Component.empty()
                : entry.rank() == 0 ? line("col_rank", columns.from()) : line("col_now");
        Component to = columns.single() ? line("col_now") : line("col_rank", columns.to());
        textRight(graphics, from, L + 118, y, CodexStyle.ACCENT);
        textRight(graphics, to, L + W, y, CodexStyle.ACCENT);
        for (int i = 0; i < rows.size(); i++) {
            SkillFigures.Row row = rows.get(i);
            int yy = y + 11 + i * 10;
            Component label = figureLabels ? line(row.label()) : Component.literal(row.label());
            Component value = Component.literal(row.to());
            Component shownValue = row.changes() ? bold(value) : value;
            // The label stops before the first number on its row (an attribute name can be long).
            int labelRoom = columns.single() ? W - font.width(shownValue) - 4 : 118 - font.width(row.from()) - 4;
            text(graphics, fit(label, labelRoom), L, yy, CodexStyle.MUTED);
            if (!columns.single()) {
                textRight(graphics, row.from(), L + 118, yy, CodexStyle.INK);
            }
            textRight(graphics, shownValue, L + W, yy, CodexStyle.INK);
        }
        return y + 11 + rows.size() * 10;
    }

    private int facts(GuiGraphics graphics, int y, List<Component[]> rows) {
        for (int i = 0; i < rows.size(); i++) {
            // A long label still leaves its value a readable width.
            Component label = fit(rows.get(i)[0], W - 40);
            text(graphics, label, L, y + i * 10, CodexStyle.MUTED);
            int labelWidth = font.width(label) + 6;
            textRight(graphics, fit(rows.get(i)[1], W - labelWidth), L + W, y + i * 10, CodexStyle.INK);
        }
        return y + rows.size() * 10;
    }

    private void detailLeaf(GuiGraphics graphics, SkillScreenPayload.Entry entry) {
        if (entry.guide().resolved()) {
            guideLeaf(graphics, entry);
            return;
        }
        SkillScreenPayload view = browser.view();
        SkillScreenPayload.Facts facts = entry.facts();
        AbilityFacts ability = AbilityLookup.of(facts.engine(), facts.ability());
        boolean locked = CodexBrowser.locked(entry);
        Component school = ability.school().isEmpty()
                ? (facts.engine().isEmpty() || facts.engine().equals(AbilityLookup.SPELL_ENGINE)
                ? Component.empty() : line("engine_" + facts.engine()))
                : Component.literal(CodexNames.school(view.skillClass(), ability.school()));
        int y = head(graphics, entry, locked, school, 100);

        y = numbers(graphics, y, entry, SkillFigures.rows(entry, ability), true);
        String atMax = SkillFigures.atMax(entry, ability);
        if (atMax != null) {
            text(graphics, fit(line("at_rank", entry.maxRank(), atMax), W), L, y + 1, CodexStyle.MUTED);
            y += 11;
        }
        dotted(graphics, L, L + W, y + 1, CodexStyle.DOTS);
        y += 5;

        List<Component[]> rows = new ArrayList<>();
        Component scales = scaling(entry, ability);
        if (entry.guide().scaling() == null && gunScaled(entry.id(), facts.powerAttribute())) {
            scales = scales.copy().append(line("scales_gun"));
        }
        rows.add(new Component[] {line("scales_on"), scales});
        if (!locked) {
            String[] area = ability.area();
            if (area != null) {
                rows.add(new Component[] {line("area"), line(area[0], area[1])});
            }
            Component also = effects(ability);
            if (also != null) {
                rows.add(new Component[] {line("also"), also});
            }
            rows.add(new Component[] {line("cast"), entry.ultimate() && browser.slotOf(entry.id()) < 0
                    ? line("slot_u") : castLine(entry)});
        }
        y = facts(graphics, y, rows);

        if (locked) {
            lockBox(graphics, y + 4, entry);
        } else {
            dotted(graphics, L, L + W, y + 1, CodexStyle.DOTS);
            costLine(graphics, y + 4, entry);
        }
        boolean rankUp = browser.canRankUp(entry);
        button(graphics, L, BUTTONS, 82, line("rank_up"), rankUp, () -> request(browser.rankUp()));
        boolean equip = browser.canEquip(entry);
        button(graphics, L + 86, BUTTONS, 82, line(equip ? "equip_slot" : "equip"), equip, browser::startEquip);
        if (view.inRift()) {
            text(graphics, fit(line("status_in_rift"), W), L, BUTTONS - 10, CodexStyle.WARN);
        }
    }

    /** Damage stays above the scroll area; every sentence and effect can be read without truncation. */
    private void guideLeaf(GuiGraphics graphics, SkillScreenPayload.Entry entry) {
        if (!detailId.equals(entry.id())) {
            detailId = entry.id();
            detailScroll = 0;
            detailMaxScroll = 0;
        }
        boolean locked = CodexBrowser.locked(entry);
        AbilityFacts ability = AbilityLookup.of(entry.facts().engine(), entry.facts().ability());
        int y = head(graphics, entry, locked, entry.passive() ? line("always_on")
                : Component.literal(CodexNames.school(browser.view().skillClass(), ability.school())), 100);
        var guide = entry.guide();
        if (!guide.figures().isEmpty()) {
            y = guideFigure(graphics, y, entry, guide.figures().getFirst());
            if (!entry.passive()) {
                text(graphics, fit(line("basis_before_defenses"), W), L, y, CodexStyle.MUTED);
                y += 11;
            }
        } else {
            text(graphics, fit(line("basis_utility"), W), L, y, CodexStyle.ACCENT);
            y += 12;
        }
        dotted(graphics, L, L + W, y + 1, CodexStyle.DOTS);
        int top = y + 5;
        int bottom = BUTTONS - 27;
        // 1.21.1 scissors use screen coordinates, independently of the book's scaled pose.
        graphics.enableScissor(screenX(L - 1), screenY(top), screenX(L + W + 1), screenY(bottom));
        int contentY = top - detailScroll;
        if (!entry.passive()) contentY = guideParagraph(graphics, contentY, line("heart_note"), CodexStyle.MUTED);
        List<SkillFigures.Row> costs = SkillFigures.rows(entry, ability).stream()
                .filter(row -> row.label().equals("mana") || row.label().equals("cooldown")).toList();
        if (!entry.passive()) contentY = numbers(graphics, contentY, entry, costs, true) + 5;
        String key = "spell.riftfall." + entry.id() + ".description";
        if (!I18n.exists(key) && !entry.facts().ability().isEmpty()) key = "spell." + entry.facts().ability().replace(':', '.') + ".description";
        if (I18n.exists(key)) {
            String prose = SkillDescription.text(net.minecraft.locale.Language.getInstance().getOrDefault(key));
            contentY = guideParagraph(graphics, contentY, Component.literal(SkillDescription.resolve(prose, guide.tokens(),
                    SkillsScreen::stageDamage, SkillsScreen::stageCoefficient, SkillsScreen::shieldAmount)), CodexStyle.INK);
        }
        if (!entry.passive()) contentY = guideParagraph(graphics, contentY, line(
                guide.scaling() == null && gunScaled(entry.id(), entry.facts().powerAttribute())
                        ? "scaling_line_gun" : "scaling_line", scaling(entry, ability)), CodexStyle.MUTED);
        for (int i = 1; i < guide.figures().size(); i++) {
            contentY = guideFigure(graphics, contentY, entry, guide.figures().get(i)) + 3;
        }
        for (Component note : guide.notes()) contentY = guideParagraph(graphics, contentY, note, CodexStyle.INK);
        if (locked) {
            lockBox(graphics, contentY + 4, entry);
            contentY += 85;
        } else if (!entry.passive()) {
            contentY = guideParagraph(graphics, contentY, line("cast_line", castLine(entry)), CodexStyle.MUTED);
        }
        graphics.disableScissor();
        detailMaxScroll = Math.max(0, contentY + detailScroll - bottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        if (detailMaxScroll > 0) {
            int height = bottom - top;
            int thumb = Math.max(10, height * height / (height + detailMaxScroll));
            int at = top + (height - thumb) * detailScroll / detailMaxScroll;
            graphics.fill(L + W + 3, top, L + W + 4, bottom, CodexStyle.TROUGH);
            graphics.fill(L + W + 3, at, L + W + 4, at + thumb, CodexStyle.ACCENT);
            text(graphics, fit(line("scroll_more"), W), L, bottom + 3, CodexStyle.MUTED);
        }
        if (browser.view().inRift()) text(graphics, fit(line("status_in_rift"), W), L, BUTTONS - 12, CodexStyle.WARN);
        else costLine(graphics, BUTTONS - 12, entry);
        button(graphics, L, BUTTONS, entry.passive() ? W : 82, line("rank_up"), browser.canRankUp(entry), () -> request(browser.rankUp()));
        boolean equip = browser.canEquip(entry);
        if (!entry.passive()) button(graphics, L + 86, BUTTONS, 82, line(equip ? "equip_slot" : "equip"), equip, browser::startEquip);
    }

    private int guideParagraph(GuiGraphics graphics, int y, Component paragraph, int color) {
        return wrapped(graphics, paragraph, L, y, W - 3, 10, Integer.MAX_VALUE, color) + 5;
    }

    /** "36 arcane damage": a deferred stage's estimate inside the authored description. */
    private static String stageDamage(String amount, String type) {
        return type.isEmpty() ? GuideLang.text("stage_damage", amount).getString()
                : GuideLang.text("stage_damage_typed", amount, GuideLang.text("damage_type." + type)).getString();
    }

    static String stageCoefficient(String percent, String type) {
        return GuideLang.text(type.isEmpty() ? "stage_coefficient" : "stage_coefficient_" + type, percent).getString();
    }

    /** "7.9 damage": a shield pool inside the authored description, or what it grows with when there is no estimate. */
    static String shieldAmount(String points) {
        return (points.isEmpty() ? GuideLang.text("shield_unknown") : GuideLang.text("shield_amount", points)).getString();
    }

    private int guideFigure(GuiGraphics graphics, int y, SkillScreenPayload.Entry entry, SkillScreenPayload.Figure figure) {
        int next = wrapped(graphics, bold(figure.label()), L, y, W, 10, Integer.MAX_VALUE, CodexStyle.ACCENT);
        var columns = SkillFigures.columns(entry);
        String from = line("figure_rank", columns.from(), figure.from()).getString();
        String to = line("figure_rank", columns.to(), figure.to()).getString();
        text(graphics, from, L, next, CodexStyle.INK);
        if (!columns.single()) {
            if (font.width(from) + font.width(to) + 8 > W) next += 10;
            textRight(graphics, bold(Component.literal(to)), L + W, next, CodexStyle.INK);
        }
        return next + 12;
    }

    private Component scaling(SkillScreenPayload.Entry entry, AbilityFacts ability) {
        Component label = entry.guide().scaling() != null ? entry.guide().scaling()
                : com.cyberday1.riftfall.skill.SkillScaling.label(ability.school());
        return label == null ? scalesOn(entry.facts().powerAttribute()) : label;
    }

    /** Fallback for abilities without school-specific scaling. */
    private Component scalesOn(String attribute) {
        if (attribute.isEmpty()) {
            return Component.empty();
        }
        MutableComponent name = attributeName(attribute).copy();
        StatBlock.Primary primary = SkillFigures.primaryOf(attribute);
        if (primary != null && !attribute.equals("riftfall:" + primary.key())) {
            name.append(" (").append(Component.translatable(ProgressionTab.CHARACTER.key("tag_" + primary.key()))).append(")");
        }
        return name;
    }

    private static Component attributeName(String attribute) {
        ResourceLocation id = ResourceLocation.tryParse(attribute);
        if (id == null) {
            return Component.literal(attribute);
        }
        Attribute found = BuiltInRegistries.ATTRIBUTE.get(id);
        return found == null ? Component.literal(CodexNames.readable(attribute))
                : Component.translatable(found.getDescriptionId());
    }

    /** "Resistance I, 8 s", every status effect the ability applies, joined. */
    private @Nullable Component effects(AbilityFacts ability) {
        if (ability.effects().isEmpty()) {
            return null;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < ability.effects().size(); i++) {
            AbilityFacts.Effect effect = ability.effects().get(i);
            if (i > 0) {
                out.append(", ");
            }
            ResourceLocation id = ResourceLocation.tryParse(effect.id());
            MobEffect mob = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
            out.append(mob == null ? Component.literal(CodexNames.readable(effect.id())) : mob.getDisplayName());
            if (effect.amplifier() > 0 || mob != null) {
                out.append(" ").append(Component.translatable("enchantment.level." + (effect.amplifier() + 1)));
            }
            out.append(", ").append(line("seconds", CharacterSheet.amount(effect.seconds())));
        }
        return out;
    }

    private void costLine(GuiGraphics graphics, int y, SkillScreenPayload.Entry entry) {
        if (entry.rank() >= entry.maxRank()) {
            text(graphics, line("mastered"), L, y, CodexStyle.INK);
            return;
        }
        int points = SkillFigures.nextPoints(entry);
        int have = browser.view().available();
        Component haveLine = line("you_have", have);
        text(graphics, fit(line(points == 1 ? "next_rank_one" : "next_rank_many", points), W - font.width(haveLine) - 6), L,
                y, CodexStyle.INK);
        textRight(graphics, haveLine, L + W, y, have < points ? CodexStyle.WARN : CodexStyle.MUTED);
    }

    /** "To learn this": the class, level 60, and this ascendancy, ticked or crossed. Ascend is on the Character page. */
    private void lockBox(GuiGraphics graphics, int y, SkillScreenPayload.Entry entry) {
        SkillScreenPayload view = browser.view();
        boolean closed = entry.status().equals(SkillBook.Spend.OTHER_ASCENDANCY.name());
        Component closedLine = line("req_closed", CodexNames.ascendancy(view.ascension()));
        // "Closed: you chose Crimson Blade Reaver" takes a second line; nothing is drawn under a closed box.
        boolean closedWraps = closed && font.width(closedLine) > W - 14;
        int box = y + 44 + (closedWraps ? 9 : 0);
        well(graphics, L - 2, y - 2, L + W + 2, box);
        int price = SkillBook.rankCost(SkillFigures.kind(entry), 1);
        Component priceLine = line(price == 1 ? "point_a_rank" : "points_a_rank", price);
        text(graphics, fit(bold(line("to_learn")), W - 4 - font.width(priceLine) - 6), L + 2, y + 1, CodexStyle.ACCENT);
        textRight(graphics, priceLine, L + W - 2, y + 1, CodexStyle.MUTED);
        boolean levelOk = view.level() >= SkillBook.ASCENSION_LEVEL;
        Object[][] rows = {
                {true, Component.literal(CodexNames.skillClass(view.skillClass())), line("req_your_class")},
                {levelOk, line("req_level", SkillBook.ASCENSION_LEVEL), line("req_you_are", view.level())},
                {false, closed ? closedLine : line("req_choose", CodexNames.ascendancy(entry.ascendancy())),
                        closed ? Component.empty() : line("req_once")}};
        for (int i = 0; i < rows.length; i++) {
            int yy = y + 13 + i * 9;
            boolean ok = (Boolean) rows[i][0];
            check(graphics, L + 2, yy, ok);
            Component left = (Component) rows[i][1];
            Component note = (Component) rows[i][2];
            int colour = ok ? CodexStyle.INK : CodexStyle.WARN;
            if (closedWraps && i == 2) {
                wrapped(graphics, left, L + 12, yy, W - 14, 9, 2, colour);
                continue;
            }
            // A long ascendancy name drops the note ("once") before it is cut; it never runs under it.
            if (!note.getString().isEmpty() && font.width(left) + 4 + font.width(note) > W - 14) {
                note = Component.empty();
            }
            int room = W - 14 - (note.getString().isEmpty() ? 0 : font.width(note) + 4);
            text(graphics, fit(left, room), L + 12, yy, colour);
            textRight(graphics, note, L + W - 2, yy, CodexStyle.MUTED);
        }
        if (!closed) {
            text(graphics, fit(line("closes_others"), W), L, box + 3, CodexStyle.MUTED);
            wrapped(graphics, line("ascend_command"), L, box + 12, W, 9, 2, CodexStyle.MUTED);
        }
    }

    private void passiveLeaf(GuiGraphics graphics, SkillScreenPayload.Entry entry) {
        SkillScreenPayload view = browser.view();
        int y = head(graphics, entry, false, line("always_on"), 110);
        String attribute = entry.facts().attribute();
        Component name = attributeName(attribute);
        SkillFigures.Row row = SkillFigures.passiveRow(entry);
        y = numbers(graphics, y, entry, List.of(new SkillFigures.Row(name.getString(), row.from(), row.to())), false);
        SkillFigures.Columns columns = SkillFigures.columns(entry);
        if (!columns.single() && columns.to() != entry.maxRank()) {
            text(graphics, line("at_rank", entry.maxRank(),
                    SkillFigures.plus(entry.facts().bonusAt(entry.maxRank()))), L, y + 1, CodexStyle.MUTED);
            y += 11;
        }
        dotted(graphics, L, L + W, y + 1, CodexStyle.DOTS);
        y += 5;
        List<Component[]> rows = new ArrayList<>();
        rows.add(new Component[] {line("attribute"), name});
        Double total = total(attribute);
        if (total != null) {
            rows.add(new Component[] {line("total_now"), Component.literal(CharacterSheet.amount(total))});
        }
        rows.add(new Component[] {line("who_can_buy"), entry.universal() ? line("every_class")
                : line("class_only", CodexNames.skillClass(view.skillClass()))});
        rows.add(new Component[] {line("loadout_slot"), line("none_always_on")});
        y = facts(graphics, y, rows);
        dotted(graphics, L, L + W, y + 1, CodexStyle.DOTS);
        if (CodexBrowser.locked(entry)) {
            lockBox(graphics, y + 6, entry);
        } else {
            costLine(graphics, y + 4, entry);
        }
        button(graphics, L, BUTTONS, W, line("rank_up"), browser.canRankUp(entry), () -> request(browser.rankUp()));
    }

    /** A Gunner active on attack damage scales on the held TaCZ gun too. */
    static boolean gunScaled(String nodeId, String attribute) {
        return nodeId.startsWith("gunner_") && attribute.equals("minecraft:generic.attack_damage");
    }

    private @Nullable Double total(String attribute) {
        ResourceLocation id = ResourceLocation.tryParse(attribute);
        if (id == null || minecraft == null || minecraft.player == null) {
            return null;
        }
        return BuiltInRegistries.ATTRIBUTE.getHolder(id)
                .filter(holder -> minecraft.player.getAttribute(holder) != null)
                .map(holder -> minecraft.player.getAttributeValue(holder))
                .orElse(null);
    }

    // -- left page: equip mode -------------------------------------------------------------------------

    private void equipLeaf(GuiGraphics graphics, SkillScreenPayload.Entry entry) {
        loadoutHeader(graphics);
        hotbar(graphics, T + 13);
        dotted(graphics, L, L + W, T + 84, CodexStyle.DOTS);
        slot(graphics, L, T + 91, 20, CodexStyle.SLOT);
        skillIcon(graphics, entry, L + 2, T + 93, 16, false);
        text(graphics, fit(bold(CodexNames.skill(entry)), W - 25), L + 25, T + 92, CodexStyle.INK);
        int x = tag(graphics, chipLabel(entry), L + 25, T + 102, CodexStyle.lever(SkillFigures.lever(entry)));
        text(graphics, line("rank_short", entry.rank(), entry.maxRank()), x + 4, T + 103, CodexStyle.MUTED);
        List<Component[]> rows = List.of(
                new Component[] {line("mana"), Component.literal(CharacterSheet.amount(entry.cost()))},
                new Component[] {line("cooldown"), Component.literal(SkillFigures.seconds(entry.facts().cooldownNow()))},
                new Component[] {line("now_in"), castLine(entry)});
        facts(graphics, T + 117, rows);
        dotted(graphics, L, L + W, T + 150, CodexStyle.DOTS);
        wrapped(graphics, line(entry.ultimate() ? "equip_hint_ultimate" : "equip_hint"), L, T + 155, W, 10, 4,
                CodexStyle.MUTED);
        button(graphics, L, BUTTONS, 82, line("read_page"), true, browser::readPicked);
        button(graphics, L + 86, BUTTONS, 82, line("cancel"), true, browser::cancelEquip);
    }

    // -- right page: the browser -------------------------------------------------------------------------

    private void browserPage(GuiGraphics graphics) {
        SkillScreenPayload view = browser.view();
        tabs(graphics);
        if (browser.tab() == CodexBrowser.Tab.UNIVERSAL) {
            text(graphics, fit(line("universal_sub"), W), R, T + 16, CodexStyle.MUTED);
        } else {
            chips(graphics, CodexBrowser.chips(view));
        }
        List<CodexBrowser.Row> rows = view.hasClass() ? browser.rows() : List.of();
        rowCount = rows.size();
        int y = LIST_TOP;
        int drawn = 0;
        for (int i = browser.scroll(); i < rows.size(); i++) {
            CodexBrowser.Row row = rows.get(i);
            int h = height(row);
            if (y + h > LIST_BOTTOM + 2) {
                break;
            }
            draw(graphics, row, y);
            y += h;
            drawn++;
        }
        if (drawn < rows.size()) {
            int top = LIST_TOP;
            int span = LIST_BOTTOM - top;
            graphics.fill(R + W + 3, top, R + W + 5, LIST_BOTTOM, CodexStyle.TROUGH);
            int a = top + span * browser.scroll() / rows.size();
            int b = top + span * Math.min(rows.size(), browser.scroll() + drawn) / rows.size();
            graphics.fill(R + W + 3, a, R + W + 5, Math.max(a + 2, b), CodexStyle.ACCENT);
        }
        footer(graphics);
    }

    private void tabs(GuiGraphics graphics) {
        int x = R;
        CodexBrowser.Tab[] all = CodexBrowser.Tab.values();
        int labels = 0;
        for (CodexBrowser.Tab each : all) {
            Component label = line("tab_" + each.name().toLowerCase(Locale.ROOT));
            labels += font.width(each == browser.tab() ? bold(label) : label);
        }
        // Three pixels a side, less when long names would carry the last tab off the page.
        int pad = Math.clamp((W - labels - 2 * (all.length - 1)) / (2 * all.length), 1, 3);
        for (CodexBrowser.Tab each : all) {
            boolean current = each == browser.tab();
            Component label = line("tab_" + each.name().toLowerCase(Locale.ROOT));
            Component shown = current ? bold(label) : label;
            int w = font.width(shown) + 2 * pad;
            hot(x, T - 2, x + w, T + 11, button -> {
                browser.tab(each);
                return true;
            });
            if (current) {
                graphics.fill(x, T - 2, x + w, T + 11, CodexStyle.ACCENT);
                text(graphics, shown, x + pad, T + 1, CodexStyle.ON_ACCENT);
            } else {
                frame(graphics, x, T - 1, x + w, T + 11, CodexStyle.TAB_IDLE);
                text(graphics, shown, x + pad, T + 1, CodexStyle.MUTED);
            }
            x += w + 2;
        }
        graphics.fill(R, T + 11, R + W, T + 12, CodexStyle.ACCENT);
    }

    private void chips(GuiGraphics graphics, List<LeverType> levers) {
        int x = R;
        for (LeverType lever : levers) {
            Component label = line("chip_" + lever.key());
            int w = font.width(label) + 3;
            int colour = CodexStyle.lever(lever);
            boolean active = lever == browser.chip();
            boolean hover = hot(x, T + 15, x + w, T + 25, button -> {
                browser.toggleChip(lever);
                return true;
            });
            if (active) {
                graphics.fill(x, T + 15, x + w, T + 25, colour);
                text(graphics, label, x + 2, T + 16, CodexStyle.ON_TAG);
            } else {
                frame(graphics, x, T + 15, x + w, T + 25, colour);
                text(graphics, label, x + 2, T + 16, colour);
            }
            if (hover) {
                tooltip(List.of(line("lever_" + lever.key())));
            }
            x += w + 2;
        }
    }

    private void footer(GuiGraphics graphics) {
        int y = T + 210;
        int x = R;
        for (CodexBrowser.Filter filter : CodexBrowser.Filter.values()) {
            boolean current = filter == browser.filter();
            Component label = line("filter_" + filter.name().toLowerCase(Locale.ROOT));
            Component shown = current ? bold(label) : label;
            int end = x + font.width(shown);
            hot(x, y - 1, end, y + 10, button -> {
                browser.filter(filter);
                return true;
            });
            text(graphics, shown, x, y, current ? CodexStyle.ACCENT : CodexStyle.MUTED);
            if (current) {
                graphics.fill(x, y + 9, end, y + 10, CodexStyle.ACCENT);
            }
            x = end + 8;
        }
        int available = browser.view().available();
        Component full = line(available == 1 ? "points_n_one" : "points_n", available);
        // Bold "12 points" where it clears the filters, else plain, else the bare number with the line on hover.
        Component points = bold(full);
        if (R + W - font.width(points) < x - 4) {
            points = full;
        }
        if (R + W - font.width(points) < x - 4) {
            points = bold(Component.literal(Integer.toString(available)));
            if (over(R + W - font.width(points), y - 1, R + W, y + 10)) {
                tooltip(List.of(full));
            }
        }
        textRight(graphics, points, R + W, y, CodexStyle.INK);
    }

    private int height(CodexBrowser.Row row) {
        return switch (row) {
            case CodexBrowser.Band band -> 14;
            case CodexBrowser.Sub sub -> 10;
            case CodexBrowser.Skill skill -> ROW;
            case CodexBrowser.Strip strip -> 19;
            case CodexBrowser.Note note -> Math.max(1, lineCount(noteText(note), W)) * 10 + 1;
        };
    }

    private void draw(GuiGraphics graphics, CodexBrowser.Row row, int y) {
        switch (row) {
            case CodexBrowser.Band band -> band(graphics, band, y);
            case CodexBrowser.Sub sub -> sub(graphics, sub, y);
            case CodexBrowser.Skill skill -> skillRow(graphics, skill.entry(), skill.locked(), y);
            case CodexBrowser.Strip strip -> strip(graphics, strip.entries(), y);
            case CodexBrowser.Note note -> wrapped(graphics, noteText(note), R, y, W, 10, 6,
                    note.line().equals("closed_names") ? CodexStyle.DIM : CodexStyle.MUTED);
        }
    }

    private Component noteText(CodexBrowser.Note note) {
        SkillScreenPayload view = browser.view();
        return switch (note.line()) {
            case "choose_at" -> view.level() < SkillBook.ASCENSION_LEVEL
                    ? line("choose_at", SkillBook.ASCENSION_LEVEL, view.level()) : line("choose_now");
            case "closed_names" -> Component.literal(String.join(", ",
                    note.names().stream().map(CodexNames::ascendancy).toList()));
            default -> line(note.line());
        };
    }

    private void band(GuiGraphics graphics, CodexBrowser.Band band, int y) {
        boolean strong = band.kind() == CodexBrowser.BandKind.CHOSEN;
        boolean closed = band.kind() == CodexBrowser.BandKind.CLOSED;
        boolean locked = band.kind() == CodexBrowser.BandKind.OPEN || band.kind() == CodexBrowser.BandKind.SHUT;
        if (band.kind() == CodexBrowser.BandKind.SHUT) {
            hot(R - 2, y, R + W + 2, y + 12, button -> {
                browser.expand(band.name());
                return true;
            });
        }
        graphics.fill(R - 2, y, R + W + 2, y + 11, strong ? CodexStyle.ACCENT : CodexStyle.BAND);
        graphics.fill(R - 2, y + 11, R + W + 2, y + 12, strong ? CodexStyle.ACCENT : CodexStyle.BAND_EDGE);
        Component label = switch (band.kind()) {
            case ACTIVES -> line("band_actives");
            case PASSIVES -> line("band_passives");
            case UNIVERSAL -> line("band_universal");
            case CLOSED -> line("band_closed");
            case SHUT -> line("band_shut", CodexNames.ascendancy(band.name()));
            case CHOSEN, OPEN -> Component.literal(CodexNames.ascendancy(band.name()));
        };
        Component right = switch (band.kind()) {
            case ACTIVES -> browser.chip() != null
                    ? line("band_actives_chip", band.shown(), band.total(), line("chip_" + browser.chip().key()))
                    : band.shown() != band.total() ? line("of_shown", band.shown(), band.total())
                    : line("band_actives_n", band.total());
            case PASSIVES -> band.shown() != band.total() ? line("of_shown", band.shown(), band.total())
                    : line("band_passives_n", band.total());
            case UNIVERSAL -> line("band_universal_n", band.total());
            case CHOSEN -> line("chosen_at", SkillBook.ASCENSION_LEVEL);
            default -> Component.literal(String.valueOf(band.total()));
        };
        int rx = R + W - 1;
        if (locked) {
            padlock(graphics, rx - 7, y + 2);
            rx -= 10;
        }
        Component shown = closed ? label : bold(label);
        // The count gives way to the band's name, down to half the band.
        right = fit(right, Math.max((rx - R) / 2, rx - (R + 1) - font.width(shown) - 4));
        int rightWidth = font.width(right);
        textRight(graphics, right, rx, y + 2, strong ? CodexStyle.ON_ACCENT_MUTED : CodexStyle.MUTED);
        text(graphics, fit(shown, rx - rightWidth - 4 - (R + 1)), R + 1, y + 2,
                strong ? CodexStyle.ON_ACCENT : closed ? CodexStyle.DIM : CodexStyle.INK);
    }

    private void sub(GuiGraphics graphics, CodexBrowser.Sub sub, int y) {
        Component label = line("sub_" + sub.kind().name().toLowerCase(Locale.ROOT));
        Component right = switch (sub.kind()) {
            case ULTIMATE -> line("sub_ultimate_n");
            case ACTIVES -> browser.chip() != null
                    ? line("sub_actives_chip", sub.shown(), line("chip_" + browser.chip().key()))
                    : Component.literal(String.valueOf(sub.total()));
            case PASSIVES -> line("sub_passives_n", sub.total());
        };
        text(graphics, label, R, y, CodexStyle.ACCENT);
        right = fit(right, Math.max(W / 2, W - font.width(label) - 8));
        textRight(graphics, right, R + W, y, CodexStyle.MUTED);
        dotted(graphics, R + font.width(label) + 4, R + W - font.width(right) - 4, y + 6, CodexStyle.DOTS);
    }

    private void skillRow(GuiGraphics graphics, SkillScreenPayload.Entry entry, boolean previewLock, int y) {
        boolean locked = previewLock || CodexBrowser.locked(entry);
        boolean selected = entry.id().equals(browser.selected()) || entry.id().equals(browser.picking());
        boolean hover = hot(R - 3, y - 1, R + W + 1, y + ROW - 1, button -> {
            if (button != PRIMARY) {
                return false;
            }
            if (browser.picking() != null) {
                browser.pick(entry.id());
            } else {
                browser.select(entry.id());
            }
            return true;
        });
        if (selected) {
            graphics.fill(R - 3, y - 1, R + W + 1, y + ROW - 1, CodexStyle.SELECTED);
            frame(graphics, R - 3, y - 1, R + W + 1, y + ROW - 1, CodexStyle.ACCENT);
        }
        slot(graphics, R, y, 18, CodexStyle.SLOT);
        LeverType lever = SkillFigures.lever(entry);
        skillIcon(graphics, entry, R + 1, y + 1, 16, locked);
        int slot = browser.slotOf(entry.id());
        if (slot >= 0) {
            graphics.drawString(font, slot == SkillBook.ULTIMATE_SLOT ? I18n.get(tab.key("slot_glyph_ultimate")) : String.valueOf(slot + 1), R + 1, y + 1,
                    SLOT_NUMBER, true);
        }
        int nx = R + 23;
        Component name = CodexNames.skill(entry);
        text(graphics, fit(selected ? bold(name) : name, W - 23 - 30), nx, y + 1, locked ? CodexStyle.DIM : CodexStyle.INK);
        int colour = locked ? CodexStyle.DIM : CodexStyle.MUTED;
        boolean closed = entry.status().equals(SkillBook.Spend.OTHER_ASCENDANCY.name());
        if (entry.passive()) {
            text(graphics, fit(passiveEffect(entry), W - 23 - 18), nx, y + 10, colour);
        } else {
            int x2 = tag(graphics, chipLabel(entry), nx, y + 9, CodexStyle.lever(lever));
            Component sub;
            // A closed row says so on the right; one still to open has the whole line for its reason.
            int room = R + W - x2 - 3 - (locked && !closed ? 0 : 18);
            if (locked && !closed) {
                sub = font.width(line("opens_ascend")) <= room ? line("opens_ascend") : line("row_opens_short");
            } else if (locked) {
                sub = entry.ultimate() ? line("row_ultimate") : Component.empty();
            } else if (entry.ultimate() && slot < 0) {
                sub = entry.rank() > 0 ? line("row_ultimate") : line("not_learned");
            } else {
                sub = castLine(entry);
            }
            text(graphics, fit(sub, room), x2 + 3, y + 10, colour);
        }
        if (locked) {
            padlock(graphics, R + W - 8, y + 2);
            if (closed) textRight(graphics, line("row_closed"), R + W, y + 10, CodexStyle.DIM);
        } else {
            Component rank = line("rank_short_plain", entry.rank(), entry.maxRank());
            boolean max = entry.rank() >= entry.maxRank();
            textRight(graphics, max ? bold(rank) : rank, R + W, y + 1, entry.rank() > 0 ? CodexStyle.INK : CodexStyle.MUTED);
            if (browser.canRankUp(entry)) {
                textRight(graphics, bold(line("plus_points", SkillFigures.nextPoints(entry))), R + W, y + 10,
                        CodexStyle.ACCENT);
            }
        }
        if (hover) {
            rowTooltip(entry, locked);
        }
    }

    /** "+10 Max Mana a rank", "+2% Crit Chance a rank": the unit and stat are the guide's, else the attribute's. */
    private Component passiveEffect(SkillScreenPayload.Entry entry) {
        String stat = SkillFigures.passiveStat(entry);
        return line("passive_effect", PassiveGuide.signed(entry.facts().bonusPerRank(), SkillFigures.passiveUnit(entry)),
                stat.indexOf(':') < 0 ? line("passive_stat_" + stat) : attributeName(stat));
    }

    private void rowTooltip(SkillScreenPayload.Entry entry, boolean locked) {
        List<Component> lines = new ArrayList<>();
        lines.add(CodexNames.skill(entry));
        lines.add(groupLabel(entry).copy().withStyle(ChatFormatting.GRAY));
        lines.add(entry.passive() ? passiveEffect(entry).copy().withStyle(ChatFormatting.GRAY)
                : line("lever_" + leverKey(SkillFigures.lever(entry), false)).copy().withStyle(ChatFormatting.GRAY));
        if (entry.passive() && entry.facts().bonusBase() != 0.0) {
            lines.add(line("passive_base", PassiveGuide.signed(entry.facts().bonusAt(1), SkillFigures.passiveUnit(entry)))
                    .withStyle(ChatFormatting.GRAY));
        }
        boolean ok = entry.status().equals(SkillBook.Spend.OK.name()) && !browser.view().inRift();
        if (!ok) {
            String status = browser.view().inRift() ? "in_rift" : entry.status().toLowerCase(Locale.ROOT);
            lines.add(line("status_" + status).copy().withStyle(locked ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        tooltip(lines);
    }

    private void strip(GuiGraphics graphics, List<SkillScreenPayload.Entry> entries, int y) {
        for (int i = 0; i < entries.size(); i++) {
            SkillScreenPayload.Entry entry = entries.get(i);
            int x = R + i * 16;
            if (x + 16 > R + W + 2) {
                break;
            }
            boolean hover = hot(x, y, x + 16, y + 16, button -> {
                browser.select(entry.id());
                return true;
            });
            skillIcon(graphics, entry, x + 1, y + 1, 14, true);
            if (entry.id().equals(browser.selected())) {
                frame(graphics, x - 1, y - 1, x + 17, y + 17, CodexStyle.ACCENT);
            }
            if (hover) {
                rowTooltip(entry, true);
            }
        }
    }
}
