package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.client.HudLayout.Element;
import com.cyberday1.riftfall.client.HudLayout.Rect;
import com.cyberday1.riftfall.client.HudPlacements.Placement;
import com.cyberday1.riftfall.gear.RelicStackIcons;
import com.cyberday1.riftfall.network.RiftPartyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NeoOrigins' HUD editor for Riftfall's own elements (the bars, the party frame, the relic stack icons, the boss
 * finder, each skill cooldown slot): drag to move, Rotate, Split/Merge the bar group, Hide/Show a cooldown slot,
 * -/+ to resize, Reset. Edits a copy of the placements
 * and saves it on close. Vanilla bars are never touched.
 */
public final class RiftHudEditorScreen extends Screen {

    private static final String KEY = "screen.riftfall.hud_editor";
    private static final int GRAB_PAD = 3;
    private static final int BACKDROP = 0xCC060610;
    private static final int BOX = 0x40000000;
    private static final int BORDER = 0xFF555555;
    private static final int BORDER_DRAG = 0xFF55FF55;
    private static final int BORDER_SELECTED = 0xFFFFD75A;
    private static final int HINT = 0xFFB0B0B0;
    private static final EnumSet<Element> BARS = EnumSet.of(Element.MANA, Element.CHARGE, Element.CLOCK, Element.SHIELD);

    /** One draggable thing: the group, a split member, the shield, the party frame or the relic stack icons. */
    private record Handle(String id, Rect rect, boolean turns) {
    }

    private final HudPlacements working = HudPlacements.instance().copy();
    private String selected = HudLayout.GROUP;
    private @Nullable String dragging;
    private double grabX;
    private double grabY;
    private Button rotate;
    private Button split;
    private Button hide;

    public RiftHudEditorScreen() {
        super(Component.translatable(KEY));
    }

    /** Client tick: the Edit HUD key opens the editor when nothing else is open. */
    static void tick(Minecraft client) {
        if (RiftKeys.drain(RiftKeys.EDIT_HUD) > 0 && client.screen == null && client.player != null) {
            client.setScreen(new RiftHudEditorScreen());
        }
    }

