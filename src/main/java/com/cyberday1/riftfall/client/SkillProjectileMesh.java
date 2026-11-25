package com.cyberday1.riftfall.client;

/** Bounded, untextured energy geometry. Physical missiles keep their authored item models. */
public final class SkillProjectileMesh {
    public enum Shape { WAVE, BOLT, LIGHTNING, ORB }
    public record Style(Shape shape, int color) { }
    private SkillProjectileMesh() { }

    public static Style style(String spell, String model) {
        if (!spell.startsWith("riftfall:")) return null;
        if (spell.equals("riftfall:arcanist_storm_orb")) return new Style(Shape.ORB, 0x73CFFF);
        if (spell.equals("riftfall:trickster_chaos_orb")) return new Style(Shape.ORB, 0xCD9AFF);
        if (model == null) return null;
        if (model.equals("riftfall:spell_projectile/lightning_shard")) return new Style(Shape.LIGHTNING, 0x73CFFF);
        if (model.equals("riftfall:spell_projectile/shadow_bolt")) return new Style(Shape.BOLT, 0xAD65E8);
        if (!model.startsWith("riftfall:spell_projectile/wave_")) return null;
        Integer color = switch (model.substring(model.lastIndexOf('_') + 1)) {
            case "arcane" -> 0x8B44FF;
            case "holy" -> 0xFFE9A0;
            case "soul" -> 0x56D8DF;
            case "frost" -> 0x9EE8FF;
            case "slash" -> 0xDAE8F4;
            case "wind" -> 0xC8F4FF;
            case "blood" -> 0xC83E58;
            case "poison" -> 0x85D64A;
            case "void" -> 0xAD65E8;
            default -> null;
        };
        return color == null ? null : new Style(Shape.WAVE, color);
    }

    public static void emit(Style style, float age, float opacity, ArcaneWaveMesh.Vertex out) {
        opacity = Math.clamp(opacity, 0, 1);
        if (style.shape == Shape.WAVE) {
            ArcaneWaveMesh.emit(age, opacity, (x, y, z, r, g, b, a) ->
                    color(out, x, y, z, style.color, (r - 139) / 102F * .86F, a));
        } else if (style.shape == Shape.ORB) {
            // A translucent spherical core with three revolving electrical/arcane strands.
            for (int lat = 0; lat < 12; lat++) for (int lon = 0; lon < 24; lon++) {
                sphere(lat, lon, age, opacity, style.color, out);
                sphere(lat + 1, lon, age, opacity, style.color, out);
                sphere(lat + 1, lon + 1, age, opacity, style.color, out);
                sphere(lat, lon + 1, age, opacity, style.color, out);
            }
            for (int strand = 0; strand < 3; strand++) for (int i = 0; i < 48; i++) for (int band = 0; band < 4; band++) {
                for (int corner = 0; corner < 4; corner++) {
                    float t = (i + (corner == 1 || corner == 2 ? 1 : 0)) / 48F;
                    float angle = t * (float) Math.PI * 1.75F + age * .08F + strand * 2.094F;
                    float z = (t - .5F) * .85F;
                    float radius = (float) Math.sqrt(Math.max(0, .23F - z * z));
                    float feather = -1 + (band + (corner >= 2 ? 1 : 0)) / 2F;
                    float side = feather * .045F;
                    color(out, (float) Math.cos(angle) * (radius + side),
                            (float) Math.sin(angle) * (radius + side), z, style.color, .85F,
                            Math.round(165 * opacity * (1 - feather * feather) * (float) Math.sin(t * Math.PI)));
                }
            }
        } else {
            // Crossed feathered ribbons remain readable from above and the side.
            for (int plane = 0; plane < 2; plane++) for (int i = 0; i < 32; i++) for (int band = 0; band < 6; band++) {
                bolt(i, band, plane, style, age, opacity, out);
                bolt(i + 1, band, plane, style, age, opacity, out);
                bolt(i + 1, band + 1, plane, style, age, opacity, out);
                bolt(i, band + 1, plane, style, age, opacity, out);
            }
        }
    }

    private static void sphere(int lat, int lon, float age, float opacity, int color, ArcaneWaveMesh.Vertex out) {
        double latitude = Math.PI * lat / 12, longitude = Math.PI * lon / 12;
        float radius = .38F + .015F * (float) Math.sin(age * .25F);
        color(out, radius * (float) (Math.sin(latitude) * Math.cos(longitude)),
                radius * (float) Math.cos(latitude), radius * (float) (Math.sin(latitude) * Math.sin(longitude)),
                color, .2F, Math.round(38 * opacity));
    }

    private static void bolt(int index, int band, int plane, Style style, float age, float opacity, ArcaneWaveMesh.Vertex out) {
        float t = index / 32F, v = -1 + band / 3F;
        float taper = (float) Math.sin(Math.PI * t), feather = 1 - v * v;
        float node = t * 7, blend = node - (int) node;
        float jag = style.shape == Shape.LIGHTNING
                ? (float) ((1 - blend) * Math.sin((int) node * 2.3 + age * .22)
                        + blend * Math.sin(((int) node + 1) * 2.3 + age * .22)) * .16F * taper : 0;
        float side = v * (style.shape == Shape.LIGHTNING ? .055F : .18F) * taper;
        color(out, jag + (plane == 0 ? side : 0), plane == 1 ? side : 0,
                -.95F + t * 2.1F, style.color, feather * .9F, Math.round(210 * opacity * feather * taper));
    }

    private static void color(ArcaneWaveMesh.Vertex out, float x, float y, float z, int rgb, float hot, int alpha) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        out.emit(x, y, z, Math.round(r + (255 - r) * hot), Math.round(g + (255 - g) * hot),
                Math.round(b + (255 - b) * hot), alpha);
    }
}
