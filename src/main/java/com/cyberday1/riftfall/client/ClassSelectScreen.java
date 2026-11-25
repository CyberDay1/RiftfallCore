package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.ClassRequestPayload;
import com.cyberday1.riftfall.network.ClassSelectPayload;
import com.cyberday1.riftfall.skill.ClassIcons;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillCommand;
import com.cyberday1.riftfall.skill.SkillNames;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.cyberday1.riftfall.client.ClassSelectLayout.BAR_MAX;
import static com.cyberday1.riftfall.client.ClassSelectLayout.BAR_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.BLURB_LINES;
import static com.cyberday1.riftfall.client.ClassSelectLayout.BLURB_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.BUTTON_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.FOOTER_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.HEAD_ICON;
import static com.cyberday1.riftfall.client.ClassSelectLayout.HEAD_ICON_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.HEAD_NAME_X;
import static com.cyberday1.riftfall.client.ClassSelectLayout.ICON;
import static com.cyberday1.riftfall.client.ClassSelectLayout.L;
import static com.cyberday1.riftfall.client.ClassSelectLayout.LINE;
import static com.cyberday1.riftfall.client.ClassSelectLayout.R;
import static com.cyberday1.riftfall.client.ClassSelectLayout.ROW;
import static com.cyberday1.riftfall.client.ClassSelectLayout.RULE2_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.RULE_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.SKILLS_HEAD_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.SKILLS_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.STATS_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.STAT_COLUMN;
import static com.cyberday1.riftfall.client.ClassSelectLayout.STATUS_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.SUB_Y;
import static com.cyberday1.riftfall.client.ClassSelectLayout.T;
import static com.cyberday1.riftfall.client.ClassSelectLayout.W;
import static com.cyberday1.riftfall.client.ClassSelectLayout.WEAPON_Y;

/**
 * The class spread. Left page: the thirteen classes, each with its item ({@link ClassIcons}) on its
 * main stat's colour, and its main and second stat. Right page: the selected class's role, weapon, stat block, base skills and five
 * ascendancies, then Choose, which asks Confirm before it sends. The server decides everything: the
 * list, each class's answer, and the choice itself ({@code ClassSelectService}).
 */
public class ClassSelectScreen extends ProgressionScreen {

    private @Nullable String selected;
    private boolean confirming;
    /** The class this screen sent, so its answer is shown once it comes back. */
    private @Nullable String sent;
    /** Each class's item, resolved when the screen opens; a class with none keeps its glyph. */
    private final Map<String, ItemStack> icons = new HashMap<>();
    /** Each class's flat sprite, drawn in place of its item when the resource exists. */
    private final Map<String, ResourceLocation> sprites = new HashMap<>();

    public ClassSelectScreen() {
        super(ProgressionTab.CLASSES);
    }

