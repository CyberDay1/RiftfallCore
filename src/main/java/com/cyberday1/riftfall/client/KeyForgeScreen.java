package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.forge.KeyForgeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The key forge's screen: a slot of shards, a number, and the key that buys.
 *
 * Stellar presentation over the existing server-owned forge and slot coordinates.
 *
 * <h2>The tier box</h2>
 * An {@link EditBox} that only accepts digits, reporting to the server through
 * {@code clickMenuButton}. Two vanilla behaviours have to be turned off around it: a container
 * screen closes on the inventory key, which would make the box impossible to type a {@code 3} into
 * if the player had rebound it, and it swallows arrow keys.
 */
public class KeyForgeScreen extends AbstractContainerScreen<KeyForgeMenu> {

    private static final Component TIER_LABEL =
            Component.translatable("container." + RiftfallCore.MODID + ".key_forge.tier");

    private static final String COST_KEY = "container." + RiftfallCore.MODID + ".key_forge.cost";

    /** Shared light ink against the stellar surface. */
    private static final int LABEL = StellarWorkshop.INK;

    /** A readable warm warning on the dark surface. */
    private static final int REFUSED = StellarWorkshop.ERROR;

    /** How many digits the box takes. Long enough for any tier anyone will forge, short enough to fit. */
    private static final int MAX_DIGITS = 6;

    private EditBox tierBox;

    /**
     * The last tier the server was told, which starts as the tier it starts holding.
     *
     * <p>
     * Tracked here rather than read back off the menu because the menu's copy of it is a
     * server-side field: {@code handleInventoryButtonClick} only sends the packet, so the client's
     * menu never runs {@code clickMenuButton} and its tier sits at {@link KeyForgeMenu#MIN_TIER}
     * forever. Deduplicating against that number would suppress a genuine change back down to
     * tier 1 &mdash; the box would read 1 while the forge still made whatever was typed before it.
     */
    private int sentTier = KeyForgeMenu.MIN_TIER;

    public KeyForgeScreen(KeyForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        tierBox = new EditBox(font, leftPos + 70, topPos + 21, 36, 10, TIER_LABEL);
        tierBox.setBordered(false);
        tierBox.setTextColor(StellarWorkshop.INK);
        tierBox.setMaxLength(MAX_DIGITS);
        tierBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        tierBox.setValue(String.valueOf(sentTier));
        tierBox.setResponder(this::tierTyped);
        addRenderableWidget(tierBox);
    }

    /**
     * Keeps a half-typed number across a window resize, the way the anvil keeps a half-typed name.
     *
     * <p>
     * {@link #init} rebuilds the box from {@link #sentTier}, which is the right value to open with
     * and the wrong one to interrupt somebody with.
     */
    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String typed = tierBox.getValue();
        super.resize(minecraft, width, height);
        tierBox.setValue(typed);
    }

    /**
     * Sends the typed tier, and says nothing at all about a box that is mid-edit.
     *
     * <p>
     * An empty box and a leading zero are both states a player passes through on the way to a
     * number rather than numbers they meant, so neither is forwarded; the server keeps the last
     * tier it was told until a usable one arrives. Anything longer than the box can hold cannot
     * overflow {@code parseInt} because {@link #MAX_DIGITS} is well inside an int.
     */
    private void tierTyped(String text) {
        if (text.isEmpty()) {
            return;
        }
        int typed = Integer.parseInt(text);
        if (typed < KeyForgeMenu.MIN_TIER || typed == sentTier || minecraft == null
                || minecraft.gameMode == null) {
            return;
        }
        sentTier = typed;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, typed);
    }

    /**
     * Lets the box keep the keystrokes it can use, and closes the screen on escape regardless.
     *
     * <p>
     * Without this a container screen closes on the inventory key, so typing a tier would shut the
     * forge the moment the digit shared a binding with it. This is vanilla's anvil name field
     * verbatim: escape is dealt with first, and everything else goes to the box before the screen.
     */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        return tierBox.keyPressed(key, scanCode, modifiers) || tierBox.canConsumeInput()
                || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 68, 160, 12, mouseX, mouseY)) typedTier().flatMap(t -> KeyForgeMenu.refusal(t, menu.level()))
                .ifPresent(why -> graphics.renderTooltip(font, why, mouseX, mouseY));
        if (isHovering(8, 54, 160, 12, mouseX, mouseY)) price().ifPresent(cost ->
                graphics.renderTooltip(font, Component.translatable(COST_KEY, cost), mouseX, mouseY));
    }

    /**
     * The title, the word beside the box, and what the typed tier costs.
     *
     * <p>
     * The price is worked out here rather than sent, because {@link KeyForgeMenu#cost} is pure
     * arithmetic on a number the client already has &mdash; and it is read off the box rather than
     * off the menu, so it answers for what is typed rather than for what the server last accepted.
     * A box that does not hold a usable tier gets no price rather than a wrong one.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 25, 5, StellarWorkshop.GOLD, false);
        graphics.drawString(font, TIER_LABEL, 66 - font.width(TIER_LABEL), 21, LABEL, false);
        price().ifPresent(cost -> graphics.drawString(font, fit(Component.translatable(COST_KEY, cost)),
                8, 56, StellarWorkshop.GOLD, false));
        var reason = typedTier().flatMap(typed -> KeyForgeMenu.refusal(typed, menu.level()));
        graphics.drawString(font, fit(reason.orElse(playerInventoryTitle)),
                8, 70, reason.isPresent() ? REFUSED : StellarWorkshop.MUTED, false);
    }

    private Component fit(Component text) {
        return font.width(text) <= 160 ? text : Component.literal(font.plainSubstrByWidth(text.getString(), 148) + "...");
    }

    private Optional<Integer> typedTier() {
        String text = tierBox.getValue();
        return text.isEmpty() ? Optional.empty()
                : Optional.of(Integer.parseInt(text));
    }

    private OptionalInt price() {
        String text = tierBox.getValue();
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        int typed = Integer.parseInt(text);
        return typed < KeyForgeMenu.MIN_TIER ? OptionalInt.empty()
                : OptionalInt.of(KeyForgeMenu.cost(typed));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        StellarWorkshop.surface(graphics, leftPos, topPos, imageWidth, imageHeight);
        // A key identifies this service; reserve astronomical stamps for the rune workshop.
        graphics.renderOutline(leftPos + 9, topPos + 5, 5, 5, 0xFFDEC68D);
        graphics.fill(leftPos + 13, topPos + 9, leftPos + 19, topPos + 10, 0xFFDEC68D);
        graphics.fill(leftPos + 16, topPos + 10, leftPos + 17, topPos + 12, 0xFFDEC68D);
        graphics.fill(leftPos + 18, topPos + 10, leftPos + 19, topPos + 12, 0xFFDEC68D);
        StellarWorkshop.star(graphics, leftPos + 160, topPos + 8, 0xFFDEC68D);
        StellarWorkshop.slab(graphics, leftPos + 67, topPos + 17, 42, 17, 0xFF090D17);
        if (tierBox.isFocused()) graphics.renderOutline(leftPos + 67, topPos + 17, 42, 17, 0xFFDEC68D);
        for (var slot : menu.slots) StellarWorkshop.well(graphics, leftPos + slot.x, topPos + slot.y, slot.index < 2);
        graphics.drawString(font, ">", leftPos + 89, topPos + 39, StellarWorkshop.GOLD, false);
    }
}
