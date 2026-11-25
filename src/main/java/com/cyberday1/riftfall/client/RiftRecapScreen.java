package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.network.RiftClaimRequestPayload;
import com.cyberday1.riftfall.network.RiftRecapPayload;
import com.cyberday1.riftfall.rift.RiftClaimView;
import com.cyberday1.riftfall.rift.RiftRecap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The rift recap and run history, in the Codex's frame. Left page: every recorded run, newest first.
 * Right page: the chosen run's recap. Opened by the server after a run ends, with the newest run
 * chosen, or by {@code /riftfall recap} with none. A fresh recap also shows held gear handed back,
 * under the list, with Claim the rest while any is still held.
 */
public class RiftRecapScreen extends ProgressionScreen {
    private static final int L = ProgressionFrame.LEFT;
    private static final int R = ProgressionFrame.RIGHT;
    private static final int T = ProgressionFrame.TOP;
    private static final int W = ProgressionFrame.PAGE_WIDTH;
    private static final int LIST_TOP = T + 26;
    private static final int ROW = 22;
    private static final int LIST_BOTTOM = ProgressionFrame.BOTTOM - 12;
    private static final int PITCH = 10;
    private static final int NO_RUN = -1;
    private static final int TOOLTIP_CAP = 16;

    private final List<RiftRecap> runs;
    private final boolean fresh;
    private int scroll;
    private int selected;
    /** Where the list stops this frame: higher while the gear block shows under it. */
    private int listBottom = LIST_BOTTOM;

    public RiftRecapScreen(RiftRecapPayload payload) {
        super(Component.translatable(RecapText.key("title")));
        this.runs = payload.runs();
        this.fresh = payload.fresh();
        this.selected = payload.fresh() && !runs.isEmpty() ? 0 : NO_RUN;
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable(RecapText.key(key), args);
    }

    private static MutableComponent line(RecapText.Line line) {
        Object[] args = line.args().stream()
                .map(arg -> arg instanceof RecapText.Rarity rarity ? rarityName(rarity.index()) : arg).toArray();
        return Component.translatable(RecapText.key(line.key()), args);
    }

    private static MutableComponent line(RecapText.Line line, List<ItemStack> back) {
        Object[] args = line.args().stream().map(arg -> arg instanceof RecapText.Names ? names(back) : arg).toArray();
        return Component.translatable(RecapText.key(line.key()), args);
    }

