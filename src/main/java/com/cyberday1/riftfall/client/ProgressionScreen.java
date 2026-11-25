package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The stellar Rift Codex shell shared by progression pages, with one navigation tab
 * per {@link ProgressionTab} on the right edge. Pages draw in book space ({@link ProgressionFrame}), so
 * the whole book scales down on a screen too small for it. Clicks are immediate mode: a page names a
 * region with {@link #hot} while drawing, and the last frame's regions answer the next click.
 */
public abstract class ProgressionScreen extends Screen {

    /** What a click region does with a button; true when it used the click. */
    @FunctionalInterface
    protected interface Click {
        boolean on(int button);
    }

    private record Hot(int x0, int y0, int x1, int y1, Click click) {
    }

    protected static final int PRIMARY = 0;
    protected static final int SECONDARY = 1;

    protected final @Nullable ProgressionTab tab;
    private final List<Hot> hots = new ArrayList<>();
    private final List<Hot> drawing = new ArrayList<>();
    private @Nullable List<Component> tooltip;
    private float scale = 1.0F;
    private int left;
    private int top;
    /** The mouse in book space, for hover. */
    protected double mouseBookX;
    protected double mouseBookY;

    protected ProgressionScreen(ProgressionTab tab) {
        super(Component.translatable(tab.titleKey()));
        this.tab = tab;
    }

    /** A spread in the book's frame that hangs off no bookmark; {@link #tab} is null. */
    protected ProgressionScreen(Component title) {
        super(title);
        this.tab = null;
    }

    /** The page for a tab. No default: a tab without a page is a compile error, not a dead bookmark. */
    public static Screen open(ProgressionTab tab) {
        return switch (tab) {
            case CHARACTER -> new CharacterSheetScreen();
            case SKILLS -> new SkillsScreen();
            case CLASSES -> new ClassSelectScreen();
            case NOTES -> new RecoveredNotesScreen();
            case SOCIAL -> new SocialScreen();
        };
    }

    @Override
    protected void init() {
        super.init();
        scale = ProgressionFrame.scale(width, height);
        left = ProgressionFrame.left(width, scale);
        top = ProgressionFrame.top(height, scale);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        mouseBookX = (mouseX - left) / scale;
        mouseBookY = (mouseY - top) / scale;
        tooltip = null;
        drawing.clear();
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        bookmarks(graphics);
        cover(graphics);
        renderPages(graphics, partialTick);
        graphics.pose().popPose();
        renderScreenSpace(graphics, mouseX, mouseY);
        hots.clear();
        hots.addAll(drawing);
        if (tooltip != null && !tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** Both pages, in book space. */
    protected abstract void renderPages(GuiGraphics graphics, float partialTick);

    /** Anything that must draw in screen space, such as an entity render, which scissors in screen pixels. */
    protected void renderScreenSpace(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    protected int screenX(double bookX) {
        return Math.round(left + (float) bookX * scale);
    }

    protected int screenY(double bookY) {
        return Math.round(top + (float) bookY * scale);
    }

    protected float bookScale() {
        return scale;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = (mouseX - left) / scale;
        double y = (mouseY - top) / scale;
        ProgressionTab[] tabs = ProgressionTab.values();
        int index = ProgressionFrame.bookmarkAt(x, y, tabs.length);
        if (index != ProgressionFrame.NO_TAB) {
            if (tabs[index] != tab && minecraft != null) {
                minecraft.setScreen(open(tabs[index]));
            }
            return true;
        }
        for (int i = hots.size() - 1; i >= 0; i--) {
            Hot hot = hots.get(i);
            if (x >= hot.x0 && x < hot.x1 && y >= hot.y0 && y < hot.y1 && hot.click.on(button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void switchTo(ProgressionTab next) {
        if (minecraft != null && next != tab) {
            minecraft.setScreen(open(next));
        }
    }

    /** Registers a click region for the next click, and answers whether the mouse is over it now. */
    protected boolean hot(int x0, int y0, int x1, int y1, Click click) {
        drawing.add(new Hot(x0, y0, x1, y1, click));
        return over(x0, y0, x1, y1);
    }

    protected boolean over(int x0, int y0, int x1, int y1) {
        return mouseBookX >= x0 && mouseBookX < x1 && mouseBookY >= y0 && mouseBookY < y1;
    }

    /** A vanilla tooltip at the mouse, drawn after the book. */
    protected void tooltip(List<Component> lines) {
        tooltip = lines;
    }

    // -- the book --------------------------------------------------------------------------------

    private void bookmarks(GuiGraphics graphics) {
        ProgressionTab[] tabs = ProgressionTab.values();
        for (int index = 0; index < tabs.length; index++) {
            int x = ProgressionFrame.BOOKMARK_X;
            int y = ProgressionFrame.bookmarkY(index);
            boolean current = tabs[index] == tab;
            StellarWorkshop.slab(graphics, x, y, ProgressionFrame.BOOKMARK_WIDTH,
                    ProgressionFrame.BOOKMARK_HEIGHT, current ? CodexStyle.SELECTED : CodexStyle.BOOKMARK_IDLE);
            if (current) graphics.fill(x + ProgressionFrame.BOOKMARK_WIDTH - 2, y + 6,
                    x + ProgressionFrame.BOOKMARK_WIDTH - 1, y + ProgressionFrame.BOOKMARK_HEIGHT - 6, CodexStyle.ACCENT);
            switch (tabs[index]) {
                case CHARACTER -> {
                    if (minecraft != null && minecraft.player != null) {
                        PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin(), x + 4, y + 6, 16);
                    }
                }
                case SKILLS -> graphics.renderItem(new ItemStack(Items.WRITABLE_BOOK), x + 4, y + 6);
                case CLASSES -> graphics.renderItem(new ItemStack(Items.IRON_SWORD), x + 4, y + 6);
                case NOTES -> graphics.renderItem(new ItemStack(Items.PAPER), x + 4, y + 6);
                case SOCIAL -> {
                    graphics.renderItem(new ItemStack(Items.WHITE_BANNER), x + 4, y + 6);
                    badge(graphics, x + ProgressionFrame.BOOKMARK_WIDTH - 9, y + 2, SocialClient.badge().total());
                }
            }
            if (mouseBookX >= ProgressionFrame.WIDTH && over(x, y, x + ProgressionFrame.BOOKMARK_WIDTH,
                    y + ProgressionFrame.BOOKMARK_HEIGHT)) {
                List<Component> tip = new ArrayList<>(List.of(Component.translatable(tabs[index].titleKey())));
                int waiting = tabs[index] == ProgressionTab.SOCIAL ? SocialClient.badge().total() : 0;
                if (waiting > 0) {
                    tip.add(Component.translatable(ProgressionTab.SOCIAL.key(waiting == 1 ? "badge_one" : "badge"), waiting)
                            .withStyle(style -> style.withColor(CodexStyle.ACCENT)));
                }
                tooltip(tip);
            }
        }
    }

    /** A count in a small gold box, drawn over items; nothing when the count is zero. */
    protected void badge(GuiGraphics graphics, int x, int y, int count) {
        if (count <= 0) {
            return;
        }
        String label = count > 9 ? "9+" : Integer.toString(count);
        int w = Math.max(8, font.width(label) + 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(x + 8 - w, y, x + 8, y + 9, CodexStyle.ACCENT);
        graphics.drawString(font, label, x + 8 - w + (w - font.width(label) + 1) / 2, y + 1, CodexStyle.ON_ACCENT, false);
        graphics.pose().popPose();
    }

    private void cover(GuiGraphics graphics) {
        int w = ProgressionFrame.WIDTH;
        int h = ProgressionFrame.HEIGHT;
        StellarWorkshop.surface(graphics, 0, 0, w, h);
        // Retain page coordinates and hit testing; the gutter is now open space with a broken gold seam.
        graphics.fill(189, 18, 190, 74, 0xFF514B4B);
        graphics.fill(191, 88, 192, 136, 0xFF343D52);
        graphics.fill(188, 155, 189, 211, 0xFF514B4B);
        StellarWorkshop.star(graphics, 186, 8, 0xFFDEC68D);
        StellarWorkshop.star(graphics, w - 8, h - 10, 0xFF869EBB);
    }

    // -- drawing helpers, all in book space ----------------------------------------------------------

    protected static Component bold(Component line) {
        return line.copy().withStyle(style -> style.withBold(true));
    }

    /** Ink text: the book convention is no shadow. Returns the x after it. */
    protected int text(GuiGraphics graphics, Component line, int x, int y, int colour) {
        graphics.drawString(font, line, x, y, colour, false);
        return x + font.width(line);
    }

    protected int text(GuiGraphics graphics, String line, int x, int y, int colour) {
        return text(graphics, Component.literal(line), x, y, colour);
    }

    protected void textRight(GuiGraphics graphics, Component line, int right, int y, int colour) {
        graphics.drawString(font, line, right - font.width(line), y, colour, false);
    }

    protected void textRight(GuiGraphics graphics, String line, int right, int y, int colour) {
        textRight(graphics, Component.literal(line), right, y, colour);
    }

    protected void textCenter(GuiGraphics graphics, Component line, int centre, int y, int colour) {
        graphics.drawString(font, line, centre - font.width(line) / 2, y, colour, false);
    }

    /** Wrapped to {@code width}, {@code pitch} pixels a line, at most {@code maxLines}. Returns the y after it. */
    protected int wrapped(GuiGraphics graphics, Component line, int x, int y, int width, int pitch, int maxLines,
                          int colour) {
        List<FormattedCharSequence> lines = font.split(line, width);
        int shown = Math.min(lines.size(), maxLines);
        for (int i = 0; i < shown; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * pitch, colour, false);
        }
        return y + shown * pitch;
    }

    protected int lineCount(Component line, int width) {
        return font.split(line, width).size();
    }

    /** Cuts a line to fit {@code width}, ending "..". */
    protected Component fit(Component line, int width) {
        if (font.width(line) <= width) {
            return line;
        }
        FormattedText head = font.substrByWidth(line, width - font.width(".."));
        MutableComponent out = Component.literal(head.getString()).withStyle(line.getStyle());
        return out.append("..");
    }

    protected static void frame(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0, x0 + 1, y1, colour);
        graphics.fill(x1 - 1, y0, x1, y1, colour);
    }

    protected static void dotted(GuiGraphics graphics, int x0, int x1, int y, int colour) {
        for (int x = x0; x < x1; x += 2) {
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    /** A gently stepped stellar slab; its interaction bounds remain rectangular. */
    protected static void well(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        StellarWorkshop.slab(graphics, x0, y0, x1 - x0, y1 - y0, CodexStyle.WELL);
    }

    protected static void slot(GuiGraphics graphics, int x, int y, int size, int fill) {
        well(graphics, x, y, x + size, y + size);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, fill);
    }

    /** A 7x8 padlock. */
    protected static void padlock(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y, x + 6, y + 1, CodexStyle.LOCK);
        graphics.fill(x + 1, y, x + 2, y + 4, CodexStyle.LOCK);
        graphics.fill(x + 5, y, x + 6, y + 4, CodexStyle.LOCK);
        graphics.fill(x, y + 3, x + 7, y + 8, CodexStyle.LOCK_BODY);
        graphics.fill(x + 3, y + 5, x + 4, y + 7, CodexStyle.LOCK);
    }

    protected static void check(GuiGraphics graphics, int x, int y, boolean ok) {
        if (ok) {
            int[][] steps = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}};
            for (int[] step : steps) {
                graphics.fill(x + step[0], y + step[1], x + step[0] + 1, y + step[1] + 2, CodexStyle.GOOD);
            }
        } else {
            for (int k = 0; k < 6; k++) {
                graphics.fill(x + k, y + k + 1, x + k + 1, y + k + 2, CodexStyle.BAD);
                graphics.fill(x + 5 - k, y + k + 1, x + 6 - k, y + k + 2, CodexStyle.BAD);
            }
        }
    }

    /** A filled tag with light text, such as a lever's or a primary's. Returns the x after it. */
    protected int tag(GuiGraphics graphics, Component label, int x, int y, int colour) {
        int w = font.width(label) + 3;
        graphics.fill(x, y, x + w, y + 9, colour);
        graphics.drawString(font, label, x + 2, y + 1, CodexStyle.ON_TAG, false);
        return x + w;
    }

    /**
     * A filled tag that acts, like a primary's +: the sheet's Ascend and the unclassed pages' Choose a class.
     * The label is cut to {@code maxWidth} with the padding. Registers a primary click.
     */
    protected void tagButton(GuiGraphics graphics, Component label, int x, int y, int maxWidth, Runnable action) {
        Component shown = fit(label, maxWidth - 6);
        int end = x + font.width(shown) + 6;
        boolean hover = hot(x, y - 1, end, y + 9, button -> {
            if (button != PRIMARY) {
                return false;
            }
            action.run();
            return true;
        });
        graphics.fill(x, y - 1, end, y + 9, hover ? CodexStyle.ACCENT_HOT : CodexStyle.ACCENT);
        graphics.drawString(font, shown, x + 3, y, CodexStyle.ON_TAG, false);
    }

    /** A gold-accented slab button, or a muted one when {@code enabled} is false. Registers its click. */
    protected void button(GuiGraphics graphics, int x, int y, int w, Component text, boolean enabled, Runnable action) {
        // Cut to the slab, as every other line in the book is: a long translation must not run over the edges.
        Component label = fit(text, w - 6);
        boolean hover = enabled && hot(x, y, x + w, y + 18, button -> {
            if (button == PRIMARY) {
                action.run();
                return true;
            }
            return false;
        });
        int h = 18;
        StellarWorkshop.slab(graphics, x, y, w, h,
                enabled ? (hover ? CodexStyle.SELECTED : 0xFF2C2B3B) : CodexStyle.BUTTON_OFF);
        graphics.fill(x + 7, y + h - 1, x + w - 8, y + h,
                enabled ? 0xFFAA9261 : 0xFF635B50);
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - 8) / 2 + 1;
        graphics.drawString(font, label, tx, ty, enabled ? CodexStyle.INK : CodexStyle.BUTTON_OFF_TEXT, false);
    }

    /** Artwork is independent of rank; locked entries retain the same silhouette, dimmed. */
    protected void skillIcon(GuiGraphics graphics, SkillScreenPayload.Entry entry, int x, int y, int size,
                             boolean dim) {
        var texture = SkillIcons.INSTANCE.texture(entry.id());
        if (texture == null) {
            glyph(graphics, SkillFigures.lever(entry), entry.passive(), x, y, size, dim);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float shade = dim ? 0.45F : 1.0F;
        graphics.setColor(shade, shade, shade, 1.0F);
        try {
            graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    /** Fallback tile in the lever's colour, with a two-letter glyph. */
    protected void glyph(GuiGraphics graphics, @Nullable LeverType lever, boolean passive, int x, int y, int size,
                         boolean dim) {
        int colour = passive ? CodexStyle.PASSIVE : CodexStyle.lever(lever);
        if (dim) {
            colour = 0xFF000000 | blend(colour & 0xFFFFFF, CodexStyle.SLOT & 0xFFFFFF);
        }
        graphics.fill(x, y, x + size, y + size, colour);
        graphics.fill(x, y, x + size, y + 1, 0x30FFFFFF);
        Component mark = Component.translatable(ProgressionTab.SKILLS.key("glyph_" + leverKey(lever, passive)));
        graphics.drawString(font, mark, x + (size - font.width(mark)) / 2 + 1, y + (size - 8) / 2 + 1,
                dim ? CodexStyle.WELL_LIGHT : CodexStyle.ON_TAG, false);
    }

    protected static String leverKey(@Nullable LeverType lever, boolean passive) {
        return passive || lever == null ? "passive" : lever.key();
    }

    private static int blend(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return (r << 16) | (g << 8) | bl;
    }

    /** Nothing here pauses a server, and there is nothing on it to lose by closing. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
