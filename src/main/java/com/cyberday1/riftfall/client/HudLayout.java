package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.client.HudPlacements.Placement;
import com.cyberday1.riftfall.gear.RelicSignatures;
import com.cyberday1.riftfall.skill.SkillBook;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Where each Riftfall HUD element goes: docked on vanilla's columns, or where the player placed it. Shared by
 * the HUD and the editor so a preview is where the HUD draws. Nothing here draws, so it is unit-tested.
 */
public final class HudLayout {

    /** The framed bar art: 71x8, fill window inset 1 px. */
    public static final int BAR_W = 71;
    public static final int BAR_H = 8;
    /** A font line; the label line sits on top of the bar. */
    public static final int LINE = 9;
    /** A vertical bar's box is this wide so its centred text fits "999/999". */
    public static final int VERTICAL_W = 44;
    static final int TEXT_GAP = 4;
    static final int STACK_GAP = 1;
    static final int SIDE_GAP = 2;
    /** A vanilla HUD row is 9 tall and the columns advance 10 per row. */
    static final int ROW = 9;
    static final int HALF_HOTBAR = 91;

    /**
     * Relic icons: item size, the gap between them, and how many fit the box: the most a player can have at once,
     * Momentum, two capped signatures, and a Berserk that outlasts its mushroom.
     */
    public static final int ICON = 16;
    static final int ICON_GAP = 2;
    static final int ICON_SLOTS = 2 + RelicSignatures.COMBAT_CAP;
    /**
     * Docked icons start this far right of the hotbar's middle: past a left-handed offhand slot (+91 to +120) and the
     * hotbar attack indicator (+97 to +115), bottom-aligned with the hotbar's items (3 px above the screen's foot).
     */
    static final int ICONS_X = 124;
    static final int ICONS_FOOT = 3;

    /** The boss finder's line: arrow, "Boss", distance and rise; and its gap right of the clock. */
    public static final int FINDER_W = 100;
    public static final int FINDER_H = 11;
    static final int FINDER_GAP = 6;

    /** A cooldown slot: the 16 px skill icon in a 1 px frame; docked slots sit this far apart. */
    public static final int COOLDOWN = 18;
    static final int COOLDOWN_GAP = 2;

    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 2.0F;
    public static final float SCALE_STEP = 0.25F;

    /** The editor's id for the mana, Charge and rift clock stack. */
    public static final String GROUP = "bars";

    public enum Element {
        MANA("mana"), CHARGE("charge"), CLOCK("rift_clock"), SHIELD("shield"), PARTY("party"),
        RELIC_STACKS("relic_stacks"), BOSS_FINDER("boss_finder");

        private final String id;

        Element(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String labelKey() {
            return "hud.riftfall." + id;
        }

        public boolean grouped() {
            return MEMBERS.contains(this);
        }
    }

    /** The group, bottom to top. */
    public static final List<Element> MEMBERS = List.of(Element.MANA, Element.CHARGE, Element.CLOCK);

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean overlaps(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        public boolean contains(double px, double py, int pad) {
            return px >= x - pad && px < right() + pad && py >= y - pad && py < bottom() + pad;
        }

        Rect union(Rect o) {
            int x0 = Math.min(x, o.x);
            int y0 = Math.min(y, o.y);
            return new Rect(x0, y0, Math.max(right(), o.right()) - x0, Math.max(bottom(), o.bottom()) - y0);
        }
    }

    public record Slot(Element element, Rect rect, float scale, boolean vertical) {
    }

    /**
     * The bars' boxes, the box the group's followers occupy (null when none is shown), and how many rows the
     * docked elements took from each vanilla column.
     */
    public record Arrangement(List<Slot> slots, @Nullable Rect group, int leftUsed, int rightUsed) {
        public @Nullable Slot slot(Element element) {
            for (Slot s : slots) {
                if (s.element() == element) {
                    return s;
                }
            }
            return null;
        }
    }

    private HudLayout() {
    }