    private static Component names(List<ItemStack> back) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < Math.min(RecapText.NAMED, back.size()); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(back.get(i).getHoverName());
        }
        return joined;
    }

    private static Component rarityName(int index) {
        return Component.translatableWithFallback(GunGrade.rarityKey(index), GunGrade.rarityName(index));
    }

    private int visibleRows() {
        return (listBottom - LIST_TOP) / ROW;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, runs.size() - visibleRows()), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    protected void renderPages(GuiGraphics graphics, float partialTick) {
        text(graphics, bold(line("heading")), L, T, CodexStyle.ACCENT);
        text(graphics, line(RecapText.recorded(runs.size())), L, T + 11, CodexStyle.MUTED);
        dotted(graphics, L, L + W, T + 22, CodexStyle.DOTS);
        RiftClaimView claim = fresh ? RiftRecapClient.claim() : RiftClaimView.NONE;
        listBottom = claim.shown() ? LIST_BOTTOM - claimHeight(claim) : LIST_BOTTOM;
        if (claim.shown()) {
            claim(graphics, claim, listBottom);
        }
        if (runs.isEmpty()) {
            wrapped(graphics, line("empty"), L, LIST_TOP, W, PITCH, 4, CodexStyle.MUTED);
        } else {
            list(graphics);
        }
        if (selected >= 0 && selected < runs.size()) {
            recap(graphics, runs.get(selected));
        } else if (!runs.isEmpty()) {
            wrapped(graphics, line("pick_hint"), R, T, W, PITCH, 4, CodexStyle.MUTED);
        }
        button(graphics, R + W - 60, ProgressionFrame.BOTTOM - 18, 60, line("close"), true, this::onClose);
    }

    private void list(GuiGraphics graphics) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, runs.size() - visibleRows())));
        int shown = Math.min(visibleRows(), runs.size() - scroll);
        long now = System.currentTimeMillis();
        for (int i = 0; i < shown; i++) {
            row(graphics, scroll + i, LIST_TOP + i * ROW, now);
        }
        if (scroll + shown < runs.size()) {
            text(graphics, line("scroll_more"), L, listBottom + 2, CodexStyle.DIM);
        }
    }

    private void row(GuiGraphics graphics, int index, int y, long now) {
        RiftRecap run = runs.get(index);
        boolean hover = hot(L, y, L + W, y + ROW - 1, button -> {
            if (button != PRIMARY) {
                return false;
            }
            selected = index;
            return true;
        });
        if (index == selected || hover) {
            graphics.fill(L, y, L + W, y + ROW - 1, index == selected ? CodexStyle.SELECTED : CodexStyle.WELL_LIGHT);
        }
        text(graphics, line(RecapText.rowOutcome(run)), L + 4, y + 2, outcomeColour(run));
        textRight(graphics, line("xp_amount", RecapText.xp(run.xpKept())), L + W - 4, y + 2, CodexStyle.INK);
        text(graphics, line(RecapText.tier(run)), L + 4, y + 12, CodexStyle.DIM);
        textRight(graphics, line(RecapText.age(run.endedAt(), now)), L + W - 4, y + 12, CodexStyle.DIM);
    }

    private static int outcomeColour(RiftRecap run) {
        return switch (run.outcome()) {
            case CLEARED -> CodexStyle.GOOD;
            case LEFT_EARLY -> CodexStyle.MUTED;
            case FELL, TIMED_OUT -> CodexStyle.BAD;
        };
    }

    private void recap(GuiGraphics graphics, RiftRecap run) {
        text(graphics, bold(line(RecapText.headline(run))), R, T, outcomeColour(run));
        text(graphics, line(RecapText.tierAndTime(run)), R, T + 11, CodexStyle.MUTED);
        text(graphics, line(RecapText.age(run.endedAt(), System.currentTimeMillis())), R, T + 21, CodexStyle.DIM);
        dotted(graphics, R, R + W, T + 32, CodexStyle.DOTS);

        int y = T + 37;
        text(graphics, bold(line("xp_heading")), R, y, CodexStyle.ACCENT);
        y += PITCH + 1;
        pair(graphics, line("xp_enemies"), line("xp_amount", RecapText.xp(run.xpEnemies())), y, CodexStyle.MUTED);
        y += PITCH;
        pair(graphics, line("xp_clear"), line(RecapText.clearValue(run)), y, CodexStyle.MUTED);
        y += PITCH;
        pair(graphics, line(RecapText.keptLabel(run)), line("xp_amount", RecapText.xp(run.xpKept())), y,
                run.reduced() ? CodexStyle.WARN : CodexStyle.ACCENT);
        y += PITCH;
        text(graphics, fit(line(RecapText.levels(run)), W), R, y, run.levelsGained() > 0 ? CodexStyle.GOOD : CodexStyle.MUTED);
        y += PITCH + 1;
        RecapText.Line note = RecapText.reducedNote(run);
        if (note != null) {
            y = wrapped(graphics, line(note), R, y, W, PITCH, 3, CodexStyle.DIM);
        }

        y += 5;
        text(graphics, bold(line("run_heading")), R, y, CodexStyle.ACCENT);
        y += PITCH + 1;
        for (RecapText.Line fact : List.of(RecapText.kills(run), RecapText.boss(run), RecapText.barrels(run),
                RecapText.shards(run), RecapText.gear(run))) {
            text(graphics, fit(line(fact), W), R, y, CodexStyle.INK);
            y += PITCH;
        }
        RecapText.Line best = RecapText.bestGear(run);
        if (best != null) {
            text(graphics, fit(line(best), W), R, y, CodexStyle.INK);
        }
    }

    /** A label on the left of the right page and its value against the right edge. */
    private void pair(GuiGraphics graphics, Component label, Component value, int y, int valueColour) {
        text(graphics, fit(label, W - font.width(value) - 6), R, y, CodexStyle.INK);
        textRight(graphics, value, R + W, y, valueColour);
    }

    /** Height of the held-gear block: rule, heading, what came back, what is still held and the button. */
    private int claimHeight(RiftClaimView claim) {
        int h = 12 + 4 + 11;
        if (!claim.back().isEmpty()) {
            h += Math.min(2, lineCount(line(RecapText.claimBack(claim.back().size()), claim.back()), W)) * PITCH;
        }
        if (claim.left() > 0) {
            h += Math.min(3, lineCount(line(RecapText.claimLeft(claim)), W)) * PITCH + 13;
        }
        return h;
    }

    /** Held gear handed back as this recap arrived, and Claim the rest while some is still held. */
    private void claim(GuiGraphics graphics, RiftClaimView claim, int top) {
        int y = top + 12;
        dotted(graphics, L, L + W, y, CodexStyle.DOTS);
        y += 4;
        text(graphics, bold(line("claim_heading")), L, y, CodexStyle.ACCENT);
        y += 11;
        if (!claim.back().isEmpty()) {
            int from = y;
            y = wrapped(graphics, line(RecapText.claimBack(claim.back().size()), claim.back()), L, y, W, PITCH, 2,
                    CodexStyle.GOOD);
            if (over(L, from, L + W, y)) {
                List<Component> lines = new ArrayList<>();
                // Capped so a full inventory's worth stays on screen.
                claim.back().stream().limit(TOOLTIP_CAP).forEach(stack -> lines.add(stack.getCount() > 1
                        ? Component.empty().append(stack.getHoverName()).append(" x" + stack.getCount())
                        : stack.getHoverName()));
                if (claim.back().size() > TOOLTIP_CAP) {
                    lines.add(Component.literal("..."));
                }
                tooltip(lines);
            }
        }
        if (claim.left() > 0) {
            y = wrapped(graphics, line(RecapText.claimLeft(claim)), L, y, W, PITCH, 3, CodexStyle.WARN);
            tagButton(graphics, line("claim_rest"), L, y + 2, W,
                    () -> PacketDistributor.sendToServer(RiftClaimRequestPayload.INSTANCE));
        }
    }
}
