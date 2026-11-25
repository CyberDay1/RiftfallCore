package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.data.AttributionCredits;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** credits.json as styled paragraphs, and those paragraphs wrapped into lines for one width. */
final class CreditsRoll {
    private static final Logger LOGGER = LogUtils.getLogger();

    enum Style {
        TITLE(3.0F, 0xE8C872), SECTION(1.4F, 0xE8C872), BUILDER(1.2F, 0xF2F2F2), NAME(1.5F, 0xF2F2F2),
        LINE(1.0F, 0xE6E6E6), BUILDS(1.0F, 0xC4C9D1), NOTE(0.85F, 0xA9AEB6), END(1.4F, 0xE8C872);

        final float scale;
        final int rgb;

        Style(float scale, int rgb) {
            this.scale = scale;
            this.rgb = rgb;
        }
    }

    /** Space before, in base line pitches. */
    record Paragraph(Component text, Style style, float spaceBefore) {}

    /** Top is in plane units from the roll's first line. */
    record Line(FormattedCharSequence text, int width, Style style, float top) {}

    record Layout(List<Line> lines, int endIndex, float pitch) {
        Line end() { return lines.get(endIndex); }
    }

    private CreditsRoll() {}

    static List<Paragraph> load(ResourceManager resources) {
        var id = RiftfallCore.id(AttributionCredits.OUTPUT.substring("assets/riftfall/".length()));
        try (Reader reader = resources.openAsReader(id)) {
            return paragraphs(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.warn("Credits roll not loaded from {}: {}", id, e.toString());
            return List.of(new Paragraph(Component.translatable(AttributionCredits.KEY + "unavailable"), Style.LINE, 0),
                    new Paragraph(Component.translatable(AttributionCredits.KEY + "end"), Style.END, 6));
        }
    }

    static List<Paragraph> paragraphs(JsonObject json) {
        List<Paragraph> out = new ArrayList<>();
        float gap = 0;
        for (JsonElement element : json.getAsJsonArray("roll")) {
            JsonObject e = element.getAsJsonObject();
            String kind = e.get("kind").getAsString();
            switch (kind) {
                case "gap" -> gap += 2;
                case "builder" -> {
                    int pieces = e.get("pieces").getAsInt();
                    out.add(new Paragraph(Component.literal(e.get("name").getAsString()), Style.BUILDER, 1.2F + gap));
                    out.add(new Paragraph(Component.translatable(AttributionCredits.KEY + (pieces == 1 ? "piece" : "pieces"),
                            String.valueOf(pieces)), Style.NOTE, 0));
                    String builds = join(e.getAsJsonArray("builds"));
                    if (!builds.isEmpty()) out.add(new Paragraph(Component.literal(builds), Style.BUILDS, 0.25F));
                    if (e.has("notes")) {
                        for (JsonElement note : e.getAsJsonArray("notes")) {
                            out.add(new Paragraph(Component.literal(note.getAsString()), Style.NOTE, 0.3F));
                        }
                    }
                    gap = 0;
                }
                case "list" -> {
                    out.add(new Paragraph(Component.literal(join(e.getAsJsonArray("items"))), Style.BUILDS, 0.3F + gap));
                    gap = 0;
                }
                default -> {
                    Style style = switch (kind) {
                        case "title" -> Style.TITLE;
                        case "heading" -> Style.SECTION;
                        case "name" -> Style.NAME;
                        case "note" -> Style.NOTE;
                        case "end" -> Style.END;
                        default -> Style.LINE;
                    };
                    float before = switch (style) {
                        case SECTION -> 1.0F;
                        case END -> 8.0F;
                        case TITLE, NAME -> 0.2F;
                        default -> 0.3F;
                    };
                    out.add(new Paragraph(text(e), style, before + gap));
                    gap = 0;
                }
            }
        }
        return out;
    }

    /** Wraps every paragraph to the column; plane units, so a tilted roll is laid out exactly as a flat one. */
    static Layout layout(List<Paragraph> paragraphs, Font font, float column, float base) {
        float pitch = (font.lineHeight + 1) * base;
        List<Line> lines = new ArrayList<>();
        int end = -1;
        float top = 0;
        for (var paragraph : paragraphs) {
            float scale = base * paragraph.style().scale;
            top += paragraph.spaceBefore() * pitch;
            int wrap = Math.max(40, (int) (column / scale));
            List<FormattedCharSequence> wrapped = font.split(paragraph.text(), wrap);
            if (wrapped.isEmpty()) wrapped = List.of(FormattedCharSequence.EMPTY);
            if (paragraph.style() == Style.END && end < 0) end = lines.size();
            for (var text : wrapped) {
                lines.add(new Line(text, font.width(text), paragraph.style(), top));
                top += (font.lineHeight + 1) * scale;
            }
        }
        if (end < 0) {
            lines.add(new Line(FormattedCharSequence.EMPTY, 0, Style.END, top + 8 * pitch));
            end = lines.size() - 1;
        }
        return new Layout(lines, end, pitch);
    }

    private static Component text(JsonObject e) {
        if (e.has("key")) {
            JsonArray args = e.getAsJsonArray("args");
            if (args == null) return Component.translatable(e.get("key").getAsString());
            Object[] values = new Object[args.size()];
            for (int i = 0; i < values.length; i++) values[i] = args.get(i).getAsString();
            return Component.translatable(e.get("key").getAsString(), values);
        }
        return Component.literal(e.has("text") ? e.get("text").getAsString() : "");
    }

    private static String join(JsonArray items) {
        List<String> out = new ArrayList<>();
        if (items != null) items.forEach(item -> out.add(item.getAsString()));
        return String.join(", ", out);
    }
}
