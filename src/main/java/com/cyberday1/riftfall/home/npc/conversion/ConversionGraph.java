package com.cyberday1.riftfall.home.npc.conversion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;

/**
 * Transaction rule 5: no chain of resident conversions may multiply a material. Each input becomes an edge
 * input -> result weighted by result count / input count; other inputs, catalysts and shards are treated as free,
 * so a pass is conservative. A cycle whose ratios multiply past 1 is a gain (Bellman-Ford on -log ratios).
 * A tag whose members are unknown is assumed to contain every result.
 */
public final class ConversionGraph {
    private static final double EPSILON = 1e-9;

    public record Edge(String from, String to, double ratio, String via) {}

    /** Members of an item tag, or null when they cannot be known here. */
    @FunctionalInterface public interface TagMembers { Set<ResourceLocation> of(TagKey<Item> tag); }

    private final List<Edge> edges = new ArrayList<>();
    private final Set<String> nodes = new LinkedHashSet<>();

    public static ConversionGraph of(Map<ResourceLocation, ResidentConversionRecipe> recipes, TagMembers members) {
        var graph = new ConversionGraph();
        var results = new LinkedHashSet<String>();
        var tags = new LinkedHashMap<String, TagKey<Item>>();
        var unknown = new LinkedHashSet<String>();
        for (var entry : recipes.entrySet()) {
            var recipe = entry.getValue();
            String result = BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString();
            results.add(result);
            for (int i = 0; i < recipe.inputs().size(); i++) {
                var input = recipe.inputs().get(i);
                double ratio = (double) recipe.result().getCount() / input.count();
                for (String source : sources(input.ingredient(), entry.getKey() + "/" + i, tags, unknown))
                    graph.edge(source, result, ratio, entry.getKey().toString());
            }
        }
        for (var tag : tags.entrySet()) {
            var known = members.of(tag.getValue());
            if (known == null) { unknown.add(tag.getKey()); continue; }
            for (var item : known) graph.edge(item.toString(), tag.getKey(), 1, "tag");
        }
        for (String node : unknown) for (String result : results) graph.edge(result, node, 1, "unknown members");
        return graph;
    }

    private static List<String> sources(Ingredient ingredient, String where, Map<String, TagKey<Item>> tags, Set<String> unknown) {
        if (ingredient.isCustom()) {
            String node = "?" + where;
            unknown.add(node);
            return List.of(node);
        }
        var out = new ArrayList<String>();
        for (var value : ingredient.getValues()) {
            if (value instanceof Ingredient.ItemValue item) out.add(BuiltInRegistries.ITEM.getKey(item.item().getItem()).toString());
            else if (value instanceof Ingredient.TagValue tag) {
                String node = "#" + tag.tag().location();
                tags.put(node, tag.tag());
                out.add(node);
            } else {
                String node = "?" + where;
                unknown.add(node);
                out.add(node);
            }
        }
        return out;
    }

    private void edge(String from, String to, double ratio, String via) {
        nodes.add(from); nodes.add(to);
        edges.add(new Edge(from, to, ratio, via));
    }

    /** A conversion that is not a recipe (a swap, salvage, a dissolve, a purity step), so one graph covers every service. */
    public ConversionGraph with(String from, String to, double ratio, String via) {
        edge(from, to, ratio, via);
        return this;
    }

    public List<Edge> edges() { return List.copyOf(edges); }

    /** A cycle that multiplies its material, or empty. */
    public Optional<List<Edge>> gainCycle() {
        var dist = new HashMap<String, Double>();
        var via = new HashMap<String, Edge>();
        for (String node : nodes) dist.put(node, 0.0);
        String touched = null;
        for (int pass = 0; pass < nodes.size(); pass++) {
            touched = null;
            for (var edge : edges) {
                double next = dist.get(edge.from()) - Math.log(edge.ratio());
                if (next < dist.get(edge.to()) - EPSILON) {
                    dist.put(edge.to(), next); via.put(edge.to(), edge); touched = edge.to();
                }
            }
            if (touched == null) return Optional.empty();
        }
        if (touched == null) return Optional.empty(); // No nodes at all.
        // Still relaxing after |V| passes: walk back |V| steps to land inside the cycle, then collect it.
        String node = touched;
        for (int i = 0; i < nodes.size(); i++) node = via.get(node).from();
        var cycle = new ArrayList<Edge>();
        String at = node;
        do { var edge = via.get(at); cycle.add(edge); at = edge.from(); } while (!at.equals(node));
        Collections.reverse(cycle);
        return Optional.of(cycle);
    }
}