    static float clampScale(float s) {
        return Float.isFinite(s) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)) : 1.0F;
    }

    /** Pixels at a scale, rounded up; the epsilon keeps float noise from adding a row. */
    static int scaled(int px, float scale) {
        return (int) Math.ceil(px * (double) scale - 1e-4);
    }

    static int barWidth(boolean vertical) {
        return vertical ? VERTICAL_W : BAR_W;
    }

    /** Horizontal: text line then bar. Vertical: label, bar standing up, a gap, value. */
    static int barHeight(boolean vertical) {
        return vertical ? LINE + BAR_W + 1 + LINE : LINE + BAR_H;
    }

    /** A box at a share of the screen, kept wholly on it. */
    static Rect placed(float xPct, float yPct, int w, int h, int guiW, int guiH) {
        int x = Math.max(0, Math.min(Math.round(xPct * guiW), guiW - w));
        int y = Math.max(0, Math.min(Math.round(yPct * guiH), guiH - h));
        return new Rect(x, y, w, h);
    }

    /** A docked box ends one row above the column's last row, like the row vanilla would draw next. */
    static Rect docked(int x, int guiH, int column, int w, int h) {
        return new Rect(x, guiH - column + ROW - h, w, h);
    }

    /** How a group member looks: the group's scale and orientation, or its own once split and set. */
    static Placement look(Element member, Placement group, HudPlacements placements) {
        Placement own = placements.split() ? placements.get(member.id()) : null;
        return own != null ? own : group;
    }

    /** The group's box when placed: room for every member, so a hidden one only leaves space at the top. */
    static int[] groupSize(Placement group) {
        int w = scaled(barWidth(group.vertical()), group.scale());
        int h = scaled(barHeight(group.vertical()), group.scale());
        int n = MEMBERS.size();
        return group.vertical() ? new int[] {n * w + (n - 1) * SIDE_GAP, h} : new int[] {w, n * h + (n - 1) * STACK_GAP};
    }

    /**
     * Lays out the shown bars. Followers of the group stack up from its bottom (or rightward from its left)
     * so a hidden member leaves no gap; docked ones stack on the right column, the shield on the left.
     */
    public static Arrangement arrange(int guiW, int guiH, int leftHeight, int rightHeight, Set<Element> shown,
                                      HudPlacements placements) {
        List<Slot> slots = new ArrayList<>();
        Placement group = placements.getOrDocked(GROUP);
        List<Element> followers = new ArrayList<>();
        for (Element m : MEMBERS) {
            if (!shown.contains(m)) {
                continue;
            }
            Placement own = placements.split() ? placements.get(m.id()) : null;
            if (own != null && own.placed()) {
                slots.add(slot(m, own, guiW, guiH));
            } else {
                followers.add(m);
            }
        }
        Rect box = null;
        int right = rightHeight;
        if (group.placed()) {
            int[] size = groupSize(group);
            box = placed(group.xPct(), group.yPct(), size[0], size[1], guiW, guiH);
            int cursor = group.vertical() ? box.x() : box.bottom();
            for (Element m : followers) {
                Placement look = look(m, group, placements);
                int w = scaled(barWidth(look.vertical()), look.scale());
                int h = scaled(barHeight(look.vertical()), look.scale());
                Rect r;
                if (group.vertical()) {
                    r = new Rect(cursor, box.y(), w, h);
                    cursor += w + SIDE_GAP;
                } else {
                    r = new Rect(box.right() - w, cursor - h, w, h);
                    cursor -= h + STACK_GAP;
                }
                slots.add(new Slot(m, r, look.scale(), look.vertical()));
            }
        } else {
            for (Element m : followers) {
                Placement look = look(m, group, placements);
                int w = scaled(barWidth(look.vertical()), look.scale());
                int h = scaled(barHeight(look.vertical()), look.scale());
                Rect r = docked(guiW / 2 + HALF_HOTBAR - w, guiH, right, w, h);
                right += h + 1;
                slots.add(new Slot(m, r, look.scale(), look.vertical()));
                box = box == null ? r : box.union(r);
            }
        }
        int left = leftHeight;
        if (shown.contains(Element.SHIELD)) {
            Placement p = placements.getOrDocked(Element.SHIELD.id());
            if (p.placed()) {
                slots.add(slot(Element.SHIELD, p, guiW, guiH));
            } else {
                int w = scaled(barWidth(p.vertical()), p.scale());
                int h = scaled(barHeight(p.vertical()), p.scale());
                slots.add(new Slot(Element.SHIELD, docked(guiW / 2 - HALF_HOTBAR, guiH, left, w, h), p.scale(), p.vertical()));
                left += h + 1;
            }
        }
        return new Arrangement(slots, followers.isEmpty() ? null : box, left - leftHeight, right - rightHeight);
    }

    private static Slot slot(Element e, Placement p, int guiW, int guiH) {
        int w = scaled(barWidth(p.vertical()), p.scale());
        int h = scaled(barHeight(p.vertical()), p.scale());
        return new Slot(e, placed(p.xPct(), p.yPct(), w, h, guiW, guiH), p.scale(), p.vertical());
    }

    /** The party frame: {@code rows} member blocks, by default down the left edge below the minimap. */
    public static Rect party(int guiW, int guiH, int rows, Placement p) {
        int w = scaled(RiftPartyHud.WIDTH + 4, p.scale());
        int h = scaled(Math.max(1, rows) * RiftPartyHud.BLOCK - 2, p.scale());
        if (p.placed()) {
            return placed(p.xPct(), p.yPct(), w, h, guiW, guiH);
        }
        int[] at = RiftPartyHud.place(guiH, Math.max(1, rows));
        return new Rect(at[0] - 2, Math.max(0, Math.min(at[1] - 2, guiH - h)), w, h);
    }

    /** The relic icons' box, room for every icon: by default right of the hotbar, level with its items. */
    public static Rect relicStacks(int guiW, int guiH, Placement p) {
        int along = ICON_SLOTS * ICON + (ICON_SLOTS - 1) * ICON_GAP;
        int w = scaled(p.vertical() ? ICON : along, p.scale());
        int h = scaled(p.vertical() ? along : ICON, p.scale());
        if (p.placed()) {
            return placed(p.xPct(), p.yPct(), w, h, guiW, guiH);
        }
        int x = Math.max(0, Math.min(guiW / 2 + ICONS_X, guiW - w));
        return new Rect(x, Math.max(0, guiH - ICONS_FOOT - h), w, h);
    }

    /**
     * The boss finder's one line: beside the clock (else the bar group's top, else on the right column), or
     * where it was placed. Kept on screen.
     */
    public static Rect bossFinder(int guiW, int guiH, Arrangement bars, int rightHeight, Placement p) {
        int w = scaled(FINDER_W, p.scale());
        int h = scaled(FINDER_H, p.scale());
        if (p.placed()) {
            return placed(p.xPct(), p.yPct(), w, h, guiW, guiH);
        }
        Slot clock = bars.slot(Element.CLOCK);
        Rect anchor = clock != null ? clock.rect() : bars.group();
        Rect r = anchor != null ? new Rect(anchor.right() + FINDER_GAP, anchor.y(), w, h)
                : docked(guiW / 2 + HALF_HOTBAR - w, guiH, rightHeight, w, h);
        return new Rect(Math.max(0, Math.min(r.x(), guiW - w)), Math.max(0, Math.min(r.y(), guiH - h)), w, h);
    }

    /** A cooldown slot's editor id and {@code hud.json} key: {@code cooldown_1} to {@code cooldown_8}, {@code cooldown_ultimate}. */
    public static String cooldownId(int slot) {
        return slot == SkillBook.ULTIMATE_SLOT ? "cooldown_ultimate" : "cooldown_" + (slot + 1);
    }

    public record CooldownSlot(int slot, Rect rect, float scale) {
    }

    /** The cooldown slots' boxes, and the rows the docked row took from both columns. */
    public record CooldownRow(List<CooldownSlot> slots, int rowsUsed) {
        public @Nullable CooldownSlot slot(int slot) {
            for (CooldownSlot s : slots) {
                if (s.slot() == slot) {
                    return s;
                }
            }
            return null;
        }
    }

    /**
     * The shown cooldown slots, each at its own scale. Docked ones form one row centred over the hotbar, in slot
     * order, closed up, bottom-aligned one row above the taller column; the row takes its height from both columns,
     * so the item name and the action bar rise over it. A placed slot sits where it was put.
     */
    public static CooldownRow cooldowns(int guiW, int guiH, int leftHeight, int rightHeight, List<Integer> shown,
                                        HudPlacements placements) {
        List<CooldownSlot> out = new ArrayList<>();
        List<Integer> docked = new ArrayList<>();
        int total = 0;
        int tallest = 0;
        for (int slot : shown) {
            Placement p = placements.getOrDocked(cooldownId(slot));
            int size = scaled(COOLDOWN, p.scale());
            if (p.placed()) {
                out.add(new CooldownSlot(slot, placed(p.xPct(), p.yPct(), size, size, guiW, guiH), p.scale()));
            } else {
                docked.add(slot);
                total += size + (docked.size() > 1 ? COOLDOWN_GAP : 0);
                tallest = Math.max(tallest, size);
            }
        }
        if (docked.isEmpty()) {
            return new CooldownRow(out, 0);
        }
        int x = Math.max(0, guiW / 2 - total / 2);
        int bottom = guiH - Math.max(leftHeight, rightHeight) + ROW;
        for (int slot : docked) {
            Placement p = placements.getOrDocked(cooldownId(slot));
            int size = scaled(COOLDOWN, p.scale());
            out.add(new CooldownSlot(slot, new Rect(Math.min(x, Math.max(0, guiW - size)), Math.max(0, bottom - size),
                    size, size), p.scale()));
            x += size + COOLDOWN_GAP;
        }
        return new CooldownRow(out, tallest + 1);
    }

    /** The n-th shown icon's top-left in the unscaled box: shown icons close up from the left, or from the top. */
    static int[] iconAt(int n, boolean vertical) {
        int along = n * (ICON + ICON_GAP);
        return vertical ? new int[] {0, along} : new int[] {along, 0};
    }

    /** One resize step, kept in range. */
    static float stepScale(float scale, int direction) {
        return clampScale(scale + direction * SCALE_STEP);
    }

    /**
     * Label and value x on a horizontal bar's text line, from the box's left: value flush right, label flush
     * left unless the two would touch, then the label moves left past the box.
     */
    static int[] textX(int labelW, int valueW) {
        int value = BAR_W - valueW;
        return new int[] {Math.min(0, value - TEXT_GAP - labelW), value};
    }
}