    @Override
    protected void init() {
        int x = width / 2 - 182;
        rotate = addRenderableWidget(Button.builder(Component.translatable(KEY + ".rotate"), b -> turn())
                .bounds(x, 6, 60, 20).build());
        split = addRenderableWidget(Button.builder(splitLabel(), b -> toggleSplit()).bounds(x + 64, 6, 60, 20).build());
        hide = addRenderableWidget(Button.builder(hideLabel(), b -> toggleHidden()).bounds(x + 128, 6, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + ".reset"), b -> {
            working.clear();
            selected = HudLayout.GROUP;
            split.setMessage(splitLabel());
        }).bounds(x + 192, 6, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> resize(-1)).bounds(x + 256, 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(1)).bounds(x + 280, 6, 20, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x + 304, 6, 60, 20).build());
    }

    private Component hideLabel() {
        return Component.translatable(KEY + (working.hidden(selected) ? ".show" : ".hide"));
    }

    /** Only a cooldown slot can be turned off so far. */
    private boolean hideable(String id) {
        return id.startsWith("cooldown_");
    }

    private void toggleHidden() {
        if (hideable(selected)) {
            working.hide(selected, !working.hidden(selected));
        }
    }

    /** The equipped slots, or the sample row when none is; hidden ones too, so they can be shown again. */
    private static List<SkillCooldownHud.Face> cooldownFaces(float partialTick) {
        List<SkillCooldownHud.Face> live = SkillCooldownHud.faces(Minecraft.getInstance(), partialTick);
        return live.isEmpty() ? SkillCooldownHud.sample() : live;
    }

    /** Docked above the bars as the HUD would put them with every bar shown. */
    private HudLayout.CooldownRow cooldownRow(HudLayout.Arrangement layout, List<SkillCooldownHud.Face> faces) {
        return HudLayout.cooldowns(width, height, RiftHudBars.lastLeft() + layout.leftUsed(),
                RiftHudBars.lastRight() + layout.rightUsed(), faces.stream().map(SkillCooldownHud.Face::slot).toList(),
                working);
    }

    private Component splitLabel() {
        return Component.translatable(KEY + (working.split() ? ".merge" : ".split"));
    }

    private HudLayout.Arrangement arrangement() {
        return HudLayout.arrange(width, height, RiftHudBars.lastLeft(), RiftHudBars.lastRight(), BARS, working);
    }

    private List<Handle> handles(HudLayout.Arrangement layout) {
        List<Handle> out = new ArrayList<>();
        if (!working.split() && layout.group() != null) {
            out.add(new Handle(HudLayout.GROUP, layout.group(), true));
        }
        for (HudLayout.Slot slot : layout.slots()) {
            if (working.split() || !slot.element().grouped()) {
                out.add(new Handle(slot.element().id(), slot.rect(), true));
            }
        }
        out.add(new Handle(Element.PARTY.id(), partyBox(), false));
        out.add(new Handle(Element.RELIC_STACKS.id(), relicBox(), true));
        out.add(new Handle(Element.BOSS_FINDER.id(), finderBox(layout), false));
        for (HudLayout.CooldownSlot slot : cooldownRow(layout, cooldownFaces(0.0F)).slots()) {
            out.add(new Handle(HudLayout.cooldownId(slot.slot()), slot.rect(), false));
        }
        return out;
    }

    private Rect finderBox(HudLayout.Arrangement layout) {
        return HudLayout.bossFinder(width, height, layout, RiftHudBars.lastRight() + layout.rightUsed(),
                working.getOrDocked(Element.BOSS_FINDER.id()));
    }

    private Rect relicBox() {
        return HudLayout.relicStacks(width, height, working.getOrDocked(Element.RELIC_STACKS.id()));
    }

    /** The real icons, or a full sample so the row can be placed before any comes in. */
    private static List<RelicStackIcons.Icon> relicIcons(float partialTick) {
        List<RelicStackIcons.Icon> live = RelicStackHud.icons(Minecraft.getInstance(), partialTick);
        return live.isEmpty() ? RelicStackHud.SAMPLE : live;
    }

    private Rect partyBox() {
        return HudLayout.party(width, height, partyRows().size(), working.getOrDocked(Element.PARTY.id()));
    }

    /** The real party, or one sample member so the frame can be placed before anyone joins. */
    private List<RiftPartyPayload.Member> partyRows() {
        Minecraft client = Minecraft.getInstance();
        List<RiftPartyPayload.Member> rows = client.player == null ? List.of()
                : RiftPartyHud.others(RiftPartyHud.latest(), client.player.getUUID());
        if (!rows.isEmpty()) {
            return rows;
        }
        long now = client.level == null ? 0L : client.level.getGameTime();
        return List.of(new RiftPartyPayload.Member(new UUID(0L, 0L), I18n.get(Element.PARTY.labelKey()), true,
                14.0F, 20.0F, 6.0, now, 150.0, 300.0));
    }

    /** What a placement starts from when the player first moves or turns something. */
    private Placement base(String id) {
        Placement group = working.getOrDocked(HudLayout.GROUP);
        for (Element m : HudLayout.MEMBERS) {
            if (m.id().equals(id)) {
                return HudLayout.look(m, group, working);
            }
        }
        return working.getOrDocked(id);
    }

    private @Nullable Handle handle(String id) {
        for (Handle h : handles(arrangement())) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return null;
    }

    /** Turns the selected element and pins it where it stands: a docked vertical bar would tower over the column. */
    private void turn() {
        Handle h = handle(selected);
        if (h != null && h.turns()) {
            working.set(h.id(), base(h.id()).turned().at(h.rect().x() / (float) width, h.rect().y() / (float) height));
        }
    }

    private void resize(int direction) {
        Placement p = base(selected);
        working.set(selected, p.scaled(HudLayout.stepScale(p.scale(), direction)));
    }

    private void toggleSplit() {
        working.split(!working.split());
        boolean member = HudLayout.MEMBERS.stream().anyMatch(m -> m.id().equals(selected));
        if (working.split() && selected.equals(HudLayout.GROUP)) {
            selected = Element.MANA.id();
        } else if (!working.split() && member) {
            selected = HudLayout.GROUP;
        }
        split.setMessage(splitLabel());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Our own backdrop in render(); no blur, so the bars read as they will over the world.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
        Minecraft client = Minecraft.getInstance();
        HudLayout.Arrangement layout = arrangement();
        List<Handle> handles = handles(layout);
        for (Handle h : handles) {
            int border = h.id().equals(dragging) ? BORDER_DRAG : h.id().equals(selected) ? BORDER_SELECTED : BORDER;
            Rect r = h.rect();
            graphics.fill(r.x() - 1, r.y() - 1, r.right() + 1, r.bottom() + 1, border);
            graphics.fill(r.x(), r.y(), r.right(), r.bottom(), BOX);
        }
        Map<Element, HudBarRenderer.Reading> live = RiftHudBars.readings(client);
        double ticks = client.level == null ? 0.0 : client.level.getGameTime() + partialTick;
        double guiScale = client.getWindow().getGuiScale();
        for (HudLayout.Slot slot : layout.slots()) {
            HudBarRenderer.Reading r = live.getOrDefault(slot.element(), sample(slot.element()));
            HudBarRenderer.draw(graphics, font, slot, r, ticks, guiScale);
        }
        long now = client.level == null ? 0L : client.level.getGameTime();
        RiftPartyHud.draw(graphics, client, partyBox(), working.getOrDocked(Element.PARTY.id()).scale(), partyRows(), now);
        Rect relic = relicBox();
        RelicStackHud.draw(graphics, font, relic, working.getOrDocked(Element.RELIC_STACKS.id()), relicIcons(partialTick));
        // The icons carry no text of their own, so the editor names the row: above it, or under it at the top edge.
        Component label = Component.translatable(Element.RELIC_STACKS.labelKey());
        int labelX = Math.max(0, Math.min(relic.x(), width - font.width(label)));
        int labelY = relic.y() >= 11 ? relic.y() - 11 : relic.bottom() + 2;
        graphics.drawString(font, label, labelX, labelY, HINT, true);

        BossFinderHud.Reading finder = BossFinderHud.reading(client, partialTick);
        BossFinderHud.draw(graphics, font, finderBox(layout), working.getOrDocked(Element.BOSS_FINDER.id()).scale(),
                finder != null ? finder : BossFinderHud.SAMPLE);

        List<SkillCooldownHud.Face> faces = cooldownFaces(partialTick);
        HudLayout.CooldownRow row = cooldownRow(layout, faces);
        for (SkillCooldownHud.Face face : faces) {
            HudLayout.CooldownSlot at = row.slot(face.slot());
            if (at != null) {
                SkillCooldownHud.draw(graphics, font, at.rect(), at.scale(), face,
                        working.hidden(HudLayout.cooldownId(face.slot())));
            }
        }

        rotate.active = handles.stream().anyMatch(h -> h.id().equals(selected) && h.turns());
        hide.active = hideable(selected);
        hide.setMessage(hideLabel());
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".hint"), width / 2, 30, HINT);
        int pct = Math.round(base(selected).scale() * 100.0F);
        graphics.drawCenteredString(font, Component.translatable(KEY + ".scale", pct), width / 2, 42, 0xFFFFFFFF);
    }

    /** A bar the server has not sent yet, so the editor can still show and place it. */
    static HudBarRenderer.Reading sample(Element element) {
        return switch (element) {
            case CHARGE -> RangedChargeHud.reading(8.0, 10.0, false);
            case CLOCK -> RiftClockHud.reading(30 * 60 * 20, 45 * 60 * 20);
            case SHIELD -> RiftShieldHud.reading(10.0, 20.0);
            default -> RiftManaHud.reading(120.0, 200.0);
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        List<Handle> handles = handles(arrangement());
        for (int i = handles.size() - 1; i >= 0; i--) {
            Handle h = handles.get(i);
            if (h.rect().contains(mouseX, mouseY, GRAB_PAD)) {
                selected = h.id();
                dragging = h.id();
                grabX = mouseX - h.rect().x();
                grabY = mouseY - h.rect().y();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null && button == 0) {
            Handle h = handle(dragging);
            if (h != null) {
                int x = (int) Math.round(Math.max(0, Math.min(mouseX - grabX, width - h.rect().w())));
                int y = (int) Math.round(Math.max(0, Math.min(mouseY - grabY, height - h.rect().h())));
                working.set(h.id(), base(h.id()).at(x / (float) width, y / (float) height));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null && button == 0) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        HudPlacements saved = HudPlacements.instance();
        saved.replaceWith(working);
        saved.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
