package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.data.AttributionCredits;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The credits roll: credits.json receding up the screen over Riftfall's sky. Esc closes, Space runs it fast, the
 * wheel scrubs, T switches tilt and flat; it ends by resting on the closing line, then returns to the parent.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class CreditsScreen extends Screen {
    private static final String KEY = AttributionCredits.KEY;
    private static final float TILT_DEGREES = 52, EYE_DISTANCE = 1.2F;
    private static final float LINES_PER_SECOND = 1.25F, FAST = 8, SCRUB_LINES = 3;
    private static final float HOLD_SECONDS = 4, FADE_SECONDS = 1.5F;
    /** Screen-height fractions: full alpha below FADE_FULL, gone above FADE_GONE. */
    private static final float FADE_FULL = 0.34F, FADE_GONE = 0.12F;
    private static final float END_REST = 0.5F;
    private static final int BUTTON_X = 8, BUTTON_Y = 8, BUTTON_STEP = 24;

    private final Screen parent;
    private final List<CreditsRoll.Paragraph> paragraphs;
    private final CreditsBackdrop backdrop = new CreditsBackdrop();
    private final CrawlStrip strip = new CrawlStrip();
    private CreditsRoll.Layout layout;
    private CrawlPerspective view;
    private Boolean tiltChoice;
    private float scroll, scrollEnd, startY, held = -1, progress;
    private long lastFrame, hintUntil;

    private CreditsScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.paragraphs = CreditsRoll.load(Minecraft.getInstance().getResourceManager());
    }

    /** Title screen and pause menu, after every other mod's buttons, at the first free spot down the left edge. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void addButton(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen pause && pause.showsPauseMenu())) return;
        Minecraft client = Minecraft.getInstance();
        Component label = Component.translatable(KEY + "open");
        int width = Math.max(80, client.font.width(label) + 12);
        List<int[]> taken = new ArrayList<>();
        for (var listener : event.getListenersList()) {
            if (listener instanceof LayoutElement e) taken.add(new int[] {e.getX(), e.getY(), e.getWidth(), e.getHeight()});
        }
        int y = freeSlot(taken, BUTTON_X, BUTTON_Y, width, 20, BUTTON_STEP, screen.height / 2);
        event.addListener(Button.builder(label, b -> client.setScreen(new CreditsScreen(screen)))
                .bounds(BUTTON_X, y, width, 20).build());
    }

    /** The first top, stepping down, where the rectangle overlaps nothing taken; the start if none fits above limit. */
    static int freeSlot(List<int[]> taken, int x, int start, int w, int h, int step, int limit) {
        for (int top = start; top + h <= limit; top += step) {
            boolean clear = true;
            for (int[] r : taken) {
                if (x < r[0] + r[2] && r[0] < x + w && top < r[1] + r[3] && r[1] < top + h) clear = false;
            }
            if (clear) return top;
        }
        return start;
    }

    @Override
    protected void init() {
        boolean tilt = tiltChoice != null ? tiltChoice : minecraft.getWindow().getGuiScale() >= 2 && height >= 180;
        float base = tilt ? 1.4F : 1.0F;
        float column = Math.min(width * 0.8F, 360);
        layout = CreditsRoll.layout(paragraphs, font, column, base);
        view = tilt ? new CrawlPerspective(width / 2F, height / 2F, height, TILT_DEGREES, height * EYE_DISTANCE)
                : CrawlPerspective.flat(width / 2F, height);
        startY = layout.pitch() * 1.5F;
        scrollEnd = Math.max(0, layout.end().top() + startY - view.planeYAt(height * END_REST));
        scroll = progress * scrollEnd;
        lastFrame = Util.getNanos();
        if (hintUntil == 0) hintUntil = Util.getMillis() + 6000;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advance();
        if (minecraft.screen != this) return;
        super.render(graphics, mouseX, mouseY, partialTick);
        drawRoll(graphics);
        drawHint(graphics);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backdrop.draw(graphics, width, height);
        graphics.fillGradient(0, 0, width, height / 4, 0xE0000000, 0x00000000);
        graphics.fillGradient(0, height - height / 8, width, height, 0x00000000, 0x80000000);
    }

    private void advance() {
        long now = Util.getNanos();
        float dt = Math.min(0.1F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        if (held >= 0) {
            held += dt;
            if (held >= HOLD_SECONDS + FADE_SECONDS) onClose();
            return;
        }
        boolean fast = InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE);
        scroll = Math.min(scrollEnd, scroll + dt * LINES_PER_SECOND * layout.pitch() * (fast ? FAST : 1));
        if (scroll >= scrollEnd) held = 0;
        progress = scrollEnd > 0 ? scroll / scrollEnd : 1;
    }

    /**
     * Tilted, the lines go flat into the strip on its texel grid and the strip goes through the perspective. Text
     * drawn straight through it flickered: the text render type turns the depth test back on, and depth worked out
     * through the divide lands a rounding error off the backdrop's and the shadow's, so glyphs failed it at random.
     */
    private void drawRoll(GuiGraphics graphics) {
        if (!view.tilted()) {
            drawFlat(graphics);
            return;
        }
        double guiScale = minecraft.getWindow().getGuiScale();
        float pitch = layout.pitch();
        float top = view.planeYAt(height * FADE_GONE) - pitch * 4;
        float bottom = pitch * 4;
        if (!strip.prepare(Math.min(width * 0.8F, 360) / 2 + 4, top, bottom, (float) guiScale * 2)) return;
        float grid = strip.texelsPerUnit();
        float shift = (float) Math.floor(scroll * grid) / grid;
        float rest = scroll - shift;
        float fadeEnd = held > HOLD_SECONDS ? Math.max(0, 1 - (held - HOLD_SECONDS) / FADE_SECONDS) : 1;
        graphics.flush();
        strip.render(() -> {
            var pose = graphics.pose();
            pose.pushPose();
            pose.last().pose().identity();
            for (var line : layout.lines()) {
                float planeY = line.top() - shift + startY;
                if (planeY > bottom) break;
                float scale = pitch / (font.lineHeight + 1) * line.style().scale;
                if (planeY + (font.lineHeight + 1) * scale < top || line.width() == 0) continue;
                pose.pushPose();
                pose.translate(CrawlStrip.snap(-line.width() * scale / 2, grid), CrawlStrip.snap(planeY, grid), 0);
                pose.scale(scale, scale, 1);
                graphics.drawString(font, line.text(), 0, 0, 0xFF000000 | line.style().rgb, true);
                pose.popPose();
            }
            pose.popPose();
            graphics.flush();
        });
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(view.divide());
        RenderSystem.applyModelViewMatrix();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2F, view.anchorY(), 0);
        pose.mulPose(Axis.XP.rotationDegrees(view.tiltDegrees()));
        strip.draw(pose.last(), -rest, planeY -> fade(view.screenY((float) planeY)) * fadeEnd);
        pose.popPose();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Flat: the lines straight onto the screen, as before; no divide, so their depth matches the backdrop's. */
    private void drawFlat(GuiGraphics graphics) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2F, view.anchorY(), 0);
        float below = layout.pitch() * 4;
        float fadeEnd = held > HOLD_SECONDS ? Math.max(0, 1 - (held - HOLD_SECONDS) / FADE_SECONDS) : 1;
        for (int i = 0; i < layout.lines().size(); i++) {
            var line = layout.lines().get(i);
            float planeY = line.top() - scroll + startY;
            if (planeY > below) break;
            int color = textColor(fade(view.screenY(planeY)) * (i >= layout.endIndex() ? fadeEnd : 1), line.style().rgb);
            if (color == 0 || line.width() == 0) continue;
            float scale = layout.pitch() / (font.lineHeight + 1) * line.style().scale;
            pose.pushPose();
            pose.translate(0, planeY, 0);
            pose.scale(scale, scale, 1);
            pose.translate(-line.width() / 2F, 0, 0);
            graphics.drawString(font, line.text(), 0, 0, color, true);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Fades lines out as they near the top of the screen. */
    private float fade(float screenY) {
        return Math.clamp((screenY / height - FADE_GONE) / (FADE_FULL - FADE_GONE), 0F, 1F);
    }

    private void drawHint(GuiGraphics graphics) {
        int color = textColor(Math.min(1, (hintUntil - Util.getMillis()) / 1000F) * 160 / 255F, 0xC4C9D1);
        if (color == 0) return;
        Component hint = Component.translatable(KEY + "hint");
        int textWidth = font.width(hint);
        float scale = Math.min(0.75F, (width - 16F) / Math.max(1, textWidth));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2F, height - 4 - font.lineHeight * scale, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, hint, -textWidth / 2, 0, color, false);
        pose.popPose();
    }

    /** ARGB for Font, or 0 to skip: Font draws an alpha byte under 4 fully opaque (Font#adjustColor). */
    static int textColor(float alpha, int rgb) {
        int a = Math.round(Math.clamp(alpha, 0F, 1F) * 255);
        return a < 5 ? 0 : a << 24 | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T) {
            tiltChoice = !view.tilted();
            showHint();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (float) scrollY * SCRUB_LINES * layout.pitch(), 0F, scrollEnd);
        if (scroll < scrollEnd) held = -1;
        else if (held < 0) held = 0;
        progress = scrollEnd > 0 ? scroll / scrollEnd : 1;
        showHint();
        return true;
    }

    private void showHint() {
        hintUntil = Math.max(hintUntil, Util.getMillis() + 3000);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        backdrop.close();
        strip.close();
    }
}