    @Override
    protected void init() {
        super.init();
        icons.clear();
        sprites.clear();
        ClassIcons.Table table = iconTable();
        table.items().forEach((id, items) -> ClassIcons.pick(items, BuiltInRegistries.ITEM::containsKey)
                .ifPresent(item -> icons.put(id, new ItemStack(BuiltInRegistries.ITEM.get(item)))));
        table.textures().forEach((id, texture) -> {
            if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
                sprites.put(id, texture);
            }
        });
        PacketDistributor.sendToServer(ClassRequestPayload.view());
    }

    /** The resource packs' table (the jar's unless a pack replaces it). */
    private static ClassIcons.Table iconTable() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ClassIcons.LOCATION);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                return ClassIcons.parseTable(reader);
            } catch (IOException | RuntimeException e) {
                // A broken pack override falls back to the jar's table.
            }
        }
        return ClassIcons.shippedTable();
    }

    private MutableComponent line(String key, Object... args) {
        return Component.translatable(tab.key(key), args);
    }

    private static String name(String id) {
        return CodexNames.skillClass(id);
    }

    private static Component classLine(String id, String part) {
        return Component.translatable(ClassSelectLayout.classKey(id, part));
    }

    private @Nullable ClassSelectPayload.Option option(ClassSelectPayload view) {
        if (view.options().isEmpty()) {
            return null;
        }
        if (selected == null) {
            selected = view.hasClass() ? view.current() : view.options().get(0).id();
        }
        for (ClassSelectPayload.Option o : view.options()) {
            if (o.id().equals(selected)) {
                return o;
            }
        }
        return view.options().get(0);
    }

    @Override
    protected void renderPages(GuiGraphics graphics, float partialTick) {
        ClassSelectPayload view = RiftClassSelect.latest();
        ClassSelectPayload.Option shown = option(view);
        listPage(graphics, view);
        if (shown == null) {
            text(graphics, line("loading"), R, T, CodexStyle.MUTED);
            return;
        }
        detailPage(graphics, view, shown);
    }

    // -- left page: the classes ----------------------------------------------------------------------

    private void listPage(GuiGraphics graphics, ClassSelectPayload view) {
        text(graphics, bold(line(view.hasClass() ? "heading_yours" : "heading_choose")), L, T, CodexStyle.ACCENT);
        Component sub = view.hasClass() ? line("sub_chosen", SkillCommand.classWithArticle(view.current()))
                : line("sub_choose");
        text(graphics, fit(sub, W), L, SUB_Y, CodexStyle.MUTED);
        dotted(graphics, L, L + W, RULE_Y, CodexStyle.DOTS);
        List<ClassSelectPayload.Option> options = view.options();
        int shown = Math.min(options.size(), ClassSelectLayout.rowsThatFit());
        for (int i = 0; i < shown; i++) {
            row(graphics, view, options.get(i), ClassSelectLayout.rowY(i));
        }
        Component footer = view.inRift() ? line("in_rift") : line(view.hasClass() ? "change_hint" : "pick_hint");
        text(graphics, fit(footer, W), L, FOOTER_Y, view.inRift() ? CodexStyle.WARN : CodexStyle.MUTED);
    }

    private void row(GuiGraphics graphics, ClassSelectPayload view, ClassSelectPayload.Option o, int y) {
        boolean isSelected = o.id().equals(selected);
        boolean hover = hot(L, y, L + W, y + ROW - 1, button -> {
            if (button == PRIMARY) {
                selected = o.id();
                confirming = false;
                return true;
            }
            return false;
        });
        if (isSelected || hover) {
            graphics.fill(L, y, L + W, y + ROW - 1, isSelected ? CodexStyle.SELECTED : CodexStyle.WELL_LIGHT);
        }
        icon(graphics, o, L, y, ICON);
        int main = ClassSelectLayout.main(o.stats());
        int second = ClassSelectLayout.second(o.stats());
        Component mainTag = primaryTag(main);
        Component secondTag = primaryTag(second);
        int tagsWidth = main < 0 || second < 0 ? 0 : font.width(mainTag) + font.width(secondTag) + 7;
        boolean yours = o.id().equals(view.current());
        Component label = Component.literal(name(o.id()));
        int nameX = L + ICON + 4;
        text(graphics, fit(isSelected || yours ? bold(label) : label, L + W - tagsWidth - 3 - nameX), nameX, y + 2,
                yours ? CodexStyle.ACCENT : CodexStyle.INK);
        if (tagsWidth > 0) {
            int x = L + W - tagsWidth;
            x = tag(graphics, mainTag, x, y + 1, CodexStyle.primary(StatBlock.Primary.values()[main])) + 1;
            tag(graphics, secondTag, x, y + 1, CodexStyle.primary(StatBlock.Primary.values()[second]));
        }
    }

    private static Component primaryTag(int index) {
        if (index < 0 || index >= StatBlock.Primary.values().length) {
            return Component.empty();
        }
        return Component.translatable(ProgressionTab.CHARACTER.key("tag_" + StatBlock.Primary.values()[index].key()));
    }

    private static int mainColour(ClassSelectPayload.Option o) {
        int main = ClassSelectLayout.main(o.stats());
        return main < 0 || main >= StatBlock.Primary.values().length
                ? CodexStyle.PASSIVE : CodexStyle.primary(StatBlock.Primary.values()[main]);
    }

    /** The class's item on its main stat's colour, or the two-letter glyph if no item resolved. */
    private void icon(GuiGraphics graphics, ClassSelectPayload.Option o, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, mainColour(o));
        graphics.fill(x, y, x + size, y + 1, 0x30FFFFFF);
        ResourceLocation sprite = sprites.get(o.id());
        if (sprite != null) {
            crisp(graphics, x, y, size, () -> graphics.blit(sprite, 0, 0, 0.0F, 0.0F,
                    ClassSelectLayout.SPRITE, ClassSelectLayout.SPRITE, ClassSelectLayout.SPRITE, ClassSelectLayout.SPRITE));
            return;
        }
        ItemStack stack = icons.getOrDefault(o.id(), ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            crisp(graphics, x, y, size, () -> graphics.renderItem(stack, 0, 0));
            return;
        }
        Component glyph = classLine(o.id(), "glyph");
        if (font.width(glyph) > size - 2) {
            glyph = Component.literal(glyph.getString().substring(0, 1));
        }
        graphics.drawString(font, glyph, x + (size - font.width(glyph) + 1) / 2, y + (size - 8) / 2 + 1,
                CodexStyle.ON_TAG, false);
    }

    /**
     * Runs {@code draw} (a 16x16 item or sprite at 0,0) centred in a {@code size} box, snapped to whole screen
     * pixels and at a whole multiple of its 16 texels ({@link ClassSelectLayout#crispPixels}), so it stays sharp
     * at any GUI scale and under the book's own shrink.
     */
    private void crisp(GuiGraphics graphics, int x, int y, int size, Runnable draw) {
        Matrix4f pose = graphics.pose().last().pose();
        Vector3f from = pose.transformPosition(x, y, 0.0F, new Vector3f());
        Vector3f to = pose.transformPosition(x + size, y + size, 0.0F, new Vector3f());
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        int x0 = (int) Math.round(from.x * gui);
        int y0 = (int) Math.round(from.y * gui);
        int box = (int) Math.min(Math.round(to.x * gui) - x0, Math.round(to.y * gui) - y0);
        int pixels = ClassSelectLayout.crispPixels(box);
        graphics.pose().pushPose();
        graphics.pose().setIdentity();
        graphics.pose().translate(0.0F, 0.0F, from.z);
        graphics.pose().scale((float) (1.0 / gui), (float) (1.0 / gui), 1.0F);
        graphics.pose().translate(x0 + (box - pixels) / 2, y0 + (box - pixels) / 2, 0.0F);
        graphics.pose().scale(pixels / (float) ClassSelectLayout.SPRITE, pixels / (float) ClassSelectLayout.SPRITE, 1.0F);
        draw.run();
        graphics.pose().popPose();
    }

    // -- right page: one class -----------------------------------------------------------------------

    private void detailPage(GuiGraphics graphics, ClassSelectPayload view, ClassSelectPayload.Option o) {
        Component role = classLine(o.id(), "role");
        int roleWidth = font.width(role) + 3;
        tag(graphics, role, R + W - roleWidth, T, mainColour(o));
        icon(graphics, o, R, HEAD_ICON_Y, HEAD_ICON);
        text(graphics, fit(bold(Component.literal(name(o.id()))), R + W - roleWidth - 4 - HEAD_NAME_X), HEAD_NAME_X,
                T + 1, CodexStyle.ACCENT);
        wrapped(graphics, classLine(o.id(), "blurb"), R, BLURB_Y, W, LINE, BLURB_LINES, CodexStyle.INK);
        int x = text(graphics, line("weapon"), R, WEAPON_Y, CodexStyle.MUTED) + 4;
        text(graphics, fit(classLine(o.id(), "weapon"), R + W - x), x, WEAPON_Y, CodexStyle.INK);
        dotted(graphics, R, R + W, RULE2_Y, CodexStyle.DOTS);

        stats(graphics, o);

        int headEnd = text(graphics, line("skills"), R, SKILLS_HEAD_Y, CodexStyle.ACCENT);
        textRight(graphics, fit(line("skills_count", o.actives().size(), o.passives()), R + W - headEnd - 6), R + W,
                SKILLS_HEAD_Y, CodexStyle.MUTED);
        List<String> skills = o.actives().stream().map(id -> SkillNames.of(id).getString()).toList();
        List<String> ascendancies = o.ascendancies().stream().map(CodexNames::ascendancy).toList();
        ClassSelectLayout.Grid[] grids = ClassSelectLayout.nameGrids(widths(skills), widths(ascendancies));
        names(graphics, skills, grids[0], SKILLS_Y);
        int head = ClassSelectLayout.ascendancyHeadY(grids[0].height());
        text(graphics, line("ascendancies", SkillBook.ASCENSION_LEVEL), R, head, CodexStyle.ACCENT);
        names(graphics, ascendancies, grids[1], head + ClassSelectLayout.HEAD_LINE);
        footer(graphics, view, o);
    }

    private void stats(GuiGraphics graphics, ClassSelectPayload.Option o) {
        StatBlock.Primary[] primaries = StatBlock.Primary.values();
        int main = ClassSelectLayout.main(o.stats());
        int top = main < 0 ? 0 : o.stats().get(main);
        for (int i = 0; i < primaries.length && i < o.stats().size(); i++) {
            int x = R + i * STAT_COLUMN;
            int centre = x + STAT_COLUMN / 2;
            int value = o.stats().get(i);
            Component tag = Component.translatable(ProgressionTab.CHARACTER.key("tag_" + primaries[i].key()));
            textCenter(graphics, tag, centre, STATS_Y, CodexStyle.MUTED);
            textCenter(graphics, bold(Component.literal(Integer.toString(value))), centre, STATS_Y + 10,
                    CodexStyle.INK);
            graphics.fill(x + 3, BAR_Y, x + 3 + BAR_MAX, BAR_Y + 3, CodexStyle.TROUGH);
            graphics.fill(x + 3, BAR_Y, x + 3 + ClassSelectLayout.bar(value, top), BAR_Y + 3,
                    CodexStyle.primary(primaries[i]));
        }
    }

    /**
     * Two columns ({@link ClassSelectLayout#grid}): down then across, or long names paired with short
     * ones when two long ones cannot share a row. A list longer than its rows ends with "+N more".
     */
    private void names(GuiGraphics graphics, List<String> names, ClassSelectLayout.Grid grid, int y) {
        if (names.isEmpty()) {
            text(graphics, line("none_yet"), R, y, CodexStyle.MUTED);
            return;
        }
        int second = grid.second();
        for (int r = 0; r < grid.rows().size(); r++) {
            ClassSelectLayout.GridRow row = grid.rows().get(r);
            int rowY = y + r * LINE;
            int room = row.right() < 0 ? W : second - ClassSelectLayout.COLUMN_GAP;
            text(graphics, fit(Component.literal(names.get(row.left())), room), R, rowY, CodexStyle.INK);
            if (row.right() >= 0) {
                text(graphics, fit(Component.literal(names.get(row.right())), W - second), R + second, rowY,
                        CodexStyle.INK);
            }
        }
        if (grid.hidden() > 0) {
            text(graphics, line("more", grid.hidden()), R, y + grid.rows().size() * LINE, CodexStyle.MUTED);
        }
    }

    private int[] widths(List<String> names) {
        return names.stream().mapToInt(font::width).toArray();
    }

    private void footer(GuiGraphics graphics, ClassSelectPayload view, ClassSelectPayload.Option o) {
        String className = name(o.id());
        if (view.hasClass()) {
            boolean justChosen = view.current().equals(sent) && SkillBook.Choose.OK.name().equals(view.result());
            if (justChosen) {
                text(graphics, fit(line("now_a", SkillCommand.classWithArticle(view.current())), W), R, STATUS_Y,
                        CodexStyle.GOOD);
            } else if (!o.id().equals(view.current())) {
                text(graphics, fit(line("class_set", name(view.current())), W), R, STATUS_Y, CodexStyle.MUTED);
            }
            if (justChosen || o.id().equals(view.current())) {
                button(graphics, R, BUTTON_Y, W, line("open_skills"), true, () -> switchTo(ProgressionTab.SKILLS));
            } else {
                button(graphics, R, BUTTON_Y, W, line("choose", className), false, () -> {
                });
            }
            return;
        }
        boolean allowed = SkillBook.Choose.OK.name().equals(o.status());
        if (confirming && allowed) {
            text(graphics, fit(line("confirm_q", className), W), R, STATUS_Y, CodexStyle.WARN);
            int half = (W - 4) / 2;
            button(graphics, R, BUTTON_Y, half, line("confirm"), true, () -> {
                sent = o.id();
                confirming = false;
                PacketDistributor.sendToServer(ClassRequestPayload.choose(o.id()));
            });
            button(graphics, R + half + 4, BUTTON_Y, W - half - 4, line("back"), true, () -> confirming = false);
            return;
        }
        if (sent != null && !view.result().isEmpty() && !SkillBook.Choose.OK.name().equals(view.result())) {
            text(graphics, fit(line("result_" + lower(view.result())), W), R, STATUS_Y, CodexStyle.BAD);
        } else if (!allowed) {
            text(graphics, fit(line("status_" + lower(o.status())), W), R, STATUS_Y, CodexStyle.WARN);
        }
        button(graphics, R, BUTTON_Y, W, line("choose", className), allowed, () -> confirming = true);
    }

    private static String lower(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && confirming) {
            confirming = false;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
