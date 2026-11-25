package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.ResidentIngredient;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import com.cyberday1.riftfall.home.npc.ResidentLang;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The shared resident page layout: a title line, the rank badge, up to six inputs in a 3x2 grid, the shard cost slot
 * beside them, an arrow, the output, and up to two footer lines (work order, requirement). Every resident category
 * extends this so the pages read alike.
 */
abstract class ResidentCategory<T> extends AbstractRecipeCategory<T> {
    static final int WIDTH = 150, HEIGHT = 84;
    static final int TEXT = 0xFF404040, BADGE = 0xFF7A4FA0, MUTED = 0xFF707070;
    static final int GRID_X = 1, GRID_Y = 23, COLUMNS = 3, MAX_INPUTS = 6, STEP = 18;
    static final int SHARD_X = GRID_X + COLUMNS * STEP + 4, SHARD_Y = GRID_Y, CATALYST_Y = GRID_Y + STEP;
    static final int ARROW_X = 82, ARROW_Y = 31, OUT_X = 120, OUT_Y = 32, FOOTER_Y = 64, LINE = 10;
    /** A page with a tier or level picker: the button row sits under the footer. */
    static final int TALL = HEIGHT + 14, BUTTON_Y = HEIGHT + 1, BUTTON_W = 56, BUTTON_H = 12;
    /** Two outputs stack as ordinary slots beside the arrow instead of the one large slot. */
    static final int OUT_PAIR_X = 116;

    ResidentCategory(RecipeType<T> type, NpcRole role, String service, IGuiHelper gui) { this(type, role, service, gui, HEIGHT); }

    ResidentCategory(RecipeType<T> type, NpcRole role, String service, IGuiHelper gui, int height) {
        super(type, Component.translatable(ViewerLang.CATEGORY, Component.translatable(ResidentLang.name(role)), Component.translatable(service)),
                gui.createDrawableIngredient(ResidentIngredients.TYPE, ResidentIngredient.of(role)), WIDTH, height);
    }

    /** A named input in grid cell {@code index}, so a picker can swap what it shows. */
    static IRecipeSlotBuilder input(IRecipeLayoutBuilder layout, int index, String name, List<ItemStack> stacks) {
        return layout.addSlot(RecipeIngredientRole.INPUT, GRID_X + (index % COLUMNS) * STEP, GRID_Y + (index / COLUMNS) * STEP)
                .setStandardSlotBackground().setSlotName(name).addItemStacks(stacks);
    }

    /** The shard slot, named so a picker can swap its count. */
    static void shards(IRecipeLayoutBuilder layout, String name, ItemStack shards) {
        layout.addInputSlot(SHARD_X, SHARD_Y).setStandardSlotBackground().setSlotName(name).addItemStack(shards)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(ViewerLang.SHARDS).withStyle(net.minecraft.ChatFormatting.GRAY)));
    }

    /** Output {@code index} of two, stacked beside the arrow. */
    static IRecipeSlotBuilder output(IRecipeLayoutBuilder layout, int index, String name, List<ItemStack> stacks) {
        return layout.addOutputSlot(OUT_PAIR_X, GRID_Y + index * STEP).setStandardSlotBackground().setSlotName(name).addItemStacks(stacks);
    }

    /** The large output slot, named so a picker can swap it. */
    static IRecipeSlotBuilder output(IRecipeLayoutBuilder layout, String name, List<ItemStack> stacks) {
        return layout.addOutputSlot(OUT_X, OUT_Y).setOutputSlotBackground().setSlotName(name).addItemStacks(stacks);
    }

    /** Swaps what a named slot shows (a picker moved); the slot's own stacks stay what JEI looks up by. */
    static void show(IRecipeExtrasBuilder extras, String name, List<ItemStack> stacks) {
        extras.getRecipeSlots().findSlotByName(name).ifPresent(slot -> {
            slot.clearDisplayOverrides();
            slot.createDisplayOverrides().addItemStacks(stacks);
        });
    }

    /** The tier or level picker under the footer; {@code apply} re-shows the slots for the picked value. */
    static void picker(IRecipeExtrasBuilder extras, List<Integer> values, int initial, String label, String hint, IntConsumer apply) {
        Consumer<Integer> changed = apply::accept;
        extras.addCycleButton(values, initial, BUTTON_W, BUTTON_H, changed)
                .setMessage(value -> Component.translatable(label, value))
                .setTooltip(Component.translatable(hint))
                .setPosition(0, BUTTON_Y);
    }

    /** The picker's starting value: 10 when offered, else the first. */
    static int initial(List<Integer> values) { return values.contains(10) ? 10 : values.getFirst(); }

    /** Inputs fill the grid left to right; a list cycles (a tag's members). Extra inputs past six are dropped. */
    static void inputs(IRecipeLayoutBuilder layout, List<List<ItemStack>> inputs) {
        for (int i = 0; i < Math.min(inputs.size(), MAX_INPUTS); i++)
            layout.addInputSlot(GRID_X + (i % COLUMNS) * STEP, GRID_Y + (i / COLUMNS) * STEP).setStandardSlotBackground().addItemStacks(inputs.get(i));
    }

    /** The shard cost as an ordinary input slot, so a Rift Shard lookup lists every page that costs shards. */
    static void shards(IRecipeLayoutBuilder layout, ItemStack shards) {
        if (shards.isEmpty()) return;
        layout.addInputSlot(SHARD_X, SHARD_Y).setStandardSlotBackground().addItemStack(shards)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(ViewerLang.SHARDS).withStyle(net.minecraft.ChatFormatting.GRAY)));
    }

    static void output(IRecipeLayoutBuilder layout, ItemStack result) {
        layout.addOutputSlot(OUT_X, OUT_Y).setOutputSlotBackground().addItemStack(result);
    }

    static void title(IRecipeExtrasBuilder extras, Component title) {
        text(extras, title, 0, TEXT);
    }

    static void badge(IRecipeExtrasBuilder extras, Component badge) {
        text(extras, badge, LINE, BADGE);
    }

    static void arrow(IRecipeExtrasBuilder extras) {
        extras.addRecipeArrowWidget().setPosition(ARROW_X, ARROW_Y);
    }

    static void footer(IRecipeExtrasBuilder extras, List<Component> lines) { footer(extras, lines.toArray(Component[]::new)); }

    /** A footer line whose hover lists more, such as every refusal behind a short summary. */
    static void footer(IRecipeExtrasBuilder extras, int line, Component text, List<Component> hover) {
        var widget = extras.addText(List.<FormattedText>of(text), WIDTH, LINE).setPosition(0, FOOTER_Y + line * LINE).setColor(MUTED);
        if (!hover.isEmpty()) widget.setTooltip(List.<FormattedText>copyOf(hover));
    }

    /** Up to two lines under the slots: the work order, then a requirement or variant note. */
    static void footer(IRecipeExtrasBuilder extras, Component... lines) {
        for (int i = 0; i < Math.min(lines.length, 2); i++)
            text(extras, lines[i], FOOTER_Y + i * LINE, MUTED);
    }

    private static void text(IRecipeExtrasBuilder extras, Component line, int y, int color) {
        extras.addText(List.<FormattedText>of(line), WIDTH, LINE).setPosition(0, y).setColor(color);
    }
}
